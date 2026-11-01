package com.aliuppal.pockethamster.cosmetics

import android.content.Context
import android.content.SharedPreferences
import com.aliuppal.pockethamster.widget.PetWidgetProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

enum class Rarity(val label: String) { STANDARD("Standard"), RARE("Rare"), FANTASY("Fantasy") }

enum class Fur(val label: String, val base: Int, val belly: Int, val cost: Int, val rarity: Rarity) {
    GOLDEN("Golden", 0xFFF2A65A.toInt(), 0xFFFFF3E2.toInt(), 0, Rarity.STANDARD),
    WHITE("Snow", 0xFFF3ECE6.toInt(), 0xFFFFFFFF.toInt(), 0, Rarity.STANDARD),
    GREY("Grey", 0xFFB9B1AB.toInt(), 0xFFF4EFEA.toInt(), 0, Rarity.STANDARD),
    CALICO("Calico", 0xFFF2A65A.toInt(), 0xFFFFF3E2.toInt(), 120, Rarity.RARE),
    BLACK("Midnight", 0xFF4A3F3A.toInt(), 0xFFEADDD0.toInt(), 120, Rarity.RARE),
    PASTEL_PINK("Pastel Pink", 0xFFF6B8CF.toInt(), 0xFFFFF0F5.toInt(), 200, Rarity.FANTASY),
    GALAXY_BLUE("Galaxy Blue", 0xFF5B6FD6.toInt(), 0xFFE6E9FF.toInt(), 250, Rarity.FANTASY),
}

enum class Slot(val label: String) { HEAD("Head"), FACE("Face"), BODY("Body"), PROP("Hand") }

enum class Season(val label: String, private val months: Set<Int>) {
    HALLOWEEN("October", setOf(10)),
    WINTER("Dec–Feb", setOf(12, 1, 2)),
    PRIDE("June", setOf(6));

    fun isActive(month: Int = LocalDate.now().monthValue) = month in months
}

enum class Item(
    val slot: Slot,
    val label: String,
    val emoji: String,
    val cost: Int,
    val season: Season? = null,
    val inShop: Boolean = true,
) {
    BOW(Slot.HEAD, "Bow", "🎀", 40),
    PARTY_HAT(Slot.HEAD, "Party hat", "🥳", 60),
    HEADPHONES(Slot.HEAD, "Headphones", "🎧", 90),
    CROWN(Slot.HEAD, "Crown", "👑", 200),
    BEANIE(Slot.HEAD, "Beanie", "🧶", 60, Season.WINTER),
    PUMPKIN_HAT(Slot.HEAD, "Pumpkin hat", "🎃", 60, Season.HALLOWEEN),
    GLASSES(Slot.FACE, "Glasses", "👓", 50),
    SUNGLASSES(Slot.FACE, "Sunglasses", "🕶️", 70),
    SLEEP_MASK(Slot.FACE, "Sleep mask", "😴", 40),
    SCARF(Slot.BODY, "Scarf", "🧣", 50, Season.WINTER),
    BACKPACK(Slot.BODY, "Backpack", "🎒", 60),
    TIE(Slot.BODY, "Tie", "👔", 50),
    CAPE(Slot.BODY, "Hero cape", "🦸", 150),
    RAINBOW_BANDANA(Slot.BODY, "Rainbow bandana", "🌈", 60, Season.PRIDE),
    WATER_BOTTLE(Slot.PROP, "Water bottle", "💧", 30),
    MAGNIFIER(Slot.PROP, "Magnifying glass", "🔍", 60),
    SEED(Slot.PROP, "Sunflower seed", "🌻", 0),
    UMBRELLA(Slot.PROP, "Umbrella", "☂️", 80),
    BREAK_SIGN(Slot.PROP, "Break sign", "🪧", 0, inShop = false);

    /** Seasonal items can only be unlocked while their season is on; once owned they stay. */
    fun purchasable() = inShop && (season?.isActive() ?: true)
}

/** Everything the renderer needs to dress the hamster. */
data class Look(
    val fur: Fur = Fur.GOLDEN,
    val head: Item? = null,
    val face: Item? = null,
    val body: Item? = null,
    val prop: Item? = null,
    val sign: String? = null,
    /** Buzzes with energy while charging during the day. */
    val shiver: Boolean = false,
    /** Night-time drowsiness: drawn with sleepy eyes even when the mood is fine. */
    val sleepy: Boolean = false,
) {
    operator fun get(slot: Slot): Item? = when (slot) {
        Slot.HEAD -> head
        Slot.FACE -> face
        Slot.BODY -> body
        Slot.PROP -> prop
    }

    fun with(slot: Slot, item: Item?): Look = when (slot) {
        Slot.HEAD -> copy(head = item)
        Slot.FACE -> copy(face = item)
        Slot.BODY -> copy(body = item)
        Slot.PROP -> copy(prop = item)
    }
}

data class Outfit(val name: String, val fur: Fur?, val items: Map<Slot, Item>)

/** Acorns, owned cosmetics, what is worn, saved outfits and the live preview from the Closet. */
object Wardrobe {
    data class State(
        val acorns: Int = 60,
        val goldenSeeds: Int = 0,
        val owned: Set<Item> = emptySet(),
        val ownedFurs: Set<Fur> = emptySet(),
        val equipped: Look = Look(),
        val outfits: List<Outfit> = emptyList(),
        val preview: Look? = null,
    ) {
        val shown: Look get() = preview ?: equipped
    }

    private val DEFAULT_ITEMS = setOf(Item.BOW, Item.SEED)
    private val DEFAULT_FURS = setOf(Fur.GOLDEN, Fur.WHITE, Fur.GREY)
    private val DEFAULT_OUTFITS = listOf(
        Outfit("Work Mode", null, mapOf(Slot.FACE to Item.GLASSES, Slot.BODY to Item.TIE)),
        Outfit("Weekend", null, mapOf(Slot.FACE to Item.SUNGLASSES, Slot.BODY to Item.BACKPACK)),
    )

    private lateinit var prefs: SharedPreferences
    private lateinit var appContext: Context
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    fun init(context: Context) {
        appContext = context.applicationContext
        prefs = appContext.getSharedPreferences("wardrobe", Context.MODE_PRIVATE)
        _state.value = load()
    }

    fun owns(item: Item) = item in _state.value.owned

    fun buy(item: Item): Boolean {
        val s = _state.value
        if (item in s.owned || !item.purchasable() || s.acorns < item.cost) return false
        commit(s.copy(acorns = s.acorns - item.cost, owned = s.owned + item, preview = null))
        equip(item.slot, item)
        return true
    }

    fun buyFur(fur: Fur): Boolean {
        val s = _state.value
        if (fur in s.ownedFurs || s.acorns < fur.cost) return false
        commit(s.copy(acorns = s.acorns - fur.cost, ownedFurs = s.ownedFurs + fur, preview = null))
        setFur(fur)
        return true
    }

    fun equip(slot: Slot, item: Item?) {
        val s = _state.value
        if (item != null && item !in s.owned) return
        commit(s.copy(equipped = s.equipped.with(slot, item), preview = null))
    }

    fun setFur(fur: Fur) {
        val s = _state.value
        if (fur !in s.ownedFurs) return
        commit(s.copy(equipped = s.equipped.copy(fur = fur), preview = null))
    }

    /** Try-on: shown at once in the app, the floating hamster and the widget. Pass null to stop. */
    fun preview(look: Look?) {
        if (_state.value.preview == look) return
        commit(_state.value.copy(preview = look), persist = false)
    }

    fun addAcorns(amount: Int) = commit(_state.value.copy(acorns = _state.value.acorns + amount))

    /** A rare golden seed is worth 50 acorns and counts towards the collection. */
    fun findGoldenSeed() {
        val s = _state.value
        commit(s.copy(acorns = s.acorns + 50, goldenSeeds = s.goldenSeeds + 1))
    }

    fun saveOutfit(name: String) {
        val s = _state.value
        val items = Slot.entries.mapNotNull { slot -> s.equipped[slot]?.let { slot to it } }.toMap()
        val outfit = Outfit(name.trim().ifBlank { "Outfit ${s.outfits.size + 1}" }, s.equipped.fur, items)
        commit(s.copy(outfits = s.outfits.filterNot { it.name == outfit.name } + outfit))
    }

    fun deleteOutfit(outfit: Outfit) = commit(_state.value.copy(outfits = _state.value.outfits - outfit))

    /** Wears every owned piece of the outfit and returns the pieces that still need unlocking. */
    fun applyOutfit(outfit: Outfit): List<Item> {
        val s = _state.value
        var look = Look(fur = outfit.fur?.takeIf { it in s.ownedFurs } ?: s.equipped.fur)
        val missing = mutableListOf<Item>()
        for ((slot, item) in outfit.items) {
            if (item in s.owned) look = look.with(slot, item) else missing += item
        }
        commit(s.copy(equipped = look, preview = null))
        return missing
    }

    private fun commit(next: State, persist: Boolean = true) {
        _state.value = next
        if (persist) save(next)
        PetWidgetProvider.refreshAll(appContext)
    }

    private fun load(): State {
        fun item(key: String) = prefs.getString(key, null)?.let { runCatching { Item.valueOf(it) }.getOrNull() }
        val owned = prefs.getStringSet("owned", null)?.mapNotNull { runCatching { Item.valueOf(it) }.getOrNull() }?.toSet()
        val furs = prefs.getStringSet("furs", null)?.mapNotNull { runCatching { Fur.valueOf(it) }.getOrNull() }?.toSet()
        val outfits = prefs.getString("outfits", null)?.let(::parseOutfits) ?: DEFAULT_OUTFITS
        return State(
            acorns = prefs.getInt("acorns", 60),
            goldenSeeds = prefs.getInt("goldenSeeds", 0),
            owned = owned ?: DEFAULT_ITEMS,
            ownedFurs = furs ?: DEFAULT_FURS,
            equipped = Look(
                fur = prefs.getString("fur", null)?.let { runCatching { Fur.valueOf(it) }.getOrNull() } ?: Fur.GOLDEN,
                head = item("head"), face = item("face"), body = item("body"), prop = item("prop"),
            ),
            outfits = outfits,
        )
    }

    private fun save(s: State) {
        val outfits = JSONArray()
        s.outfits.forEach { o ->
            val items = JSONObject()
            o.items.forEach { (slot, item) -> items.put(slot.name, item.name) }
            outfits.put(JSONObject().put("name", o.name).put("fur", o.fur?.name ?: "").put("items", items))
        }
        prefs.edit()
            .putInt("acorns", s.acorns)
            .putInt("goldenSeeds", s.goldenSeeds)
            .putStringSet("owned", s.owned.map { it.name }.toSet())
            .putStringSet("furs", s.ownedFurs.map { it.name }.toSet())
            .putString("fur", s.equipped.fur.name)
            .putString("head", s.equipped.head?.name)
            .putString("face", s.equipped.face?.name)
            .putString("body", s.equipped.body?.name)
            .putString("prop", s.equipped.prop?.name)
            .putString("outfits", outfits.toString())
            .apply()
    }

    private fun parseOutfits(raw: String): List<Outfit> = runCatching {
        val arr = JSONArray(raw)
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            val itemsJson = o.getJSONObject("items")
            val items = itemsJson.keys().asSequence().mapNotNull { key ->
                val slot = runCatching { Slot.valueOf(key) }.getOrNull()
                val item = runCatching { Item.valueOf(itemsJson.getString(key)) }.getOrNull()
                if (slot != null && item != null) slot to item else null
            }.toMap()
            Outfit(o.getString("name"), runCatching { Fur.valueOf(o.getString("fur")) }.getOrNull(), items)
        }
    }.getOrDefault(DEFAULT_OUTFITS)
}
