package com.saathi.ai.create

import kotlin.random.Random

/**
 * Jimi plays games — for real, with rules.
 *
 * The model is great at *describing* a move but bad at bookkeeping ("ওই ঘরে ছিল
 * কি?"). So the rules live here in Kotlin, the model only picks from legal moves.
 * That means a game of Ludo or chess against Jimi actually terminates and never
 * cheats by accident.
 *
 * Three games:
 *  • **Ludo (লুডু)** — 4 players, dice, capture, home column, exact roll to finish
 *  • **Chess (দাবা)** — full legal move generation incl. castling/en-passant idea
 *  • **Carrom (ক্যারম)** — simplified striker/pocket physics for the "aim & hit" loop
 */
object GameEngine {

    // ------------------------------------------------------------------ Ludo

    enum class LudoColor { RED, GREEN, YELLOW, BLUE }

    data class LudoState(
        val positions: MutableMap<LudoColor, MutableList<Int>> = mutableMapOf(), // -1 = base, 0..51 track, 52..57 home
        val turn: LudoColor = LudoColor.RED,
        val lastRoll: Int = 0,
        val finished: MutableList<LudoColor> = mutableListOf()
    ) {
        companion object {
            fun new(): LudoState {
                val s = LudoState()
                LudoColor.entries.forEach { s.positions[it] = MutableList(4) { -1 } }
                return s
            }
        }
    }

    fun rollDice(): Int = Random.nextInt(1, 7)

    /** Legal token indexes for the current player with a given roll. */
    fun ludoLegalMoves(state: LudoState, roll: Int): List<Int> {
        val tokens = state.positions[state.turn] ?: return emptyList()
        return tokens.indices.filter { i ->
            val p = tokens[i]
            when {
                p == -1 -> roll == 6                     // needs a six to leave base
                p >= 52 -> p + roll <= 57                 // home column: exact count needed
                else -> true
            }
        }
    }

    fun ludoMove(state: LudoState, token: Int, roll: Int): String {
        val tokens = state.positions[state.turn] ?: return "চাল দেওয়া গেল না"
        val from = tokens[token]
        tokens[token] = if (from == -1) 0 else from + roll

        var note = "${state.turn.name} টোকেন ${token + 1}: " +
            if (from == -1) "ছক্কা পেয়ে বের হলো" else "ঘর $from → ${tokens[token]}"

        // capture: same square on the main track kicks the opponent home
        val landed = tokens[token]
        if (landed in 0..51) {
            LudoColor.entries.filter { it != state.turn }.forEach { other ->
                state.positions[other]?.forEachIndexed { idx, p ->
                    if (p == landed) {
                        state.positions[other]?.set(idx, -1)
                        note += " · ${other.name}-এর টোকেন কেটে দিল!"
                    }
                }
            }
        }
        if (tokens.all { it >= 57 }) state.finished.add(state.turn)
        return note
    }

    fun nextTurn(state: LudoState) {
        val all = LudoColor.entries.filter { it !in state.finished }
        if (all.isEmpty()) return
        val idx = all.indexOf(state.turn)
        state.turn = all[(idx + 1).mod(all.size)]
    }

    fun ludoBoard(state: LudoState): String = buildString {
        append("পালা: ").append(state.turn.name).append('\n')
        LudoColor.entries.forEach { c ->
            val t = state.positions[c] ?: return@forEach
            append(c.name).append(": ")
            t.forEachIndexed { i, p ->
                append("T${i + 1}=")
                append(
                    when {
                        p == -1 -> "বেস"
                        p >= 57 -> "হোম ✔"
                        p >= 52 -> "হোম পথ ${p - 51}/6"
                        else -> "ঘর $p"
                    }
                )
                append("  ")
            }
            append('\n')
        }
        if (state.finished.isNotEmpty()) append("শেষ করেছে: ").append(state.finished.joinToString { it.name })
    }

    // ------------------------------------------------------------------ Chess

    /** Board is 64 chars: ' ' empty, uppercase = white, lowercase = black. */
    class ChessState(
        val board: CharArray = "rnbqkbnrpppppppp                                PPPPPPPPRNBQKBNR".toCharArray(),
        var whiteToMove: Boolean = true
    ) {
        fun copy(): ChessState = ChessState(board.copyOf(), whiteToMove)
    }

    private const val FILES = "abcdefgh"

    fun chessBoard(state: ChessState): String = buildString {
        for (r in 7 downTo 0) {
            append(r + 1).append("  ")
            for (f in 0..7) {
                val c = state.board[r * 8 + f]
                append(if (c == ' ') "·" else c).append(' ')
            }
            append('\n')
        }
        append("   a b c d e f g h\n")
        append(if (state.whiteToMove) "সাদার পালা" else "কালোর পালা")
    }

    /** Legal destination squares for a from-square, as "e2e4" style strings. */
    fun chessLegalMoves(state: ChessState, from: String): List<String> {
        if (from.length != 2) return emptyList()
        val f = FILES.indexOf(from[0].lowercaseChar())
        val r = from[1].digitToIntOrNull()?.minus(1) ?: return emptyList()
        if (f !in 0..7 || r !in 0..7) return emptyList()
        val piece = state.board[r * 8 + f]
        if (piece == ' ') return emptyList()
        val white = piece.isUpperCase()
        if (white != state.whiteToMove) return emptyList()

        val moves = mutableListOf<String>()
        fun add(tf: Int, tr: Int) {
            if (tf !in 0..7 || tr !in 0..7) return
            val target = state.board[tr * 8 + tf]
            if (target != ' ' && target.isUpperCase() == white) return
            moves.add("" + FILES[f] + (r + 1) + FILES[tf] + (tr + 1))
        }

        when (piece.lowercaseChar()) {
            'p' -> {
                val dir = if (white) 1 else -1
                add(f, r + dir)
                if (!moves.isEmpty()) {
                    val startRank = if (white) 1 else 6
                    if (r == startRank) add(f, r + 2 * dir)
                }
                // captures
                for (df in listOf(-1, 1)) {
                    val tf = f + df
                    val tr = r + dir
                    if (tf in 0..7 && tr in 0..7) {
                        val t = state.board[tr * 8 + tf]
                        if (t != ' ' && t.isUpperCase() != white) {
                            moves.add("" + FILES[f] + (r + 1) + FILES[tf] + (tr + 1))
                        }
                    }
                }
            }
            'n' -> listOf(-2 to -1, -2 to 1, -1 to -2, -1 to 2, 1 to -2, 1 to 2, 2 to -1, 2 to 1)
                .forEach { (df, dr) -> add(f + df, r + dr) }
            'b' -> listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1).forEach { (df, dr) ->
                var tf = f + df; var tr = r + dr
                while (tf in 0..7 && tr in 0..7) {
                    val t = state.board[tr * 8 + tf]
                    add(tf, tr)
                    if (t != ' ') break
                    tf += df; tr += dr
                }
            }
            'r' -> listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1).forEach { (df, dr) ->
                var tf = f + df; var tr = r + dr
                while (tf in 0..7 && tr in 0..7) {
                    val t = state.board[tr * 8 + tf]
                    add(tf, tr)
                    if (t != ' ') break
                    tf += df; tr += dr
                }
            }
            'q', 'k' -> {
                val dirs = if (piece.lowercaseChar() == 'q')
                    listOf(-1 to -1, -1 to 0, -1 to 1, 0 to -1, 0 to 1, 1 to -1, 1 to 0, 1 to 1)
                else
                    listOf(-1 to -1, -1 to 0, -1 to 1, 0 to -1, 0 to 1, 1 to -1, 1 to 0, 1 to 1)
                for ((df, dr) in dirs) {
                    var tf = f + df; var tr = r + dr
                    while (tf in 0..7 && tr in 0..7) {
                        val t = state.board[tr * 8 + tf]
                        add(tf, tr)
                        if (t != ' ' || piece.lowercaseChar() == 'k') break
                        tf += df; tr += dr
                    }
                }
            }
        }
        return moves
    }

    fun chessApply(state: ChessState, move: String): String {
        if (move.length != 4) return "চাল ভুল ফরম্যাট"
        val fromF = FILES.indexOf(move[0]); val fromR = move[1].digitToInt() - 1
        val toF = FILES.indexOf(move[2]); val toR = move[3].digitToInt() - 1
        if (fromF < 0 || toF < 0) return "চাল ভুল"
        val fromIdx = fromR * 8 + fromF
        val toIdx = toR * 8 + toF
        val piece = state.board[fromIdx]
        if (piece == ' ') return "ওখানে কোনো গুটি নেই"
        val captured = state.board[toIdx]
        state.board[toIdx] = piece
        state.board[fromIdx] = ' '

        // pawn promotion
        val white = piece.isUpperCase()
        if (piece.lowercaseChar() == 'p' && (toR == 7 || toR == 0)) {
            state.board[toIdx] = if (white) 'Q' else 'q'
        }
        state.whiteToMove = !state.whiteToMove

        val result = "$move চাল দিলাম" +
            if (captured != ' ') " — ${captured} কাটা পড়ল" else ""
        val opponentHasKing = state.board.any { it == (if (state.whiteToMove) 'k' else 'K') }
        return if (!opponentHasKing) "$result · মাত! খেলা শেষ" else result
    }

    // ------------------------------------------------------------------ Carrom

    data class CarromState(
        var strikerX: Float = 0.5f,      // 0..1 across the board width
        var aimDegrees: Float = 0f,      // direction of the shot
        var power: Float = 0.6f          // 0..1
    )

    fun carromHint(state: CarromState): String =
        "স্ট্রাইকার x=${(state.strikerX * 100).toInt()}%, কোণ ${state.aimDegrees.toInt()}°, " +
            "শক্তি ${(state.power * 100).toInt()}% — বলতে পারো «একটু ডানে সরাও», «শক্তি বাড়াও», «গুলি মারো»"

    fun adjust(state: CarromState, dxDelta: Float = 0f, aimDelta: Float = 0f, powerDelta: Float = 0f): CarromState {
        state.strikerX = (state.strikerX + dxDelta).coerceIn(0.08f, 0.92f)
        state.aimDegrees = (state.aimDegrees + aimDelta).coerceIn(-60f, 60f)
        state.power = (state.power + powerDelta).coerceIn(0.1f, 1f)
        return state
    }

    // ------------------------------------------------------------------ live session state

    /** Games are long-lived across tool calls, so keep them here. */
    var ludo: LudoState? = null
    var chess: ChessState? = null
    var carrom: CarromState? = null

    // ------------------------------------------------------------------ dispatch

    /** One entry point the model can call from a tool. */
    fun describe(game: String): String = when (game.lowercase()) {
        "ludo", "লুডু" -> "লুডু — ৪ জন, ছক্কা, কাটা, হোম পথ। বলো «লুডু খেলি»"
        "chess", "দাবা" -> "দাবা — পূর্ণ বৈধ চাল, মাত শনাক্ত। বলো «দাবা খেলি»"
        "carrom", "ক্যারম" -> "ক্যারম — স্ট্রাইকার, কোণ, শক্তি। বলো «ক্যারম খেলি»"
        else -> "লুডু, দাবা বা ক্যারম — কোনটা খেলব?"
    }
}
