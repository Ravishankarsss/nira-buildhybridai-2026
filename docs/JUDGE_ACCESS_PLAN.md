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

Android:
1. Download `NIRA-<version>.apk` from the GitHub Releases page on an Android 8.0+ phone.
2. Allow installing from this source when the phone asks, then install.
3. Open NIRA → Artwork → choose a sample file from `samples/` → Colour screen → Report.
4. In the Print Report, read "Why this recommendation": the badge shows ON_DEVICE or IN_CLOUD and the time.
5. Settings → AI and privacy: on-device status from the SDK, answer counters, Confidential mode.

Web (Chrome):
1. Open the hosted link in Chrome (version and flags as listed on the page).
2. Click "Prepare on-device model", choose a mode, click Run. The result shows the source reported by the SDK.

## 5. Decisions for Ravi

1. App Check route for Android: A (judge debug token, enforcement on) or B (monitor mode during judging).
2. Web hosting on Firebase Hosting with reCAPTCHA Enterprise: yes or no.
3. Firebase plan (Spark or Blaze) and the daily cap.
4. The four sample photos and notes (also needed for the regression tests).

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
