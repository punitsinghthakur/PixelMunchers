# Pixel Munchers (Android)

A native Android puzzle game written in Kotlin. Tap numbered tiles to send munchers out of
the hole; each one walks to a matching-color pixel, picks it up, and carries it home.
Clear the whole picture to win.

## Run it on your phone (Android Studio)

1. Install Android Studio (free): https://developer.android.com/studio
2. Unzip this folder, then in Android Studio choose **File > Open** and select the `PixelMunchers` folder.
3. Wait for the Gradle sync to finish (the first time downloads a few hundred MB).
4. On your phone, turn on **Developer options** and **USB debugging**, then plug it in.
5. Press the green **Run** button. The game installs and opens on your phone.

To get an APK file to share: **Build > Build App Bundle(s) / APK(s) > Build APK(s)**.
The file appears in `app/build/outputs/apk/debug/`.

## Build the APK without Android Studio (GitHub)

Push this folder to a GitHub repository. The included workflow
(`.github/workflows/build-apk.yml`) builds the APK automatically. Open the repo's
**Actions** tab, pick the latest run, and download `pixel-munchers-debug-apk`.

## Levels

There are 600 levels, built by `LevelGenerator.kt` from each level's number, so they take
no storage and are the same on every phone:

- Levels 1–4 teach the game with simple pictures.
- Every 5th level is a hand-drawn picture (strawberry, rocket, frog, house...). After level 100
  these sometimes come in new colors.
- The rest are shapes (heart, star, flower, butterfly, moon...) filled with patterns, or
  little creatures.
- As levels rise, pictures grow from about 10 to 24 pixels across, use 2 to 6 colors, deal tiles
  in a more mixed-up order, and hide more tiles behind "?".

Players unlock levels one at a time. The grid button at the top left opens the level map
(swipe or use the arrows to change pages). To change the number of levels, edit
`TOTAL_LEVELS` in `LevelGenerator.kt`.

## Project layout

- `LevelGenerator.kt` builds all the levels.
- `Levels.kt` holds the colors and hand-drawn pictures. Add one by adding a new `Level(...)`
  with rows of letters ('.' means empty).
- `Game.kt` holds all the rules: tile dealing, the 5-slot queue, muncher movement, win/lose.
- `GameView.kt` draws the game and the level map, and handles taps.
- `MainActivity.kt` opens the game.

## Before publishing on Google Play

- Create your own signing key (Build > Generate Signed App Bundle) and upload an `.aab`.
- Change `applicationId` in `app/build.gradle.kts` to your own package name.
- Add sound, more levels, and ads (AdMob) if you want them.
