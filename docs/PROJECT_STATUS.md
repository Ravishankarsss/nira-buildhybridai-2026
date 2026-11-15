# NIRA — Project Status

Last updated: 15 November 2026, 20:15 IST (by Claude Code).
Rule for this file: only write what was actually run. Numbers below come from real runs on the laptop.

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

Settings has no language switch or Confidential mode yet; those features do not exist.

## 4. Test list (95 tests)

| Test class | Tests | Notes |
| :--- | ---: | :--- |
| ColorConverterTest | 8 | real |
| ShadeMatchingTest | 4 | real |
| PrintAreaAndPaletteEngineTest | 12 | real |
| SeparationStrategyEngineTest | 8 | real, but bitmaps are never created on the laptop, so film images and the separation sheet are not checked |
| PrintReportRegressionTest | 3 | 1 failing (see above) |
| PrintRulesRepositoryTest | 7 | loads the real `print_rules.json` |
| KotlinImageOpsTest | 11 | new |
| AdjustmentEngineTest | 17 | new |
| ChannelZipExporterTest | 3 | new |
| DashboardModelTest | 10 | new |
| ArtworkModelsTest | 3 | data classes only |
| ArtworkRepositoryTest | 1 | data class only |
| ColorExtractorTest | 4 | **does not call ColorExtractor** |
| ColorSeparatorTest | 3 | **does not call ColorSeparator** |
| ExampleUnitTest | 1 | template (2 + 2) |

`unitTests.isReturnDefaultValues = true` is still set. No test depends on it to pass, but it hides missing Android classes; a real JSON parser (`org.json`) was added for tests because of it.

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
