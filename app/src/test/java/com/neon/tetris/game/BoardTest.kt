package com.neon.tetris.game

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BagTest {
    @Test
    fun everySevenPiecesContainEachTypeExactlyOnce() {
        val bag = SevenBag(Random(1234))
        repeat(100) {
            val seven = List(7) { bag.next() }
            assertEquals(7, seven.toSet().size)
        }
    }
}

class TetrominoTest {

    private fun cellSet(type: PieceType, rotation: Int): Set<Pair<Int, Int>> {
        val o = Tetrominoes.offsets(type, rotation)
        return (0 until 4).map { o[it * 2] to o[it * 2 + 1] }.toSet()
    }

    @Test
    fun everyShapeHasFourDistinctCellsInsideItsBox() {
        for (type in PieceType.entries) for (r in 0..3) {
            val cells = cellSet(type, r)
            assertEquals("$type/$r", 4, cells.size)
            val box = Tetrominoes.boxSize(type)
            assertTrue(cells.all { it.first in 0 until box && it.second in 0 until box })
        }
    }

    @Test
    fun nextRotationStateIsTheClockwiseTurnOfTheBox() {
        for (type in PieceType.entries) {
            if (type == PieceType.O) continue
            val n = Tetrominoes.boxSize(type)
            for (r in 0..3) {
                val turned = cellSet(type, r).map { (x, y) -> (n - 1 - y) to x }.toSet()
                assertEquals("$type $r -> ${(r + 1) % 4}", cellSet(type, (r + 1) % 4), turned)
            }
        }
    }

    @Test
    fun oPieceNeverChanges() {
        for (r in 1..3) assertEquals(cellSet(PieceType.O, 0), cellSet(PieceType.O, r))
    }

    @Test
    fun kickTablesAreMirroredBetweenOppositeRotations() {
        for (type in listOf(PieceType.T, PieceType.I)) {
            for (from in 0..3) {
                val cw = Tetrominoes.kicks(type, from, true)
                val back = Tetrominoes.kicks(type, (from + 1) % 4, false)
                assertEquals(10, cw.size)
                assertEquals(0, cw[0]); assertEquals(0, cw[1]) // first test is always "no kick"
                for (i in cw.indices) assertEquals("$type from $from idx $i", -cw[i], back[i])
            }
        }
    }
}

class BoardTest {

    @Test
    fun wallsAndFloorBlockPieces() {
        val b = Board()
        assertFalse(b.fits(PieceType.I, 0, -1, 5))
        assertTrue(b.fits(PieceType.I, 0, 0, 5))
        assertTrue(b.fits(PieceType.I, 0, 6, 5))
        assertFalse(b.fits(PieceType.I, 0, 7, 5))
        assertTrue(b.fits(PieceType.O, 0, 4, 18))
        assertFalse(b.fits(PieceType.O, 0, 4, 19))
    }

    @Test
    fun spaceAboveTheTopIsFree() {
        val b = Board()
        assertTrue(b.fits(PieceType.I, 1, 3, -3))
    }

    @Test
    fun placedBlocksCollide() {
        val b = Board()
        val piece = ActivePiece(PieceType.O, 0, 4, 18)
        assertTrue(b.place(piece))
        assertFalse(b.fits(piece))
        assertFalse(b.fits(PieceType.O, 0, 4, 17))
        assertTrue(b.fits(PieceType.O, 0, 4, 16))
        assertEquals(4, b.filledCount())
    }

    @Test
    fun placingAboveTheTopReportsLockOut() {
        val b = Board()
        assertFalse(b.place(ActivePiece(PieceType.I, 1, 3, -2)))
    }

    @Test
    fun fullRowsAreDetected() {
        val b = Board()
        b.loadRows(listOf("1111111111", "1.11111111", "2222222222"))
        assertEquals(listOf(17, 19), b.fullRows())
    }

    @Test
    fun removingWholeRowsIsClassicLineClearGravity() {
        val b = Board()
        b.loadRows(listOf("3.........", "1111111111", "2.2......."))
        val offsets = b.removeRegion((0 until 10).map { Cell(it, 18) })
        assertEquals(3, b[0, 18])          // block above fell one row into the gap
        assertEquals(1, offsets[18 * 10])
        assertEquals(0, b[0, 17])
        assertEquals(2, b[0, 19])          // blocks below did not move
        assertEquals(0, offsets[19 * 10])
        assertEquals(3, b.filledCount())
    }

    @Test
    fun removeRegionKeepsOrderAndCountsInEveryColumn() {
        val rnd = Random(7)
        repeat(300) {
            val b = Board()
            val cells = IntArray(200) { if (rnd.nextInt(100) < 50) 1 + rnd.nextInt(7) else 0 }
            b.load(cells)
            val removed = HashSet<Cell>()
            repeat(rnd.nextInt(0, 40)) { removed.add(Cell(rnd.nextInt(10), rnd.nextInt(20))) }

            val expected = (0 until 10).map { x ->
                (0 until 20).filter { y -> Cell(x, y) !in removed && cells[y * 10 + x] != 0 }.map { y -> cells[y * 10 + x] }
            }
            val offsets = b.removeRegion(removed)

            for (x in 0 until 10) {
                val actual = (0 until 20).map { y -> b[x, y] }.filter { it != 0 }
                assertEquals(expected[x], actual)
                for (y in 0 until 20) {
                    val k = offsets[y * 10 + x]
                    if (k > 0) {
                        assertEquals(cells[(y - k) * 10 + x], b[x, y])
                        val removedBelow = ((y - k + 1) until 20).count { Cell(x, it) in removed }
                        assertEquals(k, removedBelow)
                    }
                }
            }
        }
    }

    @Test
    fun snapshotIsACopy() {
        val b = Board()
        val s = b.snapshot()
        b.place(ActivePiece(PieceType.O, 0, 0, 0))
        assertArrayEquals(IntArray(200), s)
    }
}
