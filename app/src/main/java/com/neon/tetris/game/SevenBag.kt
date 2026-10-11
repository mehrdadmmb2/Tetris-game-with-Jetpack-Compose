package com.neon.tetris.game

import kotlin.random.Random

/** 7-bag randomizer: every run of seven pieces contains each tetromino exactly once. */
class SevenBag(private val random: Random = Random.Default) {
    private val bag = ArrayDeque<PieceType>()

    fun next(): PieceType {
        if (bag.isEmpty()) bag.addAll(PieceType.entries.shuffled(random))
        return bag.removeFirst()
    }
}
