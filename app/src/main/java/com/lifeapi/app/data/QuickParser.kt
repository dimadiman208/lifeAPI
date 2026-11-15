package com.lifeapi.app.data

import java.time.LocalTime
import java.util.Locale
import kotlin.math.roundToLong

sealed interface QuickAction {
    /** Executes immediately; returns undo + toast text. */
    class Run(val toast: String, val exec: suspend () -> Undo) : QuickAction
    /** Needs more data: open the meal editor prefilled. */
    class MealDraft(val items: String) : QuickAction
}

data class Parsed(
    val tag: String,
    val hue: Int,
    val title: String,
    val meta: String,
    val action: QuickAction,
    val recentLabel: String,
    val recentValue: String,
)

data class ParseContext(
    val supplements: List<Supplement>,
    /** Supplement id → "Morning · 08:00 · 2 caps" for its next pending dose today. */
    val nextDose: Map<Long, String> = emptyMap(),
    val takenToday: Set<Long>,
    val habits: List<Habit>,
    val doneToday: Set<Long>,
    val budgetCategories: List<String>,
    val accounts: List<Account>,
    val recentMeals: List<Meal>,
    val hasActiveWorkout: Boolean,
)

fun mealSlotFor(time: LocalTime): String = when {
    time.hour < 11 -> "Breakfast"
    time.hour < 15 -> "Lunch"
    time.hour < 18 -> "Snack"
    else -> "Dinner"
}

fun fmtCents(cents: Long, sign: Boolean = false): String {
    val abs = kotlin.math.abs(cents)
    val s = String.format(Locale.US, "€%,d.%02d", abs / 100, abs % 100)
    return when {
        cents < 0 -> "−$s"
        sign && cents > 0 -> "+$s"
        else -> s
    }
}

private fun String.cap() = replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

private val CATEGORY_KEYWORDS = listOf(
    "Eating out" to Regex("coffee|white|latte|cappuccino|espresso|lunch|dinner|breakfast|bar|cafe|café|restaurant|pizza|burger|sushi|kebab|кофе|обед|ужин"),
    "Groceries" to Regex("rimi|maxima|lidl|grocer|market|aldi|prisma|selver|coop|supermarket|продукт"),
    "Transport" to Regex("bolt|uber|taxi|fuel|bus|train|metro|petrol|parking|такси"),
    "Subscriptions" to Regex("spotify|netflix|icloud|youtube|subscription|chatgpt|claude|google one|apple"),
    "Fun & hobbies" to Regex("cinema|movie|game|steam|concert|book|кино"),
    "Health" to Regex("pharmacy|apteka|gym|vitamin|doctor|аптек"),
)
private val INCOME = Regex("salary|freelance|refund|income|bonus|dividend|cashback|зарплат|доход")

class QuickParser(private val repo: LifeRepository) {

    fun parse(raw: String, ctx: ParseContext): Parsed? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        val s = text.lowercase(Locale.getDefault())

        // ---- water: "500 ml", "+250ml", "water 300"
        Regex("""^(?:water\s*)?\+?(\d{2,4})\s*(?:ml|мл)?$""").find(s)?.takeIf { s.contains("ml") || s.contains("мл") || s.startsWith("water") }?.let {
            val ml = it.groupValues[1].toInt()
            return Parsed("H2O", Hue.HEALTH, "+$ml ml water", "Health · Water · now",
                QuickAction.Run("Logged $ml ml water") { repo.addWater(ml) }, "Water", "+$ml ml")
        }

        // ---- set: "bench 90x5", "squat 100 x 5"
        Regex("""^([\p{L} .'\-]+?)\s*(\d+(?:[.,]\d+)?)\s*[x×х*]\s*(\d+)$""").find(s)?.let {
            val name = it.groupValues[1].trim().cap()
            val kg = it.groupValues[2].replace(',', '.').toDouble()
            val reps = it.groupValues[3].toInt()
            val kgS = fmtKg(kg)
            return Parsed("SET", Hue.TRAIN, "$name · $kgS kg × $reps",
                if (ctx.hasActiveWorkout) "Train · adds a set to the active workout" else "Train · adds a set to today",
                QuickAction.Run("Logged $name $kgS × $reps") { repo.logLooseSet(name, kg, reps) }, name, "$kgS × $reps")
        }

        // ---- sleep: "slept 7h 24m", "sleep 7.5h", "slept 23:30-07:00"
        if (Regex("""^(slept|sleep|сон|спал)""").containsMatchIn(s)) {
            parseSleep(s)?.let { (start, end) ->
                val mins = ((end - start) / 60000).toInt()
                val label = "${mins / 60}h ${mins % 60}m"
                val span = "${hhmm(Days.timeOf(start))} → ${hhmm(Days.timeOf(end))}"
                return Parsed("ZZZ", Hue.HEALTH, "Sleep · $label", "Health · $span · last night",
                    QuickAction.Run("Logged $label sleep") { repo.logSleep(Days.dayOf(end), start, end) }, "Sleep", label)
            }
        }

        // ---- learning: "spanish 15m", "kotlin course 20 min"
        Regex("""^(?:learn(?:ed)?\s+)?(.+?)\s+(\d{1,3})\s*(?:m|min|mins|мин)$""").find(s)?.let {
            val topic = it.groupValues[1].trim().cap()
            val m = it.groupValues[2].toInt()
            return Parsed("LRN", Hue.GROW, "$topic · ${m}m", "Grow · Learning today",
                QuickAction.Run("Logged ${m}m of $topic") { repo.addLearning(topic, m) }, topic, "${m}m")
        }

        val firstWord = s.split(' ').first()
        // ---- supplement
        if (firstWord.length >= 3) {
            ctx.supplements.firstOrNull { sup ->
                val n = sup.name.lowercase(Locale.getDefault())
                n.startsWith(s) || n.split(' ').first().startsWith(firstWord)
            }?.let { sup ->
                val next = ctx.nextDose[sup.id]
                return Parsed("RX", Hue.HEALTH, "${sup.name} · ${sup.dose}",
                    if (next == null) "Health · ${if (sup.id in ctx.takenToday) "all doses done today" else "not planned today"}" else "Health · $next",
                    QuickAction.Run("${sup.name} marked as taken") { repo.setTaken(sup, Days.today(), true) }, sup.name, sup.dose)
            }
            // ---- habit
            ctx.habits.firstOrNull { h ->
                val n = h.name.lowercase(Locale.getDefault())
                n.startsWith(s) || n.split(' ').first() == firstWord
            }?.let { h ->
                val done = h.id in ctx.doneToday
                return Parsed("HBT", Hue.GROW, "${h.name} ✔", if (done) "Grow · already done today" else "Grow · completes today's habit",
                    QuickAction.Run("${h.name} done") { repo.setHabit(h.id, Days.today(), true) }, h.name, "done")
            }
        }

        // ---- meal with kcal: "chicken bowl 640 kcal 52p"
        Regex("""^(.+?)\s+(\d{2,4})\s*(?:kcal|ккал|cal)(?:\s+(\d{1,3})\s*p)?$""").find(s)?.let {
            val items = it.groupValues[1].trim().cap()
            val kcal = it.groupValues[2].toInt()
            val p = it.groupValues[3].toIntOrNull() ?: 0
            val slot = mealSlotFor(LocalTime.now())
            return Parsed("FOOD", Hue.FUEL, "$items · $kcal kcal", "Nutrition · $slot${if (p > 0) " · P ${p}g" else ""}",
                QuickAction.Run("Logged $slot · $kcal kcal") {
                    repo.addMeal(Meal(day = Days.today(), ts = Days.now(), slot = slot, items = items, kcal = kcal, protein = p, carbs = 0, fat = 0))
                }, items, "$kcal kcal")
        }

        // ---- money: "coffee 4.50", "rimi 23,40", "+600 freelance", "salary 4850"
        val money = Regex("""^\+?\s*(?:([\p{L}][\p{L} &'.\-]*?)\s*)?[+]?€?\s*(\d+(?:[.,]\d{1,2})?)\s*€?(?:\s+([\p{L}][\p{L} &'.\-]*))?$""").find(s)
        if (money != null && (money.groupValues[1].isNotBlank() || money.groupValues[3].isNotBlank())) {
            val name = (money.groupValues[1].ifBlank { money.groupValues[3] }).trim()
            val cents = (money.groupValues[2].replace(',', '.').toDouble() * 100).roundToLong()
            val income = s.startsWith("+") || INCOME.containsMatchIn(name)
            val category = if (income) "Income" else categorise(name, ctx.budgetCategories)
            val acc = ctx.accounts.firstOrNull()
            val signed = if (income) cents else -cents
            val accMeta = acc?.let { " · ${it.name}" } ?: ""
            return Parsed("EUR", if (income) Hue.GAIN else Hue.MONEY,
                "${fmtCents(signed, sign = income).removePrefix("−")} · ${name.cap()}", "Money · $category$accMeta",
                QuickAction.Run(if (income) "Logged income ${fmtCents(cents)}" else "Logged ${fmtCents(cents)} · ${name.cap()}") {
                    repo.addTxn(Txn(day = Days.today(), ts = Days.now(), name = name.cap(), category = category, amountCents = signed, accountId = acc?.id))
                }, name.cap(), fmtCents(signed, sign = income))
        }

        // ---- repeat a known meal
        ctx.recentMeals.firstOrNull { it.items.lowercase(Locale.getDefault()).contains(s) }?.let { m ->
            val slot = mealSlotFor(LocalTime.now())
            return Parsed("FOOD", Hue.FUEL, "${m.items} · ${m.kcal} kcal", "Nutrition · $slot · same as before",
                QuickAction.Run("Logged ${m.kcal} kcal") {
                    repo.addMeal(m.copy(id = 0, day = Days.today(), ts = Days.now(), slot = slot))
                }, m.items, "${m.kcal} kcal")
        }

        return Parsed("FOOD", Hue.FUEL, "Meal · ${text.cap()}", "Nutrition · add calories & macros",
            QuickAction.MealDraft(text.cap()), text.cap(), "meal")
    }

    private fun categorise(name: String, budgets: List<String>): String {
        budgets.firstOrNull { name.contains(it.lowercase(Locale.getDefault())) || it.lowercase(Locale.getDefault()).contains(name) }?.let { return it }
        return CATEGORY_KEYWORDS.firstOrNull { it.second.containsMatchIn(name) }?.first ?: "Other"
    }

    private fun parseSleep(s: String): Pair<Long, Long>? {
        val today = Days.today()
        Regex("""(\d{1,2})[:.](\d{2})\s*(?:-|–|→|to)\s*(\d{1,2})[:.](\d{2})""").find(s)?.let {
            val a = LocalTime.of(it.groupValues[1].toInt() % 24, it.groupValues[2].toInt() % 60)
            val b = LocalTime.of(it.groupValues[3].toInt() % 24, it.groupValues[4].toInt() % 60)
            val end = Days.tsOf(today, b)
            val startDay = if (a > b) today - 1 else today
            return Days.tsOf(startDay, a) to end
        }
        var minutes = 0
        Regex("""(\d+(?:[.,]\d+)?)\s*(?:h|ч)""").find(s)?.let { minutes += (it.groupValues[1].replace(',', '.').toDouble() * 60).toInt() }
        Regex("""(\d+)\s*(?:m|min|мин)\b""").find(s)?.let { minutes += it.groupValues[1].toInt() }
        if (minutes == 0) Regex("""(\d{1,2}):(\d{2})""").find(s)?.let { minutes = it.groupValues[1].toInt() * 60 + it.groupValues[2].toInt() }
        if (minutes !in 30..(16 * 60)) return null
        val now = LocalTime.now()
        val end = if (now.hour < 12) Days.now() else Days.tsOf(today, LocalTime.of(7, 0))
        return (end - minutes * 60000L) to end
    }
}

fun fmtKg(kg: Double): String = if (kg % 1.0 == 0.0) kg.toLong().toString() else String.format(Locale.US, "%.1f", kg).trimEnd('0').trimEnd('.')
fun hhmm(t: LocalTime): String = String.format(Locale.US, "%02d:%02d", t.hour, t.minute)
