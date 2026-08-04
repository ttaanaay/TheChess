package com.chessbubble.chess

sealed class ResolveResult {
    data class Resolved(val move: Move, val san: String, val newState: BoardState, val visionErrorSquares: Int) : ResolveResult()
    /** Best candidate still differed too much from what vision saw -- likely no move has happened yet, or recognition failed badly this frame. */
    object NoMatch : ResolveResult()
    /** Board identical to before (no move happened yet, or two frames captured same position). */
    object NoChange : ResolveResult()
    /** Two or more legal moves are roughly equally close to what vision saw; can't tell them apart confidently. */
    data class Ambiguous(val candidates: List<Move>) : ResolveResult()
}

/**
 * Vision recognition is never pixel-perfect (see BoardRecognizer) -- individual
 * squares get misread occasionally even with good calibration/templates. Instead
 * of requiring an EXACT 64-square match (which breaks on a single misread
 * square), this resolver picks the legal move whose resulting position is
 * CLOSEST (fewest differing squares) to what vision reported, as long as it's
 * close enough and clearly better than the next-best candidate.
 */
object MoveResolver {

    // How many misread squares we're willing to tolerate on the winning candidate.
    private const val MAX_ACCEPTABLE_DISTANCE = 10
    // The winner must beat the runner-up by at least this many squares, or we
    // can't confidently tell which move was actually played.
    private const val MIN_MARGIN_OVER_RUNNER_UP = 2

    /**
     * @param previous last confirmed BoardState (with correct side-to-move/rights/ep tracked internally)
     * @param recognizedPlacement 64-length CharArray from vision, piece-placement only
     */
    fun resolve(previous: BoardState, recognizedPlacement: CharArray): ResolveResult {
        if (previous.board.contentEquals(recognizedPlacement)) return ResolveResult.NoChange

        val legal = MoveGen.legalMoves(previous)
        if (legal.isEmpty()) return ResolveResult.NoMatch

        val scored = legal.map { m ->
            val resultState = MoveGen.applyMove(previous, m)
            Triple(m, resultState, hammingDistance(resultState.board, recognizedPlacement))
        }.sortedBy { it.third }

        val (bestMove, bestState, bestDistance) = scored[0]
        val runnerUpDistance = scored.getOrNull(1)?.third ?: Int.MAX_VALUE

        return when {
            bestDistance > MAX_ACCEPTABLE_DISTANCE -> ResolveResult.NoMatch
            (runnerUpDistance - bestDistance) < MIN_MARGIN_OVER_RUNNER_UP ->
                ResolveResult.Ambiguous(scored.filter { it.third == bestDistance }.map { it.first })
            else -> {
                val san = MoveGen.toSan(previous, bestMove)
                ResolveResult.Resolved(bestMove, san, bestState, bestDistance)
            }
        }
    }

    private fun hammingDistance(a: CharArray, b: CharArray): Int {
        var d = 0
        for (i in a.indices) if (a[i] != b[i]) d++
        return d
    }
}
