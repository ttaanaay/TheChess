package com.chessbubble.engine

/**
 * Abstraction over a UCI-speaking chess engine. Plug in a real Stockfish
 * build (see StockfishJniEngine) once you've added the native .so.
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
 * Deterministic placeholder engine so the rest of the app (move quality
 * classification, overlay, GitHub Actions build) works end-to-end before a
 * real Stockfish binary is wired in. Replace with StockfishJniEngine.
 *
 * NOTE: this stub does NOT actually understand chess -- it returns a fixed
 * value. It exists only to keep the pipeline compiling/testable.
 */
class StubEngine : ChessEngine {
    override suspend fun evaluateCp(fen: String, depth: Int, moveTimeMs: Int): Int = 0
    override fun close() {}
}

/**
 * Skeleton for a real Stockfish integration via JNI.
 *
 * To make this real:
 *  1. Add the Stockfish source (or a prebuilt Android JNI wrapper such as the
 *     open-source "stockfish-android" projects) under app/src/main/cpp, with a
 *     CMakeLists.txt, and enable `externalNativeBuild` in app/build.gradle.kts.
 *  2. Implement native methods below that start the engine process/thread,
 *     write UCI commands to it, and read "bestmove"/"info score cp ..." lines.
 *  3. Alternatively, ship a prebuilt Stockfish command-line binary in
 *     jniLibs/<abi>/ and drive it via ProcessBuilder + stdin/stdout instead of
 *     JNI -- simpler to get working first, JNI is faster/cleaner long-term.
 */
class StockfishJniEngine : ChessEngine {

    init {
        // System.loadLibrary("stockfish") // uncomment once the .so is bundled
    }

    override suspend fun evaluateCp(fen: String, depth: Int, moveTimeMs: Int): Int {
        // TODO: nativeSetPosition(fen); return nativeGoAndGetScoreCp(depth, moveTimeMs)
        throw NotImplementedError(
            "Stockfish native library not linked yet. Use StubEngine for now, " +
                "or follow the setup notes in this class's KDoc."
        )
    }

    override fun close() {
        // TODO: nativeQuit()
    }

    // private external fun nativeSetPosition(fen: String)
    // private external fun nativeGoAndGetScoreCp(depth: Int, moveTimeMs: Int): Int
    // private external fun nativeQuit()
}
