# NIRA — instructions for Claude Code

Read `docs/MASTER_CONTEXT.md` (version 2) fully before any work. It is the source of truth for the product, the hackathon rules and the priorities. Then read `docs/PROJECT_STATUS.md`.

## What this project is

NIRA is a native Android app (Kotlin, Jetpack Compose, package `com.mos.nira`) for the #BuildHybridAI 2026 hackathon. It turns garment artwork or a garment photo into a print engineering report using a deterministic engine, on-device Gemini Nano and cloud Gemini, all through Firebase AI Logic. Deadline: 1 December 2026, 3:30 am India time.

## The developer

Ravi is a garment and print domain expert. He is new to Git and day-to-day software workflow. Explain in simple English, give click-by-click steps for anything he must do by hand, and keep replies short.

## Commands

- Build: `./gradlew :app:assembleDebug`
- Unit tests: `./gradlew :app:testDebugUnitTest`
- Always run both after a change and report the real result. Never say tests passed unless you ran them.

## Hard rules

- All AI inference must go through Firebase AI Logic. Do not add TensorFlow, ML Kit vision models, or any other AI service or API.
- Never hard-code engineering values. They live in `app/src/main/assets/print_rules.json` and `print_techniques.json`. Do not invent rule values.
- Never bundle, download or generate Pantone or other third-party colour data.
- Never commit `app/google-services.json`, tokens, keys, `local.properties`, user shade libraries, buyer tech packs, or artwork showing third-party brands or characters.
- Do not push, make the repository public, or submit anything without Ravi's explicit approval.
- Do not upgrade Gradle, AGP or Kotlin unless a build requires it.
- No fake AI output and no fake inference-source indicators. Show the real `inferenceSource` only.
- Make small, single-purpose changes. Commit after each one that builds and passes tests.

## Working alongside Antigravity

Antigravity (inside Android Studio) also edits this project. Only one agent edits at a time. Before you start: confirm with Ravi that Antigravity is idle and its work is committed. When you finish: build, test, commit, and tell Ravi so Antigravity can continue from a clean state.

## Known issues to check first

1. `print_rules.json` was replaced with version 2 by hand. Verify `PrintRulesRepository.kt` reads the v2 key names (for example `process_inks` with `cyan`, `magenta`, `yellow`, `black`). Fix the loader, not the JSON.
2. `app/build.gradle.kts` sets `unitTests.isReturnDefaultValues = true`. Check whether any unit test passes only because Android stubs return default values (Bitmap, Color). Report which tests are not really testing behaviour.
3. Device test on a real garment photo (white-to-blue gradient logo on a black T-shirt) gave wrong results: fabric counted as print colours, inked area 84.9% instead of about 13%, a black ink screen on a black garment, base white under the whole photo, and CMYK + whites (6 screens) recommended for a 2-colour logo. The expected result is in `print_rules.json` under `regression_cases`. The rules to implement are under `garment_mask`, `white_base`, `ink_model`, `recommendation` and `resolution`.
4. Strategy evaluation took 16,750 ms on the vivo V60e for a 1024 px image. Earlier documents claimed under 250 ms from JVM tests on a simple image.
5. Developer messages are visible in the UI ("Part C screens and proof completed... stopping for user review", "Parts A, B & C").
6. All UI text is hard-coded in Kotlin; `strings.xml` is nearly empty. Tamil support will need the strings moved first.

## Priority order

1. Firebase Stage B: Google services plugin, Firebase BoM, Firebase AI Logic, App Check debug provider. Nothing Firebase is installed in Gradle yet. `app/google-services.json` is present and git-ignored.
2. First cloud call and first hybrid call with the real `inferenceSource` shown.
3. Fix the dark-garment engine issues (item 3 above), with the regression test.
4. Print Report Part D (ink quantity) and report layout.
5. Ask NIRA chat, technique library, dashboard update.
6. Web version for Chrome, public repository, licence, documents, video.

Feature freeze: 24 November 2026.
