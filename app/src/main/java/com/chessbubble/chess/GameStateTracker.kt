package com.chessbubble.chess

/**
 * Holds the single source of truth for "what position are we actually in".
 * Vision only ever supplies a raw 64-square placement snapshot; this class is
 * responsible for turning that into a validated move against real chess rules.
 *
 * Self-correction: if recognition briefly misreads a square, the resolver
 * might accept a wrong "closest legal move" (see MoveResolver's fuzzy
 * matching), which corrupts `current` as a baseline -- every future frame
 * then fails to match ANY legal move well, since it's being compared against
 * a board that never actually existed, and gets stuck as Ambiguous/NoMatch
 * indefinitely. To recover, if vision reports the SAME placement consistently
 * for a while without it ever resolving to a legal move, we force-adopt that
 * placement directly as the new ground truth instead of staying stuck forever.
 */
class GameStateTracker(startFen: String = BoardState.START_FEN) {

    var current: BoardState = BoardState.fromFen(startFen)
        private set

    val sanHistory = mutableListOf<String>()

    private var lastUnresolvedPlacement: CharArray? = null
    private var unresolvedStreak = 0
    private var stuckSinceMs = 0L

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
                clearStuckState()
            }
            is ResolveResult.NoChange -> {
                clearStuckState()
            }
            else -> { // Ambiguous or NoMatch -- track how long/consistently we've been stuck
                val now = System.currentTimeMillis()
                val lastPlacement = lastUnresolvedPlacement
                if (lastPlacement != null && lastPlacement.contentEquals(placement)) {
                    unresolvedStreak++
                } else {
                    unresolvedStreak = 1
                    lastUnresolvedPlacement = placement.copyOf()
                    stuckSinceMs = now
                }

                if (unresolvedStreak >= RESYNC_MIN_STREAK && (now - stuckSinceMs) >= RESYNC_MIN_STUCK_MS) {
                    android.util.Log.w(
                        "GameStateTracker",
                        "Stuck for ${now - stuckSinceMs}ms with consistent unresolved reads -- resyncing to vision directly"
                    )
                    val resynced = current.deepCopy()
                    for (i in 0 until 64) resynced.board[i] = placement[i]
                    resynced.whiteToMove = !current.whiteToMove // best guess: assume one ply passed
                    current = resynced
                    clearStuckState()
                }
            }
        }
        return result
    }

    private fun clearStuckState() {
        unresolvedStreak = 0
        lastUnresolvedPlacement = null
        stuckSinceMs = 0
    }

    fun reset(fen: String = BoardState.START_FEN) {
        current = BoardState.fromFen(fen)
        sanHistory.clear()
        clearStuckState()
    }

    companion object {
        // How many consecutive frames must show the EXACT SAME unresolved
        // placement before we trust it enough to force a resync.
        private const val RESYNC_MIN_STREAK = 3
        // ...and how long we must have been stuck for, as a safety margin
        // (avoids resyncing on a momentary freeze-frame during fast play).
        private const val RESYNC_MIN_STUCK_MS = 2500L
    }
}
