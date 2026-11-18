# NIRA — What is not finished

First written 16 November 2026; updated 17 November 2026. Checked against `docs/MASTER_CONTEXT.md` (version 2) and the code at commit `9c7bb37`.
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
| A3 | Real on-device inference (Gemini Nano) | **Done 18 Nov (web)** | Chrome 159 Beta, Prompt API available, ONLY_ON_DEVICE: the SDK reported ON_DEVICE, 3,440 ms, a real answer (evidence: docs/evidence/2026-11-18_web_chrome_only_on_device.png) | The vivo V60e still has no on-device model; Android stays cloud with the honest status |
| A4 | Actual hybrid routing | **Done 18 Nov (web)** | Chrome 159 Beta: PREFER_ON_DEVICE gave ON_DEVICE (17,439 ms, first load); ONLY_ON_DEVICE with no internet gave ON_DEVICE (3,428 ms); ONLY_IN_CLOUD gave IN_CLOUD on 17 Nov. Evidence in docs/evidence (bookmarks bar cropped off) | On the vivo PREFER_ON_DEVICE falls back to IN_CLOUD (no on-device model on that phone) |
| A5 | App Check | Partial | Debug provider in debug builds; vivo token registered and accepted | Release builds have no provider (Play Integrity not set up) |
| A6 | Public repository with all source | Ready to publish, waiting for Ravi | Secret scan done 17 Nov (clean); phone serial removed from Git | Ravi approves, then push and make public |
| A7 | Open-source licence | **Done** 17 Nov | `LICENSE` (Apache 2.0) | — |
| A8 | README and instructions | **Done** 17 Nov | `README.md` | Update screenshots and status before submission |
| A9 | Demo video, 3 minutes or less, public on YouTube | **Missing** | — | Record after the hybrid flow works |
| A10 | Devpost text and submission | **Missing** | — | Write and submit |
| A11 | Working project for judges, free of charge | Partial | Debug APK builds | Decide how judges get it (APK and/or web). Not tested on any phone other than the vivo |
| A12 | Disclosure of work done before and during the contest | Partial | Section in `README.md` | Copy into the Devpost text |

## B. AI features (Layer B and C in the master context)

None of these exist as product features. Only the test button calls AI.

| # | Feature | Status |
| :-- | :--- | :--- |
| B1 | Classify artwork as FLAT or TONAL, describe motifs | Built 17 Nov (AI read card, Colour screen). Not run on the phone yet |
| B2 | Name likely print technique from a garment photo, with evidence | Missing |
| B3 | Flag print risks (thin lines, small text, gradients) | Built 17 Nov (same card). Not run on the phone yet |
| B4 | Explain the engine's numbers in plain language | Done 18 Nov: "Why this recommendation" in the Print Report (see L18) |
| B5 | Answer one question about the job (Ask NIRA) | Missing (no screen) |
| B6 | Read tech pack text into fields | Missing |
| B7 | Deep report from the cloud, text and numbers only | **Done** 17 Nov, tested on the vivo in English and Tamil (IN_CLOUD). Four sections, never the image, blocked by Confidential mode |
| B8 | Image input to the AI (prompt + one Bitmap) | Built 17 Nov (`HybridInferenceRepository.generate`) |
| B9 | Structured (JSON) output from the AI | Partial: JSON asked in the prompt and parsed with tests; SDK schema mode not used |
| B10 | Prompt files in `assets/prompts/` | Started: `artwork_read_v1.txt` |
| B11 | Source badge and duration on every AI answer | Built for the AI read card |
| B12 | Counters: answers on device, in cloud, images uploaded | Built 17 Nov (Settings, Android) |
| B13 | Confidential mode switch that blocks cloud calls | Built 17 Nov (Settings; tested rule `AiPrivacyPolicy`) |
| B14 | Confirm dialog before any image goes to the cloud | Built 17 Nov (AI read card) |
| B15 | Diagnostics screen (mode, source, model, device status, errors) | Missing |
| B16 | On-device availability check with the real SDK status call | Done: `FirebaseAIOnDevice.checkStatus()` shown in Settings; the vivo V60e reports "unavailable" (checked 18 Nov) |
| B17 | Remote Config for model name and prompt versions | Missing (model name is a constant) |
| B18 | Firestore history and Authentication | Missing (optional in the master context) |
| B19 | Unused AI data classes `ArtworkAnalysisInput`, `ArtworkAIAnalysis` | **Done** 17 Nov: removed |

## C. Screens

| # | Screen | Status |
| :-- | :--- | :--- |
| C1 | Dashboard | Built. Not verified on phone since the redesign |
| C2 | Artwork Studio (landing, preview, colours, separation, history) | Built |
| C3 | Shade Library | Built |
| C4 | Print Report | Built (see section D) |
| C5 | Settings | Done for the hackathon: language switch, Confidential mode, on-device status, AI counters, debug-only Test AI. No report-language setting |
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
| D2 | Speed of Part A | Improved 18 Nov: laptop, photo-like 1024 px image, 7.8 s → 2.1 s, results byte-identical (parallel cores, Lab once per colour). Phone, open-to-colours time: 1280 × 714 artwork about 30 s → about 21 s; 6144 × 8192 photo about 47 s. Phone stage times (PROJECT_STATUS, 18 Nov): the older colour separation takes 11.5–15.7 s and runs before Part A; Part A itself takes 13.7–16.6 s, with the error curve (6.3–7.5 s) the largest stage. Done 18 Nov: Part A now starts before the separation; photo about 47 s → 20 s, small artwork unchanged at about 21 s. Error curve made cheaper 18 Nov: Part A on the vivo 13.7 s → 7.4 s (artwork) and 16.6 s → 9.9 s (photo), results identical |
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
| J1 | `README.md`, `LICENSE` | **Done** 17 Nov |
| J2 | `docs/ARCHITECTURE.md`, `HACKATHON_REQUIREMENTS_MATRIX.md`, `TEST_EVIDENCE.md`, `CHANGELOG.md` | Missing (named in the master context) |
| J3 | `docs/evidence/` with screenshots and logs | Started (17 Nov: web cloud result, Chrome model downloading) |
| J4 | `docs/PROJECT_STATUS.md` | Sections 1 to 6 are out of date; the dated update sections at the top are current |
| J5 | Master context section 19 (current status) | Out of date |
| J6 | Unpushed commits | 3 (`733b78d`, `c5c9683`, `9c7bb37`). Everything up to `dabdcf7` is already on GitHub; that push was not done by Claude Code |
| J7 | Secret scan before going public | **Done** 17 Nov: no keys or private files in any commit. Repeat right before publishing |
| J8 | Gradle wrapper 9.9.0 (was 9.6.0) | Changed before 15 Nov by someone; not reviewed |
| J9 | Web version for Chrome | Done for the AI proof: cloud (17 Nov) and on-device (18 Nov) both shown with the SDK source. Note: Gemini Nano's answer about an underbase was wrong (it is a white or light ink layer printed first on dark garments, not an adhesion coating), so engineering facts must keep coming from print_rules.json |

## L. Found in the phone check of 17 Nov

| # | Item | Status |
| :-- | :--- | :--- |
| L1 | Reopened analyses had no image ("artwork image not loaded"): only the gallery link was saved | **Fixed and checked on the vivo** 17 Nov: a new analysis reopened with its image after a full restart. Analyses saved before the fix need the artwork picked again |
| L2 | "AI-READY" badge on the Artwork screen meant nothing | **Removed** 17 Nov |
| L3 | `print_techniques.json`, DTF: the risks list contains a source note ("Not in the user's articles; added from web research"), shown to users as a risk | Ravi to move it out of `risks` (rules file; not changed by Claude Code) |
| L4 | Artwork Studio landing, history and other old screens still English-only text | See G2 |
| L5 | Confidential mode with a reopened analysis showed "image not loaded" instead of the Confidential message (same cause as L1) | Retest after L1 with a new analysis |
| L6 | Report silently used CMYK + whites (6 screens) for screens and ink while 3 spot inks were recommended | **Fixed and checked on the vivo** 17 Nov: the recommended separation is used until the user picks one |
| L7 | Recommendation reason said "lowest colour error" although CMYK had a lower error (CMYK not offered by the hue-family rule) | **Fixed** 17 Nov: reason states the CMYK rule; covered by a test |
| L8 | AI read was told 8 colours (older colour step) while the Print Report measured 3 | **Fixed** 17 Nov: AI read uses the report's measured colours when available |
| L9 | Report note said white screens are "added to every option" | **Fixed** 17 Nov |
| L10 | Two colour engines give different colour counts (Colour screen 8, Print Report 3 for the same artwork) | **Fixed** 18 Nov: Colour screen leads with the measured print colours; the older list is labelled "All colour clusters in the image"; lists show the measured count. Not yet checked on the phone |
| L11 | Part A took about 30 s on the vivo for a reopened analysis | Now about 21 s; see D2 |
| L12 | Opening a saved analysis showed the previous artwork's colours until Part A finished | Fixed 18 Nov: report cleared on open, older Part A run cancelled |
| L13 | Photo of a white T-shirt (2-colour print: muted sky, ink blue) measured 12 print colours | Cause found 18 Nov: on light garments (L* > 80, chroma < 9, written in the code, not in print_rules.json) the engine counts only near-white pixels as fabric and skips lighting flattening, texture smoothing and the merge of one ink's shades, so fold shadows and light/dark versions of each blue become separate colours. Test on the real photo (laptop, not committed): treating light garments like others gives exactly 2 colours (#98BFD2, #0E1D48), but fold shadows still enter the print area (36% instead of about 25%) and one test (8 solid stripes on white) drops a real orange ink. Needs Ravi's decisions and print_rules.json values: (a) how a fold shadow is told from a grey ink, (b) how a photo is told from flat artwork, so ink shades merge only in photos. Needs a brand-free regression photo. 18 Nov: rules added to print_rules.json (shadow_vs_ink, photo_vs_artwork; proposed starting values, Ravi to confirm) with ShadowInkClassifier and InputKindClassifier and synthetic tests. Wired in 18 Nov (a36486c, fbe7d52, 5d60a4e): light garments use the normal fabric rules; input mode from the entry point with the 'Treat it as a photo?' question; fold shadows removed on light-garment photos, uncertain ones flagged 'Possible fold shadow - tap to confirm' with '+N if ink'; one ink's shades merged in photos. On the vivo, the white T-shirt photo in photo mode: sky + ink blue + one blur-band blue, two greys flagged (low resolution at 30 cm: 51 px/cm < 59). Answering 'fold shadow' removed 14 regions, but the palette re-groups after removal (sky split again, more greys flagged). Still open: (1) blur bands: rule edge_blend added 18 Nov (Ravi's section, with the plateau check, blur measured in the analysed image, undo switch, low-resolution flag); all synthetic tests pass (blurred stripes at full and half size give 2 colours, mid-tone keyline stays 3, 1 px anti-aliasing merges, 3 px stays, low resolution flags, undo restores exactly). On the real white T-shirt photo it merges nothing: the blur ramp is split into 5-6 one-pixel shades, so no single shade sits between two inks, and the phone's processing puts the ink-blue rim about 10 dE00 off the linear mixing line (limit 3.0). Needs Ravi's decision: judge a whole ramp of thin shades between two ink bodies, and/or allow for processed phone images; (2) DONE 18 Nov: 'All flagged areas are fold shadows' removes every uncertain region in one tap (an 'ink' answer still wins), and grey pixels are grouped apart from coloured ones so a fold that runs into the print is judged on its own; on the vivo this removed 70 fold regions and every grey, leaving only blues (ink blue 49%, sky 25%, four in-between blues 5-9% from blur bands and sky shades, see (1)); (3) answers are not saved with the analysis; (4) Part B/C still use their own mask without the shadow removal; (5) Ravi's four photos and expected-results file |
| L14 | A small real ink is dropped silently | 18 Nov, skull print on a sand T-shirt (laptop test, photo not in the repository): the magenta eye is about 1% of the print, below the 1.5% minimum coverage, so it vanished from the colours, one screen short in a quote. The 1.5% (MIN_COVERAGE_PERCENT) is hard-coded in PrintAreaAndPaletteEngine, not in print_rules.json. Needs Ravi's rule: the value in the file, and whether a small colour is flagged ("small colour - ink or noise?") instead of dropped | DONE 18 Nov: print_rules.json small_colours (Ravi). A colour under min_coverage_percent is kept and flagged "Small colour, about N%: confirm it is an ink" when one connected area is at least min_region_mm2 and it differs from every kept colour by at least merge_threshold_de00; scattered specks are removed and counted. Kept small colours are protected from the halftone reduction (its p95 tolerance hid colours under 5%). Original skull photo (laptop): the magenta eye (1.1%) is now flagged, with two more small colours to confirm (a light sand and a mid blue). |
| L15 | WhatsApp copies damage the test photos | 18 Nov: both new photos came through WhatsApp: 1280 x 720, camera data removed, compression smooths the fabric noise (the skull photo then looks like artwork: noise 0.78). The flat Hurley photo had no fold greys at all (capture fixed the folds), but at 21 px/cm the edge tests cannot run. Needs the original camera files by USB or Drive (original quality) |
| L16 | Buyer mock-up drawings are not artwork files | 18 Nov: a tech-pack drawing has a white page and a drawn garment; the engine takes the page as the fabric (pink sweatshirt counted as print), and tapping the fabric then counts the page. Mock-ups must be cropped to the print, or the flat artwork file used. Tech packs also list techniques (sublimation, embroidery, studs) that the colour engine cannot see |
| L17 | Table, floor or wall at the photo edge counted as print | 18 Nov: print_rules.json photo_background (proposed values, Ravi to confirm): in photos a non-fabric region touching at least 25% of one side, covering at least 2% of the photo and not reaching the centre half is left out, shown striped in the mask picture, with the switch "Include the photo edge area". Camera hints now say lay the garment flat with nothing else in the photo, turn off HDR / AI scene / beauty, and do not use WhatsApp. On Ravi's original skull photo the dark strip at the left edge is not left out: it joins the print region, so the centre guard keeps it (safe side). Keyline protection added to edge_blend the same day (Ravi: black keylines are important) |
| L18 | First AI job in the product | Done 18 Nov: the Print Report has "Why this recommendation". After the engine runs, the model gets the engine result and the matching rules (text only) and writes 2-3 sentences; routing follows AiPrivacyPolicy (on-device preferred, Confidential mode blocks the cloud). The real source and time are shown, and the engine data card stays under the text. If the answer has a number not in the engine data, the text is hidden and marked "Not shown: unverified". On the vivo: IN_CLOUD, 14.7 s, all numbers verified (docs/evidence/2026-11-18_android_report_explanation_in_cloud.png). On-device on Android needs a phone with a supported model |

## K. Other loose ends

- APK is 152 MB, mostly OpenCV, which no feature uses yet.
- Debug builds start slowly unless pre-compiled (see `PROJECT_STATUS.md`, section 000). A release build was never made or tested.
- No release signing set up.
- The "Test AI" card and the debug-token display are temporary and must be removed or moved to Diagnostics.
- Cloud model name `gemini-3.8-flash` must be re-checked on the Firebase models page before submission.
- Photos taken with the in-app camera are kept in app storage and are never cleaned up.

## Work order agreed on 17 Nov

Done on 17 Nov: cloud AI (phone and Chrome), AI read card, Confidential mode and counters, print method advice, README, licence, secret scan, reopen-image fix.

Next, in this order:
1. On-device proof in Chrome Beta (office Wi-Fi, Ravi) — A3, A4.
2. Deep cloud report from text and numbers only — B7, C11.
3. Join the journey: photo → colours → AI read → method advice → Print Report → deep report; print method advice inside the Print Report — D6, C11.
4. Print Report on the real garment photo on the phone, and Part A speed — D1, D2.
5. Text of the screens in the video into `strings.xml` (English and Tamil) — G2.
6. Evidence screenshots, then video and Devpost text (Ravi) — A9, A10, J3.
7. Ravi approves: push and make the repository public — A6.

## Suggested order (Ravi decides)

1. **Prove the cloud call** on the phone (A1, A2, A5): register the token, tap Run.
2. **Decide the on-device route** (A3, A4): Kotlin 2.4.0 upgrade on Android, or the small web version in Chrome, or both. This is the biggest risk to the submission.
3. **One real AI feature in the workflow** with the source badge (B1 or B3, B8, B9, B11), then the deep cloud report (B7) and Confidential mode (B13, B14).
4. **Phone test of the report** on the real garment photo, and fix Part A speed (D1, D2).
5. **Report layout** with the four sections (C11) and the technique check (C8, D6).
6. **Repository and submission**: README, LICENSE, secret scan, evidence, video, Devpost (section A and J).
7. Move the remaining text to `strings.xml` only for the screens shown in the video (G2).

Things that can be dropped without hurting the submission: grading check, tech pack reading, dye calculator, Firestore and Authentication, Remote Config, the adjustment engine screen, 7 of the 9 languages.
