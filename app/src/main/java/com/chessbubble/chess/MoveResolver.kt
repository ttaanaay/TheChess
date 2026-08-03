package com.chessbubble.chess

sealed class ResolveResult {
    data class Resolved(val move: Move, val san: String, val newState: BoardState) : ResolveResult()
    /** Vision output didn't match any legal move from the previous state (misread square, missed a move, etc). */
    object NoMatch : ResolveResult()
    /** Board identical to before (no move happened yet, or two frames captured same position). */
    object NoChange : ResolveResult()
    /** More than one legal move produces the same resulting placement (very rare; e.g. ambiguous promotion misread). */
    data class Ambiguous(val candidates: List<Move>) : ResolveResult()
}

object MoveResolver {

    /**
     * @param previous last confirmed BoardState (with correct side-to-move/rights/ep tracked internally)
     * @param recognizedPlacement 64-length CharArray from vision, piece-placement only
     */
    fun resolve(previous: BoardState, recognizedPlacement: CharArray): ResolveResult {
        if (previous.board.contentEquals(recognizedPlacement)) return ResolveResult.NoChange

        val legal = MoveGen.legalMoves(previous)
        val matches = legal.filter { m ->
            MoveGen.applyMove(previous, m).samePlacementAs(recognizedPlacement)
        }

        return when {
            matches.isEmpty() -> ResolveResult.NoMatch
            matches.size == 1 -> {
                val m = matches[0]
                val san = MoveGen.toSan(previous, m)
                val newState = MoveGen.applyMove(previous, m)
                ResolveResult.Resolved(m, san, newState)
            }
            else -> ResolveResult.Ambiguous(matches)
        }
    }
}
