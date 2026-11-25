# NIRA — Textile Intelligence

**From garment artwork to intelligent textile decisions.**

NIRA turns a garment artwork file or a photo of a printed garment into a print engineering report: real print colours, print area, separation options, screens, mesh, ink quantity and a print method suggestion. A deterministic engine does all the numbers. On-device Gemini Nano and cloud Gemini, both through **Firebase AI Logic**, read the artwork and explain the results. The confidential artwork stays on the device unless the user agrees to send it.

Built for the **#BuildHybridAI 2026** hackathon (Google / Firebase).

> Submitted to #BuildHybridAI 2026. Judges: use the `master` branch. Measured results are in [docs/PROJECT_STATUS.md](docs/PROJECT_STATUS.md); known limits are listed honestly in [docs/UNFINISHED.md](docs/UNFINISHED.md) and under "Honest limits" below.

## For judges

- **Web (Chrome):** https://nira-90d39.web.app — choose the job, press **Explain this card**, **Ask NIRA** or **Translate**. Each answer shows the real source (`ON_DEVICE` or `IN_CLOUD`) and time.
- **Live link (phone → web):** on the phone, open a job → Decision tab → **Show on the web**. On the web page, Try it live → **Or connect your phone (live)** → type the code. The page follows changes on the phone; **↻ Refresh** reads again.
- **Android:** judge build "NIRA judge"; steps in [docs/JUDGE_ACCESS_PLAN.md](docs/JUDGE_ACCESS_PLAN.md).
- **Where on-device AI runs:** Gemini Nano in Chrome (tested 18 Nov 2026, `ON_DEVICE`). The test phone (vivo V60e) has no on-device model, so the Android app shows `IN_CLOUD` there; it never pretends.

## Honest limits

- The garment preview is a model, not a proof. Costs use sample factory rates, not a quote.
- Many rule values are labelled "assumption" until confirmed on the floor.
- First analysis of a large tonal artwork takes about 30–40 s on a mid-range phone (measured, 22 Nov 2026).

## Who it is for

Screen-print technicians, merchandisers, garment designers and sampling teams who must decide, before a sample is made, how many colours an artwork really has, which print method fits the fabric, how many screens are needed and roughly how much ink the job uses.

## Three layers

| Layer | What it does | Where |
| :--- | :--- | :--- |
| **A. Deterministic engine** (no AI) | Garment colour from the image border, print mask and print box, print colours with CIEDE2000, separation strategies (spot, halftone, CMYK), film positives, proof, ink quantity, print method rules | `app/src/main/java/com/mos/nira/core/image`, `core/adjust`, `core/advice` |
| **B. On-device AI** | Reads the artwork image (flat or tonal, motifs, print risks) with Gemini Nano through Firebase AI Logic hybrid inference | `core/ai/HybridInferenceRepository.kt`, `web/` |
| **C. Cloud AI** | Same request when on-device is not available, through Firebase AI Logic (Gemini Developer API). Images go to the cloud only after the user agrees | same |

Every AI answer shows the real `inferenceSource` reported by the SDK (`ON_DEVICE` or `IN_CLOUD`) and its duration. NIRA never guesses the source from the requested mode.

## Privacy

- **Confidential mode** (Settings) blocks every cloud request.
- An artwork image is sent to the cloud only after a clear confirmation dialog.
- **Live link:** only after **Show on the web**, the job's text facts (never the artwork) are stored in Firestore under a random 8-character code for 24 hours. `firestore.rules`: only the phone that made a code can change or delete it, nobody can list codes, expired jobs cannot be read. **Stop sharing** deletes it.
- Counters show answers on device, answers in cloud and images uploaded.
- Artwork, user shade libraries and saved analyses stay in app-private storage.
- No Pantone or other third-party colour library is included. Users import their own licensed data.

## Platforms

- **Android** (main): Kotlin, Jetpack Compose, Material 3. Package `com.mos.nira`, minimum Android 8.0 (API 26).
- **Web** (Chrome desktop): a small page in `web/` that runs the same hybrid AI calls, to prove on-device inference with Chrome's built-in model.

## Build the Android app

Requirements: Android Studio with JDK 17 or newer, Android SDK 36.

1. Clone the repository.
2. Create a Firebase project, add an Android app with package `com.mos.nira`, enable **Firebase AI Logic** (Gemini Developer API) and **App Check**.
3. Download `google-services.json` and put it in `app/`. It is git-ignored; never commit it.
4. Build and test:
   ```bash
   ./gradlew :app:assembleDebug
   ./gradlew :app:testDebugUnitTest
   ```
5. Install the debug build, open **Settings → Developer test**, copy the App Check debug token and register it in the Firebase console. Step by step: [docs/FIREBASE_SETUP.md](docs/FIREBASE_SETUP.md).

## Run the web version

1. In the Firebase project, add a **Web** app.
2. Copy `web/firebase-config.example.js` to `web/firebase-config.js` and fill in the values (git-ignored).
3. Serve the folder on localhost:
   ```bash
   python -m http.server 8080 --bind 127.0.0.1 --directory web
   ```
4. Open http://localhost:8080 in Chrome, register the App Check debug token from the DevTools console, and follow [docs/WEB_SETUP.md](docs/WEB_SETUP.md) to download Chrome's on-device model.

## Knowledge files

All engineering values live in `app/src/main/assets`, never in Kotlin:

- `print_rules.json`: mesh, halftone, white base, garment mask, ink model, recommendation tolerances, resolution, ink deposit, dye calculator formulas.
- `print_techniques.json`: 29 print techniques with artwork fit, fibre suitability, parameters, risks and sources.
- `nira_demo_shades.csv`: 150 generic demo shades made for NIRA.
- `prompts/`: AI prompt text.

Every value carries a confidence label (sourced, interpolated, article only, conflict, assumption). Techniques not yet checked on a production floor are shown as **"Not floor-verified"**. These are starting values from public trade sources; confirm with the ink maker's data sheet and a strike-off before production.

## Tests

`./gradlew :app:testDebugUnitTest` runs the JVM unit tests: colour maths (CIEDE2000 reference pairs), print mask and palette, separation strategies, a regression test from a real dark-garment device test, ink quantity against hand-calculated values, rules-file loading, adjustment operations, privacy rules, prompt and answer parsing, print method advice and language-file completeness.

Unit tests run with `isReturnDefaultValues = false`, so a test that touches an Android class (Bitmap, Color) fails instead of passing on stub values. Checked on 25 Nov 2026: all 286 tests pass this way. Bitmap output (film images, separation sheet) is checked on the phone, not in JVM tests.

## Work done for the hackathon

- The NIRA Android app in this repository was written during the contest period. The Git history starts on 15 November 2026.
- An earlier, separate Python project by the same developer (not related code) was not imported or reused.
- Textile and print knowledge comes from the developer's industry experience and from public trade sources, cited in the knowledge files.
- Development used AI coding assistants. All AI features in the app run through Firebase AI Logic.

## Licence

[Apache License 2.0](LICENSE).

## Credits

MOS Technologies. Product and textile expertise: Ravishankar.
