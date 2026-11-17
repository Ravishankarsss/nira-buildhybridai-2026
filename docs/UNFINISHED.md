# NIRA — What is not finished

Checked on 16 November 2026, 20:55 IST, against `docs/MASTER_CONTEXT.md` (version 2) and the code at commit `9c7bb37`.
Build passes; 102 unit tests pass. Feature freeze: 24 November. Deadline: 1 December, 3:30 am India time.

Status words used here:
- **Missing** = no code exists.
- **Engine only** = the calculation exists and is tested, but no screen uses it.
- **Partial** = some of it works.
- **Not verified** = code exists but has never been seen working on a phone.

## A. Hackathon must-haves (the submission fails without these)

| # | Requirement | Status | What exists | What is left |
| :-- | :--- | :--- | :--- | :--- |
| A1 | Firebase AI Logic called at runtime | **Done** (17 Nov, vivo V60e) | `HybridInferenceRepository`, "Test AI" button | — |
| A2 | Cloud inference | **Done** (17 Nov, `IN_CLOUD`, 2–10 s) | Same as A1 | No product feature uses the cloud yet |
| A3 | Real on-device inference (Gemini Nano) | **Missing** | Kotlin 2.4.0 and `firebase-ai-ondevice` active; status check works | The vivo V60e has no on-device model. Needs the Chrome web version or a supported Android phone |
| A4 | Actual hybrid routing | Partial | `PREFER_ON_DEVICE` falls back to the cloud and shows `IN_CLOUD` (tested 17 Nov) | Needs A3 to show an `ON_DEVICE` answer |
| A5 | App Check | Partial | Debug provider in debug builds; vivo token registered and accepted | Release builds have no provider (Play Integrity not set up) |
| A6 | Public repository with all source | **Missing** | Private repository | Secret scan, then make public (Ravi's approval) |
| A7 | Open-source licence | **Missing** | — | Add `LICENSE` (Apache 2.0) |
| A8 | README and instructions | **Missing** | — | `README.md`: what it is, how to build, Firebase setup, disclosure of pre-existing work |
| A9 | Demo video, 3 minutes or less, public on YouTube | **Missing** | — | Record after the hybrid flow works |
| A10 | Devpost text and submission | **Missing** | — | Write and submit |
| A11 | Working project for judges, free of charge | Partial | Debug APK builds | Decide how judges get it (APK and/or web). Not tested on any phone other than the vivo |
| A12 | Disclosure of work done before and during the contest | **Missing** | — | Short honest statement in README and Devpost |

## B. AI features (Layer B and C in the master context)

None of these exist as product features. Only the test button calls AI.

| # | Feature | Status |
| :-- | :--- | :--- |
| B1 | Classify artwork as FLAT or TONAL, describe motifs | Built 17 Nov (AI read card, Colour screen). Not run on the phone yet |
| B2 | Name likely print technique from a garment photo, with evidence | Missing |
| B3 | Flag print risks (thin lines, small text, gradients) | Built 17 Nov (same card). Not run on the phone yet |
| B4 | Explain the engine's numbers in plain language | Missing |
| B5 | Answer one question about the job (Ask NIRA) | Missing (no screen) |
| B6 | Read tech pack text into fields | Missing |
| B7 | Deep report from the cloud, text and numbers only | Missing |
| B8 | Image input to the AI (prompt + one Bitmap) | Built 17 Nov (`HybridInferenceRepository.generate`) |
| B9 | Structured (JSON) output from the AI | Partial: JSON asked in the prompt and parsed with tests; SDK schema mode not used |
| B10 | Prompt files in `assets/prompts/` | Started: `artwork_read_v1.txt` |
| B11 | Source badge and duration on every AI answer | Built for the AI read card |
| B12 | Counters: answers on device, in cloud, images uploaded | Built 17 Nov (Settings, Android) |
| B13 | Confidential mode switch that blocks cloud calls | Built 17 Nov (Settings; tested rule `AiPrivacyPolicy`) |
| B14 | Confirm dialog before any image goes to the cloud | Built 17 Nov (AI read card) |
| B15 | Diagnostics screen (mode, source, model, device status, errors) | Missing |
| B16 | On-device availability check with the real SDK status call | Missing |
| B17 | Remote Config for model name and prompt versions | Missing (model name is a constant) |
| B18 | Firestore history and Authentication | Missing (optional in the master context) |
| B19 | Unused AI data classes `ArtworkAnalysisInput`, `ArtworkAIAnalysis` | Left over from Phase 1; nothing uses them |

## C. Screens

| # | Screen | Status |
| :-- | :--- | :--- |
| C1 | Dashboard | Built. Not verified on phone since the redesign |
| C2 | Artwork Studio (landing, preview, colours, separation, history) | Built |
| C3 | Shade Library | Built |
| C4 | Print Report | Built (see section D) |
| C5 | Settings | Partial: language switch works; no Confidential mode, no report language |
| C6 | Garment camera | Not verified on phone |
| C7 | Hybrid AI Analysis | Missing |
| C8 | Technique Library | Missing as a screen. `print_techniques.json` is now read by the print method advice |
| C9 | Ask NIRA | Missing |
| C10 | Execution Diagnostics | Missing |
| C11 | Report layout with the four sections A/B/C/D kept apart (measured data, AI interpretation, engineering suggestions, needs physical validation) | Missing: the report shows engine data only |

## D. Print Report engine

| # | Item | Status |
| :-- | :--- | :--- |
| D1 | Dark-garment fixes (2 colours, tight box, 2 screens) | Done in tests. **Not verified on the real garment photo on the phone** |
| D2 | Speed of Part A | **Too slow.** 9.5 s on a laptop for a photo-like 1024 px image; the app runs it at up to 2048 px (about 4 times more pixels). Not measured on the phone |
| D3 | Re-run analysis on the cropped print box at best resolution | Missing |
| D4 | Drag the print box edges | `setPrintBox` exists in the view model; no screen calls it |
| D5 | Edge refinement for flat spot layers | Missing |
| D6 | Technique suitability check from `print_techniques.json` | Built 17 Nov: print method advice card on the Colour screen (fibre, artwork type, garment tone; rules only, tested). Not yet run on the phone; not yet in the Print Report |
| D7 | Print fit and grading check from a size chart | Missing |
| D8 | Garment photo correction: four-corner straighten, white reference, scale | Missing. `perspectiveWarp` exists in `ImageOps` but nothing calls it |
| D9 | Labels "Estimated from garment photo" on photo-based values | Missing |
| D10 | Save and reopen the full report | Partial: history saves the colour palette only, not the report or its settings; the report is recalculated on reopen |
| D11 | Confidence label on every Part D factor | Partial: some badges are fixed text ("sourced", "assumption") instead of the label from the rules file |
| D12 | 27 of 29 entries in `print_techniques.json` are `verified_by_ravi: false`; "Not floor-verified" is not shown anywhere | Missing |
| D13 | Dye and paste calculator from user recipes | Missing |
| D14 | Pantone and buyer colour codes | Partial. Rule kept: no Pantone data in the app or repository (checked 17 Nov: none in code, assets or `nira_demo_shades.csv`). Users can import their own licensed library as CSV and add codes by hand (types TCX, TPG, TN, TSX, C, U, FACTORY, BUYER). Missing: reading the buyer's Pantone code from a tech pack (B6), and showing "buyer Pantone code" under "Needs physical validation" in the report (C11) |

## E. Built but not connected to any screen

| # | Item | Status |
| :-- | :--- | :--- |
| E1 | Adjustment engine (10 operations, preview, Accept, Cancel, Undo) | Engine only |
| E2 | Channel ZIP export (one PNG per ink plus proof) | Engine only |
| E3 | OpenCV (`OpenCvImageOps`) | Loaded only to show its name in Settings. No feature uses it, and it has never been run on a real image. It adds about 138 MB to the APK |
| E4 | CameraX garment camera | Screen exists; not verified on phone |
| E5 | Minimum line width rules loader | Loaded only by the adjustment engine (E1) |
| E6 | Saved colourways | Kept in memory only |

## F. Engineering values still written in Kotlin (rule: they belong in the rules files)

| # | Value | Where |
| :-- | :--- | :--- |
| F1 | Ink deposit fallback 36 / 30 / 48 g/m2 when a mesh has no rule (should say "not available") | `SeparationStrategyEngine.calculatePartD` |
| F2 | Mesh per screen type: 110, 156, 200, 230, 305 tpi | `SeparationStrategyEngine.generatePartC` (the rules file has `mesh_rules`, not read) |
| F3 | Highlight white limits: lightness 80, chroma 10, ramp 20 | No value in the rules file. Ravi must add them |
| F4 | White garment test: lightness above 80, chroma below 9 | `PrintAreaAndPaletteEngine` |
| F5 | "Same as garment" limit 3.8 | Both engines |
| F6 | Merge limit 3.2, noise limit 1.5% | `PrintAreaAndPaletteEngine` |
| F7 | Neutral chroma 12, hue family width 30 degrees | Added on 15 Nov as assumptions; Ravi to confirm |
| F8 | Smoothing limits 8.0 and 32.0, lighting grid 8 x 8 | `PrintAreaAndPaletteEngine` |
| F9 | Halftone rules, print order, cure temperatures, fabric rules, ink systems in `print_rules.json` | Not read or shown anywhere |

## G. Language

| # | Item | Status |
| :-- | :--- | :--- |
| G1 | Text in `strings.xml` | 67 strings, in 9 languages: dashboard, camera, Settings only |
| G2 | Text still hard-coded in Kotlin | About 148 places. Print Report 61, Colour Intelligence 19, Shade Library 18, Landing 11, Separation 10, Preview 10, History 8, Settings 6 |
| G3 | Result | Switching to Tamil changes the dashboard only; the main workflow stays English |
| G4 | Translations checked by a native reader | Not done (8 languages were machine-written) |
| G5 | Engine messages (warnings, recommendation reasons) | English only, built inside the engine |

## H. Tests

| # | Item | Status |
| :-- | :--- | :--- |
| H1 | `ColorExtractorTest` (4) and `ColorSeparatorTest` (3) | Do not call the classes they are named after |
| H2 | `ExampleUnitTest`, `ArtworkRepositoryTest`, `ArtworkModelsTest` | Template or data-class checks only |
| H3 | Bitmap output (film images, separation sheet, proof bitmap) | Never tested: bitmaps are not created in laptop tests |
| H4 | Instrumented (on-phone) tests | One trivial test. Never run |
| H5 | `OpenCvImageOps` | No test; needs a phone |
| H6 | AI tests in the master context (App Check, cloud, on-device, fallback, source reporting, quota and network errors) | None |
| H7 | Tests for invalid, huge and corrupt images | Missing |
| H8 | `isReturnDefaultValues = true` | Still set |

## I. Not verified on the phone

Dashboard redesign, garment camera, Settings, language switch inside the workflow, opening a saved analysis straight into the report, dark-garment fixes on the real photo, ink quantity after the print-box fix, Share and Save to Downloads of the separation sheet, Test AI with a registered token.

## J. Documents and repository

| # | Item | Status |
| :-- | :--- | :--- |
| J1 | `README.md`, `LICENSE` | Missing |
| J2 | `docs/ARCHITECTURE.md`, `HACKATHON_REQUIREMENTS_MATRIX.md`, `TEST_EVIDENCE.md`, `CHANGELOG.md` | Missing (named in the master context) |
| J3 | `docs/evidence/` with screenshots and logs | Started (17 Nov: web cloud result, Chrome model downloading) |
| J4 | `docs/PROJECT_STATUS.md` | Sections 1 to 6 are out of date; the dated update sections at the top are current |
| J5 | Master context section 19 (current status) | Out of date |
| J6 | Unpushed commits | 3 (`733b78d`, `c5c9683`, `9c7bb37`). Everything up to `dabdcf7` is already on GitHub; that push was not done by Claude Code |
| J7 | Secret scan before going public | Not done |
| J8 | Gradle wrapper 9.9.0 (was 9.6.0) | Changed before 15 Nov by someone; not reviewed |
| J9 | Web version for Chrome | Partial: `web/` page with four modes; cloud call works in Chrome (17 Nov). On-device not tested yet |

## K. Other loose ends

- APK is 152 MB, mostly OpenCV, which no feature uses yet.
- Debug builds start slowly unless pre-compiled (see `PROJECT_STATUS.md`, section 000). A release build was never made or tested.
- No release signing set up.
- The "Test AI" card and the debug-token display are temporary and must be removed or moved to Diagnostics.
- Cloud model name `gemini-3.8-flash` must be re-checked on the Firebase models page before submission.
- Photos taken with the in-app camera are kept in app storage and are never cleaned up.

## Suggested order (Ravi decides)

1. **Prove the cloud call** on the phone (A1, A2, A5): register the token, tap Run.
2. **Decide the on-device route** (A3, A4): Kotlin 2.4.0 upgrade on Android, or the small web version in Chrome, or both. This is the biggest risk to the submission.
3. **One real AI feature in the workflow** with the source badge (B1 or B3, B8, B9, B11), then the deep cloud report (B7) and Confidential mode (B13, B14).
4. **Phone test of the report** on the real garment photo, and fix Part A speed (D1, D2).
5. **Report layout** with the four sections (C11) and the technique check (C8, D6).
6. **Repository and submission**: README, LICENSE, secret scan, evidence, video, Devpost (section A and J).
7. Move the remaining text to `strings.xml` only for the screens shown in the video (G2).

Things that can be dropped without hurting the submission: grading check, tech pack reading, dye calculator, Firestore and Authentication, Remote Config, the adjustment engine screen, 7 of the 9 languages.
