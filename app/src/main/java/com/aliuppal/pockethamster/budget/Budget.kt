package com.aliuppal.pockethamster.budget

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.roundToLong

enum class ExpenseCategory(val label: String, val emoji: String, private val keywords: List<String>) {
    FOOD("Food", "🍔", listOf("lunch", "dinner", "breakfast", "food", "coffee", "snack", "grocer", "restaurant", "pizza", "meal", "tea", "burger")),
    TRANSPORT("Transport", "🚌", listOf("taxi", "uber", "careem", "bus", "train", "fuel", "petrol", "gas", "parking", "metro", "ride", "fare")),
    SHOPPING("Shopping", "🛍️", listOf("clothes", "shoes", "shopping", "amazon", "gift", "shirt", "dress")),
    BILLS("Bills", "🧾", listOf("rent", "bill", "electric", "internet", "phone", "subscription", "netflix", "spotify")),
    FUN("Fun", "🎮", listOf("movie", "game", "cinema", "concert", "party", "ticket")),
    HEALTH("Health", "💊", listOf("medicine", "doctor", "pharmacy", "gym", "dentist")),
    OTHER("Other", "📦", emptyList());

    companion object {
        fun guess(text: String): ExpenseCategory {
            val s = text.lowercase()
            return entries.firstOrNull { c -> c.keywords.any { it in s } } ?: OTHER
        }
    }
}

enum class Currency(val label: String, val symbol: String) {
    PKR("Pakistani Rupee (Rs)", "Rs "),
    USD("US Dollar ($)", "$"),
}

data class Expense(
    val id: Long = System.nanoTime(),
    val amountCents: Long,
    val category: ExpenseCategory,
    val note: String,
    val time: Long = System.currentTimeMillis(),
)

data class BudgetSummary(
    val today: Long,
    val week: Long,
    val month: Long,
    val byCategoryWeek: Map<ExpenseCategory, Long>,
    val byCategoryMonth: Map<ExpenseCategory, Long>,
)

/** A small on-device expense log with weekly and monthly totals. */
object Budget {
    data class State(
        val expenses: List<Expense> = emptyList(),
        val monthlyLimitCents: Long = 0L,
        val currency: Currency = Currency.PKR,
    )

    private lateinit var prefs: SharedPreferences
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    fun init(context: Context) {
        prefs = context.getSharedPreferences("budget", Context.MODE_PRIVATE)
        val currency = prefs.getString("currency", null)?.let { runCatching { Currency.valueOf(it) }.getOrNull() }
            ?: if (Locale.getDefault().country == "US") Currency.USD else Currency.PKR
        _state.value = State(load(), prefs.getLong("limit", 0L), currency)
    }

    /** Changes how amounts are shown; stored amounts are not converted. */
    fun setCurrency(currency: Currency) {
        _state.value = _state.value.copy(currency = currency)
        prefs.edit().putString("currency", currency.name).apply()
    }

    @Synchronized
    fun add(expense: Expense) {
        val next = (_state.value.expenses + expense).sortedByDescending { it.time }
        _state.value = _state.value.copy(expenses = next)
        save()
    }

    @Synchronized
    fun delete(id: Long) {
        _state.value = _state.value.copy(expenses = _state.value.expenses.filterNot { it.id == id })
        save()
    }

    fun setMonthlyLimit(cents: Long) {
        _state.value = _state.value.copy(monthlyLimitCents = cents.coerceAtLeast(0L))
        prefs.edit().putLong("limit", _state.value.monthlyLimitCents).apply()
    }

    // ------------------------------------------------------------------ periods

    private val zone: ZoneId get() = ZoneId.systemDefault()

    fun dayStart(date: LocalDate = LocalDate.now()) = date.atStartOfDay(zone).toInstant().toEpochMilli()

    /** Start of this week, using the phone's locale for the first day (Monday or Sunday). */
    fun weekStart(): Long {
        val first = WeekFields.of(Locale.getDefault()).firstDayOfWeek
        return dayStart(LocalDate.now().with(TemporalAdjusters.previousOrSame(first)))
    }

    fun monthStart(): Long = dayStart(LocalDate.now().withDayOfMonth(1))

    fun summary(expenses: List<Expense> = _state.value.expenses): BudgetSummary {
        val today = dayStart()
        val week = weekStart()
        val month = monthStart()
        fun total(from: Long) = expenses.filter { it.time >= from }.sumOf { it.amountCents }
        fun byCategory(from: Long) = expenses.filter { it.time >= from }
            .groupBy { it.category }.mapValues { (_, v) -> v.sumOf { it.amountCents } }
            .toList().sortedByDescending { it.second }.toMap()
        return BudgetSummary(total(today), total(week), total(month), byCategory(week), byCategory(month))
    }

    // ------------------------------------------------------------------ formatting + parsing

    /** "Rs 1,250" or "$12.50". Rupees drop the paisa when there are none. */
    fun format(cents: Long): String {
        val currency = _state.value.currency
        val pattern = if (currency == Currency.PKR && cents % 100 == 0L) "#,##0" else "#,##0.00"
        val sign = if (cents < 0) "-" else ""
        return sign + currency.symbol + DecimalFormat(pattern, DecimalFormatSymbols(Locale.US)).format(kotlin.math.abs(cents) / 100.0)
    }

    fun parseAmount(text: String): Long? =
        text.trim().replace(",", ".").toDoubleOrNull()?.takeIf { it > 0 && it < 1e9 }?.let { (it * 100).roundToLong() }

    fun dateLabel(time: Long): String {
        val d = Instant.ofEpochMilli(time).atZone(zone).toLocalDate()
        val today = LocalDate.now()
        return when (d) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> d.format(DateTimeFormatter.ofPattern("EEE d MMM"))
        }
    }

    private val SPENT = Regex(
        "\\b(spent|paid|pay|bought|cost|costs|expense)\\b[^0-9]{0,12}?(\\d+(?:[.,]\\d{1,2})?)(?:\\s*(?:\\w{1,3}\\s+)?(?:on|for)\\s+(.+))?",
        RegexOption.IGNORE_CASE,
    )
    private val ASK = Regex(
        "how much (did|have) i (spend|spent)|my (spending|expenses)|total (spent|spending|expenses)",
        RegexOption.IGNORE_CASE,
    )

    /** "spent 12.50 on lunch" → an expense, so the hamster can keep the books from chat. */
    fun fromChat(input: String): Expense? {
        val m = SPENT.find(input) ?: return null
        val cents = parseAmount(m.groupValues[2]) ?: return null
        val note = m.groupValues[3].trim().trimEnd('.', '!', '?').ifBlank { input.trim() }.take(60)
        return Expense(amountCents = cents, category = ExpenseCategory.guess(input), note = note)
    }

    fun isSpendingQuestion(input: String) = ASK.containsMatchIn(input)

    // ------------------------------------------------------------------ storage

    private fun load(): List<Expense> = runCatching {
        val arr = JSONArray(prefs.getString("expenses", "[]"))
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            Expense(
                id = o.getLong("id"),
                amountCents = o.getLong("cents"),
                category = runCatching { ExpenseCategory.valueOf(o.getString("cat")) }.getOrDefault(ExpenseCategory.OTHER),
                note = o.optString("note"),
                time = o.getLong("time"),
            )
        }.sortedByDescending { it.time }
    }.getOrDefault(emptyList())

    private fun save() {
        val arr = JSONArray()
        _state.value.expenses.forEach {
            arr.put(
                JSONObject().put("id", it.id).put("cents", it.amountCents).put("cat", it.category.name)
                    .put("note", it.note).put("time", it.time)
            )
        }
        prefs.edit().putString("expenses", arr.toString()).apply()
    }
}
