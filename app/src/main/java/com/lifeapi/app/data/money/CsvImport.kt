package com.lifeapi.app.data.money

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToLong

data class CsvRow(val day: Long, val name: String, val cents: Long)

data class CsvResult(val rows: List<CsvRow>, val skipped: Int, val error: String? = null)

/** Generic bank-statement CSV: finds date / description / amount columns, handles ; , or tab, comma decimals, D/K indicators. */
object CsvImport {

    fun parse(text: String): CsvResult {
        val lines = text.removePrefix("﻿").lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return CsvResult(emptyList(), 0, "The file is empty")
        val head = lines.first()
        val delim = listOf(';', '\t', ',').maxBy { c -> head.count { it == c } }
        val table = lines.map { split(it, delim) }
        val header = table.first().map { it.lowercase(Locale.ROOT).trim() }

        fun col(vararg keys: String, exclude: Set<Int> = emptySet()): Int =
            header.indices.firstOrNull { i -> i !in exclude && keys.any { k -> header[i].contains(k) } } ?: -1

        var dateC = col("completed date", "booking date", "date", "datums", "kuupäev", "дата", "laiks")
        var amtC = col("amount", "summa", "sum", "сумма", "value", "betrag")
        var descC = col("beneficiary", "payee", "merchant", "description", "details", "detaļas", "saņēmējs", "name", "partner", "counterparty", "info", "детали", "описание", "reference")
        val dkC = col("debit/credit", "d/k", "d/c", "dr/cr", "kredīts/debets")
        val debitC = col("debit", "out", "расход", exclude = setOf(dkC, amtC))
        val creditC = col("credit", "in ", "приход", exclude = setOf(dkC, amtC, debitC))
        val rowTypeC = col("row type", "ieraksta tips")
        val stateC = col("state", "status")
        var hasHeader = dateC >= 0 && (amtC >= 0 || debitC >= 0)

        if (!hasHeader) {
            // Guess from the first data row's content.
            val probe = table.first()
            dateC = probe.indexOfFirst { parseDate(it) != null }
            amtC = probe.indices.lastOrNull { it != dateC && parseAmount(it.let { i -> probe[i] }, delim) != null } ?: -1
            descC = probe.indices.filter { it != dateC && it != amtC }.maxByOrNull { probe[it].length } ?: -1
            if (dateC < 0 || amtC < 0) return CsvResult(emptyList(), lines.size, "Couldn't find date and amount columns")
        }
        val data = if (hasHeader) table.drop(1) else table
        val out = ArrayList<CsvRow>()
        var skipped = 0
        for (r in data) {
            fun at(i: Int) = if (i in r.indices) r[i].trim() else ""
            if (rowTypeC >= 0 && at(rowTypeC).isNotEmpty() && at(rowTypeC) != "20") { skipped++; continue }
            if (stateC >= 0 && at(stateC).uppercase(Locale.ROOT).let { it == "REVERTED" || it == "DECLINED" || it == "FAILED" }) { skipped++; continue }
            val day = parseDate(at(dateC))
            var amt: Double? = if (amtC >= 0) parseAmount(at(amtC), delim) else null
            if (amt == null && debitC >= 0) {
                val d = parseAmount(at(debitC), delim); val c = if (creditC >= 0) parseAmount(at(creditC), delim) else null
                amt = when {
                    d != null && d != 0.0 -> -kotlin.math.abs(d)
                    c != null -> kotlin.math.abs(c)
                    else -> null
                }
            }
            if (day == null || amt == null || amt == 0.0) { skipped++; continue }
            if (dkC >= 0) {
                val dk = at(dkC).uppercase(Locale.ROOT)
                if (dk.startsWith("D")) amt = -kotlin.math.abs(amt)
                else if (dk.startsWith("K") || dk.startsWith("C")) amt = kotlin.math.abs(amt)
            }
            val name = at(descC).ifEmpty { if (amt > 0) "Income" else "Expense" }.replace(Regex("\\s+"), " ").take(80)
            out += CsvRow(day, name, (amt * 100).roundToLong())
        }
        return CsvResult(out, skipped, if (out.isEmpty()) "No transactions recognised in this file" else null)
    }

    private fun split(line: String, d: Char): List<String> {
        val res = ArrayList<String>(); val sb = StringBuilder(); var q = false; var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' && q && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
                ch == '"' -> q = !q
                ch == d && !q -> { res += sb.toString(); sb.clear() }
                else -> sb.append(ch)
            }
            i++
        }
        res += sb.toString()
        return res
    }

    private val DATE_FORMATS = listOf("yyyy-MM-dd", "dd.MM.yyyy", "dd/MM/yyyy", "d.M.yyyy", "d/M/yyyy", "dd-MM-yyyy", "yyyy.MM.dd", "yyyy/MM/dd", "dd.MM.yy", "MM/dd/yyyy")
        .map { DateTimeFormatter.ofPattern(it) }

    fun parseDate(raw: String): Long? {
        val s = raw.trim().take(10).trim()
        if (s.length < 6) return null
        for (f in DATE_FORMATS) {
            val d = runCatching { LocalDate.parse(s, f) }.getOrNull() ?: continue
            if (d.year in 1990..2100) return d.toEpochDay()
        }
        return null
    }

    fun parseAmount(raw: String, delim: Char): Double? {
        var s = raw.trim().replace(" ", "").replace(" ", "").replace("€", "").replace("EUR", "").replace("−", "-")
        if (s.isEmpty()) return null
        val neg = s.startsWith("(") && s.endsWith(")")
        s = s.trim('(', ')')
        val lastDot = s.lastIndexOf('.'); val lastComma = s.lastIndexOf(',')
        s = when {
            lastDot >= 0 && lastComma >= 0 -> if (lastComma > lastDot) s.replace(".", "").replace(',', '.') else s.replace(",", "")
            lastComma >= 0 -> if (delim != ',' || s.count { it == ',' } == 1) s.replace(',', '.') else s.replace(",", "")
            else -> s
        }
        if (!Regex("^[+-]?\\d+(\\.\\d+)?$").matches(s)) return null
        return s.toDoubleOrNull()?.let { if (neg) -it else it }
    }
}
