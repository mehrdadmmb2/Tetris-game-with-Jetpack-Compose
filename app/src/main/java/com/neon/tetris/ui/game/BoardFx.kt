package com.neon.tetris.ui.game

import androidx.compose.ui.graphics.Color
import com.neon.tetris.domain.BoardSpec
import com.neon.tetris.domain.PowerUpType
import com.neon.tetris.game.ActivePiece
import com.neon.tetris.game.ClearKind
import com.neon.tetris.game.GameEvent
import com.neon.tetris.game.GameState
import com.neon.tetris.game.Phase
import com.neon.tetris.ui.theme.Neon
import com.neon.tetris.ui.theme.lighten
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/** All positions below are in board cell units (x: 0..10, y: 0..20) so they scale with any cell size. */
class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var life: Float,
    val maxLife: Float,
    val size: Float,
    val color: Color,
    val gravity: Float,
)

class Trail(val x: Int, val top: Float, val bottom: Float, val color: Color, var life: Float, val maxLife: Float)
class Flash(val x: Int, val y: Int, var life: Float, val maxLife: Float)
class Ring(
    val cx: Float,
    val cy: Float,
    val maxRadius: Float,
    var life: Float,
    val maxLife: Float,
    val color: Color,
    val width: Float,
)

/**
 * UI-only animation state for the board. It is advanced once per frame by [step] and fed by engine
 * events through [onEvent]; the game rules never see it. Everything here is mutated on the main thread.
 */
class BoardFx {
    val particles = ArrayList<Particle>(256)
    val trails = ArrayList<Trail>()
    val flashes = ArrayList<Flash>()
    val rings = ArrayList<Ring>()

    /** Cells currently drawn by the clear animation instead of the normal block pass. */
    val animMask = BooleanArray(BoardSpec.COLS * BoardSpec.ROWS)

    var shake = 0f
    var shakeX = 0f
    var shakeY = 0f

    /** Seconds since this board was composed; drives pulsing effects. */
    var time = 0f

    /** Seconds since the game ended, or -1 while playing. */
    var gameOverTime = -1f

    // Smoothed falling piece: the engine moves in whole cells, the screen glides.
    var pieceSerial = -1
    var dispX = 0f
    var dispY = 0f
    var rotKick = 0f
    private var lastRotation = 0

    private val rnd = Random(20240607)

    fun step(dtMs: Float, state: GameState) {
        val dt = (dtMs / 1000f).coerceIn(0f, 0.05f)
        time += dt
        stepPiece(state.piece, dtMs)
        gameOverTime = if (state.phase == Phase.GAME_OVER) {
            if (gameOverTime < 0f) 0f else gameOverTime + dt
        } else {
            -1f
        }

        var i = 0
        while (i < particles.size) {
            val p = particles[i]
            p.life -= dt
            if (p.life <= 0f) {
                particles[i] = particles[particles.size - 1]
                particles.removeAt(particles.size - 1)
                continue
            }
            p.vy += p.gravity * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            val drag = (1f - 1.3f * dt).coerceAtLeast(0f)
            p.vx *= drag
            i++
        }
        trails.removeAll { it.apply { life -= dt }.life <= 0f }
        flashes.removeAll { it.apply { life -= dt }.life <= 0f }
        rings.removeAll { it.apply { life -= dt }.life <= 0f }

        shake = max(0f, shake - dt * 2.6f)
        if (shake > 0f) {
            shakeX = (rnd.nextFloat() - 0.5f) * 2f * shake
            shakeY = (rnd.nextFloat() - 0.5f) * 2f * shake
        } else {
            shakeX = 0f
            shakeY = 0f
        }
    }

    private fun stepPiece(piece: ActivePiece?, dtMs: Float) {
        if (piece == null) {
            pieceSerial = -1
            return
        }
        if (piece.serial != pieceSerial) { // a new piece: appear at its spawn position
            pieceSerial = piece.serial
            dispX = piece.x.toFloat()
            dispY = piece.y.toFloat()
            lastRotation = piece.rotation
            rotKick = 0f
            return
        }
        dispX += (piece.x - dispX) * (1f - exp(-dtMs / 26f))
        dispY += (piece.y - dispY) * (1f - exp(-dtMs / 38f))
        if (piece.rotation != lastRotation) {
            val delta = (piece.rotation - lastRotation + 4) % 4
            rotKick = when (delta) {
                1 -> -90f // drawn as the previous orientation, then eased into the new one
                3 -> 90f
                else -> 180f
            }
            lastRotation = piece.rotation
        }
        rotKick *= exp(-dtMs / 42f)
        if (abs(rotKick) < 0.5f) rotKick = 0f
    }

    fun onEvent(e: GameEvent, shakeEnabled: Boolean) {
        when (e) {
            is GameEvent.Locked -> for (c in e.cells) if (c.y >= 0) flashes.add(Flash(c.x, c.y, 0.18f, 0.18f))
            is GameEvent.HardDropped -> onHardDrop(e, shakeEnabled)
            is GameEvent.ClearBurst -> onBurst(e, shakeEnabled)
            is GameEvent.LevelUp -> confetti(BoardSpec.COLS / 2f, 7f, 90, 14f)
            is GameEvent.ComboChanged -> burstAt(BoardSpec.COLS / 2f, 5f, 26, Neon.comboColor(e.combo), 9f)
            is GameEvent.PowerUpApplied -> if (e.type == PowerUpType.DOUBLE_SCORE) {
                rings.add(Ring(BoardSpec.COLS / 2f, BoardSpec.ROWS / 2f, 12f, 0.9f, 0.9f, Neon.Green, 0.35f))
                burstAt(BoardSpec.COLS / 2f, BoardSpec.ROWS / 2f, 40, Neon.Green, 8f)
            }
            else -> Unit
        }
    }

    private fun onHardDrop(e: GameEvent.HardDropped, shakeEnabled: Boolean) {
        val color = Neon.pieceColor(e.type)
        for ((x, landed) in e.to.groupBy { it.x }) {
            val bottom = landed.maxOf { it.y } + 1f
            val top = e.from.filter { it.x == x }.minOf { it.y }.toFloat()
            if (e.distance >= 2) trails.add(Trail(x, top, bottom, color, 0.26f, 0.26f))
            repeat(5) {
                val dir = if (rnd.nextBoolean()) 1f else -1f
                addParticle(
                    x + 0.5f, bottom - 0.1f,
                    dir * (1.5f + rnd.nextFloat() * 4f), -(2f + rnd.nextFloat() * 4.5f),
                    0.35f + rnd.nextFloat() * 0.3f, 0.09f, color.lighten(0.35f), 12f,
                )
            }
        }
        if (shakeEnabled && e.distance >= 3) shake = max(shake, (0.12f + e.distance * 0.012f).coerceAtMost(0.45f))
    }

    private fun onBurst(e: GameEvent.ClearBurst, shakeEnabled: Boolean) {
        val perCell: Int
        val speed: Float
        when (e.kind) {
            ClearKind.LINES -> { perCell = 4; speed = 7f }
            ClearKind.BOMB -> { perCell = 6; speed = 12f }
            ClearKind.LIGHTNING -> { perCell = 4; speed = 9f }
            ClearKind.HAMMER -> { perCell = 7; speed = 10f }
            ClearKind.COLOR_BOMB -> { perCell = 6; speed = 8f }
        }
        for (c in e.cells) {
            val base = Neon.pieceColor(c.color)
            repeat(perCell) {
                val a = rnd.nextFloat() * 2f * PI.toFloat()
                val v = speed * (0.35f + rnd.nextFloat() * 0.75f)
                val tint = if (rnd.nextFloat() < 0.25f) Color.White else base.lighten(rnd.nextFloat() * 0.4f)
                addParticle(
                    c.x + 0.5f, c.y + 0.5f, cos(a) * v, sin(a) * v - 1.5f,
                    0.5f + rnd.nextFloat() * 0.5f, 0.10f + rnd.nextFloat() * 0.06f, tint, 14f,
                )
            }
        }
        var shakeAmount = 0f
        when (e.kind) {
            ClearKind.LINES -> shakeAmount = 0.10f * (e.cells.size / BoardSpec.COLS)
            ClearKind.BOMB -> {
                rings.add(Ring(e.focusX, e.focusY, 14f, 0.75f, 0.75f, Neon.Orange, 0.55f))
                rings.add(Ring(e.focusX, e.focusY, 9f, 0.55f, 0.55f, Neon.Yellow, 0.3f))
                burstAt(e.focusX, e.focusY, 70, Neon.Orange, 13f)
                shakeAmount = 1f
            }
            ClearKind.HAMMER -> {
                rings.add(Ring(e.focusX, e.focusY, 7f, 0.5f, 0.5f, Neon.Yellow, 0.4f))
                shakeAmount = 0.6f
            }
            ClearKind.LIGHTNING -> {
                for (y in 0 until BoardSpec.ROWS step 2) {
                    repeat(3) {
                        val dir = if (rnd.nextBoolean()) 1f else -1f
                        addParticle(
                            e.focusX, y + 0.5f, dir * (3f + rnd.nextFloat() * 8f), (rnd.nextFloat() - 0.5f) * 4f,
                            0.3f + rnd.nextFloat() * 0.35f, 0.08f, if (rnd.nextBoolean()) Color.White else Neon.Cyan, 4f,
                        )
                    }
                }
                shakeAmount = 0.45f
            }
            ClearKind.COLOR_BOMB -> {
                rings.add(Ring(e.focusX, e.focusY, 8f, 0.6f, 0.6f, Neon.Magenta, 0.3f))
                shakeAmount = 0.35f
            }
        }
        if (shakeEnabled) shake = max(shake, shakeAmount)
    }

    private fun confetti(cx: Float, cy: Float, count: Int, speed: Float) {
        val palette = listOf(Neon.Cyan, Neon.Magenta, Neon.Yellow, Neon.Green, Neon.Orange, Neon.Purple)
        repeat(count) {
            val a = rnd.nextFloat() * 2f * PI.toFloat()
            val v = speed * (0.3f + rnd.nextFloat() * 0.8f)
            addParticle(
                cx, cy, cos(a) * v, sin(a) * v - 4f,
                1.0f + rnd.nextFloat() * 0.7f, 0.10f + rnd.nextFloat() * 0.07f, palette[rnd.nextInt(palette.size)], 11f,
            )
        }
    }

    private fun burstAt(cx: Float, cy: Float, count: Int, color: Color, speed: Float) {
        repeat(count) {
            val a = rnd.nextFloat() * 2f * PI.toFloat()
            val v = speed * (0.3f + rnd.nextFloat() * 0.8f)
            val tint = if (rnd.nextFloat() < 0.3f) Color.White else color.lighten(rnd.nextFloat() * 0.3f)
            addParticle(cx, cy, cos(a) * v, sin(a) * v, 0.45f + rnd.nextFloat() * 0.5f, 0.10f + rnd.nextFloat() * 0.06f, tint, 6f)
        }
    }

    private fun addParticle(x: Float, y: Float, vx: Float, vy: Float, life: Float, size: Float, color: Color, gravity: Float) {
        if (particles.size >= MAX_PARTICLES) return
        particles.add(Particle(x, y, vx, vy, life, life, size, color, gravity))
    }

    private companion object {
        const val MAX_PARTICLES = 700
    }
}
