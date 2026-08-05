package com.chessbubble.engine

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter

/**
 * Drives a bundled Stockfish binary (app/src/main/jniLibs/arm64-v8a/libstockfish.so
 * -- a real executable disguised as a "native library" so Android extracts it
 * to disk with execute permission; see app/build.gradle.kts for why) as a
 * persistent subprocess speaking the UCI protocol over stdin/stdout.
 *
 * The binary itself is NOT committed to this repo (it's ~114MB, over
 * GitHub's 100MB push limit) -- it's downloaded fresh from the official
 * Stockfish release by the GitHub Actions workflow on every build.
 */
class StockfishEngine(context: Context) : ChessEngine {

    private val binaryPath = File(context.applicationInfo.nativeLibraryDir, "libstockfish.so").absolutePath
    private var process: Process? = null
    private var writer: OutputStreamWriter? = null
    private var reader: BufferedReader? = null
    private val mutex = Mutex()
    private var ready = false

    private suspend fun ensureStarted() {
        if (ready) return
        withContext(Dispatchers.IO) {
            try {
                File(binaryPath).setExecutable(true)
                val proc = ProcessBuilder(binaryPath)
                    .redirectErrorStream(true)
                    .start()
                process = proc
                writer = OutputStreamWriter(proc.outputStream)
                reader = BufferedReader(InputStreamReader(proc.inputStream))

                send("uci")
                readUntil { it == "uciok" }
                // Keep resource usage modest -- this runs alongside screen capture
                // and image recognition on the same phone.
                send("setoption name Threads value 2")
                send("setoption name Hash value 64")
                send("isready")
                readUntil { it == "readyok" }
                ready = true
                android.util.Log.d(TAG, "Stockfish started successfully at $binaryPath")
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to start Stockfish binary at $binaryPath", e)
                ready = false
            }
        }
    }

    override suspend fun analyze(fen: String, depth: Int, moveTimeMs: Int): EngineAnalysis {
        mutex.withLock {
            ensureStarted()
            val proc = process
            if (proc == null || !ready) return EngineAnalysis(0, null) // engine unavailable -- neutral, no crash

            return withContext(Dispatchers.IO) {
                try {
                    send("position fen $fen")
                    send("go movetime $moveTimeMs")

                    var lastScoreCp: Int? = null
                    var bestMoveUci: String? = null
                    readUntil { line ->
                        if (line.startsWith("info") && line.contains(" score ")) {
                            parseScoreCp(line, fen)?.let { lastScoreCp = it }
                        }
                        if (line.startsWith("bestmove")) {
                            val parts = line.trim().split(" ")
                            bestMoveUci = parts.getOrNull(1)?.takeIf { it != "(none)" }
                            true
                        } else {
                            false
                        }
                    }
                    EngineAnalysis(lastScoreCp ?: 0, bestMoveUci)
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Error evaluating position, restarting engine next call", e)
                    ready = false
                    EngineAnalysis(0, null)
                }
            }
        }
    }

    /**
     * UCI "score cp X" (or "score mate X") is always from the perspective of
     * the side to move IN THE GIVEN POSITION -- this interface's contract
     * (see ChessEngine) requires White's perspective, so flip sign when it
     * was Black to move.
     */
    private fun parseScoreCp(infoLine: String, fen: String): Int? {
        val tokens = infoLine.trim().split(" ")
        val scoreIdx = tokens.indexOf("score")
        if (scoreIdx == -1 || scoreIdx + 2 >= tokens.size) return null

        val kind = tokens[scoreIdx + 1] // "cp" or "mate"
        val value = tokens[scoreIdx + 2].toIntOrNull() ?: return null

        val sideToMoveWhite = fen.trim().split(" ").getOrElse(1) { "w" } == "w"
        val fromSideToMovePerspective = when (kind) {
            "cp" -> value
            "mate" -> if (value >= 0) MATE_SCORE_CP else -MATE_SCORE_CP
            else -> return null
        }
        return if (sideToMoveWhite) fromSideToMovePerspective else -fromSideToMovePerspective
    }

    private fun send(command: String) {
        writer?.apply {
            write(command)
            write("\n")
            flush()
        }
    }

    /** Reads lines until [stop] returns true for a line (inclusive), ignoring lines it doesn't need. */
    private inline fun readUntil(stop: (String) -> Boolean) {
        val r = reader ?: return
        while (true) {
            val line = r.readLine() ?: break
            if (stop(line)) break
        }
    }

    override fun close() {
        runCatching {
            send("quit")
            process?.destroy()
        }
        process = null
        ready = false
    }

    companion object {
        private const val TAG = "StockfishEngine"
        private const val MATE_SCORE_CP = 100000
    }
}
