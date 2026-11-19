// NIRA web: hybrid AI test page.
// All AI calls go through Firebase AI Logic (Gemini Developer API backend). No other AI service.
// Firebase JS SDK 13.2.0 from the official Firebase CDN (on-device inference needs 12.5.0 or later).

import { initializeApp } from "https://www.gstatic.com/firebasejs/13.2.0/firebase-app.js";
import { initializeAppCheck, CustomProvider } from "https://www.gstatic.com/firebasejs/13.2.0/firebase-app-check.js";
import {
  getAI,
  getGenerativeModel,
  GoogleAIBackend,
  InferenceMode,
  InferenceSource,
} from "https://www.gstatic.com/firebasejs/13.2.0/firebase-ai.js";

// Same cloud model as the Android app (HybridInferenceRepository.CLOUD_MODEL_NAME).
// Stable on https://firebase.google.com/docs/ai-logic/models; chosen after real runs on 17 Nov 2026.
const CLOUD_MODEL_NAME = "gemini-3.5-flash-lite";

const $ = (id) => document.getElementById(id);
const counts = { device: 0, cloud: 0 };

function showSetupError(message) {
  const el = $("setup-error");
  el.textContent = message;
  el.hidden = false;
}

// ---------------------------------------------------------------- Firebase set-up

let firebaseConfig;
try {
  // Git-ignored file. Copy firebase-config.example.js and fill it in (see docs/WEB_SETUP.md).
  ({ firebaseConfig } = await import("./firebase-config.js"));
} catch (e) {
  showSetupError("web/firebase-config.js is missing. See docs/WEB_SETUP.md, step 2.");
  throw e;
}

const isLocalhost = ["localhost", "127.0.0.1"].includes(location.hostname);

// App Check before any AI request. Debug provider on localhost only: the SDK prints the
// debug token in the DevTools console; register it in the Firebase console.
// No production web provider is set up yet, so outside localhost requests are refused.
if (isLocalhost) {
  self.FIREBASE_APPCHECK_DEBUG_TOKEN = true;
}
const app = initializeApp(firebaseConfig);
initializeAppCheck(app, {
  provider: new CustomProvider({
    getToken: () => Promise.reject(new Error("No production App Check provider is set up for the web yet.")),
  }),
  isTokenAutoRefreshEnabled: true,
});

const ai = getAI(app, { backend: new GoogleAIBackend() });

const onDeviceParams = {
  createOptions: {
    expectedInputs: [{ type: "text", languages: ["en"] }],
    expectedOutputs: [{ type: "text", languages: ["en"] }],
  },
};

function modelFor(modeName) {
  return getGenerativeModel(ai, {
    mode: InferenceMode[modeName],
    inCloudParams: { model: CLOUD_MODEL_NAME },
    onDeviceParams,
  });
}

// ---------------------------------------------------------------- Chrome on-device status

const chromeVersion = navigator.userAgentData?.brands?.find((b) => b.brand === "Google Chrome")?.version
  ?? (navigator.userAgent.match(/Chrome\/([\d.]+)/)?.[1] ?? "not Chrome");
$("chrome-version").textContent = chromeVersion;

async function refreshAvailability() {
  if (!("LanguageModel" in self)) {
    $("availability").textContent = "not available in this browser (LanguageModel API missing)";
    return null;
  }
  try {
    const state = await LanguageModel.availability({
      expectedInputs: onDeviceParams.createOptions.expectedInputs,
      expectedOutputs: onDeviceParams.createOptions.expectedOutputs,
    });
    $("availability").textContent = state;
    return state;
  } catch (e) {
    $("availability").textContent = "error: " + (e?.message ?? e);
    return null;
  }
}
await refreshAvailability();

// The model download must start from a user action (a click).
$("prepare").addEventListener("click", async () => {
  $("prepare").disabled = true;
  $("download").textContent = "Starting…";
  try {
    const model = modelFor("PREFER_ON_DEVICE");
    await model.initializeDeviceModel((fraction) => {
      $("download").textContent = `Download progress: ${(fraction * 100).toFixed(1)}%`;
    });
    $("download").textContent = "On-device model ready, or download started in Chrome.";
  } catch (e) {
    $("download").textContent = "Could not prepare the model: " + (e?.message ?? e);
  } finally {
    $("prepare").disabled = false;
    await refreshAvailability();
  }
});

// ---------------------------------------------------------------- Run

function sourceName(source) {
  if (source === InferenceSource.ON_DEVICE) return "ON_DEVICE";
  if (source === InferenceSource.IN_CLOUD) return "IN_CLOUD";
  return String(source ?? "not reported");
}

$("run").addEventListener("click", async () => {
  const modeName = document.querySelector('input[name="mode"]:checked').value;
  const prompt = $("prompt").value.trim();
  if (!prompt) return;

  $("run").disabled = true;
  $("run").textContent = "Running…";
  $("result").hidden = false;
  $("r-mode").textContent = modeName;
  $("r-model").textContent = CLOUD_MODEL_NAME;
  $("r-source").textContent = "…";
  $("r-duration").textContent = "…";
  $("r-text").textContent = "";
  $("r-error-row").hidden = true;

  const start = performance.now();
  try {
    const result = await modelFor(modeName).generateContent(prompt);
    const source = sourceName(result.response.inferenceSource);
    $("r-source").textContent = source;
    $("r-text").textContent = result.response.text();
    if (source === "ON_DEVICE") counts.device++;
    if (source === "IN_CLOUD") counts.cloud++;
  } catch (e) {
    $("r-source").textContent = modeName === "ONLY_ON_DEVICE" ? "UNAVAILABLE / ERROR" : "ERROR";
    $("r-error").textContent = isLimitReached(e)
      ? "The free AI limit for NIRA is used up for now. Try again in a few minutes; the daily limit resets at midnight Pacific time."
      : (e?.message ?? String(e));
    $("r-error-row").hidden = false;
  } finally {
    $("r-duration").textContent = `${Math.round(performance.now() - start)} ms`;
    $("c-device").textContent = counts.device;
    $("c-cloud").textContent = counts.cloud;
    $("run").disabled = false;
    $("run").textContent = "Run";
    await refreshAvailability();
  }
});

// ---------------------------------------------------------------- A real NIRA job

// The sample card JSON is exported by the Android app (CardExplanation.exportJson): the facts its
// engine and rules files produced, and the exact prompt it sent. The web sends the same prompt.
let job = null;

async function loadJob() {
  const path = $("job").value;
  $("job-facts").textContent = "";
  $("explain-result").hidden = true;
  try {
    job = await (await fetch(path)).json();
  } catch (e) {
    job = null;
    $("job-saved").textContent = "Could not load " + path + ": " + (e?.message ?? e);
    return;
  }
  $("job-saved").textContent = `${job.generated_by} · saved ${job.saved_at}`;
  for (const f of job.facts) {
    const li = document.createElement("li");
    li.textContent = f;
    $("job-facts").appendChild(li);
  }
}
$("job").addEventListener("change", loadJob);
await loadJob();

/** Same check as the Android app (ReportExplanation.unverifiedNumbers): numbers not in the facts. */
function numbers(text) {
  const cleaned = text.replace(/#[0-9A-Fa-f]{6}/g, " ");
  return [...cleaned.matchAll(/(?<![A-Za-z0-9.])\d+(?:[.,]\d+)?/g)].map((m) => Number(m[0].replace(",", ".")));
}
function unverifiedNumbers(answer, facts) {
  const known = numbers(facts.join("\n"));
  const same = (a, b) => Math.abs(a - b) < 1e-9;
  return [...new Set(numbers(answer).filter((n) =>
    !known.some((k) => same(n, k) || same(n, Math.round(k)) || same(n, Math.round(k * 10) / 10))))];
}

$("explain").addEventListener("click", async () => {
  if (!job) return;
  $("explain").disabled = true;
  $("explain-result").hidden = false;
  $("e-source").textContent = "…";
  $("e-duration").textContent = "…";
  $("e-text").textContent = "";
  $("e-check").textContent = "";
  const start = performance.now();
  try {
    const result = await modelFor("PREFER_ON_DEVICE").generateContent(job.prompt);
    const source = sourceName(result.response.inferenceSource);
    if (source === "ON_DEVICE") counts.device++;
    if (source === "IN_CLOUD") counts.cloud++;
    $("e-source").textContent = source;
    const text = result.response.text();
    const bad = unverifiedNumbers(text, job.facts);
    if (bad.length) {
      $("e-text").textContent = "";
      $("e-check").textContent = `Not shown: the answer has numbers that are not on the card (${bad.join(", ")}).`;
    } else {
      $("e-text").textContent = text;
      $("e-check").textContent = "Every number in the answer is on the card.";
    }
  } catch (e) {
    $("e-source").textContent = "ERROR";
    $("e-text").textContent = isLimitReached(e)
      ? "The free AI limit for NIRA is used up for now. Try again in a few minutes; the daily limit resets at midnight Pacific time."
      : (e?.message ?? String(e));
  } finally {
    $("e-duration").textContent = `${Math.round(performance.now() - start)} ms`;
    $("c-device").textContent = counts.device;
    $("c-cloud").textContent = counts.cloud;
    $("explain").disabled = false;
    await refreshAvailability();
  }
});

/** HTTP 429 / RESOURCE_EXHAUSTED: the project's free AI limit is used up (limits are per project). */
function isLimitReached(e) {
  const text = String(e?.message ?? e).toLowerCase();
  return e?.status === 429 || e?.customErrorData?.status === 429 ||
    text.includes("resource_exhausted") || text.includes("quota") || /\b429\b/.test(text) ||
    text.includes("rate limit") || text.includes("too many requests");
}
