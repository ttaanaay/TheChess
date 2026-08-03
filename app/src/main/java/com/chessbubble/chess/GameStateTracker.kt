package com.chessbubble.chess

/**
 * Holds the single source of truth for "what position are we actually in".
 * Vision only ever supplies a raw 64-square placement snapshot; this class is
 * responsible for turning that into a validated move against real chess rules.
 */
class GameStateTracker(startFen: String = BoardState.START_FEN) {

    var current: BoardState = BoardState.fromFen(startFen)
        private set

    val sanHistory = mutableListOf<String>()

    /**
     * Feed a new recognized placement (64-char array from BoardRecognizer).
     * Returns the resolve outcome; on success, `current` is advanced and the
     * SAN is appended to history.
     */
    fun submitRecognizedPlacement(placement: CharArray): ResolveResult {
        val result = MoveResolver.resolve(current, placement)
        if (result is ResolveResult.Resolved) {
            current = result.newState
            sanHistory.add(result.san)
        }
        return result
    }

    fun reset(fen: String = BoardState.START_FEN) {
        current = BoardState.fromFen(fen)
        sanHistory.clear()
    }
}
