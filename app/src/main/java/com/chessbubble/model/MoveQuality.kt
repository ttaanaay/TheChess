package com.chessbubble.model

/**
 * Move-quality classification, based on centipawn loss (cpLoss) relative to
 * the engine's best move in that position. Thresholds are modeled after the
 * classification chess.com/lichess use publicly; tune freely.
 *
 * cpLoss = eval(bestMove) - eval(playedMove), from the mover's perspective,
 * always >= 0 (0 means the played move WAS the engine's top choice).
 */
enum class MoveQuality(val label: String) {
    BEST("Best"),
    EXCELLENT("Excellent"),
    GREAT("Great"),
    GOOD("Good"),
    INACCURACY("Inaccuracy"),
    MISTAKE("Mistake"),
    MISS("Miss"),
    BLUNDER("Blunder");

    companion object {
        /**
         * @param cpLoss centipawn loss of the played move vs. best move (>= 0)
         * @param wasOnlyReasonableMoveMissed true if the played move missed a forced
         *        winning/saving tactic that was clearly available (used to tag MISS
         *        instead of plain INACCURACY/MISTAKE even with moderate cpLoss)
         */
        fun fromCentipawnLoss(cpLoss: Int, wasOnlyReasonableMoveMissed: Boolean = false): MoveQuality {
            if (wasOnlyReasonableMoveMissed && cpLoss >= 150) return MISS
            return when {
                cpLoss <= 0 -> BEST
                cpLoss <= 10 -> EXCELLENT
                cpLoss <= 30 -> GREAT
                cpLoss <= 60 -> GOOD
                cpLoss <= 120 -> INACCURACY
                cpLoss <= 250 -> MISTAKE
                else -> BLUNDER
            }
        }
    }
}

data class MoveEvaluation(
    val sideToMoveWasWhite: Boolean,
    val san: String,
    val evalBeforeCp: Int,
    val evalAfterCp: Int,
    val bestMoveEvalCp: Int,
    val quality: MoveQuality
)
