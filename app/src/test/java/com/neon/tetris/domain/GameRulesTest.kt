package com.neon.tetris.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LevelSystemTest {

    @Test
    fun requiredScoreFollowsLevelTimes100() {
        assertEquals(100, LevelSystem.requiredScore(1))
        assertEquals(200, LevelSystem.requiredScore(2))
        assertEquals(300, LevelSystem.requiredScore(3))
        assertEquals(10_000, LevelSystem.requiredScore(100))
    }

    @Test
    fun levelForScoreUsesCumulativeThresholds() {
        assertEquals(1, LevelSystem.levelForScore(0))
        assertEquals(1, LevelSystem.levelForScore(99))
        assertEquals(2, LevelSystem.levelForScore(100))
        assertEquals(3, LevelSystem.levelForScore(200))
        assertEquals(99, LevelSystem.levelForScore(9_899))
        assertEquals(100, LevelSystem.levelForScore(9_900))
        assertEquals(100, LevelSystem.levelForScore(10_000))
        assertEquals(100, LevelSystem.levelForScore(1_000_000))
    }

    @Test
    fun progressIsMeasuredInsideTheCurrentLevel() {
        assertEquals(0f, LevelSystem.progress(0), 1e-6f)
        assertEquals(0.5f, LevelSystem.progress(50), 1e-6f)
        assertEquals(0f, LevelSystem.progress(100), 1e-6f)
        assertEquals(0.5f, LevelSystem.progress(150), 1e-6f)
        assertEquals(1f, LevelSystem.progress(10_000), 1e-6f)
    }

    @Test
    fun fallingGetsFasterWithEveryLevel() {
        assertEquals(800L, LevelSystem.gravityIntervalMs(1))
        var previous = Long.MAX_VALUE
        for (level in 1..LevelSystem.MAX_LEVEL) {
            val interval = LevelSystem.gravityIntervalMs(level)
            assertTrue("level $level", interval <= previous && interval >= 50)
            previous = interval
        }
        assertTrue(LevelSystem.gravityIntervalMs(10) < LevelSystem.gravityIntervalMs(5))
    }
}

class ScoreRulesTest {

    @Test
    fun everyClearedCellIsWorthPoints() {
        // 1 line = 10 cells * 5 points
        assertEquals(50, ScoreRules.lineClearPoints(1, 1, false))
        assertEquals(150, ScoreRules.lineClearPoints(2, 1, false))
        assertEquals(300, ScoreRules.lineClearPoints(3, 1, false))
        assertEquals(600, ScoreRules.lineClearPoints(4, 1, false))
    }

    @Test
    fun comboMakesConsecutiveClearsWorthMore() {
        assertEquals(50, ScoreRules.lineClearPoints(1, 1, false))
        assertEquals(75, ScoreRules.lineClearPoints(1, 2, false))
        assertEquals(100, ScoreRules.lineClearPoints(1, 3, false))
        assertEquals(125, ScoreRules.lineClearPoints(1, 4, false))
    }

    @Test
    fun comboMultiplierIsCapped() {
        assertEquals(ScoreRules.comboMultiplier(ScoreRules.MAX_COMBO_STEPS), ScoreRules.comboMultiplier(99), 1e-6f)
    }

    @Test
    fun doubleScoreDoublesEverything() {
        assertEquals(100, ScoreRules.lineClearPoints(1, 1, true))
        assertEquals(30, ScoreRules.powerUpPoints(5, true))
        assertEquals(15, ScoreRules.powerUpPoints(5, false))
    }
}
