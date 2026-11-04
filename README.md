# Personal Clock

A single-user, **fully offline** Android clock for a Samsung phone (One UI), built to replace the stock
clock widget that started showing seconds.

- **Widget with no seconds**, fully customizable (font, size, alignment, background, opacity, colours, 12/24h, date, next alarm, second city clock, corner radius, padding, shadow, tap action) with a live preview. Each placed widget has its own settings.
- **Alarms** on a fixed work schedule that can **skip Thai public holidays**.
- **Timer** (several at once), **Stopwatch**, **World clock** with `UTC±X` per city.
- Uses the phone's own **system ringtones** on the alarm volume.
- **No `INTERNET` permission.** No account, no analytics. CI fails the build if the permission ever appears.

Package: `app.personal.clock` · minSdk 31 · target/compile SDK 35 · Kotlin + Jetpack Compose (Material 3) for the app, XML RemoteViews for the widget.

## Install on the phone

1. Open the repo's **Releases** page on the phone and download the APK from **"Latest build"** (or from a `v*` release).
2. Allow *Install unknown apps* for the browser when asked, then install.
3. Open the app and go to **Settings → Permission health**. Fix every red row (exact alarms, notifications, full-screen alarm, battery *Unrestricted*). Calendar access is optional.
4. Long-press the home screen → Widgets → **Clock (no seconds)**.

New builds install **over** the old one without losing alarms, as long as they are signed with the same key (see *Signing key* below; until `KEYSTORE_PASSWORD` is set, every build uses a different throw-away key and cannot update).

### Seed alarms (created on first launch, all editable)

| Alarm | Days | Time | Skip Thai holidays |
|---|---|---|---|
| Morning Mon | Mon | 05:30 | yes |
| Morning Tue-Fri | Tue–Fri | 07:30 | yes |
| Afternoon | Mon–Fri | 16:30 | yes |

Alarms fire on **Asia/Bangkok** time even if the phone changes zone (Settings → "Follow phone time zone", off by default).

## Signing key (why updates need it)

Android only installs an APK over an existing app if both are signed with the **same key**. Without a configured key, CI signs with a throw-away debug key that is different on every build, so each build must be installed by uninstalling the previous one (which deletes your alarms).

The repo contains `signing/clock-release.p12`, a permanent key protected by a long random password. **The password is not in the repo**: add it once as a GitHub secret:

*Repo → Settings → Secrets and variables → Actions → New repository secret* → name `KEYSTORE_PASSWORD`, value = the password you were given.

From then on CI signs every build with that key (the log prints the certificate SHA-256 so you can compare builds) and updates install over the old version with data intact.

- **Back up the password** (password manager). Without it the committed file cannot be used and you would have to uninstall to switch keys.
- Alias is `clock`; the key password equals the store password. `KEY_ALIAS` / `KEY_PASSWORD` secrets are optional overrides.
- Prefer to keep the key out of the repo entirely? Run `./scripts/make-keystore.sh` on a computer and add `KEYSTORE_BASE64` + `KEYSTORE_PASSWORD` (+ optional `KEY_ALIAS`, `KEY_PASSWORD`) as secrets instead; that takes priority over the file in `signing/`, and you can then delete `signing/`. Switching keys requires one uninstall.

## Builds (GitHub Actions)

`.github/workflows/build.yml` runs on every push to `main` or a `claude/**` branch, on tags `v*`, and manually:

1. unit tests → 2. `./gradlew assembleRelease` → 3. fails if the APK requests `INTERNET` → 4. uploads the APK as a workflow artifact → 5. publishes it to a GitHub Release: tags `v*` get their own release, and each branch push (`main` or `claude/**`) updates a rolling pre-release called **latest** with a stable download link.

To cut a numbered release: `git tag v1.0.0 && git push origin v1.0.0`.

## Thai holiday skip

Checked in this order, per calendar year, **when an alarm is due** and when the "next alarm" label is computed:

1. **The phone's calendar** (needs the optional Calendar permission): all-day events in any calendar whose name contains a configured pattern (default `Holidays in Thailand` or `วันหยุดในประเทศไทย`). This is data Google Calendar has already synced, so it works offline. Enable the *Holidays in Thailand* calendar in Google Calendar on the phone.
2. **Built-in list** `app/src/main/assets/th_holidays.json` if the permission is denied, no matching calendar exists, or it has no events for that year.

Settings shows which source is deciding and has a **"Test holiday check for date…"** tool. When an alarm is skipped you get a quiet *"Skipped: Songkran"* notification (can be turned off) and the next valid day is scheduled.

> ### ⚠️ Yearly reminder: refresh `th_holidays.json`
> The built-in list currently covers **2026 and 2027**. It was compiled by hand, not downloaded from an official source: **verify it against the official Bank of Thailand / cabinet list**, and add the next year every autumn. Entries marked `"provisional": true` (lunar-calendar days and some substitute days for 2027) are estimates. One-off cabinet-announced holidays and late substitute-day announcements are *not* in the file; the calendar layer catches them when it has synced. Past the last year in the file (and with no calendar data) alarms are simply **not** skipped.

## How alarms stay reliable

- `AlarmManager.setAlarmClock()` (works in Doze, shows the alarm icon, no special permission), **chain-scheduled**: only the next occurrence of each alarm is registered; when it fires or is skipped the receiver registers the following one.
- Rescheduled on app launch, `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIMEZONE_CHANGED`, `TIME_SET`, and after every edit.
- Ringing = foreground service (`mediaPlayback`) playing through `USAGE_ALARM` (alarm volume, DND "alarms" exception), a wake lock, a full-screen intent over the lock screen, Snooze/Dismiss, and auto-stop (default 10 min) with a "missed" notification.
- Timers: one exact Doze-proof alarm on the `elapsedRealtime` clock plus a system-rendered countdown notification. Stopwatch uses `elapsedRealtime` too.

### Samsung / One UI checklist
Battery **Unrestricted**, not in *Sleeping apps*; never *Force stop*; test with DND on; test with the screen off for 15+ minutes and after a reboot before trusting it for a real wake-up.

## Widget notes

- Time is a `TextClock` (`h:mm` / `HH:mm`): the system updates it each minute at no battery cost, and no seconds format exists anywhere in the widget.
- RemoteViews cannot change a typeface at runtime, so there is **one generated layout per font × shadow** (`widget_f0_s0.xml` …); generated by `python3 tools/gen_assets.py` (outputs are committed).
- **"Frosted" is not a real blur.** Widgets run in the launcher's process and cannot blur the wallpaper; "frosted" is a tinted translucent panel with a soft light edge, and the config screen says so.
- The text auto-fits: if the widget is small, the date / next-alarm / city lines are dropped before the time is shrunk below ~70%.

## Design notes / deviations from the original spec

- **Storage is `SharedPreferences` + JSON, not Room/DataStore.** Alarms must be readable synchronously from a broadcast receiver, and it keeps the dependency list to just AndroidX core/activity/lifecycle + Compose. Backup/restore falls out for free (Settings → Backup).
- Target/compile SDK is **35**; raise it in `app/build.gradle.kts` when your phone's Android version is confirmed (the app has no other version-specific code).
- Fonts (OFL 1.1, licenses in `licenses/`): DM Sans, Nunito, JetBrains Mono, Playfair Display, Barlow Condensed, Poppins Thin.

## Development

```bash
./gradlew testDebugUnitTest      # pure-Kotlin tests (scheduling maths, formatting, widget fitting)
./gradlew assembleRelease        # needs the Android SDK; CI does this for you
```
