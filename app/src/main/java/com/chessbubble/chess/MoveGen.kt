package com.chessbubble.chess

import com.chessbubble.chess.BoardState.Companion.file
import com.chessbubble.chess.BoardState.Companion.rank
import com.chessbubble.chess.BoardState.Companion.squareOf

/**
 * Standard legal chess move generation (castling, en passant, promotion,
 * check-safety filtering). No perft-level micro-optimization; clarity first,
 * this only needs to run once per recognized move (a few times per second at most).
 */
object MoveGen {

    private val KNIGHT_DELTAS = listOf(-17, -15, -10, -6, 6, 10, 15, 17)
    private val KING_DELTAS = listOf(-9, -8, -7, -1, 1, 7, 8, 9)
    private val BISHOP_DIRS = listOf(-9, -7, 7, 9)
    private val ROOK_DIRS = listOf(-8, -1, 1, 8)

    private fun onBoardDelta(from: Int, to: Int): Boolean {
        if (to < 0 || to > 63) return false
        val fFile = file(from); val tFile = file(to)
        return kotlin.math.abs(fFile - tFile) <= 2 // guards wraparound for knight/king deltas
    }

    fun isSquareAttacked(b: BoardState, square: Int, byWhite: Boolean): Boolean {
        val f = file(square); val r = rank(square)

        // Pawn attacks
        val pawnChar = if (byWhite) 'P' else 'p'
        val dr = if (byWhite) -1 else 1 // attacker's pawn sits `dr` ranks away from target
        for (df in intArrayOf(-1, 1)) {
            val af = f + df; val ar = r + dr
            if (af in 0..7 && ar in 0..7) {
                if (b.board[squareOf(af, ar)] == pawnChar) return true
            }
        }

        // Knight attacks
        val knightChar = if (byWhite) 'N' else 'n'
        for (d in KNIGHT_DELTAS) {
            val to = square + d
            if (onBoardDelta(square, to) && to in 0..63 && b.board[to] == knightChar) return true
        }

        // King attacks
        val kingChar = if (byWhite) 'K' else 'k'
        for (d in KING_DELTAS) {
            val to = square + d
            if (onBoardDelta(square, to) && to in 0..63 && b.board[to] == kingChar) return true
        }

        // Sliding: bishop/queen diagonals
        val bishopChar = if (byWhite) 'B' else 'b'
        val rookChar = if (byWhite) 'R' else 'r'
        val queenChar = if (byWhite) 'Q' else 'q'
        for (d in BISHOP_DIRS) {
            var to = square
            var prevFile = f
            while (true) {
                to += d
                if (to !in 0..63) break
                val tf = file(to)
                if (kotlin.math.abs(tf - prevFile) != 1) break
                prevFile = tf
                val c = b.board[to]
                if (c != '.') {
                    if (c == bishopChar || c == queenChar) return true
                    break
                }
            }
        }
        for (d in ROOK_DIRS) {
            var to = square
            var prevFile = f
            while (true) {
                val stepIsHorizontal = (d == 1 || d == -1)
                to += d
                if (to !in 0..63) break
                val tf = file(to)
                if (stepIsHorizontal && kotlin.math.abs(tf - prevFile) != 1) break
                if (!stepIsHorizontal && tf != prevFile) break
                prevFile = tf
                val c = b.board[to]
                if (c != '.') {
                    if (c == rookChar || c == queenChar) return true
                    break
                }
            }
        }
        return false
    }

    private fun findKing(b: BoardState, white: Boolean): Int {
        val target = if (white) 'K' else 'k'
        for (i in 0 until 64) if (b.board[i] == target) return i
        return -1
    }

    fun inCheck(b: BoardState, white: Boolean): Boolean {
        val kingSq = findKing(b, white)
        if (kingSq == -1) return false
        return isSquareAttacked(b, kingSq, !white)
    }

    /** All fully-legal moves for the side to move. */
    fun legalMoves(b: BoardState): List<Move> {
        val pseudo = pseudoLegalMoves(b)
        return pseudo.filter { m ->
            val after = applyMove(b, m)
            !inCheck(after, b.whiteToMove)
        }
    }

    private fun pseudoLegalMoves(b: BoardState): List<Move> {
        val moves = mutableListOf<Move>()
        val white = b.whiteToMove
        for (sq in 0 until 64) {
            val piece = b.board[sq]
            if (piece == '.') continue
            if (white != piece.isUpperCase()) continue
            when (piece.uppercaseChar()) {
                'P' -> genPawnMoves(b, sq, white, moves)
                'N' -> genLeaperMoves(b, sq, white, KNIGHT_DELTAS, 'N', moves)
                'K' -> { genLeaperMoves(b, sq, white, KING_DELTAS, 'K', moves); genCastling(b, sq, white, moves) }
                'B' -> genSliderMoves(b, sq, white, BISHOP_DIRS, 'B', moves)
                'R' -> genSliderMoves(b, sq, white, ROOK_DIRS, 'R', moves)
                'Q' -> { genSliderMoves(b, sq, white, BISHOP_DIRS, 'Q', moves); genSliderMoves(b, sq, white, ROOK_DIRS, 'Q', moves) }
            }
        }
        return moves
    }

    private fun genPawnMoves(b: BoardState, sq: Int, white: Boolean, out: MutableList<Move>) {
        val f = file(sq); val r = rank(sq)
        val dir = if (white) 1 else -1
        val startRank = if (white) 1 else 6
        val promoRank = if (white) 7 else 0
        val piece = if (white) 'P' else 'p'

        val oneStep = squareOf(f, r + dir)
        if (r + dir in 0..7 && b.board[oneStep] == '.') {
            addPawnMoveWithPromotion(sq, oneStep, piece, r + dir == promoRank, false, out)
            if (r == startRank) {
                val twoStep = squareOf(f, r + 2 * dir)
                if (b.board[twoStep] == '.') {
                    out.add(Move(sq, twoStep, piece))
                }
            }
        }
        for (df in intArrayOf(-1, 1)) {
            val cf = f + df
            if (cf !in 0..7) continue
            val cr = r + dir
            if (cr !in 0..7) continue
            val target = squareOf(cf, cr)
            val targetPiece = b.board[target]
            if (targetPiece != '.' && targetPiece.isUpperCase() != white) {
                addPawnMoveWithPromotion(sq, target, piece, cr == promoRank, true, out)
            } else if (target == b.epSquare && targetPiece == '.') {
                out.add(Move(sq, target, piece, isCapture = true, isEnPassant = true))
            }
        }
    }

    private fun addPawnMoveWithPromotion(from: Int, to: Int, piece: Char, isPromo: Boolean, isCapture: Boolean, out: MutableList<Move>) {
        if (isPromo) {
            for (promo in listOf('q', 'r', 'b', 'n')) {
                out.add(Move(from, to, piece, promotion = promo, isCapture = isCapture))
            }
        } else {
            out.add(Move(from, to, piece, isCapture = isCapture))
        }
    }

    private fun genLeaperMoves(b: BoardState, sq: Int, white: Boolean, deltas: List<Int>, pieceLetter: Char, out: MutableList<Move>) {
        val piece = if (white) pieceLetter else pieceLetter.lowercaseChar()
        for (d in deltas) {
            val to = sq + d
            if (!onBoardDelta(sq, to) || to !in 0..63) continue
            val target = b.board[to]
            if (target == '.' ) {
                out.add(Move(sq, to, piece))
            } else if (target.isUpperCase() != white) {
                out.add(Move(sq, to, piece, isCapture = true))
            }
        }
    }

    private fun genSliderMoves(b: BoardState, sq: Int, white: Boolean, dirs: List<Int>, pieceLetter: Char, out: MutableList<Move>) {
        val piece = if (white) pieceLetter else pieceLetter.lowercaseChar()
        for (d in dirs) {
            var to = sq
            var prevFile = file(sq)
            while (true) {
                val horizontal = (d == 1 || d == -1)
                val diag = !horizontal
                to += d
                if (to !in 0..63) break
                val tf = file(to)
                if (horizontal && kotlin.math.abs(tf - prevFile) != 1) break
                if (diag && kotlin.math.abs(tf - prevFile) != 1) break
                prevFile = tf
                val target = b.board[to]
                if (target == '.') {
                    out.add(Move(sq, to, piece))
                } else {
                    if (target.isUpperCase() != white) out.add(Move(sq, to, piece, isCapture = true))
                    break
                }
            }
        }
    }

    private fun genCastling(b: BoardState, kingSq: Int, white: Boolean, out: MutableList<Move>) {
        val rankIdx = if (white) 0 else 7
        val expectedKingSq = squareOf(4, rankIdx)
        if (kingSq != expectedKingSq) return
        if (inCheck(b, white)) return

        val canK = if (white) b.castling.whiteKingSide else b.castling.blackKingSide
        val canQ = if (white) b.castling.whiteQueenSide else b.castling.blackQueenSide
        val piece = if (white) 'K' else 'k'

        if (canK) {
            val f = squareOf(5, rankIdx); val g = squareOf(6, rankIdx); val h = squareOf(7, rankIdx)
            if (b.board[f] == '.' && b.board[g] == '.' && b.board[h] == (if (white) 'R' else 'r')) {
                if (!isSquareAttacked(b, f, !white) && !isSquareAttacked(b, g, !white)) {
                    out.add(Move(kingSq, g, piece, isCastleKingSide = true))
                }
            }
        }
        if (canQ) {
            val d = squareOf(3, rankIdx); val c = squareOf(2, rankIdx); val bSq = squareOf(1, rankIdx); val a = squareOf(0, rankIdx)
            if (b.board[d] == '.' && b.board[c] == '.' && b.board[bSq] == '.' && b.board[a] == (if (white) 'R' else 'r')) {
                if (!isSquareAttacked(b, d, !white) && !isSquareAttacked(b, c, !white)) {
                    out.add(Move(kingSq, c, piece, isCastleQueenSide = true))
                }
            }
        }
    }

    /** Applies a move (assumed pseudo-legal/legal) and returns a NEW BoardState. */
    fun applyMove(b: BoardState, m: Move): BoardState {
        val nb = b.deepCopy()
        val white = b.whiteToMove
        val movingPiece = nb.board[m.from]

        // Reset ep square by default; set below only for double pawn push
        nb.epSquare = -1

        // Handle en passant capture: remove the captured pawn (which is NOT on m.to)
        if (m.isEnPassant) {
            val capturedPawnSq = if (white) m.to - 8 else m.to + 8
            nb.board[capturedPawnSq] = '.'
        }

        // Move the piece
        nb.board[m.from] = '.'
        nb.board[m.to] = if (m.promotion != null) {
            if (white) m.promotion.uppercaseChar() else m.promotion.lowercaseChar()
        } else movingPiece

        // Castling: move the rook too
        if (m.isCastleKingSide) {
            val rankIdx = rank(m.from)
            nb.board[squareOf(7, rankIdx)] = '.'
            nb.board[squareOf(5, rankIdx)] = if (white) 'R' else 'r'
        }
        if (m.isCastleQueenSide) {
            val rankIdx = rank(m.from)
            nb.board[squareOf(0, rankIdx)] = '.'
            nb.board[squareOf(3, rankIdx)] = if (white) 'R' else 'r'
        }

        // Double pawn push -> set ep square
        if (movingPiece.uppercaseChar() == 'P' && kotlin.math.abs(rank(m.to) - rank(m.from)) == 2) {
            nb.epSquare = (m.from + m.to) / 2
        }

        // Update castling rights if king or rook moved/captured
        fun clearRightsFor(square: Int) {
            when (square) {
                squareOf(4, 0) -> { nb.castling.whiteKingSide = false; nb.castling.whiteQueenSide = false }
                squareOf(4, 7) -> { nb.castling.blackKingSide = false; nb.castling.blackQueenSide = false }
                squareOf(0, 0) -> nb.castling.whiteQueenSide = false
                squareOf(7, 0) -> nb.castling.whiteKingSide = false
                squareOf(0, 7) -> nb.castling.blackQueenSide = false
                squareOf(7, 7) -> nb.castling.blackKingSide = false
            }
        }
        clearRightsFor(m.from)
        clearRightsFor(m.to)

        // Halfmove clock
        nb.halfmoveClock = if (movingPiece.uppercaseChar() == 'P' || m.isCapture) 0 else b.halfmoveClock + 1
        if (!white) nb.fullmoveNumber = b.fullmoveNumber + 1
        nb.whiteToMove = !white
        return nb
    }

    /** Builds full algebraic (SAN) notation for a legal move `m` played from position `b`. */
    fun toSan(b: BoardState, m: Move): String {
        if (m.isCastleKingSide) return withCheckSuffix(b, m, "O-O")
        if (m.isCastleQueenSide) return withCheckSuffix(b, m, "O-O-O")

        val pieceLetter = m.piece.uppercaseChar()
        val destName = BoardState.squareName(m.to)

        if (pieceLetter == 'P') {
            val base = if (m.isCapture) {
                "${('a' + BoardState.file(m.from))}x$destName"
            } else {
                destName
            }
            val promo = m.promotion?.let { "=" + it.uppercaseChar() } ?: ""
            return withCheckSuffix(b, m, base + promo)
        }

        // Disambiguation: check other legal moves of same piece type & destination
        val others = legalMoves(b).filter {
            it.piece.uppercaseChar() == pieceLetter && it.to == m.to && it.from != m.from
        }
        var disambiguation = ""
        if (others.isNotEmpty()) {
            val sameFile = others.any { BoardState.file(it.from) == BoardState.file(m.from) }
            val sameRank = others.any { BoardState.rank(it.from) == BoardState.rank(m.from) }
            disambiguation = when {
                !sameFile -> "${('a' + BoardState.file(m.from))}"
                !sameRank -> "${(BoardState.rank(m.from) + 1)}"
                else -> BoardState.squareName(m.from)
            }
        }
        val captureMark = if (m.isCapture) "x" else ""
        return withCheckSuffix(b, m, "$pieceLetter$disambiguation$captureMark$destName")
    }

    private fun withCheckSuffix(b: BoardState, m: Move, san: String): String {
        val after = applyMove(b, m)
        val opponentWhite = after.whiteToMove // side to move after the move = opponent
        if (!inCheck(after, opponentWhite)) return san
        val noMoves = legalMoves(after).isEmpty()
        return san + (if (noMoves) "#" else "+")
    }

    /**
     * Parses a UCI long-algebraic move string (e.g. "e2e4", "e7e8q" for
     * promotion, "e1g1" for kingside castling) into the matching legal Move
     * from position `b`, or null if it isn't legal there (shouldn't normally
     * happen for a move an engine just suggested from that exact position).
     */
    fun moveFromUci(b: BoardState, uci: String): Move? {
        if (uci.length < 4) return null
        val from = runCatching { BoardState.squareFromName(uci.substring(0, 2)) }.getOrNull() ?: return null
        val to = runCatching { BoardState.squareFromName(uci.substring(2, 4)) }.getOrNull() ?: return null
        val promo = if (uci.length >= 5) uci[4].lowercaseChar() else null
        return legalMoves(b).firstOrNull { it.from == from && it.to == to && it.promotion == promo }
    }

    /** Converts a UCI move string to SAN from position `b`, or null if it isn't a legal move there. */
    fun uciToSan(b: BoardState, uci: String): String? {
        val move = moveFromUci(b, uci) ?: return null
        return toSan(b, move)
    }
}
