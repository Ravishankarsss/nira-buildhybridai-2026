# NIRA web version (Chrome) — setup

Last updated: 17 November 2026.

Purpose: prove real on-device inference (Gemini Nano in Chrome) through Firebase AI Logic, and give a second platform. Same Firebase project (`nira-90d39`), same cloud model as Android (`gemini-3.5-flash-lite`).

Files: `web/index.html`, `web/app.js`, `web/style.css`, `web/firebase-config.example.js`.
`web/firebase-config.js` holds the web config and is git-ignored. Never commit it.

Firebase JS SDK 13.2.0 is loaded from the official Firebase CDN (`www.gstatic.com/firebasejs`). On-device inference needs 12.5.0 or later. Checked on 17 Nov 2026 against:
- https://firebase.google.com/docs/ai-logic/hybrid/web/get-started
- https://firebase.google.com/docs/ai-logic/hybrid/web/configuration-options
- https://developer.chrome.com/docs/ai/prompt-api

## This laptop (checked 17 Nov 2026)

| Requirement (Chrome Prompt API) | This laptop |
| :--- | :--- |
| Chrome 148 or later (Firebase docs say 139+) | Chrome 157 — OK |
| Windows 10 or 11 | Windows 11 — OK |
| Free space at least 22 GB on the drive with the Chrome profile | 36 GB free on C: — OK, but the model is removed if free space drops below 10 GB |
| GPU with more than 4 GB VRAM, **or** CPU with 16 GB RAM and 4+ cores | GPU: AMD Radeon integrated, 0.5 GB (too small). CPU: 15.3 GB usable RAM, 16 threads — borderline |
| Unmetered network for the first download | Check before downloading (the model is several GB) |

On 15 Nov Chrome reported `downloadable`, which means Chrome considers this laptop eligible. Whether the model actually runs is only known after the download.

## Status (17 Nov 2026)

Steps 1 to 5 are done on this laptop: NIRA Web app registered, config file created, debug token from Ravi's Chrome registered, cloud call works.

## Step 1 — Register a Web app in Firebase (once)

1. Open https://console.firebase.google.com → project **nira-90d39**.
2. Click the gear icon → **Project settings** → **General** tab.
3. Under **Your apps**, click **Add app** → the **Web** icon (`</>`).
4. App nickname: `NIRA Web`. Leave **Firebase Hosting** unticked. Click **Register app**.
5. The page shows a block starting `const firebaseConfig = {`. Keep this page open for step 2.

## Step 2 — Create web/firebase-config.js

1. In Android Studio, open the `web` folder. Right-click `firebase-config.example.js` → **Copy**, then right-click `web` → **Paste**, and name the copy `firebase-config.js`.
2. Open `firebase-config.js` and fill in each value from the console block in step 1.5 (`apiKey`, `authDomain`, `projectId`, `storageBucket`, `messagingSenderId`, `appId`).
3. Save. Git ignores this file; do not send it to anyone.

## Step 3 — Start the page

In the Android Studio **Terminal** (bottom of the window), from the project folder:

```bash
python -m http.server 8080 --bind 127.0.0.1 --directory web
```

Then open **http://localhost:8080** in Chrome. (It must be `localhost`: App Check debug mode is switched on only there.)

Stop the server with **Ctrl + C** in the terminal.

## Step 4 — Register the web App Check debug token

The web app has its own debug token, separate from the phone.

1. In Chrome on the NIRA page press **F12** → **Console** tab.
2. Find the line `Firebase App Check debug token: ...` and copy only the code (36 characters).
3. Firebase console → **App Check** → **Apps** → the **NIRA Web** row → three dots (⋮) → **Manage debug tokens** → **Add debug token**. Name: `laptop Chrome`. Value: paste. **Save**.
4. Check the dialog now lists the token, click **Done**, wait one minute.

The token is stored in this Chrome profile. Clearing site data for localhost creates a new one.

## Step 5 — Cloud test

1. Choose **ONLY_IN_CLOUD**, click **Run**.
2. Expected: Inference source `IN_CLOUD`, a one-sentence answer, a few seconds.

## Step 6 — On-device model

1. Look at **Prompt API availability (Chrome report)**:
   - `available` → go to step 7.
   - `downloadable` → click **Prepare on-device model** and wait. The download is several GB and can take a long time. Leave the tab open.
   - `downloading` → wait; open `chrome://on-device-internals` in a new tab to see progress.
   - `unavailable`, or "LanguageModel API missing" → open `chrome://flags`, search `prompt-api-for-gemini-nano`, set the matching flags to **Enabled**, click **Relaunch**, and check again. If it still says `unavailable`, this laptop does not meet the hardware limits above.

## Step 7 — On-device and hybrid tests

1. Choose **ONLY_ON_DEVICE**, click **Run**. Expected: `ON_DEVICE`. Try with Wi-Fi switched off too.
2. Choose **PREFER_ON_DEVICE**, click **Run**. Expected: `ON_DEVICE` when the model is ready, `IN_CLOUD` when it is not.
3. Take a screenshot of each result for `docs/evidence/` (with no token visible).

## Notes

- On-device limits from the Firebase docs: single-turn only, text plus JPEG/PNG images, structured output supported, about 9,216 tokens per request. Languages in Chrome: English, Japanese, Spanish, German, French (no Tamil on device).
- Outside `localhost` App Check refuses requests on purpose: no production web provider (reCAPTCHA Enterprise) is set up yet. That is a task before public hosting.
