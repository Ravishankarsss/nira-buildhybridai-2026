# NIRA — hackathon review and phased plan (22 Nov 2026)

Written for Ravi. Everything here was checked on 22 Nov 2026: the official rules page, the code, and the vivo V60e phone. Nothing in this file is a promise of a result; the scores are honest estimates.

Deadline: **1 Dec 2026, 3:30 am India time** (30 Nov, 2:00 pm Pacific). Feature freeze: **24 Nov**.

---

## 1. Your direct questions

### 1.1 Can the GitHub repository be "only for judges"?

**No.** The official rules (devpost rules page, read 22 Nov) say:

> "The repository must be public and open source by including an open source license file."

The licence must be visible at the top of the repository page (the About section). A private repository shared with judges does not pass Stage One (pass/fail). So the repository must be made public before you submit.

What we already have: `LICENSE` (Apache 2.0) and `README.md`. What is left before making it public (Phase 1):

1. Secret scan of the whole Git history (keys, tokens, `google-services.json`, `judge.properties`, `web/firebase-config.js`, `local.properties`). These four files are git-ignored and were never committed; the scan proves it.
2. Check no buyer tech packs, third-party brand artwork or private photos are in the history.
3. README refresh: what NIRA is, how to build, how hybrid AI works, judge links, what was built in the contest period.
4. **You** change the visibility on GitHub (click-by-click steps will be given). I do not change visibility.

Note: the rules also say "Entrants must not include production API keys … within their repository". The Firebase web config and the reCAPTCHA site key are public by design (domain-limited), but we keep them out of Git anyway.

### 1.2 Devpost page not filled

I cannot log in to Devpost for you. Section 7 below has the full text ready to paste, field by field.

### 1.3 Lion on navy: why "Discharge + digital print"?

The result is correct **by your own rules**:

| Step | Where the value comes from |
|---|---|
| Garment navy = dark | `white_base`: garment L* below 70 → dark garment |
| Fibre cotton | `discharge_digital.suitable_fibres`: "cotton (100%, reactive-dyed, dischargeable)" |
| Artwork 15 colours, tonal | `discharge_digital.artwork_fit`: "ANY" (digital prints any number of colours) |
| Feel soft | `print_brief.feel_of_technique.discharge_digital`: "soft" (matches) |
| Ranking | `print_brief.method_order`: matches feel → fibre listed → **verified by Ravi** (true for discharge_digital, 19 Nov) |
| 1 screen | `discharge_digital.screens`: 1 (white discharge screen in the shape of the artwork) |
| ₹50.40 / pc | Sample factory rates: fixed ₹950 for 1 screen + ₹48.50 per piece digital. "Sample rates — not a quote". Same for the wolf because the cost does not depend on the number of colours for digital. |

**Your idea for the top of the job page (agreed, Phase 1):**

- The small picture at the top shows the artwork on white. Better: show **the print on the chosen garment** (the same picture as "See it on your garment": with white base when the method uses a base or discharge, otherwise without).
- The title "Artwork · orange, red, yellow, magenta, …" is a long colour list and is cut off. Better:
  - Title: **"Artwork on navy cotton"** (garment colour + fibre from the four answers)
  - Line 2: **"15 print colours · 500 pcs"**
  - Line 3 (unchanged): method · screens · cost · verified by Ravi
- All words come from the answers and the engine. No AI naming ("lion") because AI must not invent job data; if you want a name, the user can type it.

### 1.4 "Ask NIRA comes late" and what is slow (measured on the vivo V60e)

| What | Time | Why |
|---|---|---|
| Fresh analysis, lion 1254 × 1254 px, 15 colours | **30–35 s** | Engine work on the phone: garment mask, colour measurement (CIEDE2000), separation strategies. Earlier measurement: Part A 13.7 s, strategy evaluation up to 16.7 s. |
| Ask NIRA button on a new job | ~5 s after the 4th answer | By design it waits for the decision card's method. |
| **Reopening a saved job** | **45–60 s, and the 4 answers are lost** | Reopening runs the whole analysis again and does not restore the background choice, garment, fibre, feel or quantity. So "4 questions left" returns and Ask NIRA never appears until you answer again. **This is the biggest problem.** |

Fixes (Phase 1 and 2):

1. **Save the answers with the job** (background, garment, fibre, fabric, feel, quantity, swaps) and restore them on reopen → method and Ask NIRA come back without new taps.
2. **Save the analysis result** (palette, print mask, strategies) with the job → reopening takes about 1 s instead of 45–60 s.
3. **Show progress** during the first analysis ("Measuring colours… 1 of 3") so 30 s does not look like a freeze.
4. **Show Ask NIRA earlier**: before the four answers it can answer about the measured artwork only (colours, coverage, what NIRA noticed).
5. Engine speed (Phase 3): profile Part A and strategy evaluation on the phone; analyse artwork at a fixed working size; skip strategies that cannot pass.

### 1.5 How much of NIRA is AI, and how to improve it

Measured from the code on 22 Nov:

- Kotlin code: 25,187 lines. Files that touch AI: 5,293 lines (**about 21%**).
- 11 prompt files in `assets/prompts` (card explain, Ask NIRA job + Home, voice versions, buyer brief reader, artwork reader, sheet translation, Cost-Down explanation, report explanation, deep report).
- One gateway: `core/ai/HybridInferenceRepository.kt` (Firebase AI Logic hybrid; the real `inferenceSource` is shown).

**User-facing features that use AI:** Explain the card, Ask NIRA on a job (text + voice + actions), Ask NIRA on Home, conversation mode, buyer brief reader (photo of a tech-pack page), translated Print Decision Sheet, Cost-Down explanation, AI activity log. **Features with no AI (on purpose):** colour measurement, separation, method decision, screens, mesh, ink, cost, strike-off rules.

For this hackathon the judges score *how well hybrid AI is used*, not the percentage. A higher share of AI would be worse if it touched engineering values. The story is: **"AI explains, answers, translates and acts; the engine and Ravi's rules decide; NIRA checks every number the AI says."** Ways to make the AI part stronger without breaking that rule:

1. **Show on-device AI on video** (Chrome, Gemini Nano works since 18 Nov). The vivo has no Nano (`unavailable`), so the Android demo is cloud; the web shows ON_DEVICE. Say this openly.
2. **Ask NIRA before the answers** (see 1.4) — AI is useful from the first second.
3. **AI reads the artwork** (on-device first in Chrome): "flat or tonal, small text, thin lines, gradients" → shown as "AI noticed", always labelled AI and never changing the engine values.
4. **"Why this method?" in one tap**: the AI explains the ranking table above in plain words, with NIRA's number check.
5. **AI activity on the web** already exists — show it in the video (on device / in cloud / held back).

---

## 2. What is still pending for the hackathon (must-have gate first) — updated 25 Nov

| # | Requirement (Stage One, pass/fail) | Status |
|---|---|---|
| 1 | Firebase AI Logic imported and called at runtime | Done (Android + web) |
| 2 | Real on-device inference | Done in Chrome (ON_DEVICE 3,440 ms, 18 Nov). Not on the vivo (no Nano). |
| 3 | Cloud inference | Done (IN_CLOUD, Android and web) |
| 4 | Actual hybrid routing | Done (PREFER_ON_DEVICE / PREFER_IN_CLOUD, real source shown) |
| 5 | App Check | Done (Android debug provider + judge build; web reCAPTCHA Enterprise) |
| 6 | Public repository + licence in About | Done (public, Apache 2.0) |
| 7 | Working project, free, for judges | Done: web live; judge APK tested on clean Android 8.0 and 14 emulators (25 Nov) |
| 8 | English materials and interface | Done (app default English, 9 languages) |
| 9 | **Public YouTube video ≤ 3 min** | Video v2 made (2:48); **Ravi: confirm captions, upload** |
| 10 | **Devpost text, disclosure of pre-existing work** | Text ready in section 7; **Ravi: fill the form and submit by 30 Nov** |
| 11 | No production keys / PII / confidential data in repo or form | Done: secret scans clean (latest 24 Nov, before the push) |

Other open items: step 7 Part C (read the free limits for gemini-3.5-flash-lite), revoke the judge token after judging, strike-off check screen, your answers on the contrast rule (chroma?) and the 1254 px resolution warning.

---

## 3. Five judges — scores (1 to 5) and what each would ask for

The official Stage Two has four equal criteria (1–5 each, averaged): Technological Implementation, Design, Potential Impact, Quality of the Idea. The five views below map onto them.

### Judge 1 — normal software judge (does it work, is it clear?) — **3.5 / 5**
- Good: real app and live web page; real AI answers with source and time; nothing fake.
- Against: first analysis 30–35 s with no progress; reopening a job loses answers; long job pages.
- Wants: a 60-second "happy path" that always works; a judge quick-start (web link + APK + 3 steps).

### Judge 2 — UI/UX designer — **3.5 / 5**
- Good: consistent dark theme, tabs, "NIRA noticed" strip, see-it-on-garment preview, web page now image-led.
- Against: dense text on cards, many amber warnings, small 11–12 sp text, no onboarding, no light theme, the header title is a cut-off colour list, the Ask NIRA button appears late.
- Wants: onboarding (3 screens), progress states, fewer and grouped warnings, larger type on the floor, a light theme option, header with the print on the garment.

### Judge 3 — software engineer — **4 / 5**
- Good: clear layers (engine / on-device / cloud), one AI gateway, 263 unit tests, rules in JSON not code, App Check on both platforms, real `inferenceSource`, AI answers checked by code.
- Against: engine speed on the phone; no cache of analysis; known issue that some tests may pass only because Android stubs return defaults (`isReturnDefaultValues = true`) — must be reported in the README; on-device not shown on Android hardware.
- Wants: an architecture diagram, test evidence, performance numbers, honest limits section.

### Judge 4 — business judge — **3.5 / 5**
- Good: a specific, real audience (print technicians, merchandisers, sampling teams in India), privacy for buyer artwork, cost per piece and Cost-Down, buyer-language sheet.
- Against: no numbers on the problem (how many samples are remade, cost of a wrong first strike-off), no adoption path, no pricing idea.
- Wants: one slide of "before NIRA / after NIRA" with your own floor experience (time to decide, remakes avoided), who pays, and next steps (pilot with one factory).

### Judge 5 — senior textile printing expert — **4 / 5**
- Good: rules from a real expert with sources and "verified by Ravi"; discharge risks (reactive dyes, dye lots, formaldehyde); mesh per screen; white base logic; background question; strike-off rules.
- Against: many rule values are still "assumption"; contrast check is lightness only (purple on white is flagged); every 1254 px artwork is "low resolution"; ink weight missing for discharge paste; 15 colours on watercolour art should also offer simulated process as a second choice for screen.
- Wants: confirm or adjust the placeholder values; add chroma to the contrast rule if you agree; show "simulated process" as an alternative for tonal art on dark garments when screen printing is preferred.

### Estimated official scores today
| Criterion | Today | After Phases 1–3 |
|---|---|---|
| Technological Implementation | 3.5–4 | 4–4.5 |
| Design | 3.5 | 4–4.5 |
| Potential Impact | 3.5–4 | 4–4.5 |
| Quality of the Idea | 4 | 4–4.5 |

---

## 4. Web page polish (from what winning hackathon pages usually have)

General patterns of winning Devpost projects (not a copy of any page):

1. **Video at the top of the page** (embed the YouTube demo under the hero) — judges often watch only this.
2. **"For judges" box**: web link, APK link, 3 test steps, what works on which device (on-device in Chrome; cloud on Android).
3. **Architecture diagram** (engine → rules → AI gateway → on-device / cloud → checks), drawn simply.
4. **Numbers with evidence**: "ON_DEVICE 3.4 s in Chrome", "IN_CLOUD 3.3 s", "263 unit tests", "29 techniques", each linked to evidence.
5. **Before / after** strip: the print decision by hand vs with NIRA (your floor words).
6. **Open source and links**: GitHub (after public), licence, README.
7. **Accessibility and speed**: image sizes already small; add `alt` texts (done), keyboard focus styles, check contrast on muted text.
8. **Honest limits** section: "model preview, not a proof", "sample rates, not a quote", "the vivo has no on-device model".

---

## 5. App polish ideas (patterns from good phone apps, fitted to NIRA)

| Pattern | Seen in apps like | For NIRA |
|---|---|---|
| Short onboarding with one real example | camera and design apps | 3 screens: "Artwork in → 4 questions → decision", with a sample job to open |
| Progress with named steps | photo editors, scanners | "Measuring colours… Separations… Decision" during the 30 s |
| Instant reopen of past work | document scanners | save answers + analysis with the job |
| One big result card | banking / weather apps | header with print-on-garment picture + method + cost |
| Grouped alerts | to-do / mail apps | one "NIRA noticed" list; contrast warnings grouped (done for same colour) |
| Theme choice | most Material 3 apps | Light theme + current dark; "Floor mode" with larger text and high contrast |
| Share as an image | social / receipt apps | share the decision card as a PNG for WhatsApp |
| Haptics and sound on success | many apps | small vibration when the decision is ready |

**Theme proposal:** keep the NIRA dark theme as default (it matches the web and the logo). Add (a) a **Light theme** for bright factory floors, and (b) **Floor mode**: 1.3× text, high-contrast colours, bigger buttons. Colours stay the NIRA violet/cyan. Phase 2, after feature freeze only if small.

---

## 6. Phased plan — one phase at a time, each built, tested, checked on the phone and committed

### Phase 1 — 22–23 Nov (must-haves and the biggest UX problem)
1. Save answers and analysis with the job; reopen restores them (fixes "Ask NIRA late" on reopen). Unit tests for save/restore.
2. Job header: print-on-garment picture, title "Artwork on <colour> <fibre>", "15 print colours · 500 pcs".
3. Progress steps during the first analysis.
4. Secret scan of Git history + README refresh (judge quick-start, limits, contest-period disclosure).
5. You make the repository public (click-by-click), licence visible in About.

### Phase 2 — 24 Nov (feature freeze day)
1. Ask NIRA before the four answers (measured facts only).
2. Web: "For judges" box, architecture diagram, evidence numbers, honest limits; video slot ready.
3. Devpost text pasted by you (section 7), images uploaded.
4. If time: Light theme / Floor mode (only if it does not risk the build).

### Phase 3 — 25–28 Nov (speed, proof, video)
1. Engine speed profiling on the phone; target under 15 s for the lion.
2. Test the judge APK on a second phone or emulator; Part C free-limit check.
3. Demo video (≤ 3 min): Chrome ON_DEVICE, Android IN_CLOUD, lion on navy, hummingbird cost, Ask NIRA voice action, AI activity. Script first, then record.
4. Your answers on rule placeholders (contrast chroma, resolution, simulated process).

### Phase 4 — 29–30 Nov (final)
1. Final checks against the Stage One list (section 2).
2. Final publish of web, push, video public on YouTube.
3. **You** press Submit on Devpost.

---

## 7. Devpost text (ready to paste; check every line before you submit)

**Project name:** NIRA — Textile Intelligence

**Elevator pitch (max 200 characters):**
> Garment artwork in, print decision out: method, screens, ink and cost from a print expert's rules. Gemini explains and answers on-device first, cloud when needed, and NIRA checks every number.

**About the project:**

**Inspiration**
Before a garment sample is printed, a merchandiser or print master must decide: how many colours does the artwork really have, which print method fits this fabric and garment colour, how many screens, does it need a white base, and what will it cost? Today this is decided by experience, on the phone with the print master, often after a wrong strike-off. The buyer's artwork is confidential, and factory networks are poor.

**What it does**
- Reads an artwork file or a photo of a printed garment on the phone: the real print colours (CIEDE2000), print area, background and fabric.
- Asks four questions (garment colour, fibre and fabric, quantity, feel) and shows one decision card: the method, second choices, screens, white base, mesh and ink per screen, and the risks — each line says where it came from and whether the expert checked it on the floor.
- Shows the print on the chosen garment, with and without a white base.
- Calculates cost per piece from the factory's own rates, with Cost-Down options.
- Ask NIRA: questions by text or voice (Tamil or English) about one job or all jobs; NIRA can act ("make the navy ink royal blue", "1000 pieces") with Undo.
- Reads a buyer brief photo, and makes a Print Decision Sheet in the buyer's language.
- A web page runs the same AI on a real NIRA job in Chrome.

**How we built it**
- Android: Kotlin, Jetpack Compose. Web: HTML/JS on Firebase Hosting.
- A deterministic engine and a rules file written by a garment print expert make every engineering value. The AI never makes them.
- Firebase AI Logic hybrid inference: on-device Gemini Nano first (PREFER_ON_DEVICE), cloud Gemini when needed; voice goes to the cloud. Every answer shows the real `inferenceSource` and time from the SDK.
- NIRA checks every AI answer: numbers must exist in the facts, colour codes are blocked, actions must be on a fixed list and are validated before they run. Failed answers are held back and logged on the AI activity page.
- Firebase App Check: debug provider for the judge build on Android, reCAPTCHA Enterprise on the web.
- Confidential mode blocks every cloud call; images go to the cloud only after the user agrees.

**Challenges we ran into**
- The test phone has no on-device model (SDK status `unavailable`), so on-device AI was proven in Chrome (ON_DEVICE, 3.4 s); the Android app shows the real cloud source instead of pretending.
- The small on-device model gave wrong print facts in early tests, which is why the AI only explains facts the engine produced, and every number is checked.
- Dark garments: fabric counted as ink, base white under the whole photo, too many screens — fixed with garment masks and white-base rules, with a regression test from a real photo.

**Accomplishments that we're proud of**
- A real print master's rules, with "verified by Ravi" where checked on the floor.
- Hybrid AI that is honest about where it ran, and checked by code.
- One flow from artwork to a buyer sheet in nine languages.

**What we learned**
- In an engineering domain, the best use of AI is to explain, answer and act on facts the engine owns — not to produce the numbers.

**What's next for NIRA**
- Pilot with one print factory; strike-off check screen; faster engine; more techniques and rule values confirmed on the floor.

**Built with:** kotlin, jetpack-compose, android, firebase-ai-logic, gemini-nano, gemini, firebase-app-check, recaptcha-enterprise, firebase-hosting, javascript, html5, css3

**Try it out links:**
- https://nira-90d39.web.app (web, Chrome)
- GitHub repository (after it is public)
- Judge APK and steps: see the repository README

**Disclosure of pre-existing work (check before submitting):**
NIRA's Android and web code was written during the contest period (first commit 15 Nov 2026). An earlier, separate Python project by the same author is not part of this submission and none of its code is used. *(Ravi: confirm the contest start date against this.)*

**Testing instructions (short):**
Web: open the link in Chrome, choose the job, press "Explain this card". Android: install the judge APK from the repository, open Home → Ask NIRA, or open an artwork and answer four questions. The vivo-class phones without Gemini Nano answer from the cloud; the source is shown on every answer.
