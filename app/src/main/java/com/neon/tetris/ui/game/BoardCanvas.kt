package com.neon.tetris.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.neon.tetris.domain.BoardSpec
import com.neon.tetris.domain.GameSettings
import com.neon.tetris.domain.PowerUpType
import com.neon.tetris.game.ClearAnimation
import com.neon.tetris.game.ClearKind
import com.neon.tetris.game.GameState
import com.neon.tetris.game.Phase
import com.neon.tetris.game.Tetrominoes
import com.neon.tetris.ui.theme.Neon
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * The playfield. Drawing is one Canvas redrawn every frame; the engine only publishes discrete states,
 * so this composable owns the frame clock that drives smoothing, particles and the clear animations.
 */
@Composable
fun BoardCanvas(
    state: GameState,
    settings: GameSettings,
    fx: BoardFx,
    cell: Dp,
    onMove: (Int) -> Unit,
    onRotate: () -> Unit,
    onSoftDropStep: () -> Unit,
    onHardDrop: () -> Unit,
    onTapCell: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cellPx = with(LocalDensity.current) { cell.toPx() }
    val painter = remember(cellPx) { BlockPainter(cellPx) }
    val latest by rememberUpdatedState(state)
    var frame by remember { mutableLongStateOf(0L) }

    LaunchedEffect(fx) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dtMs = if (last == 0L) 16f else ((now - last) / 1_000_000f).coerceAtMost(50f)
                last = now
                fx.step(dtMs, latest)
                frame = now
            }
        }
    }

    val targeting = state.phase == Phase.TARGETING
    val playable = state.phase == Phase.FALLING
    val swipe = settings.swipeControls
    val moveCb by rememberUpdatedState(onMove)
    val rotateCb by rememberUpdatedState(onRotate)
    val softStepCb by rememberUpdatedState(onSoftDropStep)
    val hardCb by rememberUpdatedState(onHardDrop)
    val tapCb by rememberUpdatedState(onTapCell)

    Canvas(
        modifier = modifier
            .size(cell * BoardSpec.COLS, cell * BoardSpec.ROWS)
            .pointerInput(targeting, playable, swipe) {
                val cellSize = size.width / BoardSpec.COLS.toFloat()
                when {
                    targeting -> detectTapGestures { offset ->
                        val x = (offset.x / cellSize).toInt().coerceIn(0, BoardSpec.COLS - 1)
                        val y = (offset.y / cellSize).toInt().coerceIn(0, BoardSpec.ROWS - 1)
                        tapCb(x, y)
                    }
                    playable && swipe -> detectBoardSwipes(
                        cellSize = cellSize,
                        onMove = { moveCb(it) },
                        onSoftDrop = { softStepCb() },
                        onHardDrop = { hardCb() },
                        onRotate = { rotateCb() },
                    )
                    else -> Unit
                }
            },
    ) {
        drawBoard(latest, fx, painter, settings, frame)
    }
}

// ---------------------------------------------------------------------------------------------- gestures

/**
 * Tap = rotate. Drag sideways = move one column per cell dragged. Drag down = soft drop steps.
 * Fast flick down = hard drop. Fast flick up = rotate.
 */
private suspend fun PointerInputScope.detectBoardSwipes(
    cellSize: Float,
    onMove: (Int) -> Unit,
    onSoftDrop: () -> Unit,
    onHardDrop: () -> Unit,
    onRotate: () -> Unit,
) {
    val slop = viewConfiguration.touchSlop
    val flingDown = 1500.dp.toPx()
    val flingUp = 1200.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val tracker = VelocityTracker()
        tracker.addPosition(down.uptimeMillis, down.position)
        var totalX = 0f
        var totalY = 0f
        var accX = 0f
        var accY = 0f
        var dragging = false
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) {
                val elapsed = change.uptimeMillis - down.uptimeMillis
                if (!dragging) {
                    if (elapsed < 350L) onRotate()
                } else {
                    val v = tracker.calculateVelocity()
                    val vertical = abs(v.y) > abs(v.x) * 1.4f
                    if (vertical && v.y > flingDown && totalY > cellSize * 1.5f) onHardDrop()
                    else if (vertical && v.y < -flingUp && totalY < -cellSize) onRotate()
                }
                break
            }
            tracker.addPosition(change.uptimeMillis, change.position)
            val d = change.positionChange()
            totalX += d.x
            totalY += d.y
            if (!dragging && hypot(totalX, totalY) > slop) dragging = true
            if (dragging) {
                accX += d.x
                accY += d.y
                while (accX >= cellSize) { onMove(1); accX -= cellSize }
                while (accX <= -cellSize) { onMove(-1); accX += cellSize }
                val stepY = cellSize * 0.9f
                while (accY >= stepY) { onSoftDrop(); accY -= stepY }
                if (accY < 0f) accY = 0f
                change.consume()
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------- drawing

private const val COLS = BoardSpec.COLS
private const val ROWS = BoardSpec.ROWS

internal fun DrawScope.drawBoard(
    state: GameState,
    fx: BoardFx,
    painter: BlockPainter,
    settings: GameSettings,
    @Suppress("UNUSED_PARAMETER") frame: Long,
) {
    val cell = painter.cell
    val w = cell * COLS
    val h = cell * ROWS
    val sx = if (settings.screenShake) fx.shakeX * cell * 0.35f else 0f
    val sy = if (settings.screenShake) fx.shakeY * cell * 0.35f else 0f

    translate(sx, sy) {
        drawBoardFrame(w, h, cell)

        fx.animMask.fill(false)
        val clear = state.clear
        if (clear != null) for (c in clear.cells) fx.animMask[c.y * COLS + c.x] = true

        drawSettled(state, fx, painter)
        if (clear != null) drawClearAnimation(clear, painter, w, h)
        if (state.phase == Phase.TARGETING) drawTargetingHints(state, fx, painter, w, h)
        drawGhostAndPiece(state, fx, painter, settings)
        drawTrailsAndFlashes(fx, cell)
        drawRingsAndParticles(fx, cell)
        if (fx.gameOverTime >= 0f) drawGameOver(state, fx, cell, w)
    }
}

private fun DrawScope.drawBoardFrame(w: Float, h: Float, cell: Float) {
    val corner = CornerRadius(cell * 0.35f)
    for (i in 3 downTo 1) {
        val pad = cell * 0.12f * i
        drawRoundRect(
            color = Neon.Cyan.copy(alpha = 0.05f),
            topLeft = Offset(-pad, -pad),
            size = Size(w + pad * 2, h + pad * 2),
            cornerRadius = CornerRadius(corner.x + pad),
        )
    }
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(Color(0xFF0C0634), Color(0xFF070320)), 0f, h),
        size = Size(w, h),
        cornerRadius = corner,
    )
    val grid = Color.White.copy(alpha = 0.05f)
    for (x in 1 until COLS) drawLine(grid, Offset(x * cell, 0f), Offset(x * cell, h), strokeWidth = 1f)
    for (y in 1 until ROWS) drawLine(grid, Offset(0f, y * cell), Offset(w, y * cell), strokeWidth = 1f)
    drawRoundRect(
        brush = Brush.linearGradient(listOf(Neon.Cyan, Neon.Magenta), Offset.Zero, Offset(w, h)),
        size = Size(w, h),
        cornerRadius = corner,
        style = Stroke(width = cell * 0.08f),
    )
}

private fun DrawScope.drawSettled(state: GameState, fx: BoardFx, painter: BlockPainter) {
    val cell = painter.cell
    val collapse = state.collapse
    for (y in 0 until ROWS) {
        for (x in 0 until COLS) {
            val i = y * COLS + x
            val id = state.board[i]
            if (id == 0 || fx.animMask[i]) continue
            var drawY = y.toFloat()
            if (collapse != null) {
                val fell = collapse.offsets[i]
                if (fell > 0) drawY = y - fell * (1f - collapse.progress * collapse.progress) // accelerating fall
            }
            drawBlock(painter, id, x * cell, drawY * cell, glow = 0.6f)
        }
    }
}

// ----- clear animations -----

private class CellLook(var scale: Float = 1f, var whiten: Float = 0f, var glow: Float = 1f, var rot: Float = 0f, var alpha: Float = 1f)

private val sharedLook = CellLook()

private fun easeOut(t: Float): Float = 1f - (1f - t) * (1f - t)

/** Per-cell look over the animation timeline. LINES: Flash -> Glow -> Shrink -> (particles fired by the engine). */
private fun fillLook(look: CellLook, kind: ClearKind, p: Float) {
    look.scale = 1f; look.whiten = 0f; look.glow = 1f; look.rot = 0f; look.alpha = 1f
    when (kind) {
        ClearKind.LINES -> when {
            p < 0.16f -> {
                look.whiten = 0.55f + 0.45f * abs(sin(p / 0.16f * PI.toFloat() * 3f))
                look.glow = 1.6f
            }
            p < 0.42f -> {
                val t = (p - 0.16f) / 0.26f
                look.whiten = 0.3f * (1f - t); look.glow = 1.6f + 2.4f * t; look.scale = 1f + 0.10f * t
            }
            p < 0.72f -> {
                val t = (p - 0.42f) / 0.30f
                look.scale = 1.1f * (1f - t * t); look.glow = 4f * (1f - t); look.rot = 160f * t; look.alpha = 1f - 0.3f * t
            }
            else -> look.alpha = 0f
        }
        ClearKind.BOMB -> when {
            p < 0.30f -> {
                val t = p / 0.30f
                look.whiten = 0.25f + 0.65f * t; look.glow = 1.5f + 2.5f * t; look.scale = 1f + 0.06f * sin(t * 18f)
            }
            p < 0.72f -> {
                val t = (p - 0.30f) / 0.42f
                look.scale = 1f - t * t; look.whiten = 0.9f * (1f - t); look.glow = 4f * (1f - t); look.rot = 220f * t
            }
            else -> look.alpha = 0f
        }
        ClearKind.LIGHTNING -> when {
            p < 0.35f -> {
                look.whiten = if (((p * 70f).toInt() and 1) == 0) 0.95f else 0.35f; look.glow = 3f
            }
            p < 0.70f -> {
                val t = (p - 0.35f) / 0.35f
                look.scale = 1f - t; look.whiten = 0.6f * (1f - t); look.glow = 3f * (1f - t); look.rot = 90f * t
            }
            else -> look.alpha = 0f
        }
        ClearKind.HAMMER -> when {
            p < 0.25f -> {
                look.whiten = 0.9f; look.glow = 3f; look.scale = 1f + 0.12f * (p / 0.25f)
            }
            p < 0.60f -> {
                val t = (p - 0.25f) / 0.35f
                look.scale = 1.12f * (1f - t * t); look.whiten = 0.9f * (1f - t); look.glow = 3f * (1f - t); look.rot = 180f * t
            }
            else -> look.alpha = 0f
        }
        ClearKind.COLOR_BOMB -> when {
            p < 0.60f -> {
                val pulse = 0.5f + 0.5f * sin(p * 38f)
                look.glow = 1.5f + 2.5f * pulse; look.whiten = 0.15f + 0.35f * pulse; look.scale = 1f + 0.08f * pulse
            }
            p < 0.92f -> {
                val t = (p - 0.60f) / 0.32f
                look.scale = 1f - t * t; look.glow = 3f * (1f - t); look.rot = 180f * t
            }
            else -> look.alpha = 0f
        }
    }
}

private fun DrawScope.drawClearAnimation(clear: ClearAnimation, painter: BlockPainter, w: Float, h: Float) {
    val cell = painter.cell
    val p = clear.progress
    val focus = Offset(clear.focusX * cell, clear.focusY * cell)

    // effects drawn underneath the blocks
    when (clear.kind) {
        ClearKind.LIGHTNING -> if (p < 0.55f) {
            val flicker = if (((p * 70f).toInt() and 1) == 0) 1f else 0.55f
            drawRect(
                Neon.Cyan.copy(alpha = 0.22f * flicker * (1f - p / 0.55f)),
                Offset(clear.column * cell, 0f), Size(cell, h),
            )
        }
        ClearKind.COLOR_BOMB -> if (p < 0.6f) {
            val pulse = 0.5f + 0.5f * sin(p * 38f)
            for (c in clear.cells) {
                drawLine(
                    Neon.pieceColor(c.color).copy(alpha = 0.35f + 0.3f * pulse), focus,
                    Offset((c.x + 0.5f) * cell, (c.y + 0.5f) * cell), strokeWidth = cell * 0.07f, cap = StrokeCap.Round,
                )
            }
        }
        else -> Unit
    }

    for (c in clear.cells) {
        fillLook(sharedLook, clear.kind, p)
        val look = sharedLook
        if (look.alpha <= 0.01f || look.scale <= 0.01f) continue
        val pivot = Offset((c.x + 0.5f) * cell, (c.y + 0.5f) * cell)
        rotate(look.rot, pivot) {
            drawBlock(painter, c.color, c.x * cell, c.y * cell, look.alpha, look.glow, look.scale, look.whiten)
        }
    }

    // effects drawn on top of the blocks
    when (clear.kind) {
        ClearKind.LINES -> {
            val beam = when {
                p < 0.16f -> 0f
                p < 0.42f -> (p - 0.16f) / 0.26f
                p < 0.72f -> 1f
                else -> 1f - (p - 0.72f) / 0.28f
            }
            if (beam > 0f) {
                for (y in clear.cells.map { it.y }.distinct()) {
                    val thickness = cell * (0.16f + 0.3f * beam)
                    drawRect(
                        brush = Brush.horizontalGradient(
                            listOf(
                                Color.Transparent, Neon.Cyan.copy(alpha = 0.7f * beam), Color.White.copy(alpha = 0.95f * beam),
                                Neon.Magenta.copy(alpha = 0.7f * beam), Color.Transparent,
                            ),
                        ),
                        topLeft = Offset(0f, (y + 0.5f) * cell - thickness / 2f),
                        size = Size(w, thickness),
                    )
                }
            }
        }
        ClearKind.BOMB -> {
            val fp = easeOut((p / 0.55f).coerceIn(0f, 1f))
            val radius = max(cell * 9f * fp, 1f)
            val a = (1f - p).coerceIn(0f, 1f)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.9f * a), Neon.Orange.copy(alpha = 0.65f * a), Neon.Red.copy(alpha = 0.25f * a), Color.Transparent),
                    center = focus, radius = radius,
                ),
                radius = radius, center = focus,
            )
        }
        ClearKind.LIGHTNING -> if (p < 0.6f) {
            val alpha = if (p < 0.35f) 1f else 1f - (p - 0.35f) / 0.25f
            drawBolt(cell, clear.column, clear.id * 131 + (p * 22f).toInt(), alpha)
        }
        ClearKind.HAMMER -> {
            if (p < 0.3f) {
                val a = 1f - p / 0.3f
                val r = cell * (1.2f + 2.4f * easeOut(p / 0.3f))
                drawCircle(
                    brush = Brush.radialGradient(listOf(Color.White.copy(alpha = 0.9f * a), Neon.Yellow.copy(alpha = 0.4f * a), Color.Transparent), center = focus, radius = r),
                    radius = r, center = focus,
                )
            }
            if (p < 0.5f) {
                val rnd = Random(clear.id)
                val a = 1f - p / 0.5f
                for (i in 0 until 9) {
                    val ang = (i / 9f) * 2f * PI.toFloat() + rnd.nextFloat()
                    val len = cell * (0.8f + rnd.nextFloat() * 1.6f) * easeOut((p / 0.2f).coerceAtMost(1f))
                    drawLine(
                        Color.White.copy(alpha = 0.8f * a), focus, focus + Offset(cos(ang) * len, sin(ang) * len),
                        strokeWidth = cell * 0.07f, cap = StrokeCap.Round,
                    )
                }
            }
        }
        ClearKind.COLOR_BOMB -> Unit
    }
}

private fun DrawScope.drawBolt(cell: Float, column: Int, seed: Int, alpha: Float) {
    val rnd = Random(seed)
    val x0 = (column + 0.5f) * cell
    val bottom = cell * ROWS + cell * 0.5f
    val path = Path()
    var y = -cell * 0.5f
    path.moveTo(x0, y)
    while (y < bottom) {
        y = (y + cell * (0.9f + rnd.nextFloat() * 0.9f)).coerceAtMost(bottom)
        path.lineTo(x0 + (rnd.nextFloat() - 0.5f) * cell * 1.3f, y)
    }
    val round = { width: Float -> Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round) }
    drawPath(path, Neon.Cyan.copy(alpha = 0.22f * alpha), style = round(cell * 1.0f))
    drawPath(path, Neon.Cyan.copy(alpha = 0.55f * alpha), style = round(cell * 0.42f))
    drawPath(path, Color.White.copy(alpha = alpha), style = round(cell * 0.14f))
}

// ----- targeting, piece, effects -----

private fun DrawScope.drawTargetingHints(state: GameState, fx: BoardFx, painter: BlockPainter, w: Float, h: Float) {
    val cell = painter.cell
    val pulse = 0.5f + 0.5f * sin(fx.time * 6f)
    drawRect(Color.Black.copy(alpha = 0.25f), Offset.Zero, Size(w, h))
    when (state.targeting) {
        PowerUpType.LIGHTNING -> for (x in 0 until COLS) {
            drawRect(Neon.Cyan.copy(alpha = (0.03f + 0.07f * pulse) * (1 + x % 2)), Offset(x * cell, 0f), Size(cell, h))
        }
        PowerUpType.HAMMER -> drawRoundRect(
            color = Neon.Yellow.copy(alpha = 0.35f + 0.5f * pulse), size = Size(w, h),
            cornerRadius = CornerRadius(cell * 0.35f), style = Stroke(width = cell * 0.14f),
        )
        PowerUpType.COLOR_BOMB -> for (y in 0 until ROWS) for (x in 0 until COLS) {
            if (state.board[y * COLS + x] != 0) {
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.25f + 0.5f * pulse),
                    topLeft = Offset(x * cell + cell * 0.04f, y * cell + cell * 0.04f),
                    size = Size(cell * 0.92f, cell * 0.92f),
                    cornerRadius = CornerRadius(cell * 0.2f), style = Stroke(width = cell * 0.07f),
                )
            }
        }
        else -> Unit
    }
}

private fun DrawScope.drawGhostAndPiece(state: GameState, fx: BoardFx, painter: BlockPainter, settings: GameSettings) {
    val piece = state.piece ?: return
    if (state.phase == Phase.READY) return
    val cell = painter.cell
    val o = Tetrominoes.offsets(piece.type, piece.rotation)
    val color = Neon.pieceColor(piece.type)

    if (settings.ghostPiece && state.phase != Phase.GAME_OVER && state.ghostY > piece.y) {
        for (i in 0 until 4) {
            val row = state.ghostY + o[i * 2 + 1]
            if (row < 0) continue
            val topLeft = Offset((piece.x + o[i * 2]) * cell + cell * 0.06f, row * cell + cell * 0.06f)
            val ghostSize = Size(cell * 0.88f, cell * 0.88f)
            drawRoundRect(color.copy(alpha = 0.12f), topLeft, ghostSize, CornerRadius(cell * 0.18f))
            drawRoundRect(color.copy(alpha = 0.55f), topLeft, ghostSize, CornerRadius(cell * 0.18f), style = Stroke(width = cell * 0.05f))
        }
    }

    val box = Tetrominoes.boxSize(piece.type)
    val pivot = Offset((fx.dispX + box / 2f) * cell, (fx.dispY + box / 2f) * cell)
    rotate(fx.rotKick, pivot) {
        for (i in 0 until 4) {
            val row = fx.dispY + o[i * 2 + 1]
            if (row < -0.6f) continue
            drawBlock(painter, piece.type.id, (fx.dispX + o[i * 2]) * cell, row * cell, glow = 1.3f)
        }
    }
}

private fun DrawScope.drawTrailsAndFlashes(fx: BoardFx, cell: Float) {
    for (t in fx.trails) {
        val a = (t.life / t.maxLife).coerceIn(0f, 1f)
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color.Transparent, t.color.copy(alpha = 0.55f * a)),
                startY = t.top * cell, endY = t.bottom * cell,
            ),
            topLeft = Offset(t.x * cell + cell * 0.1f, t.top * cell),
            size = Size(cell * 0.8f, (t.bottom - t.top) * cell),
        )
    }
    for (f in fx.flashes) {
        val a = (f.life / f.maxLife).coerceIn(0f, 1f)
        drawRoundRect(
            color = Color.White.copy(alpha = 0.85f * a),
            topLeft = Offset(f.x * cell + cell * 0.04f, f.y * cell + cell * 0.04f),
            size = Size(cell * 0.92f, cell * 0.92f), cornerRadius = CornerRadius(cell * 0.2f),
        )
    }
}

private fun DrawScope.drawRingsAndParticles(fx: BoardFx, cell: Float) {
    for (r in fx.rings) {
        val life = (r.life / r.maxLife).coerceIn(0f, 1f)
        val progress = 1f - life
        drawCircle(
            color = r.color.copy(alpha = 0.75f * life),
            radius = r.maxRadius * cell * easeOut(progress),
            center = Offset(r.cx * cell, r.cy * cell),
            style = Stroke(width = max(1f, cell * r.width * life)),
        )
    }
    for (p in fx.particles) {
        val a = (p.life / p.maxLife).coerceIn(0f, 1f)
        val center = Offset(p.x * cell, p.y * cell)
        val radius = max(0.5f, p.size * cell * (0.4f + 0.6f * a))
        drawCircle(p.color.copy(alpha = 0.28f * a), radius * 2.4f, center)
        drawCircle(p.color.copy(alpha = a), radius, center)
    }
}

private fun DrawScope.drawGameOver(state: GameState, fx: BoardFx, cell: Float, w: Float) {
    val lit = (fx.gameOverTime / 0.05f).toInt().coerceAtMost(ROWS)
    for (r in 0 until lit) {
        val y = ROWS - 1 - r
        drawRect(Color(0xFF05020F).copy(alpha = 0.55f), Offset(0f, y * cell), Size(w, cell))
        for (x in 0 until COLS) {
            if (state.board[y * COLS + x] != 0) {
                drawRoundRect(
                    Color.Gray.copy(alpha = 0.55f),
                    Offset(x * cell + cell * 0.05f, y * cell + cell * 0.05f),
                    Size(cell * 0.9f, cell * 0.9f), CornerRadius(cell * 0.18f),
                )
            }
        }
    }
}
