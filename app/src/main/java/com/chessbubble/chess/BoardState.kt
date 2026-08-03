package com.chessbubble.chess

/**
 * 0x88-free simple 8x8 board. Index = rank * 8 + file.
 * rank 0 = rank "1" (White's back rank), file 0 = file "a".
 * Piece chars: standard FEN letters, uppercase = White, lowercase = Black, '.' = empty.
 */
data class CastlingRights(
    var whiteKingSide: Boolean = true,
    var whiteQueenSide: Boolean = true,
    var blackKingSide: Boolean = true,
    var blackQueenSide: Boolean = true
) {
    fun copy2() = CastlingRights(whiteKingSide, whiteQueenSide, blackKingSide, blackQueenSide)
}

class BoardState(
    val board: CharArray = CharArray(64) { '.' },
    var whiteToMove: Boolean = true,
    var castling: CastlingRights = CastlingRights(),
    /** square index behind a pawn that just double-pushed, or -1 */
    var epSquare: Int = -1,
    var halfmoveClock: Int = 0,
    var fullmoveNumber: Int = 1
) {
    fun deepCopy(): BoardState = BoardState(
        board.copyOf(), whiteToMove, castling.copy2(), epSquare, halfmoveClock, fullmoveNumber
    )

    fun pieceAt(square: Int): Char = board[square]

    fun isWhitePiece(c: Char) = c != '.' && c.isUpperCase()
    fun isBlackPiece(c: Char) = c != '.' && c.isLowerCase()

    /** Compares only piece placement, ignoring side-to-move/rights/clocks. Used to match vision snapshots. */
    fun samePlacementAs(other: CharArray): Boolean = board.contentEquals(other)

    companion object {
        fun file(sq: Int) = sq % 8
        fun rank(sq: Int) = sq / 8
        fun squareOf(file: Int, rank: Int) = rank * 8 + file
        fun squareName(sq: Int): String = "${('a' + file(sq))}${rank(sq) + 1}"
        fun squareFromName(name: String): Int {
            val f = name[0] - 'a'
            val r = name[1] - '1'
            return squareOf(f, r)
        }

        fun fromFen(fen: String): BoardState {
            val parts = fen.trim().split(" ")
            val placement = parts[0]
            val board = CharArray(64) { '.' }
            var rank = 7
            var file = 0
            for (ch in placement) {
                when {
                    ch == '/' -> { rank--; file = 0 }
                    ch.isDigit() -> file += ch.digitToInt()
                    else -> { board[squareOf(file, rank)] = ch; file++ }
                }
            }
            val whiteToMove = parts.getOrElse(1) { "w" } == "w"
            val castlingStr = parts.getOrElse(2) { "-" }
            val castling = CastlingRights(
                whiteKingSide = castlingStr.contains('K'),
                whiteQueenSide = castlingStr.contains('Q'),
                blackKingSide = castlingStr.contains('k'),
                blackQueenSide = castlingStr.contains('q')
            )
            val epStr = parts.getOrElse(3) { "-" }
            val ep = if (epStr == "-") -1 else squareFromName(epStr)
            val half = parts.getOrElse(4) { "0" }.toIntOrNull() ?: 0
            val full = parts.getOrElse(5) { "1" }.toIntOrNull() ?: 1
            return BoardState(board, whiteToMove, castling, ep, half, full)
        }

        val START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
    }

    fun toFen(): String {
        val sb = StringBuilder()
        for (rank in 7 downTo 0) {
            var emptyCount = 0
            for (file in 0 until 8) {
                val c = board[squareOf(file, rank)]
                if (c == '.') {
                    emptyCount++
                } else {
                    if (emptyCount > 0) { sb.append(emptyCount); emptyCount = 0 }
                    sb.append(c)
                }
            }
            if (emptyCount > 0) sb.append(emptyCount)
            if (rank > 0) sb.append('/')
        }
        sb.append(if (whiteToMove) " w " else " b ")
        val c = buildString {
            if (castling.whiteKingSide) append('K')
            if (castling.whiteQueenSide) append('Q')
            if (castling.blackKingSide) append('k')
            if (castling.blackQueenSide) append('q')
        }
        sb.append(if (c.isEmpty()) "-" else c)
        sb.append(' ')
        sb.append(if (epSquare == -1) "-" else squareName(epSquare))
        sb.append(' ').append(halfmoveClock).append(' ').append(fullmoveNumber)
        return sb.toString()
    }

    /** Piece-placement-only FEN field, used as the direct output of vision recognition. */
    fun toPlacementFen(): String = toFen().substringBefore(' ')
}
