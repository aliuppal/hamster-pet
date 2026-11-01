package com.aliuppal.pockethamster.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Expenses must never reach OpenRouter: budget chats are stripped from the AI history. */
class AiHistoryPrivacyTest {
    private fun user(text: String) = ChatMessage(fromPet = false, text = text)
    private fun pet(text: String) = ChatMessage(fromPet = true, text = text)

    @Test
    fun dropsBudgetMessagesAndTheirReplies() {
        val history = listOf(
            user("Hi Peanut!"),
            pet("Squeak! Hi hi!"),
            user("spent 500 on lunch"),
            pet("*scribbles in a tiny notebook* Noted Rs 500 for 🍔 lunch. That's Rs 500 this week and Rs 500 this month."),
            user("paid 1200 for taxi"),
            pet("Some other reply that follows a budget message"),
            user("how much did I spend this week?"),
            pet("*flips through notebook* This week: Rs 1,700. This month: Rs 1,700."),
            user("tell me a joke"),
            pet("What's a hamster's favourite car? A hamster wheel-drive!"),
            ChatMessage(fromPet = true, system = true, text = "AI brain unavailable (HTTP 429), using the offline brain."),
        )

        val safe = PetBrain.aiSafeHistory(history).map { it.text }

        assertEquals(
            listOf("Hi Peanut!", "Squeak! Hi hi!", "tell me a joke", "What's a hamster's favourite car? A hamster wheel-drive!"),
            safe,
        )
        assertTrue(safe.none { "Rs" in it || "500" in it || "1200" in it || "taxi" in it || "lunch" in it })
    }

    @Test
    fun budgetReplyIsDroppedEvenIfAnotherLineSlipsInBetween() {
        val history = listOf(
            user("spent 12.50 on coffee"),
            pet("Good morning! ☀️"),
            pet("*scribbles in a tiny notebook* Noted $12.50 for 🍔 coffee."),
            user("let's play"),
        )
        val safe = PetBrain.aiSafeHistory(history).map { it.text }
        assertEquals(listOf("let's play"), safe)
    }

    @Test
    fun recognisesBudgetMessages() {
        assertTrue(PetBrain.isBudgetMessage("I spent 300 on groceries"))
        assertTrue(PetBrain.isBudgetMessage("How much did I spend this month?"))
        assertTrue(PetBrain.isBudgetMessage("📒 Show my expenses"))
        assertTrue(PetBrain.isBudgetMessage("What was my biggest expense last week?"))
        assertTrue(!PetBrain.isBudgetMessage("How can I save money?"))
        assertTrue(!PetBrain.isBudgetMessage("What is the capital of France?"))
        assertTrue(!PetBrain.isBudgetMessage("I spent the day at the park"))
    }
}
