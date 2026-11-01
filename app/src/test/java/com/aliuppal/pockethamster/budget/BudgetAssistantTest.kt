package com.aliuppal.pockethamster.budget

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BudgetAssistantTest {
    @Before
    fun setUp() {
        Budget.init(RuntimeEnvironment.getApplication())
        Budget.setCurrency(Currency.PKR)
        val now = System.currentTimeMillis()
        Budget.add(Expense(amountCents = 50_000, category = ExpenseCategory.FOOD, note = "lunch", time = now))
        Budget.add(Expense(amountCents = 120_000, category = ExpenseCategory.TRANSPORT, note = "taxi", time = now - 1_000))
        Budget.add(Expense(amountCents = 30_000, category = ExpenseCategory.FOOD, note = "coffee", time = now - 2_000))
    }

    @Test
    fun listsExpensesWithTotal() {
        val a = BudgetAssistant.answer("📒 Show my expenses")!!
        assertTrue(a, "lunch" in a && "taxi" in a && "coffee" in a)
        assertTrue(a, "Total: Rs 2,000" in a)
    }

    @Test
    fun totalsByCategory() {
        val a = BudgetAssistant.answer("How much did I spend on food this month?")!!
        assertTrue(a, "Rs 800" in a && "Food" in a)
    }

    @Test
    fun findsBiggestExpense() {
        val a = BudgetAssistant.answer("🏆 Biggest expense this month")!!
        assertTrue(a, "taxi" in a && "Rs 1,200" in a)
    }

    @Test
    fun budgetLeft() {
        assertTrue(BudgetAssistant.answer("How much budget is left?")!!.contains("haven't set a monthly budget"))
        Budget.setMonthlyLimit(500_000)
        val a = BudgetAssistant.answer("How much budget is left?")!!
        assertTrue(a, "Rs 3,000 left" in a)
    }

    @Test
    fun questionsNeverLogExpenses() {
        assertNull(Budget.fromChat("what did I spend 3 days ago?"))
        assertNull(Budget.fromChat("how much did I spend 500 on?"))
        assertNotNull(Budget.fromChat("spent 500 on lunch"))
        assertEquals(3, Budget.state.value.expenses.size)
    }

    @Test
    fun generalQuestionsAndSmallTalkGoToNormalChat() {
        assertNull(BudgetAssistant.answer("How can I save money?"))
        assertNull(BudgetAssistant.answer("What is the capital of France?"))
        assertNull(BudgetAssistant.answer("I spent the day at the park"))
        assertFalse(BudgetAssistant.isBudgetQuestion("Tell me a joke"))
    }
}
