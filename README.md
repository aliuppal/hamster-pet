# Pocket Hamster

A cute virtual pet hamster for Android. It has a simple chat, and the hamster lives on your home screen.

## Download

**[⬇ PocketHamster.apk](https://github.com/aliuppal/hamster-pet/raw/main/apk/PocketHamster.apk)** (Android 8.0+, about 8.5 MB)

Open the link on your phone, tap the downloaded file and allow installing from this source. If Play Protect warns about an unrecognised app, tap **More details → Install anyway**. After installing, open the app and tap **Put on home screen**, then allow **Display over other apps**.

## Features

- **Pet tab:** an animated hamster (Peanut by default; rename it in Settings) with Food, Happiness and Energy stats that slowly drop over time. Use Feed 🌻, Play 🎡 and Sleep 🌙, or tap the hamster to pet it.
- **Chat tab:** talk to your hamster. Chatting cheers it up, and words act too: "want a seed?" feeds it, "let's play" plays, "good night" puts it to sleep.
  - Works fully offline with a built-in personality.
  - Optional AI replies: add an OpenRouter API key in Settings. Only free models (ids ending in `:free`) are allowed, with free fallbacks.
- **On the home screen:**
  - **Floating hamster.** After you allow *Display over other apps*, the hamster roams your whole screen on top of the home screen and other apps. Drag it anywhere and it stays where you drop it. Tap to pet it, double-tap to open the chat. A low-priority notification has a **Hide** button, and the hamster comes back after a reboot.
  - **Home screen widget.** Shows the hamster in its current outfit, its mood, stat bars, and Feed / Play / Chat buttons.
- **Lives with you (context-aware):**
  - After 30+ minutes in social apps it holds up a **"Let's take a break!"** sign. This uses Usage access and needs your opt-in after a disclosure.
  - It shivers when the phone is on silent or vibrate, wears sunglasses at full brightness, and goes to sleep (with a sleep mask) when you charge the phone at night.
  - It says "Good morning! ☀️" at 8 AM and "Time to wind down 🌙" at 10 PM, and jokes about a low battery at 15%. It carries an umbrella when it's raining near you (approximate location, Open-Meteo, opt-in).
- **Wellness:**
  - **Hydration loop.** The hamster gets thirsty, puts on its backpack and water bottle, and asks you to tap it after a sip. Tap it, or tap "I drank water" in the notification, and you get a hop dance and +20 🌰, and it keeps the bottle for 10 minutes.
  - **Steps (Health Connect).** Every 1,000 steps cheers the hamster up. At 5,000 steps it finds a rare golden seed.
  - **Screen time.** Staying under your daily goal makes the stats decay 40% slower.
  - **Focus mode.** A 25-minute session with glasses on earns +15 🌰.
- **Reminder settings.** You can switch Health, Productivity and Pet care reminders on or off. Swipe a category's reminder away 3 times in a row and the hamster says "Okay, I'll let you be for a while!" and backs off for 4 hours.
- **Closet.** Seven furs (Standard, Rare, Fantasy), plus hats, glasses, scarves, a cape, a backpack and props. Seasonal items (pumpkin hat in October, beanie and scarf in Dec–Feb, rainbow bandana in June) can only be bought in season. Tapping an item is a **live try-on** in the app, the floating hamster and the widget at the same time. Unlock items with acorns 🌰, and save **outfits** such as "Work Mode" and "Weekend".
- **Budget tracker.** Log expenses by category, see **this week** and **this month** totals and a per-category breakdown, and set an optional monthly budget. You can also log by chat: "spent 12.50 on lunch". Ask "how much did I spend?" for a summary.

On first launch the app offers to put the hamster on your home screen straight away.

## Build

Requirements: JDK 17 and the Android SDK (platform 35).

```bash
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # renders every mood to app/build/pet-renders/*.png
```

Install it on a phone with USB debugging turned on: `adb install app/build/outputs/apk/debug/app-debug.apk`. You can also copy the APK to the phone and open it.

## Tech

Kotlin and Jetpack Compose (Material 3), minSdk 26 and targetSdk 35. The hamster is drawn in code by `PetRenderer`, so the same art appears in the app, the overlay (a `WindowManager` view in a foreground service) and the widget (`RemoteViews` bitmap). State is kept in SharedPreferences, and stats decay based on how much time has passed.
