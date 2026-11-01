package com.aliuppal.pockethamster.chat

import com.aliuppal.pockethamster.AppSettings
import com.aliuppal.pockethamster.budget.Budget
import com.aliuppal.pockethamster.pet.Mood
import com.aliuppal.pockethamster.pet.PetRepository
import com.aliuppal.pockethamster.pet.PetState
import com.aliuppal.pockethamster.wellness.Wellness
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

enum class PetAction { FEED, PLAY, SLEEP, WAKE, PET, WATER }

enum class Outcome { FED, TOO_FULL, PLAYED, TOO_TIRED, SLEPT, WOKE, PETTED, HYDRATED }

class BrainReply(val text: String, val usedAi: Boolean, val aiError: String?)

object PetBrain {

    fun perform(action: PetAction): Outcome = when (action) {
        PetAction.FEED -> if (PetRepository.feed()) Outcome.FED else Outcome.TOO_FULL
        PetAction.PLAY -> if (PetRepository.play()) Outcome.PLAYED else Outcome.TOO_TIRED
        PetAction.SLEEP -> { PetRepository.sleep(); Outcome.SLEPT }
        PetAction.WAKE -> { PetRepository.wake(); Outcome.WOKE }
        PetAction.PET -> { PetRepository.pet(); Outcome.PETTED }
        PetAction.WATER -> { Wellness.logWater(); Outcome.HYDRATED }
    }

    /** Talking to the hamster can also do things: "want a seed?" feeds it, "let's play" plays. */
    fun detectAction(input: String): PetAction? {
        val s = input.lowercase()
        fun has(vararg words: String) = words.any { Regex("\\b$it").containsMatchIn(s) }
        return when {
            Regex("(drank|drink|had|sip|glass of) (some |a )?(water|glass)|hydrated").containsMatchIn(s) -> PetAction.WATER
            has("wake", "good morning", "get up", "rise and shine") -> PetAction.WAKE
            has("seed", "snack", "food", "eat", "feed", "carrot", "treat", "cookie", "nut", "apple",
                "dinner", "lunch", "breakfast", "broccoli") -> PetAction.FEED
            has("play", "wheel", "ball", "game", "run", "tunnel", "race") -> PetAction.PLAY
            has("sleep", "nap", "bed", "good night", "goodnight", "rest") -> PetAction.SLEEP
            has("pet", "cuddle", "hug", "scratch", "stroke", "boop", "love you") -> PetAction.PET
            else -> null
        }
    }

    suspend fun respond(input: String, history: List<ChatMessage>): BrainReply {
        // Budget keeping is answered directly so amounts are never made up by an AI model.
        Budget.fromChat(input)?.let { e ->
            Budget.add(e)
            PetRepository.chatted()
            val s = Budget.summary()
            return BrainReply(
                "*scribbles in a tiny notebook* Noted ${Budget.format(e.amountCents)} for ${e.category.emoji} ${e.note}. " +
                    "That's ${Budget.format(s.week)} this week and ${Budget.format(s.month)} this month.",
                usedAi = false, aiError = null,
            )
        }
        if (Budget.isSpendingQuestion(input)) {
            val s = Budget.summary()
            val top = s.byCategoryMonth.entries.firstOrNull()
            val topLine = top?.let { " Most of it went on ${it.key.emoji} ${it.key.label} (${Budget.format(it.value)})." } ?: ""
            return BrainReply(
                "*flips through notebook* This week: ${Budget.format(s.week)}. This month: ${Budget.format(s.month)}.$topLine",
                usedAi = false, aiError = null,
            )
        }
        val outcome = detectAction(input)?.let { perform(it) }
        PetRepository.chatted()
        val state = PetRepository.state.value
        var aiError: String? = null
        val key = AppSettings.apiKey
        if (key.isNotBlank()) {
            try {
                return BrainReply(askOpenRouter(key, history, state, outcome), true, null)
            } catch (e: Exception) {
                aiError = e.message ?: e.javaClass.simpleName
            }
        }
        return BrainReply(offlineReply(input, state, outcome), false, aiError)
    }

    // ---------------------------------------------------------------- offline brain

    private fun offlineReply(input: String, state: PetState, outcome: Outcome?): String {
        if (outcome != null) return PetLines.forOutcome(outcome)
        val s = input.lowercase().trim()
        fun has(vararg p: String) = p.any { Regex(it).containsMatchIn(s) }
        return when {
            state.sleeping -> pick(
                "zzz... *mumbles* ...sunflower seeds... zzz",
                "*snores softly* ...five more minutes... zzz",
                "zzz... (say \"wake up\" if you need me) ...zzz",
            )
            has("\\b(hi|hello|hey|hiya|yo|howdy)\\b") -> pick(
                "Squeak! Hi hi! I'm so happy you're here!",
                "*pops out of the bedding* Oh! Hello!",
                "Hiii! *waves a tiny paw*",
            )
            has("how are you", "how do you feel", "you ok", "how's it going", "hungry\\?", "are you hungry") -> statusLine(state)
            has("your name", "who are you", "what are you") ->
                "I'm ${state.name}, your pocket hamster! I live in your phone, run on your home screen, and I love seeds."
            has("joke", "funny", "make me laugh") -> pick(
                "Why did the hamster get a job? He wanted to make a little extra cheddar... for his cheek pouches!",
                "What do hamsters do on the weekend? Hamster-dance! *wiggles*",
                "Why are hamsters bad at secrets? They always spill the beans... and the seeds.",
                "What's a hamster's favourite car? A hamster wheel-drive!",
            )
            has("sad", "lonely", "bad day", "tired", "stress", "upset", "cry") -> pick(
                "Aww. *climbs onto your hand* I'm here. Want to tell me about it?",
                "*gives you the biggest tiny hug* You're doing great. Seeds make everything better, want one?",
                "Sending squeaky love your way! *nuzzles*",
            )
            has("thank") -> pick("You're welcome! *happy squeak*", "Anytime! *twitches whiskers*")
            has("\\bbye\\b", "see you", "later") -> pick(
                "Bye bye! I'll be running around on your home screen!",
                "See you soon! I'll keep the wheel warm.",
            )
            has("love", "cute", "good boy", "good girl", "adorable") -> pick(
                "*blushes* Hehe, you're cute too!",
                "*happy wiggle* I love you more than sunflower seeds! ...Almost.",
            )
            s.endsWith("?") -> pick(
                "Hmm... *tilts head* I'm just a hamster, but I think... yes? Maybe? Seeds?",
                "That's a big question for a small hamster! *thinks very hard*",
                "*scratches ear* I don't know, but I know where I hid a peanut!",
            )
            else -> PetLines.idle(state.mood)
        }
    }

    private fun statusLine(s: PetState): String {
        val bits = buildList {
            if (s.fullness < 30) add("my tummy is rumbling") else if (s.fullness > 80) add("my tummy is full")
            if (s.energy < 20) add("I'm really sleepy") else if (s.energy > 80) add("I have SO much energy")
            if (s.happiness < 30) add("I've been a bit lonely") else if (s.happiness > 80) add("I'm super happy")
        }
        return if (bits.isEmpty()) "I'm doing okay! Just nibbling and napping." else
            "Well, ${bits.joinToString(" and ")}! *squeak*"
    }

    // ---------------------------------------------------------------- OpenRouter (free models only)

    private suspend fun askOpenRouter(
        key: String, history: List<ChatMessage>, state: PetState, outcome: Outcome?,
    ): String = withContext(Dispatchers.IO) {
        val models = (listOf(AppSettings.model.trim()) + AppSettings.FREE_FALLBACKS)
            .distinct().filter { it.endsWith(":free") }.take(3)
        if (models.isEmpty()) throw IOException("only :free models are allowed")

        val messages = JSONArray().put(JSONObject().put("role", "system").put("content", systemPrompt(state, outcome)))
        history.filter { !it.system }.takeLast(12).forEach {
            messages.put(JSONObject().put("role", if (it.fromPet) "assistant" else "user").put("content", it.text))
        }
        val body = JSONObject()
            .put("model", models.first())
            .put("models", JSONArray(models))
            .put("messages", messages)
            .put("max_tokens", 160)
            .put("temperature", 0.9)
            // Many free models are reasoning models; without this they can return empty text.
            .put("reasoning", JSONObject().put("effort", "low").put("exclude", true))

        val conn = URL("https://openrouter.ai/api/v1/chat/completions").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.doOutput = true
            conn.setRequestProperty("Authorization", "Bearer $key")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("X-Title", "Pocket Hamster")
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = conn.responseCode
            val raw = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                val msg = runCatching { JSONObject(raw).getJSONObject("error").getString("message") }.getOrNull()
                throw IOException(if (msg != null && "free-models-per-day" in msg) "daily free limit reached" else "HTTP $code")
            }
            val text = JSONObject(raw).getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").optString("content").trim()
            if (text.isEmpty()) throw IOException("empty reply")
            text
        } finally {
            conn.disconnect()
        }
    }

    private fun systemPrompt(s: PetState, outcome: Outcome?): String {
        val event = when (outcome) {
            Outcome.FED -> "The user just fed you a tasty sunflower seed; your cheeks are stuffed."
            Outcome.TOO_FULL -> "The user offered food but you are completely full."
            Outcome.PLAYED -> "The user just played with you on your wheel."
            Outcome.TOO_TIRED -> "The user wanted to play but you are too tired."
            Outcome.SLEPT -> "You are now curling up to go to sleep."
            Outcome.WOKE -> "The user just woke you up."
            Outcome.PETTED -> "The user just petted and cuddled you."
            Outcome.HYDRATED -> "The user just drank a glass of water; cheer them on, you sip from your water bottle too."
            null -> ""
        }
        return """
            You are ${s.name}, a tiny, cute pet hamster who lives inside the user's phone and runs around their home screen.
            Reply in 1-2 short sentences with simple, playful words. Sometimes add hamster actions in asterisks like *squeak* or *stuffs cheeks*.
            Stay in character: never say you are an AI or a language model. Keep it kind and family friendly.
            Current feelings: mood ${s.mood.label.lowercase()}, fullness ${s.fullness.roundToInt()}/100, happiness ${s.happiness.roundToInt()}/100, energy ${s.energy.roundToInt()}/100${if (s.sleeping) ", you are asleep and talk sleepily" else ""}.
            $event
        """.trimIndent()
    }
}

/** Short lines shared by the chat, the in-app pet and the floating overlay bubble. */
object PetLines {
    fun forOutcome(o: Outcome): String = when (o) {
        Outcome.FED -> pick("*stuffs a seed into cheek pouch* Nom nom! Thank you!", "Ooh, a snack! *nibble nibble nibble*", "*cheeks puff up* Mmmf! Fanks!")
        Outcome.TOO_FULL -> pick("*pats tummy* I'm sooo full... I'll hide it in my bedding for later!", "No more room in my cheeks! *mmf*")
        Outcome.PLAYED -> pick("Wheel time! *runs very fast going nowhere* Wheee!", "*zooms through the tunnel* Again! Again!", "*rolls around* That was the best!")
        Outcome.TOO_TIRED -> pick("*yawns* Too sleepy to play... maybe after a nap?", "My little legs are tired... zzz?")
        Outcome.SLEPT -> pick("*curls into a fuzzy ball* Night night... zzz", "*burrows into the bedding* Sweet dreams...")
        Outcome.WOKE -> pick("*stretches tiny paws* I'm awake! Hamsters love nighttime though, hehe.", "*yawn* Good morning! Is it seed o'clock?")
        Outcome.PETTED -> pick("*leans into your finger* Hehe, that tickles!", "*happy squeak* More pets please!")
        Outcome.HYDRATED -> pick("*sips from tiny bottle* Hydrated! 💧 We're a great team!", "Glug glug! 💧 +20 acorns for us!")
    }

    fun tap(mood: Mood): String = when (mood) {
        Mood.SLEEPING -> pick("zzz...", "*snore*", "mmf... zzz")
        Mood.HUNGRY -> pick("Seed please?", "*tummy rumbles*", "Hungry...")
        Mood.TIRED -> pick("*yawn*", "Sleepy...", "Nap time?")
        Mood.SAD -> pick("You came back!", "Play with me?", "*sniff*")
        else -> pick("Squeak!", "Hehe!", "*wiggles*", "Hi!", "Boop!", "*twitches nose*")
    }

    fun idle(mood: Mood): String = when (mood) {
        Mood.HAPPY -> pick("*happy squeaks* I love hanging out with you!", "*does a little spin* Life is good!", "Wanna see me run on my wheel?")
        Mood.HUNGRY -> pick("That's nice... but my tummy is rumbling. Got a seed?", "*sniffs the air* Is that food?")
        Mood.TIRED -> pick("*yawns* I could use a nap...", "Mmm... sleepy hamster...")
        Mood.SAD -> pick("I missed you! Will you play with me?", "*looks up with big eyes* Can we cuddle?")
        Mood.SLEEPING -> "zzz..."
        Mood.OK -> pick("*twitches whiskers* Tell me more!", "Ooh, interesting! *nibbles*", "Hehe, okay!")
    }

    /** Things the floating hamster says to itself now and then. */
    fun chatter(mood: Mood, name: String): String? = when (mood) {
        Mood.HUNGRY -> pick("I'm hungry...", "Seeds? Anyone?", "*tummy rumble*")
        Mood.SAD -> pick("Play with me?", "I'm lonely...", "Tap me!")
        Mood.TIRED -> pick("*yawn*", "So sleepy...")
        Mood.SLEEPING -> pick("zzz", null)
        else -> pick("*sniff sniff*", "La la la~", "$name is exploring!", "Double-tap to chat!", null, null)
    }
}

private fun <T> pick(vararg options: T): T = options.random()
