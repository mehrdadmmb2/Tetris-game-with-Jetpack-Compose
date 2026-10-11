package com.neon.tetris.game

/** The seven tetrominoes. [id] doubles as the colour index stored in board cells (0 = empty). */
enum class PieceType(val id: Int) {
    I(1), O(2), T(3), S(4), Z(5), J(6), L(7);

    companion object {
        fun fromId(id: Int): PieceType? = entries.firstOrNull { it.id == id }
    }
}

data class Cell(val x: Int, val y: Int)

/** A falling piece. [x]/[y] is the top-left corner of its rotation box; [serial] is unique per spawned piece. */
data class ActivePiece(
    val type: PieceType,
    val rotation: Int,
    val x: Int,
    val y: Int,
    val serial: Int = 0,
) {
    fun cells(): List<Cell> {
        val o = Tetrominoes.offsets(type, rotation)
        return List(4) { Cell(x + o[it * 2], y + o[it * 2 + 1]) }
    }
}

/** Shapes (4 rotation states each) and Super Rotation System wall-kick data. */
object Tetrominoes {

    // x,y pairs inside the rotation box. Order matches PieceType.entries: I, O, T, S, Z, J, L.
    private val SHAPES: Array<Array<IntArray>> = arrayOf(
        arrayOf( // I (4x4 box)
            intArrayOf(0, 1, 1, 1, 2, 1, 3, 1),
            intArrayOf(2, 0, 2, 1, 2, 2, 2, 3),
            intArrayOf(0, 2, 1, 2, 2, 2, 3, 2),
            intArrayOf(1, 0, 1, 1, 1, 2, 1, 3),
        ),
        Array(4) { intArrayOf(1, 0, 2, 0, 1, 1, 2, 1) }, // O
        arrayOf( // T
            intArrayOf(1, 0, 0, 1, 1, 1, 2, 1),
            intArrayOf(1, 0, 1, 1, 2, 1, 1, 2),
            intArrayOf(0, 1, 1, 1, 2, 1, 1, 2),
            intArrayOf(1, 0, 0, 1, 1, 1, 1, 2),
        ),
        arrayOf( // S
            intArrayOf(1, 0, 2, 0, 0, 1, 1, 1),
            intArrayOf(1, 0, 1, 1, 2, 1, 2, 2),
            intArrayOf(1, 1, 2, 1, 0, 2, 1, 2),
            intArrayOf(0, 0, 0, 1, 1, 1, 1, 2),
        ),
        arrayOf( // Z
            intArrayOf(0, 0, 1, 0, 1, 1, 2, 1),
            intArrayOf(2, 0, 1, 1, 2, 1, 1, 2),
            intArrayOf(0, 1, 1, 1, 1, 2, 2, 2),
            intArrayOf(1, 0, 0, 1, 1, 1, 0, 2),
        ),
        arrayOf( // J
            intArrayOf(0, 0, 0, 1, 1, 1, 2, 1),
            intArrayOf(1, 0, 2, 0, 1, 1, 1, 2),
            intArrayOf(0, 1, 1, 1, 2, 1, 2, 2),
            intArrayOf(1, 0, 1, 1, 0, 2, 1, 2),
        ),
        arrayOf( // L
            intArrayOf(2, 0, 0, 1, 1, 1, 2, 1),
            intArrayOf(1, 0, 1, 1, 1, 2, 2, 2),
            intArrayOf(0, 1, 1, 1, 2, 1, 0, 2),
            intArrayOf(0, 0, 1, 0, 1, 1, 1, 2),
        ),
    )

    // SRS kick tests as (dx, dy) pairs, y pointing UP (the usual published tables).
    // Indexed by the rotation state we are rotating FROM.
    private val JLSTZ_CW = arrayOf(
        intArrayOf(0, 0, -1, 0, -1, 1, 0, -2, -1, -2),  // 0 -> 1
        intArrayOf(0, 0, 1, 0, 1, -1, 0, 2, 1, 2),      // 1 -> 2
        intArrayOf(0, 0, 1, 0, 1, 1, 0, -2, 1, -2),     // 2 -> 3
        intArrayOf(0, 0, -1, 0, -1, -1, 0, 2, -1, 2),   // 3 -> 0
    )
    private val JLSTZ_CCW = arrayOf(
        intArrayOf(0, 0, 1, 0, 1, 1, 0, -2, 1, -2),     // 0 -> 3
        intArrayOf(0, 0, 1, 0, 1, -1, 0, 2, 1, 2),      // 1 -> 0
        intArrayOf(0, 0, -1, 0, -1, 1, 0, -2, -1, -2),  // 2 -> 1
        intArrayOf(0, 0, -1, 0, -1, -1, 0, 2, -1, 2),   // 3 -> 2
    )
    private val I_CW = arrayOf(
        intArrayOf(0, 0, -2, 0, 1, 0, -2, -1, 1, 2),    // 0 -> 1
        intArrayOf(0, 0, -1, 0, 2, 0, -1, 2, 2, -1),    // 1 -> 2
        intArrayOf(0, 0, 2, 0, -1, 0, 2, 1, -1, -2),    // 2 -> 3
        intArrayOf(0, 0, 1, 0, -2, 0, 1, -2, -2, 1),    // 3 -> 0
    )
    private val I_CCW = arrayOf(
        intArrayOf(0, 0, -1, 0, 2, 0, -1, 2, 2, -1),    // 0 -> 3
        intArrayOf(0, 0, 2, 0, -1, 0, 2, 1, -1, -2),    // 1 -> 0
        intArrayOf(0, 0, 1, 0, -2, 0, 1, -2, -2, 1),    // 2 -> 1
        intArrayOf(0, 0, -2, 0, 1, 0, -2, -1, 1, 2),    // 3 -> 2
    )

    /** Flat x,y offsets of the four cells for a rotation state. */
    fun offsets(type: PieceType, rotation: Int): IntArray = SHAPES[type.ordinal][rotation and 3]

    /** Side length of the (square) rotation box. */
    fun boxSize(type: PieceType): Int = if (type == PieceType.I || type == PieceType.O) 4 else 3

    fun minY(type: PieceType, rotation: Int): Int {
        val o = offsets(type, rotation)
        return minOf(minOf(o[1], o[3]), minOf(o[5], o[7]))
    }

    /**
     * Kick tests for rotating from [from]. Returned flat as dx,dy pairs, y pointing UP,
     * so callers apply `x + dx`, `y - dy`.
     */
    fun kicks(type: PieceType, from: Int, clockwise: Boolean): IntArray {
        val table = if (type == PieceType.I) {
            if (clockwise) I_CW else I_CCW
        } else {
            if (clockwise) JLSTZ_CW else JLSTZ_CCW
        }
        return table[from and 3]
    }
}
