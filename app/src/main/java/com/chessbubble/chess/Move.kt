package com.chessbubble.chess

data class Move(
    val from: Int,
    val to: Int,
    val piece: Char,
    val promotion: Char? = null,   // 'q','r','b','n' (lowercase; case fixed on apply)
    val isCapture: Boolean = false,
    val isEnPassant: Boolean = false,
    val isCastleKingSide: Boolean = false,
    val isCastleQueenSide: Boolean = false
)
