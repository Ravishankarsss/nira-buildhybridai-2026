# NIRA — how judges get a working project

Written 18 November 2026. Deadline: 1 December 2026, 3:30 am India time (30 Nov, 2:00 pm Pacific). Feature freeze: 24 November.

Hackathon rule: a working project for judging, free of charge. Firebase App Check is mandatory (debug tokens are accepted). English interface and materials.

## 1. The problem today

| Path | Works for Ravi | Works for a judge | Why not |
| :--- | :--- | :--- | :--- |
| Android debug APK | Yes (vivo V60e) | **No AI** | App Check is enforced. A judge's phone creates its own debug token, which is not registered, so every AI request fails with 403 "App attestation failed" (seen on 17 Nov before the vivo token was saved). |
| Web page (`web/`) | Yes, on `localhost` in Ravi's Chrome | **No** | It runs only on `localhost`; App Check debug mode is on only there, and no production web provider (reCAPTCHA Enterprise) is set up. `web/firebase-config.js` is not in the repository. |
| Engine (colours, report, ink) | Yes | Yes, in the APK | No AI needed; works offline. |

So the engine works for anyone, but no judge can see the hybrid AI, which is the main thing being judged.

## 2. What judges need to see

1. The engine: photo or artwork → measured print colours → Print Report (screens, white base, ink).
2. Hybrid AI with the real source shown: ON_DEVICE when a model exists, IN_CLOUD when not, and the honest fallback.
3. App Check working.
4. Privacy: text only to the cloud by default; Confidential mode.

Gemini Nano runs on few phones (the vivo V60e reports `unavailable`) and in Chrome only on capable computers. Most judges will therefore see IN_CLOUD on Android. ON_DEVICE is best shown in Chrome and in the video.

## 3. Plan

### 3.1 Web (Chrome) — public link, App Check enforced (recommended)

1. Add the reCAPTCHA Enterprise provider for App Check on the web app (invisible, score based). Keep the debug provider for `localhost` only.
2. Host `web/` on Firebase Hosting (free on the Spark plan). Deploy the web config with the site; the web API key is public by design, protected by App Check and API-key restrictions (HTTP referrer = the hosting domain).
3. Judges open the link in Chrome. The page shows the Prompt API status:
   - `available`: ONLY_ON_DEVICE gives ON_DEVICE (as in Ravi's evidence of 18 Nov).
   - otherwise: PREFER_ON_DEVICE falls back to IN_CLOUD, shown honestly.
4. Short instructions on the page: which Chrome version, the two `chrome://flags` settings used on Ravi's machine, and that the first model download is several GB on unmetered Wi-Fi.
5. To verify before submission: reCAPTCHA Enterprise free tier and pricing, and that Firebase AI Logic accepts reCAPTCHA Enterprise tokens with enforcement on. Do not claim it until tested.

Gap: the web page is a hybrid AI test page, not the full product. A small web Print Report is not planned before the freeze; say so in Devpost.

### 3.2 Android — APK on GitHub Releases

Choose one App Check route (Ravi decides; see section 5):

- **Route A (recommended): judge build with one registered debug token.** A separate build variant sets one debug token, registered in the Firebase console, on first start. Enforcement stays on. The token is shared only through the Devpost testing instructions (to be verified as visible to judges only), and is revoked after judging. Debug tokens are explicitly accepted by the rules. Risk: anyone who gets that APK can use the token; limited by quota caps and revocation.
- **Route B: Play Integrity in a release build, enforcement switched to monitor during judging.** A sideloaded APK cannot pass Play Integrity, so requests would be allowed only because enforcement is off. Honest, simple, but App Check is then not protecting anything during judging; this must be stated.
- Route C (Play Store internal or closed testing) is not workable: testers are added by email, and judges are not known in advance.

Steps for either route:
1. Release-signed APK (signing key kept out of Git), R8 shrinking on, and an arm64-v8a build to bring the size down from 155 MB (debug build, 18 Nov). Measure; do not promise a number.
2. Upload to GitHub Releases on the public repository, with the SHA-256 of the file.
3. Test on at least one phone other than the vivo and on the Android emulator: install, first start, Artwork, Print Report, "Why this recommendation", Settings → on-device status.
4. Write the judge steps (section 4) into the README and the Devpost testing instructions.

### 3.3 Cost and limits (free for judges)

- AI runs through Firebase AI Logic with the Gemini Developer API backend (`GenerativeBackend.googleAI()`; web `GoogleAIBackend`). Judges pay nothing; NIRA's Firebase project carries the use.
- To check with Ravi: is the project on the Spark (free) or Blaze plan? On Blaze, set a budget alert and a daily request cap before publishing. Check the free-tier rate limit for `gemini-3.5-flash-lite` so ten judges at once do not hit it.
- AI calls happen only after a user action or once per new Print Report result, text only by default.

### 3.4 Sample files for judges

Judges need artwork and a garment photo that may be published:
- Ravi's own designs only. No brands, characters, buyer or employer artwork, no faces or neck labels, GPS removed.
- Planned: the four photos agreed on 18 Nov (light garment with grey ink and a fold; same laid flat; dark garment with white print and a fade; Ravi's own artwork file), with Ravi's expected-result notes.
- Put them in `samples/` in the repository and on the Releases page.

## 4. Judge steps (draft, to finish after testing)

Android (judge build "NIRA judge", link only in the Devpost testing instructions):
1. On an Android 8.0+ phone, download `NIRA-judge-<version>.apk` from the link in the testing instructions.
2. Allow installing from this source when the phone asks, then install. It is called "NIRA judge".
3. Home → "Start a print job" → Artwork file → choose a sample from `samples/`.
4. On the job page answer the four Brief questions (garment colour, fibre, quantity, feel). The top bar shows the method, screens and cost per piece; the tabs show Decision, Cost and Engineering.
5. Decision tab → "Explain this card", or the ✦ Ask NIRA button (type, or tap the microphone to talk). Every answer shows its source as reported by the SDK (On this phone / In cloud) and the time.
6. Home → "Hybrid AI on this phone" → AI activity: every AI request, on-device or cloud, and whether NIRA's checks passed it.
7. Settings → AI and privacy: on-device status from the SDK, Confidential mode.

Web (Chrome):
1. Open the hosted link in Chrome (version and flags as listed on the page).
2. Click "Prepare on-device model", choose a mode, click Run. The result shows the source reported by the SDK.

## 5. Decisions (Ravi, 18 Nov 2026)

1. **Android: route A**, with two safeguards. The judge token comes from a git-ignored local file at build time, never from the repository. The judge APK is shared only through a link in the Devpost testing instructions, not on the public Releases page. The public Releases build has the engine working and an AI screen saying "Use the judge build or the web link for AI". The token is revoked after judging.
   - Found 18 Nov: Firebase documents a fixed debug token only for instrumented tests in CI (`DebugAppCheckTestHelper`, `firebaseAppCheckDebugSecret`), not for an app given to users, and says debug tokens and debug builds must not be shipped to production. Route A therefore relies on undocumented behaviour: storing the registered token where the debug provider keeps its secret before App Check starts. It must be tested on a phone with app data cleared before we rely on it. A documented production alternative (a custom provider) needs a server, which the Spark plan cannot run. If route A fails the test, the fallback is route B, stated openly.
2. **Web: yes**, Firebase Hosting with reCAPTCHA Enterprise; the debug provider stays for `localhost` only. Firebase docs: on the Spark plan reCAPTCHA Enterprise works with four score levels (0.1, 0.3, 0.7, 0.9).
3. **Plan: Spark (no billing).** No charge is possible; when a limit is reached requests fail with HTTP 429.
   - Cloud model confirmed 18 Nov: `gemini-3.5-flash-lite` on Android (`CLOUD_MODEL_NAME`) and web (`web/app.js`). `gemini-3.8-flash` appears only in history notes (replaced on 17 Nov after two 180 s time-outs).
   - Limits (Firebase AI Logic quotas page and Gemini API rate-limits page, read 18 Nov): limits are per project, not per key or user, so all judges share them; measured as requests per minute, requests per day and tokens per minute/day; the daily count resets at midnight Pacific time; Firebase AI Logic adds 100 requests per minute per user. The free-tier numbers for `gemini-3.5-flash-lite` are not published on the page; they are shown for the project in Google AI Studio → Rate limits (aistudio.google.com/rate-limit). Ravi to read them there and note them here.
   - Done 18 Nov: a clear message when the limit is reached, on Android (QuotaExceededException or HTTP 429) and on the web page.
4. **Photos:** Ravi, by 20 November.

## 6. Order of work

| By | Task |
| :--- | :--- |
| 20 Nov | Ravi's decisions 1–3; Ravi's photos |
| 21 Nov | Web: reCAPTCHA Enterprise + Firebase Hosting; test from another computer |
| 22 Nov | Android: judge build (route A or B), release signing, size check |
| 23 Nov | Test on a second phone and the emulator; samples in `samples/` |
| 24 Nov | Feature freeze; README judge steps; Releases page |
| 25–29 Nov | Evidence, video, Devpost text (testing instructions from section 4) |
| 30 Nov | Final check of both links from a clean phone and a clean Chrome profile |

Never claim a path works for judges until it has been tested from a device that is not Ravi's.

## 7. Results so far

20 Nov 2026 — Android route A, first test:
- Judge build type in `app/build.gradle.kts`: app id `com.mos.nira.judge` ("NIRA judge"), installs beside NIRA, debug-signed, not debuggable (package flags 0x0), 13.9 MB (`./gradlew :app:assembleJudge`).
- The token is a new random value in the git-ignored `judge.properties`; Ravi registered it in the Firebase console for the NIRA judge app only. `app/google-services.json` (git-ignored) now holds both apps.
- Fresh install on the vivo V60e (new app, empty data): Home → Ask NIRA → one question answered `IN_CLOUD` in 11,400 ms with App Check enforcement on. So storing the token before App Check starts works on a clean start.
- Still to do: the same test on a second phone or the emulator (section 6); revoke the token after judging.

21 Nov 2026 — Web:
- reCAPTCHA Enterprise site key created by Ravi (domains nira-90d39.web.app, nira-90d39.firebaseapp.com, localhost); NIRA Web registered in App Check. The key is in the git-ignored `web/firebase-config.js` (a site key is public and domain-limited).
- Published with Ravi's approval: `firebase deploy --only hosting` -> https://nira-90d39.web.app (6 files).
- Live test from the Claude browser pane (a browser with no registered debug token): "Explain this card" answered `IN_CLOUD` in 3,888 ms with App Check enforced; the number, method-name and colour-code checks passed. So reCAPTCHA Enterprise App Check works on the hosted page.
- 22 Nov: web page v3 published (people section, built-by section, phone and web section, FAQ). Live "Explain this card" answered `IN_CLOUD` in 3,289 ms; all checks passed. The laptop screenshot on the page is this real answer.
- 22 Nov: judge APK (all features to commit 4b637c8) on a fresh Android emulator (Medium Phone, Android 16, x86_64, never had NIRA): Home → Ask NIRA "What can I print on nylon?" answered `IN_CLOUD` in 3,914 ms with App Check enforced. Route A works on a second, clean device.
- 22 Nov (Step 7 Part C, read by Ravi in Google AI Studio, project NIRA, free tier): gemini-3.5-flash-lite limits are 15 requests per minute, 250K tokens per minute, 500 requests per day. Peak use in the last 28 days: 2 RPM, 3.81K TPM, 14 RPD. 500 a day is enough for judging; if many judges test at the same minute, more than 15 requests per minute get the app's clear 'free limit used up' message.
- 22 Nov: repository made public by Ravi's decision; `master` moved forward to the latest work (fast-forward); Apache-2.0 licence detected by GitHub; website and topics set.
- Still to do: open the link from a computer or phone that is not Ravi's.
