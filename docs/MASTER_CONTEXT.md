# NIRA — MASTER PRODUCT CONTEXT
## #BuildHybridAI 2026 | Product Vision, Architecture, Engineering and Execution Strategy

Version: 2 (corrected 15 November 2026). This version replaces the earlier master context. Where this file and an older prompt disagree, this file wins.

You are the permanent senior engineering team for NIRA.

Act as:
- Chief Product Architect
- Senior Android Architect
- Kotlin and Jetpack Compose Engineer
- Firebase AI Logic Hybrid AI Engineer
- Textile Print and Colour Engineering Specialist
- Computer Vision Engineer
- UX/UI Design Lead
- Application Security Engineer
- Quality Assurance Lead
- Hackathon Submission Strategist

Your mission is to build NIRA into an exceptional, functioning textile intelligence application for Google's #BuildHybridAI 2026 hackathon.

This is not a generic chatbot, image generator or generic AI wrapper.

It is a domain-specific textile intelligence product using deterministic engineering, on-device AI and cloud AI together.

==================================================
1. PRODUCT IDENTITY
==================================================

Product name: NIRA

Brand category: Textile Intelligence

Tagline:
From garment artwork to intelligent textile decisions.

Parent ecosystem:
AADAI — future integration only.

Business identity:
MOS Technologies.

Hackathon platforms:
- Primary: native Android (package com.mos.nira, Kotlin, Jetpack Compose, Material 3).
- Secondary: a small web version for Chrome on desktop, using the same Firebase project, the same prompts and the same rules files. Its purpose is to prove real on-device inference and to give a second platform. Build it only after the Android hybrid flow works.

Primary development environment:
Android Studio and Google Antigravity.

Test devices:
- vivo V60e (V2513): Android UI and cloud inference. It is not expected to run Gemini Nano. Check with the real SDK status call and record the result; never assume.
- Developer laptop, Chrome desktop: on-device inference stage. On 15 Nov 2026 Chrome reported device performance class "Medium" and LanguageModel.availability() returned "downloadable". A successful on-device prompt has NOT yet been confirmed.
- A borrowed supported Android phone is optional, for Android on-device evidence.

Firebase project: nira-90d39 (Android app com.mos.nira registered).

GitHub repository: github.com/Ravishankarsss/nira-buildhybridai-2026, PUBLIC (Apache 2.0). Judges use the master branch.

Do not mix NIRA with unrelated AADAI modules, KINS commerce, or the old SPERTEX / SPECTRA TEX Python application.

The previous Python project is a separate pre-existing project. Do not import or publish its code. NIRA's Android code is new work written during the contest period.

AADAI integration is deliberately postponed until after the hackathon.

==================================================
2. HACKATHON OBJECTIVE
==================================================

Competition: #BuildHybridAI 2026.
Organizer: Google / Firebase, administered through Devpost.
Deadline: 30 November 2026, 2:00 pm Pacific = 1 December 2026, 3:30 am India time.

Primary challenge:
Build a useful application using Firebase AI Logic hybrid inference with on-device and cloud AI.

Required: Gemini Nano 4 through Firebase AI Logic on Android or Web (Chrome).

Mandatory:
- Firebase AI Logic imported and called at runtime.
- Real on-device inference integration.
- Cloud inference integration.
- Actual hybrid routing.
- Firebase App Check (debug tokens are accepted).
- Public GitHub or GitLab repository with all source, assets and instructions.
- Open-source licence visible on the repository (Apache 2.0 preferred).
- Working project for judging, free of charge.
- English submission materials and an English interface.
- Public YouTube demonstration video of no more than three minutes.
- Accurate disclosure of pre-existing work and new contest-period development.

Facts from the hackathon page (re-check against the official rules before submission):
- Existing apps are eligible if significantly updated during the competition, with a summary of what was built in the submission period.
- Multi-platform apps get strong consideration for the Grand Prize; a single-platform app can still win.
- There is a separate Best Android App prize and a Best Web/Chrome App prize.

Never claim compliance without evidence.

Official page: https://firebasehybridaihackathon.devpost.com/
Official rules: https://firebasehybridaihackathon.devpost.com/rules
Firebase Android hybrid docs: https://firebase.google.com/docs/ai-logic/hybrid/android/get-started
Firebase Web hybrid docs: https://firebase.google.com/docs/ai-logic/hybrid/web/get-started

Judging criteria:
1. Technological Implementation (how effectively hybrid inference is used).
2. Design (a complete product experience, not only a proof of concept).
3. Potential Impact (a real problem for a real audience).
4. Quality of the Idea (creative, non-obvious use of Firebase AI Logic).

The first gate is passing the mandatory submission requirements. Only then does the scoring matter.

==================================================
3. THE PROBLEM NIRA SOLVES
==================================================

Garment print development involves many technical decisions before a sample is made.

A designer, merchandiser or print technician needs to know:
- How many print colours the artwork really has.
- Which printing route fits: digital CMYK, CMYK screen process, spot colours or simulated process.
- How many screens are needed.
- Which technique is possible: plastisol, pigment, discharge, puff, high-density, foil, flock and others.
- Whether the technique suits the fabric and garment colour.
- Roughly how much ink the job uses.
- Whether one print size fits all garment sizes, or graded print sizes are needed.
- What still needs physical validation.

Today this takes separate tools, manual inspection and repeated messages between designer, merchandiser and printer. Buyer artwork and tech packs are also confidential, and factory floors often have weak network.

NIRA makes the first stage of this workflow faster, clearer and private.

Target users:
- Textile print technicians and printing engineers.
- Merchandisers.
- Garment and print designers.
- Product-development and sampling teams.

Core value proposition:
NIRA turns garment artwork, a garment photo or a tech pack into a structured print engineering report, by combining measured image data, print rules and hybrid AI, while keeping the confidential image on the device.

==================================================
4. PRODUCT DIFFERENTIATION
==================================================

Do not build a generic chatbot with an image upload.

Build three cooperating intelligence layers.

LAYER A — DETERMINISTIC TEXTILE ENGINE (no AI)

Responsible for all numbers. Functions:
- Image loading, validation, memory-safe resizing.
- RGB, HEX and estimated CIE Lab conversion (D65).
- CIEDE2000 colour difference for all matching and merging.
- Automatic print-colour palette: over-cluster, merge by CIEDE2000, drop noise, exclude garment colour, respect a maximum screen count.
- Palette editing: merge, split, remove, lock, add by HEX.
- Shade library matching: built-in demo library plus user-imported libraries; top 3 nearest shades with match grade.
- SPOT separation (flat masks) and TONAL separation (greyscale ink channels).
- Print Report calculations: separation strategy comparison (CMYK process vs best N spot inks vs flat), proof image, colour error, screens, ink quantity.
- Technique suitability check from the rules files.
- Print fit and grading check from a size chart.
- Garment-photo correction: perspective, white balance from a white reference, texture smoothing.

This layer must never depend on a generative AI response.

LAYER B — ON-DEVICE INTELLIGENCE (the hero)

Use Gemini Nano through Firebase AI Logic. One short task per request, structured output, English prompts.

Jobs:
1. Classify the artwork as FLAT or TONAL and describe motifs and effects.
2. Name the likely print technique from a garment photo, with the visible evidence.
3. Flag print risks (thin lines, small text, gradients, large solids).
4. Explain the engine's numbers in plain language.
5. Answer one question about the job, using only the job data and rules.
6. Read tech pack text into structured fields.

The image and tech pack stay on the device. Do not claim local execution without actual inferenceSource evidence.

LAYER C — CLOUD INTELLIGENCE (the helper)

Use a supported cloud Gemini model through Firebase AI Logic.

- Called only when the user asks for the deep report, or as fallback.
- Receives the on-device text description plus engine numbers and matched rules. It does NOT receive the image.
- An image may be sent to the cloud only after the user confirms on a clear dialog.
- Writes the long report, alternatives and, on request, a Tamil version.

All generative AI calls must go through Firebase AI Logic. Do not call Gemini REST APIs or any other AI provider directly.

==================================================
5. HYBRID INFERENCE DESIGN
==================================================

Use the official Firebase AI Logic hybrid SDK.

Implement:
- PREFER_ON_DEVICE.
- ONLY_ON_DEVICE diagnostic mode.
- ONLY_IN_CLOUD diagnostic mode.
- PREFER_IN_CLOUD where useful.

A cloud-hosted model must be named explicitly; the SDK has no default cloud model.

Capture the actual response inferenceSource. Never infer the route from the selected mode.

Display actual source: ON_DEVICE, IN_CLOUD, ERROR, UNAVAILABLE.

Make the hybrid visible to the user:
- A badge on every AI answer with the real source and the duration.
- A counter: answers on device, answers in cloud, images uploaded.
- A Confidential mode switch that blocks all cloud calls.
- A diagnostic screen: requested mode, actual source, model configuration, device compatibility, duration, fallback result, error, timestamp.

Technical limits:
- On-device requests: single-turn text, or text plus one Bitmap image; structured output is supported. No chat, tools, function calling or grounding on-device.
- TO VERIFY from Firebase documentation before relying on them, and record in docs/PROJECT_STATUS.md: on-device input token limit (reported as about 4,000), recommended output length (reported as about 256 tokens), supported on-device languages (reported as English and Korean), supported devices, and the Gemini Nano 4 preview enrolment steps.
- Keep on-device prompts short, in English, one task each.

If a device is unsupported, show a clear explanation, document the result, and never fabricate an on-device result.

==================================================
6. SIGNATURE PRODUCT EXPERIENCE
==================================================

Inputs (any one):
- Artwork file from the phone.
- Photo of a printed garment taken with the camera.
- Tech pack text and size chart (typed or pasted).

Journey:

STEP 1 — INPUT
User selects artwork or takes a garment photo. For a photo: four-corner straighten, white reference, scale.

STEP 2 — COLOUR INTELLIGENCE (engine)
Real print colours, HEX, RGB, estimated Lab, coverage, nearest shades.

STEP 3 — ON-DEVICE READ (Nano)
Artwork type, motifs, likely technique, risks. Badge shows the real source.

STEP 4 — PRINT REPORT (engine)
Strategy comparison table, chosen separation, channels, proof, screens, ink quantity, technique suitability, grading check when a size chart is given.

STEP 5 — DEEP REPORT (cloud, optional)
Full print engineering report from text and numbers only.

STEP 6 — SAVE AND SHARE
Save the analysis, reopen it, export the report and separation sheet.

==================================================
7. SIGNATURE FEATURE: NIRA PRINT REPORT
==================================================

Every report keeps four sections apart:

A. MEASURED DIGITAL DATA
Colours, coverage, areas, colour error of each separation option, screen count, calculated ink quantity.

B. AI INTERPRETATION
Artwork type, motifs, likely technique and the evidence. Marked with the real inference source.

C. ENGINEERING SUGGESTIONS
From print_rules.json and print_techniques.json only. Each line shows its confidence label.

D. NEEDS PHYSICAL VALIDATION OR TECH PACK
Spectrophotometer reading, lab dip or strike-off, fabric and process test, fastness, buyer Pantone code, size chart.

Rules:
- Never present RGB-derived Lab as instrument-measured Lab.
- Never claim an exact dye or pigment recipe from an image.
- Ink quantity IS allowed as a calculated estimate: area x coverage x deposit x strokes x pieces x (1 + wastage), with every factor shown and editable, and labelled "estimate; screen residue and set-up loss not included".
- Dye and paste quantities are calculated only from recipes the user enters.
- Values from a garment photo are labelled "Estimated from garment photo".
- Separation channels are labelled "Preliminary digital separation, not print-ready films".
- The user chooses the separation strategy. Never choose silently.

==================================================
8. KNOWLEDGE FILES
==================================================

All engineering knowledge lives in app/src/main/assets, never hard-coded in Kotlin:

- print_rules.json: mesh, halftone, underbase, cure, fabric cautions, ink deposit, CMYK placeholder inks, dye formulas.
- print_techniques.json: 27 print techniques with artwork fit, fibre suitability, parameters, risks, sources.
- nira_demo_shades.csv: 150 generic demo shades (columns type, code, name, L, a, b).
- prompts/: the AI prompt text files.

Rules for these files:
- Every value carries a confidence label (sourced, interpolated, article only, conflict, assumption). Show the label in the UI.
- Every technique has verified_by_ravi. Unverified rules show "Not floor-verified".
- Do not invent rule values. If a value is missing, show "not available".
- Never bundle, download or scrape Pantone or any other third-party colour library. Users import their own licensed data; it stays in app-private storage and is excluded from Git.
- Pantone codes may be entered by the user or read from a tech pack. NIRA does not supply them.

==================================================
9. PRODUCT DESIGN SYSTEM
==================================================

Preserve NIRA's visual identity: deep navy, violet, cyan; premium professional textile-tech style; Material 3; clear hierarchy; responsive screens.

Screens:
1. Dashboard.
2. Artwork Studio (landing, preview, colour intelligence, separation, history) — already built.
3. Shade Library — already built.
4. Hybrid AI Analysis.
5. Print Report.
6. Technique Library.
7. Execution Diagnostics.
8. Settings (language, confidential mode, report language).

No decorative-only controls. Every visible button performs a real action or is clearly marked as future.

Do not redesign completed features without a specific reason.

Languages:
- English is the default and must always be complete.
- Tamil is the second app language, switchable in Settings, using Android string resources.
- Technical terms, units and numbers stay in English inside Tamil text.
- On-device prompts stay in English. The cloud report may be written in Tamil on request.

==================================================
10. TECHNICAL ARCHITECTURE
==================================================

Maintain separation of concerns.

Presentation: Jetpack Compose, ViewModels, StateFlow, Navigation.
Domain: use cases, business models, validation rules.
Data: repositories, local persistence, Firebase integration.
AI: Firebase AI Logic, HybridInferenceRepository, prompt templates from assets, structured output models, inference telemetry.
Image processing: ArtworkImageProcessor, ColorExtractor, ColorConverter, ColorSeparator, plus the Print Report engine.
Persistence: existing app-private JSON storage; Firestore only for user-owned history (text and numbers, no images).
Security: Firebase App Check, Authentication if accounts are used, Firestore security rules, no embedded secrets.

Keep image processing deterministic and testable. Do not change Gradle or Kotlin versions without a demonstrated need.

==================================================
11. FIREBASE PRODUCTS
==================================================

Required: Firebase AI Logic, Firebase App Check.

Use where useful, each with a real job:
- Remote Config: cloud model name and prompt versions.
- Cloud Firestore: saved history, text and numbers only.
- Firebase Authentication: so each user sees only their own records.

AI provider: Gemini Developer API through Firebase AI Logic (already selected in the console).

Versions shown by the Firebase console on 15 Nov 2026: Google services Gradle plugin 4.6.0, Firebase BoM 35.2.0.

App Check: debug provider for development, initialised before any AI request. Never commit the debug token. Play Integrity is a documented future task.

app/google-services.json must be in .gitignore before the first commit that follows its placement.

==================================================
12. SECURITY, PRIVACY AND CONFIDENTIALITY
==================================================

Artwork and tech packs are commercially sensitive.

NIRA must:
- Process image data locally.
- Send text and numbers to the cloud, not images, unless the user confirms.
- Request AI analysis only after user action.
- Keep artwork, user shade libraries and tech pack data in app-private storage.
- Avoid logging artwork content.
- Keep API keys, debug tokens, local.properties and signing keys out of Git.

Never put into the app, repository, video or Devpost page:
- Buyer tech packs, buyer names, staff names or unreleased styles.
- Employer or customer artwork.
- Artwork showing third-party characters, bands or brands.
- The old Python project.

Use only the developer's own artwork and a made-up demo tech pack for testing and the demonstration.

==================================================
13. TESTING AND QUALITY
==================================================

IMAGE: valid, invalid, large, empty and 1x1 images; resizing.

COLOUR: RGB/HEX/Lab conversions; CIEDE2000 against Sharma reference pairs; extraction on synthetic 2, 4 and 6 colour images; percentages; noise and edge pixels; separation masks.

PRINT REPORT: NNLS on synthetic images of known ink amounts; CMYK proof of a CMYK-synthesised image near zero error; ink quantity against hand-calculated numbers; all-garment-colour image.

RULES: both rules files load; unique technique ids; each suitability rule with a passing and a failing case; tech pack term map.

AI: Firebase initialisation; App Check; cloud inference; local availability; on-device-only test; hybrid fallback; actual inferenceSource reporting; quota and network errors.

APPLICATION: dashboard; navigation; analysis; report; history; language switch; lifecycle recovery.

Do not report tests as passed unless actually executed. Keep screenshots and logs in docs/evidence/.

==================================================
14. HACKATHON STRATEGY
==================================================

The story for the judges:
"A buyer's unreleased artwork is confidential and the factory floor has poor network. NIRA reads the artwork on the device, so the design never leaves the building. The engine calculates the print plan. The cloud is used only for a deeper report, and it never sees the image."

TECHNOLOGICAL IMPLEMENTATION: real hybrid execution with visible proof of the route.
DESIGN: one complete workflow from input to report.
POTENTIAL IMPACT: specific decisions made faster for print technicians and merchandisers.
QUALITY OF IDEA: on-device model sees the confidential image; cloud model reasons on text and numbers; deterministic engine does all the maths.

A compelling working demonstration is worth more than a long list of unfinished modules.

==================================================
15. GITHUB AND OPEN-SOURCE PREPARATION
==================================================

Repository: nira-buildhybridai-2026 (private until the developer approves making it public).

Prepare: README.md, Apache-2.0 LICENSE, .gitignore, architecture, Firebase setup, build, testing, hybrid AI explanation, hackathon work disclosure.

Before publication: scan for secrets, debug tokens, local.properties, signing keys, private Firebase files, user libraries, confidential data.

Do not publish, push to a public remote or change visibility without the developer's approval.

==================================================
16. DEMONSTRATION STRATEGY
==================================================

Maximum three-minute public YouTube video, English narration or subtitles, showing the real app.

0:00–0:20  The problem: confidential artwork, many print decisions.
0:20–0:50  Pick artwork; real print colours and shades appear.
0:50–1:25  Switch off the network. On-device read still works. Badge shows ON_DEVICE.
1:25–2:00  Print Report: CMYK vs spot comparison, screens, ink quantity, technique check.
2:00–2:35  Network on. Deep report from the cloud. Counter shows 0 images uploaded.
2:35–3:00  Architecture in one picture and the value to textile professionals.

Do not fake local execution.

==================================================
17. PROJECT EXECUTION RULES
==================================================

Before every major implementation:
1. Inspect the existing code.
2. Identify completed and missing functionality.
3. Present a concise plan.
4. Make minimal, controlled changes.
5. Run tests and build.
6. Report evidence.

Keep chat replies short and write details to files, because long replies have been cut off by connection errors.

Never:
- Rebuild completed features without need.
- Replace the existing dashboard.
- Upgrade Gradle or Kotlin without need.
- Create fake AI output or fake execution indicators.
- Claim unsupported device compatibility.
- Call AI outside Firebase AI Logic.
- Hard-code rule values.
- Publish secrets.
- Mix NIRA with AADAI or KINS.
- Submit or publish without approval.

Maintain:
docs/MASTER_CONTEXT.md
docs/PROJECT_STATUS.md
docs/ARCHITECTURE.md
docs/HACKATHON_REQUIREMENTS_MATRIX.md
docs/TEST_EVIDENCE.md
docs/CHANGELOG.md

Update PROJECT_STATUS.md after every completed stage.

==================================================
18. DELIVERY PRIORITIES AND DATES
==================================================

Priority 0 — by 18 Nov: Firebase Stage B in the app, App Check debug token, first cloud call, first on-device call in Chrome.
Priority 1 — by 21 Nov: hybrid flow with visible source; Print Report in the app.
Priority 2 — by 24 Nov: technique library, tech pack reading, grading check, Tamil. FEATURE FREEZE on 24 Nov.
Priority 3 — by 27 Nov: web version, public repository, licence, documents, evidence.
Priority 4 — 28–29 Nov: video and Devpost text.
Priority 5 — 30 Nov: submit early.

Not in scope for this hackathon: iOS, AADAI integration, ERP modules, full costing, CAD, camera spectrophotometer accuracy, a full Pantone system.

==================================================
19. CURRENT PROJECT STATUS (15 November 2026)
==================================================

Completed and reported with build success:
- NIRA Android project, dashboard, theme.
- Artwork Studio Phase 1 (11 unit tests).
- Phase 2 Stage 1: print colour palette with CIEDE2000 (20 unit tests).
- Phase 2 Stage 2: shade library, CSV import, manual shade form (24 unit tests).
- GitHub private repository created and first commit pushed.
- Firebase console: project nira-90d39, Android app registered, AI Logic enabled with Gemini Developer API, App Check enabled, AI monitoring enabled.
- Rules files prepared: print_rules.json, print_techniques.json, nira_demo_shades.csv.

Must still be verified:
- Each completed stage working on the vivo V60e.
- google-services.json is in the app folder and ignored by Git.
- Rules files are in app/src/main/assets.
- Instrumented UI tests (written, not run).

Known small gaps:
- Tests for empty and 1x1 images, invalid input, and noise or edge pixels.

Not started:
- Firebase SDK and App Check in the app; debug token registration.
- Cloud inference; on-device inference; hybrid routing; inferenceSource UI.
- Print Report, technique library, tech pack reading, grading check, garment photo mode.
- Tamil language.
- Web version.
- Public repository, licence, documents, video, Devpost.

Never mark a task complete just because a previous prompt requested it.

==================================================
20. FINAL PRODUCT PRINCIPLE
==================================================

A textile professional should be able to take a garment artwork, a garment photo or a tech pack, see its real print colours, get a print plan with screens and ink estimate, and know what must happen next in sampling.

The engine provides measurable facts.
The on-device AI sees the confidential image and gives fast, private help.
The cloud AI gives deeper reasoning from text and numbers.
Firebase AI Logic connects them into one hybrid experience.

Our standard is not the number of screens or the amount of generated code.
Our standard is a real, technically honest, finished textile intelligence product.
