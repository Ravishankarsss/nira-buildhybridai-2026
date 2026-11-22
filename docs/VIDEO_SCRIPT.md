# NIRA demo video — script (maximum 3:00)

Rules from the hackathon: public on YouTube, at most 3 minutes (only the first 3 minutes are judged), English narration or English subtitles, show the project working on its platforms. Show only real screens. No made-up AI answers, no app screens from image generators.

Record: phone screen with the Android screen recorder (Quick settings → Screen recorder, sound off) and the laptop with Windows Game Bar (Win + G) or OBS. Narration can be recorded afterwards over the clips.

Before recording:
- Phone: notifications off (Do Not Disturb), battery and time visible is fine, no WhatsApp or private photos in the picker (put the lion, hummingbird and wolf files at the top of the gallery).
- Laptop: Chrome with Gemini Nano ready (chrome://on-device-internals shows the model), only the NIRA tab open, bookmarks bar hidden.
- Open each job once before recording, so reopening is instant in the session.

| Time | Screen | What to do | Narration (simple English) |
|---|---|---|---|
| 0:00–0:15 | Your face or the printing table, then the NIRA Home | — | "Before a garment sample is printed, someone must decide: which method, how many screens, white base or not, and what it costs. Today that is guesswork and a wrong strike-off. I am Ravi, a print engineer, and I built NIRA." |
| 0:15–0:40 | Phone: Artwork file → lion → navy → Auto-detect → job page | Tap "Drop it (not ink)", answer navy, cotton, soft, 500 | "NIRA measures the artwork on the phone: fifteen print colours, the background, the print area. I answer four questions." |
| 0:40–1:05 | Job header and Decision tab | Show "Artwork on navy cotton", Discharge + digital print, 1 screen, ₹50.40, verified by Ravi; scroll the decision card lines with their sources | "The decision comes from my rules file, not from AI. Every line says where it came from and whether I checked it on the floor." |
| 1:05–1:20 | See it on your garment + Cost tab | Show with and without white base; Cost-Down | "It shows the print on the garment, with and without a white base, and the cost per piece from the factory's own rates." |
| 1:20–1:50 | Ask NIRA (phone) | Ask by voice: "Make the navy ink royal blue". Show the answer, the source "In the cloud", the check line, and Undo | "Gemini, through Firebase AI Logic, explains and acts. It can only use the job's facts, and NIRA checks every number before I see it. Here it ran in the cloud, because this phone has no on-device model — NIRA always shows where it ran." |
| 1:50–2:20 | Laptop, Chrome, the web page "Try it live" | Press "Explain this card" with Chrome's Gemini Nano ready: show **On this device** and the time; then the Hybrid AI counters | "On a laptop in Chrome, the same job is explained by Gemini Nano on this computer — on-device first, cloud only when needed. App Check protects every call." |
| 2:20–2:40 | Web: Translate, or the phone Print Decision Sheet in Tamil | Translate to Tamil; show the number check | "The sheet goes to the floor in their language, and the numbers are checked line by line." |
| 2:40–2:55 | Phone: Home → AI activity | Show the list: on device / in cloud / held back | "Every AI request is listed. Nothing leaves the phone in Confidential mode." |
| 2:55–3:00 | NIRA logo and the web address | — | "NIRA. Artwork in, print decision out." |

Keep it under 2:55 to be safe. Check the final file length before uploading.

After recording:
1. Upload to YouTube as **Public** (not Unlisted, not Private). Title: "NIRA — Textile Intelligence | #BuildHybridAI 2026". Add English subtitles (YouTube auto-captions, then correct the print words).
2. Paste the YouTube link in Devpost and send it to Claude to add a video box to the web page.
