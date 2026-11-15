package com.lifeapi.app.data.money

import com.lifeapi.app.data.CatRule
import com.lifeapi.app.data.LifeRepository
import com.lifeapi.app.data.Txn
import java.text.Normalizer
import java.util.Locale

/**
 * Turns raw bank / CSV text into a readable merchant and a spending category.
 * Order: remembered user rule (by merchant) → bank MCC → keywords → null (caller falls back to "Other"/"Income").
 */
object Merchant {
    private val NOISE_WORDS = Regex(
        "(?u)\\b(pirkums( ar karti)?|kartes (maksājums|darījums)|karte|card ?payment|card ?purchase|purchase|payment|maksājums|pos|" +
            "apple ?pay|google ?pay|gpay|contactless|bezkontakt\\w*|visa|mastercard|maestro|debit|credit|eur|usd|gbp|sek|ref|nr|" +
            "id|transaction|darījums|online|internetā|www|https?)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val CARD_MASK = Regex("\\d{2,6}[*xX•]{2,}\\d{0,4}|[*xX•]{3,}\\d{2,4}")
    private val DATE = Regex("\\b\\d{4}-\\d{1,2}-\\d{1,2}\\b|\\b\\d{1,2}[./-]\\d{1,2}[./-]\\d{2,4}\\b|\\b\\d{1,2}[./]\\d{1,2}\\b")
    private val TIME = Regex("\\b\\d{1,2}:\\d{2}(:\\d{2})?\\b")
    private val AMOUNT = Regex("\\b\\d+[.,]\\d{2}\\b")
    private val DIGITS = Regex("\\b[\\w-]*\\d[\\w-]*\\b")
    private val LEGAL = Regex("(?u)\\b(sia|as|uab|oü|ou|ltd|llc|gmbh|inc|ab|oy|plc|bv|sa)\\b\\.?", RegexOption.IGNORE_CASE)
    private val TRAIL_PLACE = Regex(
        "(?u)(\\s+(riga|rīga|riia|jurmala|jūrmala|marupe|mārupe|ogre|jelgava|liepaja|liepāja|daugavpils|ventspils|valmiera|sigulda|" +
            "kekava|ķekava|salaspils|adazi|ādaži|tukums|cesis|cēsis|vilnius|tallinn|lv|lt|ee|lva|ltu|est|dublin|ie|irl|london|gb|gbr|" +
            "luxembourg|lu|nl|de|us|usa|se|fi))+\\s*$",
        RegexOption.IGNORE_CASE,
    )

    /** Readable merchant name, e.g. "PIRKUMS 4567****1234 14.11.2026 RIMI HYPER Riga LV" → "Rimi Hyper". "" if nothing usable. */
    fun clean(raw: String): String {
        var s = raw.replace('\n', ' ').replace('\t', ' ')
        // payment-terminal / processor prefixes ("NYX*Wash", "SQ *Cafe", "SUMUP *Shop") — keep only the brand
        s = s.replace(PROCESSOR, " ")
        s = s.replace(CARD_MASK, " ").replace(DATE, " ").replace(TIME, " ").replace(AMOUNT, " ")
        s = s.replace(Regex("[\"'`/\\\\|;:#,()\\[\\]{}<>=_]+"), " ").replace('*', ' ')
        s = s.replace(NOISE_WORDS, " ").replace(DIGITS, " ").replace(LEGAL, " ")
        s = s.replace(Regex("\\s+"), " ").trim().trim('.', '-', '&', '+', ' ')
        // drop trailing city / country codes (repeat: "Riga LV")
        repeat(2) { s = s.replace(TRAIL_PLACE, "").trim() }
        val words = s.split(' ').filter { it.length >= 2 && it.any(Char::isLetter) }.take(4)
        if (words.isEmpty()) return ""
        return words.joinToString(" ") { w ->
            if (w.length <= 3 && w.all { it.isUpperCase() } && w.lowercase() in KEEP_UPPER) w
            else w.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }
        }.take(40)
    }

    /** Processor prefix glued to the merchant with "*" or "_" (Nayax, Square, SumUp, PayPal, Zettle, iZettle, Toast…). */
    private val PROCESSOR = Regex("(?i)\\b(nyx|nya|sq|sumup|paypal|pp|zettle|izettle|iz|tst|sp|ecwid|stripe|payu|vivawallet)\\s*[*_]\\s*")
    private val PROCESSOR_WORDS = setOf("nyx", "nya", "sq", "sumup", "paypal", "pp", "zettle", "izettle", "iz", "tst", "sp", "ecwid", "stripe", "payu", "vivawallet")

    private val KEEP_UPPER = setOf("kfc", "lmt", "ikea", "bp", "dpd", "hbo", "ups", "tet", "tv")

    /**
     * Stable key for rules/grouping: lowercase, diacritics folded, letters only, first 2 words (2nd cut to 6 letters).
     * Raw bank text ("PIRKUMS 4567********1234 …") is cleaned first, so a card number can never become a "merchant".
     */
    fun key(name: String): String {
        val src = if (Recategorize.looksRaw(name)) clean(name) else name
        val folded = Normalizer.normalize(src.lowercase(Locale.ROOT), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        var words = folded.replace(Regex("[^a-z ]"), " ").replace(Regex("\\s+"), " ").trim().split(' ').filter { it.length >= 2 }
        if (words.size > 1 && words.first() in PROCESSOR_WORDS) words = words.drop(1)
        // Two words, the second cut to 6 letters: banks truncate names at different lengths
        // ("Acme Wash Centr" / "Acme Wash Centra" / "Acme Wash Centra Lv") — they must stay one merchant.
        val k = when {
            words.isEmpty() -> ""
            words.size == 1 -> words[0]
            else -> words[0] + " " + words[1].take(6)
        }
        return if (isBad(k)) "" else k
    }

    /** Keys that came from generic bank words, not a merchant (legacy bug: "pirkums 4567 1234"). */
    fun isBad(k: String): Boolean = k.isBlank() || k.any { it.isDigit() } ||
        Regex("^(pirkums|karte|kartes|card|payment|purchase|pos|maksajums)\\b").containsMatchIn(k)
}

/** Categories the app may assign automatically (seeded into the Category table). */
val AUTO_EXPENSE = listOf(
    "Groceries", "Eating out", "Transport", "Car wash", "Subscriptions", "Fun & hobbies", "Health", "Home", "Rent & utilities",
    "Shopping", "Travel", "Cash", "Fees", "Other",
)

private fun mccCategory(code: String): String? {
    val m = code.trim().toIntOrNull() ?: return null
    return when (m) {
        5411, 5422, 5441, 5451, 5462, 5499, 5300 -> "Groceries"
        5811, 5812, 5813, 5814 -> "Eating out"
        4111, 4112, 4121, 4131, 4784, 4789, 5541, 5542, 5983, 7523, 7512, 7513, 5172, 7531, 7538, 7542 -> "Transport"
        in 3000..3299, 4511, 4582, 4722, 7011, in 3501..3999, 7012, 7032, 7033 -> "Travel"
        5122, 5912, 5975, 5976, 8011, 8021, 8031, 8041, 8042, 8043, 8049, 8050, 8062, 8071, 8099, 7997, 7298 -> "Health"
        4899, 5815, 5816, 5817, 5818, 5968, 7372 -> "Subscriptions"
        5200, 5211, 5231, 5251, 5261, 5712, 5713, 5714, 5718, 5719, 5722, 5992, 5995 -> "Home"
        7832, 7841, 7911, 7922, 7929, 7932, 7933, 7941, 7991, 7992, 7993, 7994, 7996, 7998, 7999, 5733, 5735, 5941, 5942, 5945, 5946, 5970, 5971 -> "Fun & hobbies"
        5045, 5310, 5311, 5331, 5399, 5611, 5621, 5631, 5641, 5651, 5655, 5661, 5681, 5691, 5697, 5698, 5699, 5732, 5734, 5944, 5948, 5949, 5964, 5965, 5969, 5977, 5999, 5932, 5931 -> "Shopping"
        4900, 4814, 4816, 6513 -> "Rent & utilities"
        6010, 6011 -> "Cash"
        6012, 6051, 6211, 6300, 9211, 9222, 9311, 9399, 9402 -> "Fees"
        else -> null
    }
}

/** Ordered keyword rules (first match wins, specific before generic). Matched on folded lowercase text. */
private val KEYWORDS: List<Pair<String, Regex>> = listOf(
    "Cash" to "\\batm\\b|bankomat|cash withdrawal|skaidra nauda|naudas iznems|izmaksa skaidr|снятие налич",
    "Fees" to "valutas kurss|valūtas kurss|komisija|commission|\\bfee\\b|fees|maksa par|apkalposanas|service charge|monthly charge|konta uzturesan|procent|interest|soda nauda|penalty",
    "Car wash" to "putoas|car ?wash|carwash|auto ?mazg|automasg|mazgatuv|mazgasan|\\bmazg|\\bwash\\b|waschanlage|автомойк|мойк",
    "Eating out" to "bolt food|wolt|foodpanda|glovo|coffee|kafij|caffeine|coffee address|double coffee|latte|espresso|starbucks|cafe|kafejn|restaurant|restoran|bistro|pizza|burger|sushi|kebab|kebab|mcdonald|hesburger|\\bkfc\\b|\\blido\\b|cili|čili|subway|burger king|vapiano|pelmeni|street burger|\\bbar\\b|\\bpub\\b|krog|ēdnīc|ednic|кафе|кофе",
    "Groceries" to "narvesen|\\blats\\b|\\blats\\s|\\brimi\\b|maxima|\\blidl\\b|\\btop\\b|elvi|\\bmego\\b|\\baibe\\b|\\bnuko\\b|prisma|selver|\\bcoop\\b|\\bspar\\b|aldi|grocer|supermarket|lielveikal|veikals|tirgus|market|\\biki\\b|produkt|продукт",
    "Transport" to "csdd|longo|caurlaiz|\\btolls?\\b|\\bbolt\\b|uber|taxi|taksi|yandex go|forus|citybee|carguru|fiqsy|circle k|\\bneste\\b|virsi|virši|viada|\\bkool\\b|\\bstatoil|degviel|fuel|petrol|parking|stavviet|autostav|rigas satiksme|rīgas satiksme|e-talons|etalons|pasazieru vilciens|vivi|\\bltc\\b|autoosta|\\bbus\\b|train|metro|tram|tramvaj|trolejbus|mobilly|mobilitate",
    "Travel" to "airbaltic|air baltic|ryanair|wizz|lufthansa|booking\\.com|airbnb|hotel|hostel|viesnic|tallink|tallinkas|flixbus|lux express|ecolines|expedia|skyscanner",
    "Subscriptions" to "hetzner|tribute|trbt|spotify|netflix|youtube|icloud|apple\\.com|itunes|apple services|google one|google storage|google play|disney|hbo|max\\.com|chatgpt|openai|claude|anthropic|github|notion|dropbox|adobe|microsoft 365|xbox|playstation|steam ?purchase|patreon|subscription|abonement|abonements|go3|shortcut|tet\\+",
    "Health" to "aptieka|apteka|benu|mēness|meness|euroaptieka|sirowa|pharma|pharmacy|clinic|klinika|doctor|ārsts|arsts|zobu|dent|medicin|laborator|e\\. gulbja|centrala laboratorija|gym|sporta klubs|myfitness|lemon gym|elixia|pulse|vitamin|аптек",
    "Home" to "\\bikea\\b|\\bdepo\\b|k-rauta|\\bjysk\\b|kursi|bauhaus|home ?&|homeart|hardware|celtniec|mebel|furniture|sleep ?wise",
    "Rent & utilities" to "\\bīre\\b|\\bire\\b|rent\\b|latvenergo|elektrum|enefit|alexela|rigas udens|rīgas ūdens|siltums|rigas siltums|gaze|latvijas gaze|apsaimnieko|komunal|\\btet\\b|lattelecom|\\blmt\\b|tele2|\\bbite\\b|internet|electricity|utilit|insurance|apdrosin|if p&c|balta|ergo|gjensidige|seesam|bta\\b",
    "Shopping" to "denim dream|drogas|douglas|kristiana|pepco|sportland|decathlon|\\bhm\\b|h&m|zara|reserved|lindex|\\bcos\\b|mango|euronics|elkor|220\\.lv|rd electronics|\\bmaxi\\b|aliexpress|amazon|temu|shein|ebay|zalando|apranga|ksenukai|ksenukai|apple store|clothing|apģērb|apgerb|apavi|shoes|cosmetic|parfum",
    "Fun & hobbies" to "cinema|kino|forum cinemas|apollo|movie|steam|game|spēļ|concert|koncert|bilesu|biļešu|bilesu serviss|ticket|teatr|theat|muzej|museum|book|grāmat|gramat|jāņa sēta|janis roze|zvaigzne",
).map { (c, p) -> c to Regex(p) }

private val INCOME_KW = listOf(
    "Salary" to Regex("salary|\\balga\\b|darba samaksa|wage|payroll|зарплат"),
    "Refund" to Regex("refund|return|atmaksa|atgriez|cashback|chargeback|возврат"),
    "Freelance" to Regex("invoice|freelance|upwork|rēķin|rekin|fiverr"),
)

private fun fold(s: String): String =
    Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

/** Pure categoriser: MCC first, then keywords. null = nothing matched. */
fun categorize(text: String, cents: Long, mcc: String = ""): String? {
    val raw = text.lowercase(Locale.ROOT)
    val f = fold(text)
    if (cents > 0) return INCOME_KW.firstOrNull { it.second.containsMatchIn(raw) || it.second.containsMatchIn(f) }?.first
    mccCategory(mcc)?.let { return it }
    return KEYWORDS.firstOrNull { it.second.containsMatchIn(raw) || it.second.containsMatchIn(f) }?.first
}

/** Categoriser with the user's remembered merchant rules. Load once per batch. */
class Categorizer(private val rules: Map<String, String>) {
    fun category(merchantKey: String, text: String, cents: Long, mcc: String): String? {
        if (merchantKey.isNotBlank()) rules[merchantKey]?.let { return it }
        return categorize(text, cents, mcc)
    }

    companion object {
        suspend fun load(repo: LifeRepository) = Categorizer(repo.money.catRules().associate { it.merchant to it.category })
    }
}

/** Re-categorisation of stored rows and "remember this merchant" edits. */
object Recategorize {
    /**
     * Re-runs the categoriser over every automatically-categorised row (never userCategory / transfers).
     * Fills merchant keys from name/note. Returns rows changed.
     */
    suspend fun all(repo: LifeRepository): Int {
        val money = repo.money
        // 1. Repair (idempotent): rules saved under a generic bank-word key applied one category to every card purchase.
        val badRules = money.catRules().filter { Merchant.isBad(it.merchant) }
        badRules.forEach { money.deleteCatRule(it.merchant) }
        val ruleBroken = badRules.map { it.merchant }.toSet()
        // 2. Migrate rule keys to the current key scheme (e.g. old 3-word "nyx acme washcentr" → "acme washce"). Newest wins.
        for (r in money.catRules().sortedBy { it.ts }) {
            val k = Merchant.key(r.merchant)
            if (k != r.merchant) {
                money.deleteCatRule(r.merchant)
                if (k.isNotBlank()) {
                    val existing = money.catRules().firstOrNull { it.merchant == k }
                    if (existing == null || existing.ts <= r.ts) money.upsertCatRule(r.copy(merchant = k))
                }
            }
        }
        // 2b. A category you picked by hand for a merchant becomes that merchant's rule when none exists yet
        //     (older edits were saved under broken keys, so e.g. one "EML Baltic" stayed Eating out while the rest fell to Other).
        run {
            val rules = money.catRules().map { it.merchant }.toMutableSet()
            money.allTxnsNow()
                .filter { it.userCategory && it.amountCents < 0 && it.toAccountId == null && it.category != TRANSFER }
                .groupBy { keyFor(it) }
                .forEach { (k, rows) ->
                    if (k.isBlank() || Merchant.isBad(k) || k in rules) return@forEach
                    val cats = rows.map { it.category }.distinct()
                    if (cats.size == 1) { money.upsertCatRule(CatRule(k, cats[0], rows.maxOf { it.ts })); rules += k }
                }
        }
        val cat = Categorizer.load(repo)
        val changed = mutableListOf<Txn>()
        // 3. Every row gets its merchant key recomputed (user-chosen rows too — key only, never their category or name).
        for (t0 in money.allTxnsNow()) {
            val key = keyFor(t0)
            if (t0.userCategory || t0.toAccountId != null || t0.category == TRANSFER) {
                if (key != t0.merchant) changed += t0.copy(merchant = key)
                continue
            }
            val fromBadKey = t0.merchant.isNotBlank() && Merchant.isBad(t0.merchant)
            // rows that only had their category because of a broken rule start over from the fallback
            val t = if (fromBadKey && (t0.merchant in ruleBroken || ruleBroken.isEmpty() && t0.extId != null)) t0.copy(category = if (t0.amountCents > 0) "Income" else "Other") else t0
            val text = "${t.name} ${t.note}"
            val fallback = if (t.amountCents > 0) "Income" else "Other"
            // categories the old keyword list got wrong are re-evaluated; any other meaningful category (manual entry) is kept
            val reconsider = t.category == "Other" || t.category == "Income" || t.category.isBlank() || t.extId != null
            val newCat = cat.category(key, text, t.amountCents, t.mcc) ?: if (reconsider) fallback else t.category
            val newName = displayName(t)
            if (newCat != t0.category || key != t0.merchant || newName != t0.name) {
                changed += t.copy(category = newCat, merchant = key, name = newName)
            }
        }
        if (changed.isNotEmpty()) money.updateTxns(changed)
        return changed.size
    }

    /** First " · " segment of a bank row's note = the bank's own party/remittance text. */
    private fun bankText(t: Txn): String? = if (t.extId == null) null else t.note.substringBefore(" · ").trim().takeIf { it.isNotBlank() }

    /** Merchant key from what the bank sent (stable across user renames), else from the name. */
    fun keyFor(t: Txn): String {
        val raw = bankText(t)?.takeIf { looksRaw(it) }
        return Merchant.key(raw ?: t.name)
    }

    /** Bank rows: readable name re-derived from the bank text (drops "Nyx " etc.); otherwise unchanged. */
    private fun displayName(t: Txn): String {
        val raw = bankText(t)?.takeIf { looksRaw(it) } ?: return if (t.extId != null && looksRaw(t.name)) Merchant.clean(t.name).ifBlank { t.name } else t.name
        return Merchant.clean(raw).ifBlank { t.name }
    }

    /**
     * Moves [rows] (one month of one category, say) to [category]: they become user-chosen, every merchant among them is
     * remembered, and that merchant's other automatic rows follow. Returns undo.
     */
    suspend fun moveRows(repo: LifeRepository, rows: List<Txn>, category: String): suspend () -> Unit {
        val money = repo.money
        val keys = rows.map { it.merchant.takeUnless { k -> k.isBlank() || Merchant.isBad(k) } ?: keyFor(it) }.filter { it.isNotBlank() && !Merchant.isBad(it) }.toSet()
        val ids = rows.map { it.id }.toSet()
        val followers = keys.flatMap { money.txnsByMerchant(it) }
            .filter { it.id !in ids && !it.userCategory && it.toAccountId == null && it.category != TRANSFER }
            .distinctBy { it.id }
        val prevRules = money.catRules().filter { it.merchant in keys }
        val before = rows + followers
        val now = System.currentTimeMillis()
        money.updateTxns(rows.map { it.copy(category = category, userCategory = true) } + followers.map { it.copy(category = category) })
        keys.forEach { money.upsertCatRule(CatRule(it, category, now)) }
        return {
            money.updateTxns(before)
            keys.forEach { k -> prevRules.firstOrNull { it.merchant == k }?.let { money.upsertCatRule(it) } ?: money.deleteCatRule(k) }
        }
    }

    /** User picked [category] for [t]. Marks it as user-chosen; with [remember], applies to all rows of the merchant and saves a rule. */
    suspend fun assign(repo: LifeRepository, t: Txn, category: String, remember: Boolean, newName: String? = null, newNote: String? = null): suspend () -> Unit {
        val money = repo.money
        val key = t.merchant.takeUnless { it.isBlank() || Merchant.isBad(it) } ?: keyFor(t)
        val before = mutableListOf(t)
        val edited = t.copy(category = category, userCategory = true, merchant = key, name = newName?.takeIf { it.isNotBlank() } ?: t.name, note = newNote ?: t.note)
        val others = if (remember && key.isNotBlank()) money.txnsByMerchant(key).filter { it.id != t.id && !it.userCategory && it.toAccountId == null && it.category != TRANSFER } else emptyList()
        before += others
        val prevRule = if (remember && key.isNotBlank()) money.catRules().firstOrNull { it.merchant == key } else null
        money.updateTxn(edited)
        if (others.isNotEmpty()) money.updateTxns(others.map { it.copy(category = category) })
        if (remember && key.isNotBlank()) money.upsertCatRule(CatRule(key, category, System.currentTimeMillis()))
        return {
            money.updateTxns(before)
            if (remember && key.isNotBlank()) { if (prevRule != null) money.upsertCatRule(prevRule) else money.deleteCatRule(key) }
        }
    }

    /** Bulk: put every non-user row of a merchant into [category] and remember it. */
    suspend fun assignMerchant(repo: LifeRepository, merchantKey: String, category: String): suspend () -> Unit {
        val money = repo.money
        if (Merchant.isBad(merchantKey)) return {}
        val rows = money.txnsByMerchant(merchantKey).filter { !it.userCategory && it.toAccountId == null && it.category != TRANSFER }
        val prevRule = money.catRules().firstOrNull { it.merchant == merchantKey }
        money.updateTxns(rows.map { it.copy(category = category) })
        money.upsertCatRule(CatRule(merchantKey, category, System.currentTimeMillis()))
        return {
            money.updateTxns(rows)
            if (prevRule != null) money.upsertCatRule(prevRule) else money.deleteCatRule(merchantKey)
        }
    }

    /** Raw bank text: long, has digits/card masks, or ALL CAPS. */
    fun looksRaw(s: String): Boolean =
        s.length > 28 || s.any { it.isDigit() } || s.contains('*') || (s.length > 4 && s == s.uppercase(Locale.ROOT) && s.any { it.isLetter() })
}
