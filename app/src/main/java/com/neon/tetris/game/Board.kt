package com.neon.tetris.game

import com.neon.tetris.domain.BoardSpec

/**
 * The settled blocks. Cells hold a colour id (0 = empty, 1..7 = [PieceType.id]).
 * Rows above the visible area (y < 0) are treated as free so pieces may rotate/kick partly above the top.
 */
class Board(val cols: Int = BoardSpec.COLS, val rows: Int = BoardSpec.ROWS) {

    private val grid = IntArray(cols * rows)

    operator fun get(x: Int, y: Int): Int = grid[y * cols + x]

    fun inBounds(x: Int, y: Int): Boolean = x in 0 until cols && y in 0 until rows

    private fun isFree(x: Int, y: Int): Boolean =
        x in 0 until cols && y < rows && (y < 0 || grid[y * cols + x] == 0)

    fun fits(type: PieceType, rotation: Int, px: Int, py: Int): Boolean {
        val o = Tetrominoes.offsets(type, rotation)
        for (i in 0 until 4) {
            if (!isFree(px + o[i * 2], py + o[i * 2 + 1])) return false
        }
        return true
    }

    fun fits(piece: ActivePiece): Boolean = fits(piece.type, piece.rotation, piece.x, piece.y)

    /** Writes the piece into the grid. Returns false if any cell was above the visible area (and so was dropped). */
    fun place(piece: ActivePiece): Boolean {
        var allVisible = true
        for (c in piece.cells()) {
            if (c.y < 0) allVisible = false
            else if (inBounds(c.x, c.y)) grid[c.y * cols + c.x] = piece.type.id
        }
        return allVisible
    }

    fun fullRows(): List<Int> {
        val result = ArrayList<Int>(4)
        for (y in 0 until rows) {
            var full = true
            for (x in 0 until cols) if (grid[y * cols + x] == 0) { full = false; break }
            if (full) result.add(y)
        }
        return result
    }

    fun filledCount(): Int = grid.count { it != 0 }

    fun countFilled(positions: Collection<Cell>): Int =
        positions.count { inBounds(it.x, it.y) && grid[it.y * cols + it.x] != 0 }

    /**
     * Removes the blocks at [positions] and lets everything above them in the same column drop by the number
     * of removed positions beneath it. For whole rows this is exactly classic line-clear gravity; for a column,
     * 3×3 area or colour it keeps the rest of the stack intact.
     * Returns, for every cell index in the new layout, how many rows that block fell (0 = did not move).
     */
    fun removeRegion(positions: Collection<Cell>): IntArray {
        val offsets = IntArray(cols * rows)
        val removed = BooleanArray(cols * rows)
        for (c in positions) if (inBounds(c.x, c.y)) removed[c.y * cols + c.x] = true
        for (x in 0 until cols) {
            var below = 0
            for (y in rows - 1 downTo 0) {
                val i = y * cols + x
                if (removed[i]) {
                    grid[i] = 0
                    below++
                } else if (below > 0 && grid[i] != 0) {
                    val t = (y + below) * cols + x
                    grid[t] = grid[i]
                    grid[i] = 0
                    offsets[t] = below
                }
            }
        }
        return offsets
    }

    fun snapshot(): IntArray = grid.copyOf()

    fun load(cells: IntArray) {
        require(cells.size == grid.size)
        cells.copyInto(grid)
    }

    fun clear() = grid.fill(0)

    /** Test/debug helper: rows given top to bottom, '.' empty, digit/letter = piece id. */
    fun loadRows(lines: List<String>) {
        clear()
        val startY = rows - lines.size
        lines.forEachIndexed { r, line ->
            line.forEachIndexed { x, ch ->
                if (x < cols && ch != '.' && ch != ' ') {
                    grid[(startY + r) * cols + x] = if (ch.isDigit()) ch - '0' else 1
                }
            }
        }
    }
}
