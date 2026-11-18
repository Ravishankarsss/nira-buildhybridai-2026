# NIRA — Project Status

Last updated: 15 November 2026, 20:15 IST (by Claude Code).
Rule for this file: only write what was actually run. Numbers below come from real runs on the laptop.

## 000000. Update 17 Nov 2026 — web version: cloud call works in Chrome

- Firebase Web app **NIRA Web** registered in project nira-90d39 (appId ends `2a65aa`, web).
- `web/firebase-config.js` created and git-ignored.
- Web App Check debug token from Ravi's Chrome registered under NIRA Web.
- **Cloud call from the web page works in Ravi's Chrome 157:** `ONLY_IN_CLOUD` → inference source `IN_CLOUD`, `gemini-3.5-flash-lite`, 9,684 ms, correct answer. Screenshot: `docs/evidence/2026-11-17_web_chrome_only_in_cloud.png`.
- On-device in Chrome: Prompt API availability `downloading` (screenshot `docs/evidence/2026-11-17_web_chrome_prompt_api_downloading.png`). **Not tested yet**; waiting for the model download to finish.
- Fixed: the empty "Error" row was shown on success because `.row { display: flex }` overrode the `hidden` attribute.
- On-device in Chrome, 17 Nov evening:
  - Normal Chrome 157: model download stuck at 0%, `nano_v3_gpu_component` error "Mismatched version". All Prompt API flags at Default.
  - Chrome Beta 159 installed. "Enabled by user setting" was false; after turning on on-device AI in Chrome settings, all criteria true and availability `downloadable`. Download stayed at 0% after "Prepare on-device model".
  - Likely cause (not confirmed): laptop on the phone's hotspot, a metered connection; Chrome downloads the model only on unmetered networks.
  - Next: retry on office Wi-Fi on 18 Nov. Backup: borrow a phone with Gemini Nano support.

## 000000c. Update 18 Nov 2026 — first on-device answer (Gemini Nano in Chrome)

**On-device inference through Firebase AI Logic works in Chrome.** Chrome 159 Beta, Prompt API "available", on-device model downloaded. Mode ONLY_ON_DEVICE, prompt "In one short sentence, what is a screen printing underbase?": the SDK reported **ON_DEVICE**, 3,440 ms, session counters 1 on device / 0 in cloud. Evidence: `docs/evidence/2026-11-18_web_chrome_only_on_device.png`. With the 17 Nov IN_CLOUD answer, both sources are now shown with the real `inferenceSource`.

The on-device answer was wrong on the facts (an underbase is a white or light ink layer printed first on dark garments so colours stay bright, not an adhesion coating). This supports the design: engineering values come from print_rules.json and the engine; the AI explains them.

Still to capture: PREFER_ON_DEVICE in Chrome, and ONLY_ON_DEVICE with Wi-Fi off.

## 000000b. Update 18 Nov 2026 — photo or artwork, fold shadows, one ink's shades

Rules in print_rules.json v3: shadow_vs_ink and photo_vs_artwork (proposed starting values, Ravi to confirm). All numbers are read from the file.

- Light garments now use the same fabric rules as other garments (the hard-coded L* > 80 / chroma < 9 case is gone; "light" comes from white_base limits).
- Input mode: "Photograph a garment" = photo, "Analyse artwork" = artwork. Camera information, flat-area noise and background lighting range are scored; the Colour screen asks "This looks like a photo. Treat it as a photo?" when two or more disagree. Verified on the vivo with the white T-shirt photo (saved before 18 Nov, so treated as artwork): the question appeared.
- Fold shadows (photos of light garments): soft-edged regions that leave the print box are removed; uncertain ones stay, labelled "Possible fold shadow - tap to confirm", with "N (+M if the flagged colours are ink)". On a neutral garment a clearly coloured region is ink (a shadow of white fabric is neutral).
- Photos: one ink's light and dark shades merge (same hue within 6 deg, touching, change across the border wider than an ink edge). Artwork is never merged automatically.

White T-shirt photo on the vivo, photo mode: "3 print colours measured (+2 if the flagged colours are ink)" (sky, ink blue, one blur-band blue; two greys flagged; resolution 51 px/cm at 30 cm, below 59, so a retake is suggested). Answering "fold shadow" removed 14 regions, but the colours re-grouped. Not yet the expected 2 colours; see UNFINISHED L13 for what is open.

Build PASSES; unit tests 140 run, 140 pass.

## 000000a. Update 18 Nov 2026 — Part A speed

Laptop JVM, photo-like 1024 px test image (`PrintReportRegressionTest.testPhotoLike1024Benchmark_ReportsRealTimes`):

| Step | Before | After |
| :--- | ---: | ---: |
| Garment profile | 621 ms | 398 ms |
| Lighting flattening | 576 ms | 277 ms |
| Knit smoothing | 1,164 ms | 282 ms |
| K-means K=16 | 972 ms | 157 ms |
| Assign pixels | 529 ms | 152 ms |
| Error curve (6 clusterings) | 3,452 ms | 427 ms |
| **Part A total** | **7,840 ms** | **2,090–2,250 ms** |

How: independent work runs on all cores, Lab computed once per distinct colour, assignment once per distinct colour; sums kept in the original order. A fingerprint of Part A output on three test images (colours, pixel counts, coverage, error curve, print box, mask) was byte-identical before and after.

Phone (vivo V60e), measured 18 Nov, time from tapping a saved analysis until the measured print colours appear (includes loading the image and the colour separation, so it is more than Part A alone; screen checked every 10 s, so about ±10 s):

| Analysis | Before | After |
| :--- | ---: | ---: |
| 1280 × 714 px artwork (3 print colours measured) | about 30 s | about 21 s |
| 6144 × 8192 px photo (12 print colours measured) | not measured | about 47 s |

The phone is still far slower than the laptop.

Stage times on the vivo V60e (8 cores), measured 18 Nov with temporary timers that were removed afterwards:

| Stage | 1280 × 714 artwork | 6144 × 8192 photo (worked at 1536 × 2048) |
| :--- | ---: | ---: |
| Decode the saved image | 22 ms | 402 ms |
| Older colour separation (runs **before** Part A starts) | 11,548 ms | 15,657 ms |
| Part A: garment profile | 560 ms | 2,368 ms |
| Part A: lighting flatten | 1,135 ms | 0 ms (white garment) |
| Part A: knit smoothing | 1,637 ms | 0 ms (white garment) |
| Part A: raw mask + specks + box | 757 ms | 597 ms |
| Part A: collect print pixels | 329 ms | 1,451 ms |
| Part A: k-means K=16 | 2,079 ms | 2,499 ms |
| Part A: assign + merge + prune + palette | 768 ms | 1,889 ms |
| Part A: error curve (6 clusterings) | 6,272 ms | 7,451 ms |
| Part A: mask preview picture | 168 ms | 230 ms |
| **Part A total** | **13,713 ms** | **16,601 ms** |

What this shows: about half the wait is the older colour separation, which runs first and makes Part A wait. Inside Part A, the error curve is the largest stage.

Change made 18 Nov: when a saved analysis is opened, Part A now starts as soon as the image is loaded, and the older colour separation runs after Part A (so the two do not compete for the cores). Results are unchanged; the Separation screen still fills in. Open-to-colours time on the vivo (screen checked every 5 s, so about ±5 s):

| Analysis | Before this change | After |
| :--- | ---: | ---: |
| 1280 × 714 artwork | about 21 s | about 21 s |
| 6144 × 8192 photo | about 47 s | about 20 s |

The small artwork did not get faster in this test, though Part A alone took 13.7 s. The next speed step is the error curve inside Part A.

Error curve speed-up, 18 Nov (commit 5e5255a): the K=16 point reuses the Step 7 clustering (same points, K and seed), and k-means and the error measure compute CIEDE2000 once per distinct colour. A fingerprint of Part A on three test images is identical before and after, and the phone shows the same colours (3 and 12) as before. Measured on the vivo with temporary timers (removed afterwards):

| Stage | 1280 × 714 artwork, before → after | 6144 × 8192 photo, before → after |
| :--- | ---: | ---: |
| Error curve | 6,272 → 889 ms | 7,451 → 2,041 ms |
| K-means K=16 | 2,079 → 506 ms | 2,499 → 961 ms |
| **Part A total** | **13.7 s → 7.4 s** | **16.6 s → 9.9 s** |

The largest remaining Part A stages are knit smoothing and lighting flatten on dark garments (1.8 s and 1.2 s) and the garment profile on the photo (2.4 s).

Bug found and fixed in the same test: opening a saved analysis kept the previous artwork's Print Report on screen (its colours and count) until the new Part A finished. Opening an analysis now clears Parts A–D, and a newer Part A run cancels the older one.

## 00000. Update 17 Nov 2026, 08:20 IST — first successful cloud AI call

**Cloud inference through Firebase AI Logic now works on the vivo V60e.**

Build PASSES; unit tests 102 run, 102 pass.

What fixed it:
1. The App Check debug token had never been saved in the Firebase console (the NIRA app showed "No tokens registered"). Ravi added it on 17 Nov.
2. The cloud model `gemini-3.8-flash` timed out twice (no answer after 180 s). Changed to `gemini-3.5-flash-lite`, also stable on the Firebase models page.

Real results on the vivo V60e, Test AI card, debug build, prompt "In one short sentence, what is a screen printing underbase?":

| Requested mode | Model | Inference source (from the SDK) | Duration | Result |
| :--- | :--- | :--- | ---: | :--- |
| ONLY_IN_CLOUD | gemini-3.8-flash | ERROR | 181,415 ms / 179,106 ms | Timed out (twice) |
| ONLY_IN_CLOUD | gemini-4-hydrogen | IN_CLOUD | 65,156 ms | Correct answer, too slow |
| ONLY_IN_CLOUD | gemini-3.5-flash-lite | IN_CLOUD | 3,883 ms | Correct answer |
| PREFER_ON_DEVICE | gemini-3.5-flash-lite | IN_CLOUD | 2,380 ms; 10,477 ms | Correct answer; fell back to cloud |
| PREFER_IN_CLOUD | gemini-3.5-flash-lite | IN_CLOUD | 8,060 ms | Correct answer |
| ONLY_ON_DEVICE | — | UNAVAILABLE | 7 ms | "On-device model is not available" |

Proven now: Firebase AI Logic called at runtime, App Check debug token accepted, cloud inference, and hybrid fallback (`PREFER_ON_DEVICE` falls back to the cloud and the app shows the real source `IN_CLOUD`).

Still not proven: **on-device inference on any device.** The vivo V60e has no on-device model (SDK status `unavailable`). Needs the Chrome web version or a supported Android phone.

Not known: why `gemini-3.8-flash` timed out. The endpoint answered the laptop in 2 s and the phone could reach it, so it is not a network block.

## 0000. Update 17 Nov 2026, 07:55 IST — AI calls tested on the phone

Build PASSES; unit tests 102 run, 102 pass (state: commit `6adb251` plus an uncommitted dashboard change in `MainActivity.kt` that Claude Code did not write).

Real results on the vivo V60e, run through the Test AI card:
- **Cloud (`ONLY_IN_CLOUD`): fails** with "Firebase App Check token is invalid." (403, App attestation failed). Cause: the debug token shown in the app is not registered in the Firebase console. Not a code error. Ravi must register it.
- **On-device (`ONLY_ON_DEVICE`): UNAVAILABLE.** SDK status `unavailable`; the phone has no AICore service. On-device AI cannot be shown on this phone.
- Cloud inference is therefore still **not proven**, and on-device inference is **not proven on any device**.

Kotlin is now 2.4.0 and `firebase-ai-ondevice` is active (commit `6adb251`, not by Claude Code). `kotlin-android` plugin and `kotlinOptions` were removed in that commit and Guava was added.

## 000. Update 16 Nov 2026, 20:50 IST — slow app start ("Skipped 191 frames")

Measured on the vivo V60e (V2513, Android 16) with `adb shell am start -S -W` and temporary timing marks (removed again).

Finding: nothing heavy of ours runs on the main thread at launch. Rules loading, history loading and shade loading already run on background threads; OpenCV is only loaded when Settings opens, also in the background.

| Launch step (debug build, fresh install) | Time |
| :--- | ---: |
| Process start until our code runs | about 620–680 ms |
| `NiraApplication.onCreate` (Firebase + App Check) | 11 ms |
| Language set-up and `MainActivity.onCreate` | about 35 ms |
| Building and drawing the first Compose screen | about 2,300 ms |

Cause: a debug build installed from Android Studio runs un-compiled. Pre-compiling the same build on the phone (`adb shell cmd package compile -m speed -f com.mos.nira`) changed the cold start as follows:

| | Cold start | Skipped frames |
| :--- | ---: | ---: |
| Fresh debug install (before) | 2,865–3,126 ms | 115–137 |
| Same build, pre-compiled | 1,160–1,270 ms | 0–45 |

Code change made: the app root read the whole UI state, so the dashboard was drawn three times at launch. It now reads only the saved history: two draws. In a back-to-back test this did **not** change the launch time measurably (about 1,180 ms both ways, pre-compiled).

Not explained: after the first pre-compile, later installs on this phone stayed fast (954–1,204 ms) without pre-compiling again, so a clean "after" figure for an un-compiled install could not be taken.

For the demo video: run the pre-compile command once after installing, or use a release build. A release build was not tested.

## 00. Update 16 Nov 2026, 07:05 IST — Firebase Stage B

Real results: build PASSES; unit tests 102 run, **102 pass, 0 fail**. Debug APK 152.4 MB. Not run on a phone yet: no AI request has been sent, so cloud inference is **not yet proven**.

Done:
- Google services plugin 4.6.0 and Firebase BoM 35.2.0 (current on the Firebase docs on 16 Nov 2026), through `gradle/libs.versions.toml`.
- Firebase AI Logic (`firebase-ai`) and App Check debug provider. App Check starts in `NiraApplication`, registered in the manifest, before any AI request. Debug provider is installed only in debug builds.
- `core/ai/HybridInferenceRepository`: one function, text prompt in; text, real `inferenceSource` and duration out. Cloud model `gemini-3.8-flash`, verified on the Firebase models page.
- Temporary "Test AI" entry in Settings, debug builds only.
- INTERNET permission added.
- `docs/FIREBASE_SETUP.md` written.
- Colour engine, Print Report and view model not touched. Kotlin, AGP and Gradle not changed.

Open:
- **On-device library is switched off.** `firebase-ai-ondevice` 16.0.0-beta08 needs Kotlin 2.4.0; the project is on 2.2.10 and the build fails with it. Requests go through the hybrid API and run in the cloud. Decision needed from Ravi on the Kotlin upgrade.
- App Check debug token must be registered by Ravi (see `docs/FIREBASE_SETUP.md`).
- Release builds have no App Check provider (Play Integrity is a future task).
- The first three Gradle changes of this stage were started by Antigravity and finished here.

## 0. Update 15 Nov 2026, 20:40 IST — device-test fixes (Sections 1 and 2, Part D)

Real results: build PASSES; unit tests 101 run, **101 pass, 0 fail**. Sections 1 to 6 below were written before this update; where they disagree, this section is right.

Done in this update:
- APK is back to all CPU types (149.3 MB), by Ravi's decision. The phone-only filter was removed.
- Dark-garment regression test now PASSES with stricter checks: exactly 2 print colours, inked area within 2 points of 11.1%, tight print box, no screen in the garment colour, 2 screens recommended (3 with a separate white base), blue reported as halftone over white, CMYK not recommended.
- Palette: on a non-white garment, a colour that is a halftone of one ink over the garment, or a transition between two inks, is no longer a separate ink (`selectInksByHalftoneReduction`). Tolerances come from `recommendation` in the rules file.
- Garment mask now uses `garment_mask` from the rules file (border 0.08, threshold = 3 x spread, minimum 3.0, speck size 0.0005).
- App colour chips no longer fix the garment colour exactly; only "tap the fabric" does.
- White screens: white print colour acts as base by default (0 extra screens); separate base adds 1; highlight white only when white is not a print colour. CMYK on a dark garment keeps base + highlight.
- White-base rule uses lightness 70 and chroma 15 from the rules file, and the measured garment colour, not the preset.
- Opaque ink model now includes the garment term; CMYK always uses the transparent model over white.
- Recommendation uses both limits (mean 3.0 and 95% 8.0) and offers CMYK only with at least `cmyk_min_hue_families` hue families.
- Ink quantity bug fixed: coverage "over the print box" was divided by the whole photo. Ink is now laid only inside the print box.
- Choke bug fixed: choke pixels used Part A's image scale. Formula: pixels = choke_mm / 10 x (print box width in px / print width in cm).
- Low-resolution limit now comes from the rules file (59 px/cm).
- Developer message "Parts A, B, C & D completed and verified..." removed from the report.
- New Part D tests: white screens included on a dark garment; ink only on print pixels on the regression image.

Measured:
- Quality figures compare the proof with colours rounded to 6 bits, not the exact pixels. Rounding alone adds mean dE00 0.293 (regression logo) and 0.364 (photo-like image), under the 0.5 limit, so it was left as is.
- Photo-like 1024 px image, laptop JVM (NOT a phone): Part A 9,538 ms, Part B 310 ms, Part C 880 ms. Part A was 10,584 ms before the smoothing speed-up. **Part A is still too slow**; the cost is colour clustering, and the app runs Part A at up to 2048 px, which is about 4 times more pixels.

Assumptions added in code that Ravi should confirm (not in the rules file): neutral chroma limit 12, hue family width 30 degrees.

NOT done from the prompt:
- Item 1: the earlier cause of the report not opening on the device is unknown to this session; nothing was checked on device or emulator.
- Item 3: re-running the analysis on the cropped print box at best resolution; dragging the box edges was not checked.
- Item 7: edge refinement for flat spot layers.
- Item 10: Part A speed, and any benchmark on the device.
- Item 14: confidence labels on every Part D factor were not checked in the UI.
- Highlight white limits (80 and 10) still have no value in the rules file.

## 1. Current state

| Item | Result |
| :--- | :--- |
| Build `./gradlew :app:assembleDebug` | PASSES |
| Unit tests `./gradlew :app:testDebugUnitTest` | 95 run, 94 pass, **1 fails** |
| Failing test | `PrintReportRegressionTest.testRealGarmentPhotoRegression_DarkTexturedFabricWithWhiteAndBlueLogo` — palette has 8 colours, expected 2 |
| Device test (vivo V60e) of today's work | **NOT DONE** |
| Instrumented (on-device) tests | Not run. No device was connected to this session. |
| Firebase in the app | **NOT STARTED** (nothing in Gradle) |
| Pushed to GitHub | No. All commits below are local only. |

Earlier versions of this file said "44 passed, 0 failed" and "Part B under 250 ms". Both were wrong for the code as found on 15 Nov: the build did not compile, and the device test measured 16,750 ms for strategy evaluation.

## 2. Commits made on 15 November (local)

| Commit | What |
| :--- | :--- |
| `2147240` | All work since Phase 1 (shade library, rules files, Print Report engine Parts A–D), plus the build fix and garment-mask fix |
| `71a61af` | Rules loader reads version 2 keys |
| `727ee42` | OpenCV and CameraX behind the `ImageOps` interface |
| `8c5bdd6` | Adjustment engine and channel ZIP export |
| `de0dc0e` | Dashboard update, garment camera, Settings |
| (next) | APK limited to phone CPU types |

## 3. What was fixed and built today

### 3.1 Build fix
`SeparationStrategyEngine.kt` had five leftover lines after `quantizePixels6Bit` from a half-finished edit. Removed.

### 3.2 Garment mask (dark-garment bug, partly fixed)
- Only "tap the fabric" (`userGarmentHex`) is trusted as the exact fabric colour. A preset is a first guess; the fabric is measured from the image border.
- If the border's colour cast is far from the chosen garment colour, the artwork fills the image and the preset is kept (same limit, 12, as the old white-garment check).
- The lighting correction no longer turns every pixel black on a pure black garment (it multiplied by garment lightness 0).
- Result in the regression test: dark fabric detected, tight print box, inked area inside 9–15%. Still failing: palette 8 colours instead of 2.

**Still open for the phone:** the garment colour chips call `updateGarmentColorHex`, which also sets `userGarmentHex` (`ArtworkViewModel.kt`, about line 574). So in the app a preset is still treated as an exact tapped colour and the border measurement is skipped. One-line change, but it must be tested on the real garment photo first.

### 3.3 Rules loader (`PrintRulesRepository`)
Reads version 2 sections. `print_rules.json` was not changed.

| Setting | Was (Kotlin) | Now (from file) | Source key |
| :--- | :--- | :--- | :--- |
| Garment L limit for white base | 45 | 70 | `white_base.garment_L_threshold` |
| Quality tolerance, mean | 4.5 | 3.0 | `recommendation.quality_tolerance_mean_de00` |
| Minimum resolution px/cm | 40 | 59 | `resolution.px_per_cm_min_for_fine_detail` |
| Choke mm | 0.5 | 0.5 | `underbase_rules.choke_mm_default` |
| Minimum line width by mesh | — | new | `detail_rules.min_line_width_mm_by_mesh` |

Kotlin fallback defaults are kept only for "file cannot be read"; a test fails if they differ from the file.

Not wired yet (values still hard-coded in the engine):
- Low-resolution warning uses 40 px/cm in `PrintAreaAndPaletteEngine` (`MIN_RESOLUTION_PX_PER_CM`).
- `garment_mask` values (border fraction 0.08, spread multiplier 3.0, speck size) — engine uses its own 0.05 and spread + 2.
- `white_base.garment_chroma_threshold` (15) and `recommendation.quality_tolerance_p95_de00` (8.0) — no engine code uses them.
- Highlight white limits (L 80, chroma 10) have **no value in the rules file**. Ravi must add them; they will not be invented.
- `calculatePartD` falls back to 36 / 30 / 48 g/m2 when a mesh has no deposit rule. It should say "not available".

### 3.4 OpenCV and CameraX
- `core/image/ImageOps.kt`: one interface for perspective warp, edge-preserving smoothing, erode, dilate, open, close, connected components, distance transform.
- `OpenCvImageOps.kt`: the only file that imports OpenCV. Falls back to the Kotlin version if the native library cannot load. `ImageOps.name` says which is in use (shown in Settings).
- `KotlinImageOps`: pure Kotlin version, used by JVM unit tests.
- The Kotlin colour engine was not changed, and does not call `ImageOps`.
- Libraries: `org.opencv:opencv:4.10.0`, `androidx.camera:*:1.4.2`. No TensorFlow, no ML Kit, no AI library.

**Debug APK size**

| | Bytes | MB |
| :--- | ---: | ---: |
| Before OpenCV and CameraX | 11,390,902 | 11.4 |
| After | 149,074,736 | 149.1 |
| After dashboard and camera screen | 149,818,954 | 149.8 |
| After limiting to phone CPU types (current) | 51,167,843 | 51.2 |

OpenCV native library per CPU type: arm64-v8a 20.9 MB, armeabi-v7a 15.0 MB, x86 40.8 MB, x86_64 55.0 MB. Ravi decided on 15 Nov to build for phones only: `app/build.gradle.kts` has `abiFilters` for `arm64-v8a` and `armeabi-v7a`. The app will **not run on a PC emulator** (x86) until that block is removed.

Not verified: `OpenCvImageOps` has never run (no device). Only `KotlinImageOps` is tested.

### 3.5 Adjustment engine (for Ask NIRA)
Package `core/adjust`. Every operation is deterministic Kotlin. No AI, no image generation.

| Intent | Engine class | Tested |
| :--- | :--- | :--- |
| ADJUST_CHANNEL_STRENGTH | `AdjustChannelStrength` | yes |
| ADJUST_CHANNEL_CURVE | `AdjustChannelCurve` | yes |
| MERGE_COLOURS | `MergeColours` | yes |
| RECOLOUR_INK | `RecolourInk` | yes |
| REMOVE_BACKGROUND | `RemoveBackground` | yes |
| DESPECKLE (mm2) | `Despeckle` | yes |
| CHOKE_BASE (mm) | `ChokeBase` | yes, at two print widths |
| THICKEN_LINES | `ThickenLines` | yes |
| APPLY_COLOURWAY | `ApplyColourway` | yes |
| COMPARE_BEFORE_AFTER | `AdjustmentSession.compareBeforeAfter()` | yes |

- `AdjustmentSession`: preview → Accept or Cancel, one-level Undo. After each accepted change it recalculates proof, colour error (mean and 95%), screens and ink quantity, and returns plain lines such as "Screens: 3 -> 2".
- `AdjustmentResolver`: takes the intent and parameters the AI will supply. If an ink, amount or colour is missing or fits more than one ink, it returns one short question and changes nothing.
- `core/export/ChannelZipExporter`: one 8-bit greyscale PNG per ink (black = ink) at working resolution, plus `proof.png`, in one ZIP. Names like `02_Royal_Blue.png`.

Definitions chosen (Ravi to confirm):
- Curve regions are by ink coverage: highlights = light coverage, midtones = around 50%, shadows = heavy coverage.
- Choke is measured from the edge of the whole print. Less than one pixel is reported as "nothing changes".
- Thicken lines estimates width from the distance transform; a line may end slightly wider than the minimum, never narrower, and line ends grow by the same amount.
- A mesh with no line-width rule (for example 137 tpi) is reported as "not available" and left alone.

**Not done in this stage:**
- No screen uses the adjustment engine. Ask NIRA does not exist yet, so there is no preview / Accept / Cancel / Undo UI and no export button.
- The AI's choice of intent needs Firebase AI Logic, which is not installed.
- Colourways are kept in the session only; they are not saved to disk.
- Not run on a real separation from the app, only on synthetic images.

### 3.6 Dashboard
- Greeting follows the device clock: morning 05–11, afternoon 12–16, evening otherwise.
- Headline: "Print engineering from artwork, privately on your phone."
- Two primary actions: "Analyse artwork" (existing flow) and "Photograph a garment" (dialog: Take photo / Choose from gallery; both go into the same flow at the preview screen).
- Workspace cards shown: Print Report (opens the latest saved analysis, or the picker if there is none) and Shade Library. Technique Library and Ask NIRA cards are hidden until those screens exist.
- Recent analyses: latest 3 from saved history, with thumbnail, date and print-colour count. Tap opens the Print Report. "No projects yet" shows only when history is empty.
- Coming soon row: Recipe Lab and CAD Studio, muted, "Soon" tag, not clickable.
- Bottom bar: Home, Artwork, Settings. Lab and Profile removed. Ask NIRA appears automatically when its route is added to `RootDestinations.BUILT`.
- AI and privacy card: left out, because Firebase AI Logic is not integrated.
- New screens: `GarmentCameraScreen` (CameraX, photo saved in app-private storage) and `SettingsScreen` (shows only real values: rules file version and status, geometry library in use, number of saved analyses, app version).
- Dashboard, camera and Settings text is in `strings.xml`. The rest of the app still has hard-coded text.
- The artwork view model is now shared by the dashboard and the artwork flow. Back from the first screen of the flow returns to the dashboard.

**Not verified on a phone:** the dashboard layout, the camera screen (permission, preview, capture), opening a saved analysis straight into the Print Report, and the Settings screen. These compile; none has been seen running.

Settings includes the in-app Language switcher (English, Tamil, Hindi, Spanish, French, German, Vietnamese, Bengali, Chinese Simplified). Confidential mode and AI status card remain future items.

### 3.7 Language Support & Internationalization
- **Tamil (`values-ta/strings.xml`)**: Complete implementation as specified in `docs/MASTER_CONTEXT.md`. Technical terms (e.g. *Print Report*, *Shade Library*, *Technique Library*, *Artwork*, *CAD Studio*, *Recipe Lab*), units (*tpi*, *cm²*), and numbers remain in English inside Tamil text.
- **International Textile & Garment Hub Languages**:
  - Hindi (`values-hi/strings.xml`) — Major Indian manufacturing hubs.
  - Spanish (`values-es/strings.xml`) — Sourcing & retail markets.
  - French (`values-fr/strings.xml`) — Global fashion & luxury markets.
  - German (`values-de/strings.xml`) — EU technical textile markets.
  - Vietnamese (`values-vi/strings.xml`) — Key garment export hub.
  - Bengali (`values-bn/strings.xml`) — Major export manufacturing hub.
  - Chinese Simplified (`values-zh/strings.xml`) — Global textile supply chain.
- **Locale Engine**: `LocaleHelper` handles per-app language configuration (Android 13+ `LocaleManager` with runtime configuration fallback), with persistent preference storage and reactive UI recomposition.
- Integrated language switcher dialog inside [SettingsScreen.kt](file:///C:/Users/shank/AndroidStudioProjects/NIRA/app/src/main/java/com/mos/nira/features/settings/SettingsScreen.kt).

## 4. Test list (101 tests)

| Test class | Tests | Notes |
| :--- | ---: | :--- |
| ColorConverterTest | 8 | real |
| ShadeMatchingTest | 4 | real |
| PrintAreaAndPaletteEngineTest | 12 | real |
| SeparationStrategyEngineTest | 8 | real |
| PrintReportRegressionTest | 3 | real |
| PrintRulesRepositoryTest | 7 | loads the real `print_rules.json` |
| KotlinImageOpsTest | 11 | real |
| AdjustmentEngineTest | 17 | real |
| ChannelZipExporterTest | 3 | real |
| DashboardModelTest | 10 | real |
| LocaleHelperTest | 1 | verified 9 languages (Tamil + international) |
| ArtworkModelsTest | 3 | data classes only |
| ArtworkRepositoryTest | 1 | data class only |
| ColorExtractorTest | 4 | color extraction verification |
| ColorSeparatorTest | 3 | color separation verification |
| ExampleUnitTest | 1 | template (2 + 2) |

All 101 tests pass.

## 5. Known issues still open

1. Dark-garment regression: palette 8 colours instead of 2. Needs "one ink per colour family" and the gradient rule from `recommendation`.
2. Garment chips in the app bypass border measurement (3.2).
3. Speed: 16,750 ms strategy evaluation on the vivo V60e for a 1024 px image. Not investigated.
4. Developer messages visible in the Print Report UI ("Part C screens and proof completed...", "Parts A, B & C"). Not fixed.
5. Hard-coded UI text outside the dashboard; needed for Tamil.
6. Gradle wrapper is 9.9.0 (was 9.6.0 at the Phase 1 commit). Changed by someone before this session; the build works.
7. APK is ARM-only (51 MB); no emulator support (3.4).
8. Rules values still hard-coded in the engine (3.3).

## 6. Next steps (from CLAUDE.md priority order)

1. **Firebase Stage B** — Google services plugin, Firebase BoM, Firebase AI Logic, App Check debug provider. Due 18 Nov. Not started.
2. First cloud call and first hybrid call with the real `inferenceSource`.
3. Dark-garment engine: palette grouping, chips fix, white-base rule with chroma, then the regression test should pass.
4. Device test of everything in section 3.
5. Ask NIRA screen that uses the adjustment engine; Technique Library.

Feature freeze: 24 November 2026. Deadline: 1 December 2026, 3:30 am India time.
