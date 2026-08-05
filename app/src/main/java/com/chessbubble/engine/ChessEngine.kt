package com.chessbubble.engine

/**
 * @param scoreCp centipawn score from WHITE's perspective (positive = good for White)
 * @param bestMoveUci the engine's top move in UCI long algebraic form (e.g.
 *        "e2e4", "e7e8q" for promotion, "e1g1" for kingside castling), or
 *        null if the engine had no legal move (checkmate/stalemate) or failed.
 */
data class EngineAnalysis(val scoreCp: Int, val bestMoveUci: String?)

/**
 * Abstraction over a UCI-speaking chess engine. The real implementation is
 * StockfishEngine (drives a bundled Stockfish binary via ProcessBuilder/UCI
 * -- see that file for details on how the binary gets into the APK).
 */
interface ChessEngine {
    /**
     * Analyzes `fen` for the given search depth/time. Mate scores in scoreCp
     * should be mapped to a large sentinel (e.g. +/-100000).
     */
    suspend fun analyze(fen: String, depth: Int = 14, moveTimeMs: Int = 300): EngineAnalysis

    fun close()
}

/**
 * Deterministic placeholder engine (always returns a neutral score, no best
 * move) -- useful for testing the rest of the pipeline without waiting on
 * real engine analysis, or as a quick fallback if StockfishEngine ever fails
 * to start on a given device.
 */
class StubEngine : ChessEngine {
    override suspend fun analyze(fen: String, depth: Int, moveTimeMs: Int) = EngineAnalysis(0, null)
    override fun close() {}
}
