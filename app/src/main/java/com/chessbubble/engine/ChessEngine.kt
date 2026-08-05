package com.chessbubble.engine

/**
 * Abstraction over a UCI-speaking chess engine. The real implementation is
 * StockfishEngine (drives a bundled Stockfish binary via ProcessBuilder/UCI
 * -- see that file for details on how the binary gets into the APK).
 */
interface ChessEngine {
    /**
     * Evaluates `fen` to the given search depth/time and returns the score in
     * centipawns from WHITE's perspective (positive = good for White).
     * Mate scores should be mapped to a large sentinel (e.g. +/-100000).
     */
    suspend fun evaluateCp(fen: String, depth: Int = 14, moveTimeMs: Int = 300): Int

    fun close()
}

/**
 * Deterministic placeholder engine (always returns 0) -- useful for testing
 * the rest of the pipeline without waiting on real engine analysis, or as a
 * quick fallback if StockfishEngine ever fails to start on a given device.
 */
class StubEngine : ChessEngine {
    override suspend fun evaluateCp(fen: String, depth: Int, moveTimeMs: Int): Int = 0
    override fun close() {}
}
