# Third-party notices

Personal Clock itself is released under the [MIT License](LICENSE). It bundles or links the following third-party
material, which keeps its own license.

## Fonts (SIL Open Font License 1.1)

Eight static, single-weight TTFs in `app/src/main/res/font/` are cut from the variable originals with fontTools
(see `tools/gen_assets.py`). The full license text and copyright line for each is in [`licenses/`](licenses/).

| Font | File | License text |
|---|---|---|
| DM Sans | `dm_sans.ttf` | [`OFL-dm_sans.txt`](licenses/OFL-dm_sans.txt) |
| Nunito | `nunito.ttf` | [`OFL-nunito.txt`](licenses/OFL-nunito.txt) |
| Fredoka | `fredoka.ttf` | [`OFL-fredoka.txt`](licenses/OFL-fredoka.txt) |
| Quicksand | `quicksand.ttf` | [`OFL-quicksand.txt`](licenses/OFL-quicksand.txt) |
| JetBrains Mono | `jetbrains_mono.ttf` | [`OFL-jetbrains_mono.txt`](licenses/OFL-jetbrains_mono.txt) |
| Playfair Display | `playfair_display.ttf` | [`OFL-playfair_display.txt`](licenses/OFL-playfair_display.txt) |
| Barlow Condensed | `barlow_condensed.ttf` | [`OFL-barlow_condensed.txt`](licenses/OFL-barlow_condensed.txt) |
| Poppins (Thin) | `poppins_thin.ttf` | [`OFL-poppins.txt`](licenses/OFL-poppins.txt) |

The fonts are bundled with the app, not sold on their own, which the OFL allows. Quicksand and Playfair Display
declare a *Reserved Font Name*; the OFL says a modified version (such as a static instance cut from a variable font)
should not be distributed under that name. If you redistribute this app publicly, either confirm the upstream
reserved-name status or rename those two fonts in the UI and resource files.

## Libraries (Apache License 2.0)

Linked at build time from Google's Maven repository (see `app/build.gradle.kts` for versions):

- AndroidX Core KTX, Activity Compose, Lifecycle (runtime-ktx, runtime-compose)
- Jetpack Compose (BOM: UI, Graphics, Foundation, Material 3, Material Icons Core)
- Kotlin standard library and the Kotlin Gradle/Compose compiler plugins

License text: <https://www.apache.org/licenses/LICENSE-2.0>

## Data

`app/src/main/assets/th_holidays.json` is a hand-compiled list of Thai public holidays. It is not copied from a
licensed dataset; verify it against the official Bank of Thailand / cabinet announcements (see the README).
