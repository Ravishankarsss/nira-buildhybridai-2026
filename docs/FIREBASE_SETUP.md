# NIRA — Firebase setup (Stage B)

Last updated: 16 November 2026.
Firebase project: `nira-90d39`. Android app: `com.mos.nira`.

## 1. What is in the app

| Item | Value | Where |
| :--- | :--- | :--- |
| Google services Gradle plugin | 4.6.0 | `gradle/libs.versions.toml` |
| Firebase BoM | 35.2.0 | `gradle/libs.versions.toml` |
| Firebase AI Logic | `firebase-ai` (version from the BoM, 18.2.0) | `app/build.gradle.kts` |
| App Check debug provider | `firebase-appcheck-debug` (version from the BoM) | `app/build.gradle.kts` |
| On-device library | `firebase-ai-ondevice` 16.0.0-beta08 — **listed but switched off**, see section 6 | `app/build.gradle.kts` |
| App Check start-up | `NiraApplication.kt`, registered in the manifest | runs before any AI request |
| AI calls | `core/ai/HybridInferenceRepository.kt` | the only class that calls AI |
| Cloud model | `gemini-3.8-flash` (constant `CLOUD_MODEL_NAME`) | checked on the Firebase models page, 16 Nov 2026 |
| Test button | Settings → "DEVELOPER TEST (DEBUG BUILD ONLY)" → Test AI | temporary |

Versions were checked on 16 Nov 2026 against:
- https://firebase.google.com/docs/android/setup
- https://firebase.google.com/docs/ai-logic/hybrid/android/get-started
- https://firebase.google.com/docs/app-check/android/debug-provider
- https://firebase.google.com/docs/ai-logic/models

## 2. Files that must never be committed

- `app/google-services.json` (already in `.gitignore`)
- The App Check debug token (do not paste it into any file in this project, a screenshot, the video or Devpost)
- `local.properties`

## 3a. Find the App Check debug token inside the app (use this on the vivo V60e)

The vivo V60e hides debug-level Logcat lines, so the Logcat method in section 3 shows nothing on that phone. Debug builds show the token in the app instead.

1. Install and open a debug build of NIRA (green **Run** button in Android Studio).
2. Tap **Settings** and scroll to the bottom, to **DEVELOPER TEST (DEBUG BUILD ONLY)**.
3. If **App Check debug token** says "Not created yet", tap **Run** once and wait for it to finish. The request will fail the first time; that is expected.
4. Tap **Copy** next to the token.
5. Register it in the Firebase console (section 4), wait one minute, then tap **Run** again.

How it works: the token is read from where the `firebase-appcheck-debug` 20.1.0 library stores it (SharedPreferences file `com.google.firebase.appcheck.debug.store.<persistence key>`, key `com.google.firebase.appcheck.debug.DEBUG_SECRET`), checked in the library's own classes. The app never logs it, never writes it to a file, and the card does not exist in release builds (`BuildConfig.DEBUG`). Re-check the storage names if the Firebase BoM version changes.

Uninstalling the app or clearing its data creates a new token, which must be registered again.

## 3. Find the App Check debug token in Logcat

The token is created the first time a **debug build** runs on a device. Each phone or emulator has its own token.

1. Connect the vivo V60e with the USB cable and allow USB debugging on the phone.
2. In Android Studio, pick the phone in the device box at the top, then press the green **Run** button (▶). Wait until NIRA opens on the phone.
3. In Android Studio open **View → Tool Windows → Logcat**.
4. In the Logcat search box at the top, type exactly:
   `tag:DebugAppCheckProvider`
5. On the phone, in NIRA, tap **Settings**, scroll to the bottom, and tap **Test AI → Run**. (The token is printed when the app first asks App Check for a token.)
6. Look in Logcat for a line like this:
   ```
   D DebugAppCheckProvider: Enter this debug secret into the allow list in the Firebase Console for your project: 123a4567-b89c-12d3-e456-789012345678
   ```
7. Copy only the code at the end (the part that looks like `123a4567-b89c-12d3-e456-789012345678`).

If the line does not appear: clear the Logcat window, close NIRA fully on the phone, open it again and repeat step 5. Make sure the Logcat level filter is not set to "Warn" or "Error" (the line is level Debug).

## 4. Register the token in the Firebase console

1. Open https://console.firebase.google.com and choose the project **nira-90d39**.
2. In the left menu open **Security → App Check**.
3. Click the **Apps** tab.
4. Find the Android app **com.mos.nira**. Click the three dots (⋮) at the right end of its row.
5. Click **Manage debug tokens**.
6. Click **Add debug token**.
7. Name: type something like `vivo V60e - Ravi`. Value: paste the code from Logcat.
8. Click **Save**, then **Done**.

It can take a minute or two before the token is accepted.

## 5. Check that it works

1. On the phone open NIRA → **Settings** → scroll down → **Test AI → Run**.
2. Expected on the vivo V60e:
   - **Inference source (reported by the SDK):** `IN_CLOUD`
   - **Response:** one sentence about a screen printing underbase
   - **Duration:** some number of ms
3. If it shows `ERROR`, read the **Error** line:
   - Text about App Check or "attestation": the token is not registered yet, or it was copied wrongly. Repeat sections 3 and 4.
   - Text about the network: check Wi-Fi or mobile data.
   - Text about the model name or "not found": tell Claude Code; the model name must be checked again on the Firebase models page.
4. Take a screenshot of the result and keep it in `docs/evidence/` (with the token not visible).

App Check enforcement for Firebase AI Logic is switched on in the console under **Security → App Check → APIs → Firebase AI Logic → Enforce**. With enforcement on, a request without a registered token fails; that is the correct behaviour.

## 6. On-device inference on Android — not active yet

The hybrid API is in use (`HybridInferenceRepository` asks for `PREFER_ON_DEVICE`), but the on-device library is switched off:

- `firebase-ai-ondevice` 16.0.0-beta08 needs **Kotlin 2.4.0**. This project uses Kotlin 2.2.10, and the build fails with the library added.
- Without the library every request runs in the cloud, and the app shows the real source, `IN_CLOUD`.
- To switch it on: upgrade Kotlin to 2.4.0 (Ravi's decision), then remove the `//` in front of `implementation(libs.firebase.ai.ondevice)` in `app/build.gradle.kts`.
- The vivo V60e is not expected to run Gemini Nano. This must be checked with the real SDK status call once the library is on; do not assume.
- On-device limits from the Firebase docs: about 4,000 input tokens, output above about 256 tokens discouraged, English and Korean validated, app must be in the foreground, Android 8.0 (API 26) or higher.

## 7. Release builds

Release builds install no App Check provider yet. Play Integrity is the planned production provider and is a future task. Do not ship the debug provider or a debug token in a release.
