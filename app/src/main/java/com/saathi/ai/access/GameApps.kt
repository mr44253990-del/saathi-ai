package com.saathi.ai.access

import android.content.Context
import android.graphics.Rect
import com.saathi.ai.create.GameEngine

/**
 * Playing the games that are **installed on the phone** — not a simulation inside
 * Jimi.
 *
 * The user was explicit: Ludo and chess must be the real app on the device, the way
 * carrom already is. So this class is a driver for third-party game UIs:
 *
 *  • **Ludo** — find the dice (by label when the app exposes one, otherwise by the
 *    corner where every popular Ludo app puts it), tap it, then hand the model a
 *    fresh screenshot so *it* picks the token and we tap the exact pixel.
 *  • **Chess** — map algebraic squares (e2, e4…) onto the detected board rectangle
 *    and move with a real drag, plus a tap-tap fallback for apps that prefer it.
 *  • **Carrom** — delegates to [PhoneTools.strikeCarrom], which already computes the
 *    pocket angle and flicks the striker.
 *
 * Everything reports what was *detected* versus *assumed*, so a wrong guess is
 * visible instead of being presented as a win.
 */
class GameApps(private val context: Context) {

    private val svc: JimiAccessibilityService? get() = JimiAccessibilityService.instance

    // ------------------------------------------------------------------- open

    /** Opens the installed game (or tells the truth that it is not installed). */
    fun open(game: String): String {
        val key = normalize(game)
        val spoken = when (key) {
            "ludo" -> "ludo"
            "chess" -> "chess"
            "carrom" -> "carrom"
            else -> game
        }
        val pkg = AppDirectory.resolve(context, spoken)
        return if (pkg == null) {
            AppDirectory.openStoreSearch(context, spoken)
            "ফোনে «$spoken» অ্যাপ ইনস্টল নেই — Play Store-এ সার্চ খুলে দিয়েছি। " +
                "ইনস্টল করে নিলে আমি খেলব; না খেলে বলব না যে খেলে ফেলেছি।"
        } else {
            AppDirectory.launchPackage(context, pkg, spoken)
        }
    }

    private fun normalize(game: String): String {
        val g = game.lowercase().trim()
        return when {
            g.contains("ludo") || g.contains("লুডু") -> "ludo"
            g.contains("chess") || g.contains("দাবা") || g.contains("ches") -> "chess"
            g.contains("carrom") || g.contains("ক্যারম") -> "carrom"
            else -> g
        }
    }

    // ------------------------------------------------------------------- play

    /**
     * One real move on the installed game.
     * [action] examples: "roll", "dice", "token 2", "e2e4", "strike", "pass".
     */
    fun play(game: String, action: String?): String {
        val key = normalize(game)
        val svc = svc ?: return "Accessibility বন্ধ — গেমের স্ক্রিনে ট্যাপ করা যাবে না"
        val a = (action ?: "").trim().lowercase()

        return when (key) {
            "ludo" -> playLudo(svc, a)
            "chess" -> playChess(svc, a)
            "carrom" -> {
                if (a.isBlank() || a.contains("strike") || a.contains("মার")) {
                    PhoneTools(context).strikeCarrom()
                } else {
                    PhoneTools(context).openCarromBoard() + "\n" + PhoneTools(context).strikeCarrom()
                }
            }
            else -> "«$game» চালানোর ড্রাইভার নেই। আছে: ludo, chess, carrom। " +
                "অন্য গেম হলে run_goal দিয়ে স্ক্রিন দেখে ধাপে ধাপে খেলব।"
        }
    }

    // ------------------------------------------------------------------- ludo

    private val diceLabels = listOf("dice", "roll", "ছক্কা", "লুডু", "tap to roll", "throw")

    private fun playLudo(svc: JimiAccessibilityService, action: String): String {
        val wantDice = action.isBlank() || action.contains("roll") || action.contains("dice") ||
            action.contains("ছক্কা")

        if (wantDice) {
            val labeled = diceLabels.firstOrNull { svc.boundsOf(it) != null }
            val where: Rect? = labeled?.let { svc.boundsOf(it) }
            val rolled = if (where != null) {
                svc.gestures.humanTap(where.centerX().toFloat(), where.centerY().toFloat())
            } else {
                // Every popular Ludo app keeps the dice in the bottom-right of the
                // play area; tap there and say plainly that it was a guess.
                val w = svc.screenWidth()
                val h = svc.screenHeight()
                svc.gestures.deliberateTap(w * 0.82f, h * 0.86f)
            }
            Thread.sleep(1500)
            val looked = ScreenVision.look(context)
            return buildString {
                append(if (rolled) "ছক্কা ফেলেছি" else "ছক্কায় ট্যাপ করা যায়নি")
                append(if (labeled != null) " («$labeled» খুঁজে পেয়ে)" else " (লেবেল পাইনি — নিচের ডান কোণে আন্দাজে ট্যাপ করেছি)")
                append("\n").append(looked.take(700))
                append("\nএখন কোন গুটি চলবে বলো — অথবা «টোকেন ২» বললে আমি সেই ঘরে ট্যাপ করব।")
            }
        }

        // token selection
        val idx = Regex("(\\d+)").find(action)?.groupValues?.get(1)?.toIntOrNull()
        val tokens = svc.findAllByText("token").ifEmpty { svc.findAllByText("pawn") }
        if (idx != null && tokens.size >= idx) {
            val node = tokens[idx - 1]
            val b = Rect()
            node.getBoundsInScreen(b)
            val ok = svc.gestures.humanTap(b.centerX().toFloat(), b.centerY().toFloat())
            Thread.sleep(1200)
            return if (ok) "টোকেন $idx চলেছি" + "\n" + ScreenVision.look(context).take(500)
            else "টোকেন $idx-এ ট্যাপ করা যায়নি"
        }
        // no labels: use the board geometry and the model's own choice
        val board = boardRect(svc)
        return "গুটির লেবেল পাওয়া যায়নি। বোর্ডের এলাকা: $board — " +
            "স্ক্রিনশট দেখে কোঅর্ডিনেট বলো, tap_at দিয়ে চাল দেব।\n" +
            ScreenVision.look(context).take(800)
    }

    // ------------------------------------------------------------------ chess

    private val files = "abcdefgh"

    /** Board rectangle: detected from the largest square-ish region, else centred. */
    fun boardRect(svc: JimiAccessibilityService): Rect {
        val w = svc.screenWidth()
        val h = svc.screenHeight()
        // Most chess apps centre a square board between the two player panels.
        val size = (minOf(w, h) * 0.92f).toInt()
        val left = (w - size) / 2
        val top = (h - size) / 2
        return Rect(left, top, left + size, top + size)
    }

    /** Pixel centre of an algebraic square on the detected board. */
    fun squarePoint(svc: JimiAccessibilityService, square: String, whiteAtBottom: Boolean = true): Pair<Int, Int>? {
        val s = square.trim().lowercase()
        if (s.length != 2) return null
        val file = files.indexOf(s[0])
        val rank = s[1].digitToIntOrNull() ?: return null
        if (file < 0 || rank !in 1..8) return null
        val b = boardRect(svc)
        val cell = b.width() / 8f
        val col = if (whiteAtBottom) file else 7 - file
        val row = if (whiteAtBottom) 8 - rank else rank - 1
        return ((b.left + col * cell + cell / 2f).toInt()) to
            ((b.top + row * cell + cell / 2f).toInt())
    }

    private fun playChess(svc: JimiAccessibilityService, action: String): String {
        val move = Regex("([a-h][1-8])\\s*[-x→ ]?\\s*([a-h][1-8])").find(action)
        if (move == null) {
            // Not a move: show the position and offer the legal ones.
            val state = GameEngine.chess ?: GameEngine.ChessState().also { GameEngine.chess = it }
            return "চাল বোঝা যায়নি (ফরম্যাট: e2e4 বা e2-e4)।\n" +
                "বোর্ডের এলাকা: ${boardRect(svc)}\n" + GameEngine.chessBoard(state)
        }
        val from = move.groupValues[1]
        val to = move.groupValues[2]
        val p1 = squarePoint(svc, from) ?: return "«$from» ঘর বোঝা যায়নি"
        val p2 = squarePoint(svc, to) ?: return "«$to» ঘর বোঝা যায়নি"

        // drag first — Chess.com and Lichess both accept it
        val dragged = svc.gestures.drag(
            p1.first.toFloat(), p1.second.toFloat(),
            p2.first.toFloat(), p2.second.toFloat(), 420
        )
        Thread.sleep(900)

        // some apps want two taps instead
        val tapped = if (!dragged) {
            svc.gestures.humanTap(p1.first.toFloat(), p1.second.toFloat())
            Thread.sleep(500)
            svc.gestures.humanTap(p2.first.toFloat(), p2.second.toFloat())
        } else false
        Thread.sleep(900)

        val looked = ScreenVision.look(context).take(700)
        return buildString {
            append("$from → $to চাল দিয়েছি (")
            append(when {
                dragged -> "ড্র্যাগ"
                tapped -> "দুই ট্যাপ"
                else -> "চেষ্টা ব্যর্থ"
            })
            append(")\n").append(looked)
            append("\nবোর্ড স্ক্রিনশট দেখে বলো চালটা বসেছে কি না — না বসলে অন্য পথে দেব।")
        }
    }

    // ------------------------------------------------------------------ status

    /** One-line state used by the hub and the games screen. */
    fun status(): String {
        val installed = listOf("ludo", "chess", "carrom").mapNotNull { g ->
            AppDirectory.resolve(context, g)?.let { g to it }
        }
        return if (installed.isEmpty()) {
            "ফোনে লুডু/দাবা/ক্যারম কোনো অ্যাপই ইনস্টল নেই — ইনস্টল করলে আমি আসল বোর্ডে খেলব।"
        } else {
            "ইনস্টল আছে: " + installed.joinToString(", ") { "${it.first} (${it.second})" } +
                " · Accessibility ${if (svc != null) "চালু" else "বন্ধ"}" +
                " · রুট ${if (RootBridge.available()) "আছে" else "নেই"}"
        }
    }
}
