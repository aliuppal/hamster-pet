# Pocket Hamster

A cute virtual pet hamster for Android. It has a simple chat, and the hamster lives on your home screen.

## Download

**[⬇ PocketHamster.apk](https://github.com/aliuppal/hamster-pet/raw/main/apk/PocketHamster.apk)** (Android 8.0+, about 6.5 MB)

Open the link on your phone, tap the downloaded file and allow installing from this source. If Play Protect warns about an unrecognised app, tap **More details → Install anyway**. After installing, open the app and tap **Put on home screen**, then allow **Display over other apps**.

## Features

- **Pet tab:** an animated hamster (Peanut by default; rename it in Settings) with Food, Happiness and Energy stats that slowly drop over time. Use Feed 🌻, Play 🎡 and Sleep 🌙, or tap the hamster to pet it.
- **Chat tab:** talk to your hamster. Chatting cheers it up, and words act too: "want a seed?" feeds it, "let's play" plays, "good night" puts it to sleep.
  - Works fully offline with a built-in personality.
  - Optional AI replies: add an OpenRouter API key in Settings. Only free models (ids ending in `:free`) are allowed, with free fallbacks.
- **On the home screen:**
  - **Floating hamster.** After you allow *Display over other apps*, the hamster walks along the bottom of your screen on top of the home screen and other apps. Tap to pet it (it hops), double-tap to open the chat, drag to move it (it falls back down when you let go). It sometimes says things in a speech bubble, and its mood shows in its face. A low-priority notification has a **Hide** button. It comes back after a reboot.
  - **Home screen widget.** Shows the hamster, its mood, stat bars, and Feed / Play / Chat buttons.

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
