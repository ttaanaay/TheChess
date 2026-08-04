package com.chessbubble.chess

/**
 * Holds the single source of truth for "what position are we actually in".
 * Vision only ever supplies a raw 64-square placement snapshot; this class is
 * responsible for turning that into a validated move against real chess rules.
 *
 * Self-correction: if recognition briefly misreads a square (or jitters
 * between two readings of the same physical square from frame to frame), the
 * resolver can fail to match ANY legal move, and `current` gets stuck as a
 * stale baseline -- every future frame then compares against a position that
 * no longer reflects reality. Unlike a first attempt at this that required
 * seeing the SAME unresolved placement repeated several times before
 * recovering (which never triggers if the real board keeps changing while
 * we're stuck, e.g. the player keeps moving during the confusion), this
 * version just tracks how long it's been since we last had a confirmed good
 * state and force-resyncs to whatever vision currently reports once that
 * exceeds a threshold, regardless of whether readings are stable.
 */
class GameStateTracker(startFen: String = BoardState.START_FEN) {

    var current: BoardState = BoardState.fromFen(startFen)
        private set

    val sanHistory = mutableListOf<String>()

    private var lastGoodStateAt = System.currentTimeMillis()

    /**
     * Feed a new recognized placement (64-char array from BoardRecognizer).
     * Returns the resolve outcome; on success, `current` is advanced and the
     * SAN is appended to history.
     */
    fun submitRecognizedPlacement(placement: CharArray): ResolveResult {
        val result = MoveResolver.resolve(current, placement)

        when (result) {
            is ResolveResult.Resolved -> {
                current = result.newState
                sanHistory.add(result.san)
                lastGoodStateAt = System.currentTimeMillis()
            }
            is ResolveResult.NoChange -> {
                lastGoodStateAt = System.currentTimeMillis()
            }
            else -> { // Ambiguous or NoMatch
                val stuckForMs = System.currentTimeMillis() - lastGoodStateAt
                if (stuckForMs >= RESYNC_STUCK_THRESHOLD_MS) {
                    android.util.Log.w(
                        "GameStateTracker",
                        "Stuck for ${stuckForMs}ms without a confirmed state -- resyncing to latest vision reading"
                    )
                    val resynced = current.deepCopy()
                    for (i in 0 until 64) resynced.board[i] = placement[i]
                    resynced.whiteToMove = !current.whiteToMove // best guess: assume one ply passed
                    current = resynced
                    lastGoodStateAt = System.currentTimeMillis()
                }
            }
        }
        return result
    }

    fun reset(fen: String = BoardState.START_FEN) {
        current = BoardState.fromFen(fen)
        sanHistory.clear()
        lastGoodStateAt = System.currentTimeMillis()
    }

    companion object {
        // How long we tolerate being stuck (Ambiguous/NoMatch) before giving up
        // on matching a legal move and just trusting vision directly.
        private const val RESYNC_STUCK_THRESHOLD_MS = 3000L
    }
}
