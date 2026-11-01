package com.aliuppal.pockethamster.budget

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Answers questions about the user's own spending from the on-device expense log. These answers
 * are built locally and never go through an AI model, so expenses never leave the phone.
 */
object BudgetAssistant {
    /** Every budget answer starts with this, which also lets the AI-history filter recognise it. */
    const val PREFIX = "*flips through notebook*"
    private const val DAY = 24 * 60 * 60 * 1000L
    private const val LIST_LIMIT = 10

    private val TOPIC = Regex("\\b(expenses?|spent|spend|spending|budget|bought|purchases?|money)\\b", RegexOption.IGNORE_CASE)
    // A question or a request, so statements like "I spent the day at the park" stay small talk.
    private val ASKING = Regex(
        "\\?|^\\W*(how|what|show|list|see|view|which|did|tell|give|total|biggest|largest|average|expenses?|spending|budget)\\b" +
            "|\\bmy (expenses|spending|budget|purchases)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val ADVICE =Regex("\\b(how (can|do|should|could) i|tips?|advice|ideas?|ways? to)\\b", RegexOption.IGNORE_CASE)
    private val MINE = Regex("\\b(my|did i|have i|i've|i have|am i)\\b", RegexOption.IGNORE_CASE)
    private val LEFT = Regex("\\b(left|remaining|remain|over budget|under budget|within budget)\\b", RegexOption.IGNORE_CASE)
    private val BIGGEST = Regex("\\b(biggest|largest|most expensive|highest|max)\\b", RegexOption.IGNORE_CASE)
    private val AVERAGE = Regex("\\b(average|per day|a day|daily)\\b", RegexOption.IGNORE_CASE)
    private val LIST = Regex("\\b(show|list|see|view|what (are|were|did|have)|which)\\b", RegexOption.IGNORE_CASE)
    private val HOW_MUCH = Regex("\\b(how much|total)\\b", RegexOption.IGNORE_CASE)

    private enum class Period(val label: String) {
        TODAY("today"), YESTERDAY("yesterday"), THIS_WEEK("this week"), LAST_WEEK("last week"),
        THIS_MONTH("this month"), LAST_MONTH("last month"), ALL("so far"),
    }

    /**
     * True for questions about the user's own spending ("how much did I spend on food?").
     * General money advice ("how can I save money?") is left for the normal chat brain.
     */
    fun isBudgetQuestion(text: String): Boolean {
        if (!TOPIC.containsMatchIn(text) || !ASKING.containsMatchIn(text)) return false
        return !ADVICE.containsMatchIn(text) || MINE.containsMatchIn(text)
    }

    fun answer(text: String): String? {
        if (!isBudgetQuestion(text)) return null
        val s = text.lowercase()
        val state = Budget.state.value
        val asked = period(s)
        val category = ExpenseCategory.guess(s).takeIf { it != ExpenseCategory.OTHER }

        return PREFIX + " " + when {
            LEFT.containsMatchIn(s) -> left(state)
            BIGGEST.containsMatchIn(s) -> biggest(state.expenses, asked ?: Period.THIS_MONTH, category)
            AVERAGE.containsMatchIn(s) -> average(state.expenses, asked ?: Period.THIS_MONTH)
            LIST.containsMatchIn(s) && !HOW_MUCH.containsMatchIn(s) -> list(state.expenses, asked ?: Period.THIS_WEEK, category)
            else -> total(state.expenses, asked ?: Period.THIS_MONTH, category)
        }
    }

    // ------------------------------------------------------------------ answers

    private fun list(all: List<Expense>, p: Period, cat: ExpenseCategory?): String {
        val items = inPeriod(all, p).filter { cat == null || it.category == cat }
        val what = cat?.let { "${it.emoji} ${it.label} expenses" } ?: "expenses"
        if (items.isEmpty()) return "No $what logged ${p.label}. Tell me “spent 500 on lunch” to add one!"
        val lines = items.take(LIST_LIMIT).joinToString("\n") {
            "• ${it.category.emoji} ${it.note.ifBlank { it.category.label }}: ${Budget.format(it.amountCents)} (${Budget.dateLabel(it.time)})"
        }
        val more = if (items.size > LIST_LIMIT) "\n…and ${items.size - LIST_LIMIT} more in the Budget tab." else ""
        return "Your $what ${p.label}:\n$lines$more\nTotal: ${Budget.format(items.sumOf { it.amountCents })}"
    }

    private fun total(all: List<Expense>, p: Period, cat: ExpenseCategory?): String {
        val items = inPeriod(all, p)
        if (cat != null) {
            val c = items.filter { it.category == cat }
            return "You spent ${Budget.format(c.sumOf { it.amountCents })} on ${cat.emoji} ${cat.label} ${p.label} " +
                "(${c.size} expense${plural(c.size)})."
        }
        if (items.isEmpty()) return "Nothing spent ${p.label}. Your wallet is happy! 🌻"
        val top = items.groupBy { it.category }.mapValues { (_, v) -> v.sumOf { it.amountCents } }.maxByOrNull { it.value }
        val topLine = top?.let { " Most went on ${it.key.emoji} ${it.key.label} (${Budget.format(it.value)})." } ?: ""
        return "You spent ${Budget.format(items.sumOf { it.amountCents })} ${p.label} across ${items.size} expense${plural(items.size)}.$topLine"
    }

    private fun biggest(all: List<Expense>, p: Period, cat: ExpenseCategory?): String {
        val e = inPeriod(all, p).filter { cat == null || it.category == cat }.maxByOrNull { it.amountCents }
            ?: return "No expenses ${p.label} yet."
        return "Your biggest expense ${p.label} was ${e.category.emoji} ${e.note.ifBlank { e.category.label }}: " +
            "${Budget.format(e.amountCents)} (${Budget.dateLabel(e.time)})."
    }

    private fun average(all: List<Expense>, p: Period): String {
        val (from, to) = range(p)
        val start = maxOf(from, all.minOfOrNull { it.time } ?: from)
        val days = ((minOf(to, System.currentTimeMillis()) - start) / DAY + 1).coerceAtLeast(1)
        val sum = inPeriod(all, p).sumOf { it.amountCents }
        return "You're spending about ${Budget.format(sum / days)} a day ${p.label} (${Budget.format(sum)} over $days day${plural(days.toInt())})."
    }

    private fun left(state: Budget.State): String {
        if (state.monthlyLimitCents <= 0) {
            return "You haven't set a monthly budget yet. Set one in the 💰 Budget tab and I'll keep count!"
        }
        val spent = Budget.summary(state.expenses).month
        val left = state.monthlyLimitCents - spent
        val today = LocalDate.now()
        val daysLeft = ChronoUnit.DAYS.between(today, today.withDayOfMonth(today.lengthOfMonth())) + 1
        return if (left >= 0) {
            "You've spent ${Budget.format(spent)} of ${Budget.format(state.monthlyLimitCents)} this month. " +
                "${Budget.format(left)} left for $daysLeft day${plural(daysLeft.toInt())}, about ${Budget.format(left / daysLeft)} a day."
        } else {
            "Uh oh, you're ${Budget.format(-left)} over this month's ${Budget.format(state.monthlyLimitCents)} budget. Maybe a few no-spend days? 🌻"
        }
    }

    // ------------------------------------------------------------------ periods

    private fun period(s: String): Period? = when {
        "yesterday" in s -> Period.YESTERDAY
        "today" in s -> Period.TODAY
        "last week" in s -> Period.LAST_WEEK
        "week" in s -> Period.THIS_WEEK
        "last month" in s -> Period.LAST_MONTH
        "month" in s -> Period.THIS_MONTH
        "all time" in s || "ever" in s || "overall" in s || "so far" in s -> Period.ALL
        else -> null
    }

    private fun range(p: Period): Pair<Long, Long> {
        val now = System.currentTimeMillis()
        val today = Budget.dayStart()
        val week = Budget.weekStart()
        val month = Budget.monthStart()
        return when (p) {
            Period.TODAY -> today to now
            Period.YESTERDAY -> today - DAY to today
            Period.THIS_WEEK -> week to now
            Period.LAST_WEEK -> week - 7 * DAY to week
            Period.THIS_MONTH -> month to now
            Period.LAST_MONTH -> Budget.dayStart(LocalDate.now().withDayOfMonth(1).minusMonths(1)) to month
            Period.ALL -> 0L to now
        }
    }

    private fun inPeriod(all: List<Expense>, p: Period): List<Expense> {
        val (from, to) = range(p)
        return all.filter { it.time in from..to }
    }

    private fun plural(n: Int) = if (n == 1) "" else "s"
}
