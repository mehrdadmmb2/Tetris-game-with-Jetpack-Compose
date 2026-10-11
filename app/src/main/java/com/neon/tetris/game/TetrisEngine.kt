package com.neon.tetris.game

import com.neon.tetris.domain.BoardSpec
import com.neon.tetris.domain.LevelSystem
import com.neon.tetris.domain.PowerUpRules
import com.neon.tetris.domain.PowerUpType
import com.neon.tetris.domain.ScoreRules
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

/**
 * The complete Tetris rule set with no Android dependency.
 *
 * Drive it from one thread: call [update] with elapsed milliseconds from a game loop and forward input to the
 * action functions. Observe [state] (a StateFlow snapshot, published whenever something visible changes) and
 * [events] (one-shot happenings used for effects and persistence).
 */
class TetrisEngine(private val random: Random = Random.Default) {

    companion object {
        const val NEXT_COUNT = 3
        const val SPAWN_X = 3
        const val LOCK_DELAY_MS = 500L
        const val SOFT_LOCK_DELAY_MS = 120L
        const val MAX_LOCK_RESETS = 15
        const val SOFT_DROP_INTERVAL_MS = 45L
        const val COLLAPSE_MS = 240L

        const val LINE_CLEAR_MS = 560L
        const val BOMB_MS = 820L
        const val LIGHTNING_MS = 680L
        const val HAMMER_MS = 620L
        const val COLOR_BOMB_MS = 720L
    }

    private class ActiveClear(
        val id: Int,
        val kind: ClearKind,
        val cells: List<AnimCell>,
        val positions: List<Cell>,
        val durationMs: Long,
        val focusX: Float,
        val focusY: Float,
        val column: Int,
    ) {
        var elapsed = 0L
        var burstDone = false
        val progress: Float get() = (elapsed.toFloat() / durationMs).coerceIn(0f, 1f)
    }

    private class ActiveCollapse(val offsets: IntArray, val durationMs: Long) {
        var elapsed = 0L
        val progress: Float get() = (elapsed.toFloat() / durationMs).coerceIn(0f, 1f)
    }

    private val cols = BoardSpec.COLS
    private val rows = BoardSpec.ROWS
    private val board = Board()
    private var bag = SevenBag(random)
    private val queue = ArrayDeque<PieceType>()

    private var phase = Phase.READY
    private var phaseBeforePause = Phase.FALLING
    private var piece: ActivePiece? = null
    private var pieceSerial = 0
    private var gravityAcc = 0L
    private var lockTimer = 0L
    private var lockResets = 0
    private var lowestY = Int.MIN_VALUE
    private var softDrop = false

    private var score = 0
    private var level = 1
    private var lines = 0
    private var combo = 0
    private var doubleMs = 0L
    private var targeting: PowerUpType? = null

    private var animSerial = 0
    private var clear: ActiveClear? = null
    private var collapse: ActiveCollapse? = null
    private var revision = 0L

    private val _state = MutableStateFlow(buildState())
    val state: StateFlow<GameState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<GameEvent>(
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<GameEvent> = _events.asSharedFlow()

    /** True while a game exists that the player can continue (running or paused, not over). */
    val inProgress: Boolean get() = phase != Phase.READY && phase != Phase.GAME_OVER

    // ------------------------------------------------------------------ lifecycle

    fun startNewGame() {
        board.clear()
        bag = SevenBag(random)
        queue.clear()
        score = 0; level = 1; lines = 0; combo = 0; doubleMs = 0L
        targeting = null; clear = null; collapse = null; softDrop = false
        piece = null; pieceSerial = 0
        refillQueue()
        phase = Phase.FALLING
        emit(GameEvent.Started)
        if (spawnNext()) publish()
    }

    /** Back to an idle, empty state (used when the player abandons a game). */
    fun reset() {
        board.clear()
        queue.clear()
        piece = null; clear = null; collapse = null; targeting = null; softDrop = false
        score = 0; level = 1; lines = 0; combo = 0; doubleMs = 0L
        phase = Phase.READY
        publish()
    }

    fun pause() {
        if (phase == Phase.FALLING || phase == Phase.TARGETING || phase == Phase.CLEARING || phase == Phase.COLLAPSING) {
            phaseBeforePause = phase
            phase = Phase.PAUSED
            softDrop = false
            emit(GameEvent.Paused)
            publish()
        }
    }

    fun resume() {
        if (phase == Phase.PAUSED) {
            phase = phaseBeforePause
            emit(GameEvent.Resumed)
            publish()
        }
    }

    // ------------------------------------------------------------------ game loop

    fun update(dtMs: Long) {
        val dt = dtMs.coerceIn(0L, 100L)
        if (dt == 0L) return
        when (phase) {
            Phase.FALLING -> { tickDoubleScore(dt); tickFalling(dt) }
            Phase.CLEARING -> { tickDoubleScore(dt); tickClearing(dt) }
            Phase.COLLAPSING -> { tickDoubleScore(dt); tickCollapsing(dt) }
            else -> Unit
        }
    }

    private fun tickDoubleScore(dt: Long) {
        if (doubleMs <= 0L) return
        val before = doubleMs
        doubleMs = (doubleMs - dt).coerceAtLeast(0L)
        if (doubleMs == 0L) {
            emit(GameEvent.DoubleScoreEnded)
            publish()
        } else if (before / 1000 != doubleMs / 1000) {
            publish()
        }
    }

    private fun tickFalling(dt: Long) {
        var cur = piece ?: return
        val levelInterval = LevelSystem.gravityIntervalMs(level)
        val interval = if (softDrop) minOf(levelInterval, SOFT_DROP_INTERVAL_MS) else levelInterval

        var moved = false
        gravityAcc += dt
        while (gravityAcc >= interval) {
            val next = cur.copy(y = cur.y + 1)
            if (board.fits(next)) {
                cur = next
                gravityAcc -= interval
                moved = true
            } else {
                gravityAcc = 0L
                break
            }
        }
        if (moved) {
            piece = cur
            lockTimer = 0L
            noteLowest(cur.y)
        }

        if (!board.fits(cur.copy(y = cur.y + 1))) {
            lockTimer += dt
            val delay = if (softDrop) SOFT_LOCK_DELAY_MS else LOCK_DELAY_MS
            if (lockTimer >= delay) {
                lockPiece()
                return
            }
        } else {
            lockTimer = 0L
        }
        if (moved) publish()
    }

    private fun tickClearing(dt: Long) {
        val c = clear ?: return
        c.elapsed += dt
        if (!c.burstDone && c.progress >= c.kind.burstAt) {
            c.burstDone = true
            emit(GameEvent.ClearBurst(c.kind, c.cells, c.focusX, c.focusY))
        }
        if (c.elapsed >= c.durationMs) finishClear(c) else publish()
    }

    private fun tickCollapsing(dt: Long) {
        val c = collapse
        if (c == null) {
            finishCollapse()
            return
        }
        c.elapsed += dt
        if (c.elapsed >= c.durationMs) finishCollapse() else publish()
    }

    // ------------------------------------------------------------------ player input

    fun moveLeft(): Boolean = moveHorizontal(-1)
    fun moveRight(): Boolean = moveHorizontal(1)

    private fun moveHorizontal(dx: Int): Boolean {
        val p = piece ?: return false
        if (phase != Phase.FALLING) return false
        val moved = p.copy(x = p.x + dx)
        if (!board.fits(moved)) return false
        piece = moved
        onPieceAdjusted()
        emit(GameEvent.Moved)
        publish()
        return true
    }

    fun rotate(clockwise: Boolean): Boolean {
        val p = piece ?: return false
        if (phase != Phase.FALLING || p.type == PieceType.O) return false
        val to = if (clockwise) (p.rotation + 1) and 3 else (p.rotation + 3) and 3
        val kicks = Tetrominoes.kicks(p.type, p.rotation, clockwise)
        var i = 0
        while (i < kicks.size) {
            val nx = p.x + kicks[i]
            val ny = p.y - kicks[i + 1] // table is y-up, board is y-down
            if (board.fits(p.type, to, nx, ny)) {
                piece = p.copy(rotation = to, x = nx, y = ny)
                onPieceAdjusted()
                emit(GameEvent.Rotated(clockwise))
                publish()
                return true
            }
            i += 2
        }
        return false
    }

    /** Hold-to-soft-drop: while true, gravity runs at the soft drop speed. */
    fun setSoftDrop(active: Boolean) {
        softDrop = active && phase == Phase.FALLING
    }

    /** Moves the piece down exactly one row (used by swipe-down). */
    fun softDropStep(): Boolean {
        val p = piece ?: return false
        if (phase != Phase.FALLING) return false
        val next = p.copy(y = p.y + 1)
        if (!board.fits(next)) return false
        piece = next
        gravityAcc = 0L
        lockTimer = 0L
        noteLowest(next.y)
        emit(GameEvent.SoftDropped)
        publish()
        return true
    }

    fun hardDrop() {
        val p = piece ?: return
        if (phase != Phase.FALLING) return
        var y = p.y
        while (board.fits(p.type, p.rotation, p.x, y + 1)) y++
        val landed = p.copy(y = y)
        piece = landed
        emit(GameEvent.HardDropped(p.type, p.cells(), landed.cells(), y - p.y))
        lockPiece()
    }

    // ------------------------------------------------------------------ power-ups

    /**
     * Activates a power-up. Availability (purchase + charges) is checked by the caller; the engine reports
     * [GameEvent.PowerUpApplied] once a charge has really been spent, or [GameEvent.PowerUpRejected].
     */
    fun usePowerUp(type: PowerUpType) {
        if (phase != Phase.FALLING && phase != Phase.TARGETING) return
        when (type) {
            PowerUpType.BOMB -> applyBomb()
            PowerUpType.DOUBLE_SCORE -> applyDoubleScore()
            PowerUpType.LIGHTNING, PowerUpType.HAMMER, PowerUpType.COLOR_BOMB -> {
                targeting = type
                phase = Phase.TARGETING
                emit(GameEvent.TargetingStarted(type))
                publish()
            }
        }
    }

    fun cancelTargeting() {
        if (phase != Phase.TARGETING) return
        val t = targeting
        targeting = null
        phase = Phase.FALLING
        if (t != null) emit(GameEvent.TargetingCancelled(t))
        publish()
    }

    /** Confirms the target (board cell) of a pending Lightning / Hammer / Color Bomb. */
    fun targetCell(x: Int, y: Int) {
        if (phase != Phase.TARGETING || !board.inBounds(x, y)) return
        when (targeting) {
            PowerUpType.LIGHTNING -> applyLightning(x)
            PowerUpType.HAMMER -> applyHammer(x, y)
            PowerUpType.COLOR_BOMB -> applyColorBomb(x, y)
            else -> Unit
        }
    }

    private fun applyBomb() {
        val firstRow = rows - PowerUpRules.BOMB_ROWS
        val positions = ArrayList<Cell>(PowerUpRules.BOMB_ROWS * cols)
        for (y in firstRow until rows) for (x in 0 until cols) positions.add(Cell(x, y))
        beginPowerUpClear(
            PowerUpType.BOMB, ClearKind.BOMB, positions,
            cols / 2f, firstRow + PowerUpRules.BOMB_ROWS / 2f, -1, BOMB_MS,
        )
    }

    private fun applyLightning(x: Int) {
        val positions = List(rows) { Cell(x, it) }
        beginPowerUpClear(
            PowerUpType.LIGHTNING, ClearKind.LIGHTNING, positions,
            x + 0.5f, rows / 2f, x, LIGHTNING_MS,
        )
    }

    private fun applyHammer(cx: Int, cy: Int) {
        val r = PowerUpRules.HAMMER_RADIUS
        val positions = ArrayList<Cell>()
        for (y in cy - r..cy + r) for (x in cx - r..cx + r) if (board.inBounds(x, y)) positions.add(Cell(x, y))
        beginPowerUpClear(
            PowerUpType.HAMMER, ClearKind.HAMMER, positions,
            cx + 0.5f, cy + 0.5f, -1, HAMMER_MS,
        )
    }

    private fun applyColorBomb(tx: Int, ty: Int) {
        val color = board[tx, ty]
        if (color == 0) {
            emit(GameEvent.PowerUpRejected(PowerUpType.COLOR_BOMB, RejectReason.NOTHING_TO_CLEAR))
            return
        }
        val positions = ArrayList<Cell>()
        for (y in 0 until rows) for (x in 0 until cols) if (board[x, y] == color) positions.add(Cell(x, y))
        beginPowerUpClear(
            PowerUpType.COLOR_BOMB, ClearKind.COLOR_BOMB, positions,
            tx + 0.5f, ty + 0.5f, -1, COLOR_BOMB_MS,
        )
    }

    private fun applyDoubleScore() {
        if (doubleMs > 0L) {
            emit(GameEvent.PowerUpRejected(PowerUpType.DOUBLE_SCORE, RejectReason.ALREADY_ACTIVE))
            return
        }
        doubleMs = PowerUpRules.DOUBLE_SCORE_DURATION_MS
        emit(GameEvent.PowerUpApplied(PowerUpType.DOUBLE_SCORE))
        publish()
    }

    private fun beginPowerUpClear(
        type: PowerUpType,
        kind: ClearKind,
        positions: List<Cell>,
        focusX: Float,
        focusY: Float,
        column: Int,
        baseMs: Long,
    ) {
        val cells = positions.filter { board[it.x, it.y] != 0 }.map { AnimCell(it.x, it.y, board[it.x, it.y]) }
        if (cells.isEmpty()) {
            emit(GameEvent.PowerUpRejected(type, RejectReason.NOTHING_TO_CLEAR))
            return
        }
        targeting = null
        startClear(kind, cells, positions, scaled(baseMs), focusX, focusY, column)
        emit(GameEvent.PowerUpApplied(type))
        addScore(ScoreRules.powerUpPoints(cells.size, doubleMs > 0L), focusX, focusY)
        publish()
    }

    // ------------------------------------------------------------------ locking, clearing, scoring

    private fun lockPiece() {
        val p = piece ?: return
        val cells = p.cells()
        val fullyInside = board.place(p)
        emit(GameEvent.Locked(p.type, cells))
        piece = null
        softDrop = false
        if (!fullyInside) {
            gameOver()
            return
        }
        val full = board.fullRows()
        if (full.isEmpty()) {
            combo = 0
            if (spawnNext()) publish()
        } else {
            beginLineClear(full)
            publish()
        }
    }

    private fun beginLineClear(fullRows: List<Int>) {
        val n = fullRows.size
        combo += 1
        val points = ScoreRules.lineClearPoints(n, combo, doubleMs > 0L)
        val positions = ArrayList<Cell>(n * cols)
        for (y in fullRows) for (x in 0 until cols) positions.add(Cell(x, y))
        val cells = positions.map { AnimCell(it.x, it.y, board[it.x, it.y]) }
        lines += n
        val focusY = fullRows.average().toFloat() + 0.5f
        startClear(ClearKind.LINES, cells, positions, scaled(LINE_CLEAR_MS), cols / 2f, focusY, -1)
        emit(GameEvent.LinesCleared(fullRows, n, points, combo))
        if (combo >= 2) emit(GameEvent.ComboChanged(combo))
        addScore(points, cols / 2f, focusY)
    }

    private fun startClear(
        kind: ClearKind,
        cells: List<AnimCell>,
        positions: List<Cell>,
        durationMs: Long,
        focusX: Float,
        focusY: Float,
        column: Int,
    ) {
        clear = ActiveClear(++animSerial, kind, cells, positions, durationMs, focusX, focusY, column)
        phase = Phase.CLEARING
    }

    private fun finishClear(c: ActiveClear) {
        val offsets = board.removeRegion(c.positions)
        clear = null
        if (offsets.any { it > 0 }) {
            collapse = ActiveCollapse(offsets, COLLAPSE_MS)
            phase = Phase.COLLAPSING
            publish()
        } else {
            finishCollapse()
        }
    }

    private fun finishCollapse() {
        collapse = null
        // A power-up can shift blocks so that another row becomes complete: chain it like a normal clear.
        val full = board.fullRows()
        if (full.isNotEmpty()) {
            beginLineClear(full)
            publish()
            return
        }
        phase = Phase.FALLING
        if (piece == null) {
            if (!spawnNext()) return
        } else {
            gravityAcc = 0L
            lockTimer = 0L
        }
        publish()
    }

    private fun addScore(points: Int, x: Float, y: Float) {
        if (points <= 0) return
        score += points
        emit(GameEvent.ScoreAdded(points, x, y))
        val newLevel = LevelSystem.levelForScore(score)
        if (newLevel > level) {
            level = newLevel
            emit(GameEvent.LevelUp(newLevel))
        }
    }

    private fun scaled(baseMs: Long): Long = (baseMs * LevelSystem.animationSpeedFactor(level)).toLong()

    // ------------------------------------------------------------------ spawning & helpers

    private fun refillQueue() {
        while (queue.size < NEXT_COUNT + 1) queue.addLast(bag.next())
    }

    private fun spawnNext(): Boolean {
        refillQueue()
        val type = queue.removeFirst()
        refillQueue()
        val spawn = ActivePiece(type, 0, SPAWN_X, -Tetrominoes.minY(type, 0), ++pieceSerial)
        piece = spawn
        gravityAcc = 0L
        lockTimer = 0L
        lockResets = 0
        lowestY = spawn.y
        if (!board.fits(spawn)) {
            gameOver()
            return false
        }
        return true
    }

    private fun gameOver() {
        phase = Phase.GAME_OVER
        softDrop = false
        targeting = null
        clear = null
        collapse = null
        emit(GameEvent.GameOver)
        publish()
    }

    /** Lock-delay "infinity" rule: moving/rotating on the ground restarts the timer, at most [MAX_LOCK_RESETS] times. */
    private fun onPieceAdjusted() {
        val p = piece ?: return
        if (board.fits(p.copy(y = p.y + 1))) {
            lockTimer = 0L
        } else if (lockResets < MAX_LOCK_RESETS) {
            lockTimer = 0L
            lockResets++
        }
    }

    private fun noteLowest(y: Int) {
        if (y > lowestY) {
            lowestY = y
            lockResets = 0
        }
    }

    private fun ghostY(p: ActivePiece?): Int {
        if (p == null || phase == Phase.GAME_OVER) return -1
        var y = p.y
        while (board.fits(p.type, p.rotation, p.x, y + 1)) y++
        return y
    }

    private fun buildState(): GameState {
        val p = piece
        val c = clear
        val col = collapse
        return GameState(
            revision = ++revision,
            phase = phase,
            board = board.snapshot(),
            piece = p,
            ghostY = ghostY(p),
            next = queue.take(NEXT_COUNT),
            score = score,
            level = level,
            lines = lines,
            combo = combo,
            levelProgress = LevelSystem.progress(score),
            doubleScoreRemainingMs = doubleMs,
            targeting = targeting,
            clear = c?.let { ClearAnimation(it.id, it.kind, it.cells, it.progress, it.focusX, it.focusY, it.column) },
            collapse = col?.let { CollapseAnimation(it.offsets, it.progress) },
        )
    }

    private fun publish() {
        _state.value = buildState()
    }

    private fun emit(event: GameEvent) {
        _events.tryEmit(event)
    }

    // ------------------------------------------------------------------ test hooks

    internal val boardForTest: Board get() = board

    internal fun debugSetPiece(type: PieceType, rotation: Int = 0, x: Int = SPAWN_X, y: Int = 0) {
        piece = ActivePiece(type, rotation, x, y, ++pieceSerial)
        gravityAcc = 0L; lockTimer = 0L; lockResets = 0; lowestY = y
        publish()
    }

    internal fun debugPublish() = publish()
}
