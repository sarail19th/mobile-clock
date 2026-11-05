# Local checks (no Android SDK needed)

Why: the sandboxes this project is developed in cannot reach Google's Maven or SDK hosts, so `./gradlew` cannot build the
app there; CI (GitHub Actions) is the real compiler. These two tiny Gradle projects catch most mistakes *before* a CI round-trip.

| Check | What it proves | What it cannot check |
|---|---|---|
| `fwcheck` | the non-UI Kotlin (core, data, alarm, holiday, widget) type-checks against the real Android 15 framework | Jetpack Compose UI code (`ui/`), resources/manifest linking |
| `jvmcheck` | all pure-Kotlin unit tests in `app/src/test` pass | anything needing a device |

## Use

```bash
cd tools/local-check
# 1. the Android framework jar (from Maven Central; ~185 MB; git-ignored)
mkdir -p jars && curl -L -o jars/android-all.jar \
  https://repo.maven.apache.org/maven2/org/robolectric/android-all/15-robolectric-12650502/android-all-15-robolectric-12650502.jar
# 2. stub R class generated from app/src/main/res (re-run whenever resources are added/removed)
python3 gen_r.py
# 3. type-check, and run the tests
(cd fwcheck  && gradle compileKotlin -q)
(cd jvmcheck && gradle run -q)          # prints passed=N failed=0
```

Needs a JDK 21 and a system Gradle. Notes:
- A line like `The daemon has terminated unexpectedly ... Kotlin compile daemon is ready` is noise, not an error; real errors start with `e: file:///...`.
- When you add a test class, add it to the list in `jvmcheck/shim/Runner.kt`. `shim/Junit.kt` implements only the few `Assert` functions the tests use (add more if a test needs them).
- CI uses the real JUnit and a real `org.json` (`testImplementation("org.json:json:...")`).
- Compose code cannot be checked here: expect a CI round-trip for UI changes and read the failure with the GitHub MCP tools (`get_job_logs`, `failed_only`).
