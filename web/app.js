// NIRA web: hybrid AI test page.
// All AI calls go through Firebase AI Logic (Gemini Developer API backend). No other AI service.
// Firebase JS SDK 13.2.0 from the official Firebase CDN (on-device inference needs 12.5.0 or later).

import { initializeApp } from "https://www.gstatic.com/firebasejs/13.2.0/firebase-app.js";
import { initializeAppCheck, CustomProvider, ReCaptchaEnterpriseProvider } from "https://www.gstatic.com/firebasejs/13.2.0/firebase-app-check.js";
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
let recaptchaEnterpriseSiteKey = "";
try {
  // Git-ignored file. Copy firebase-config.example.js and fill it in (see docs/WEB_SETUP.md).
  ({ firebaseConfig, recaptchaEnterpriseSiteKey = "" } = await import("./firebase-config.js"));
} catch (e) {
  showSetupError("web/firebase-config.js is missing. See docs/WEB_SETUP.md, step 2.");
  throw e;
}

const isLocalhost = ["localhost", "127.0.0.1"].includes(location.hostname);

// App Check before any AI request.
// - localhost: the debug provider; the SDK prints the debug token in the DevTools console and it
//   is registered in the Firebase console.
// - the hosted page: reCAPTCHA Enterprise (invisible, score based). Without a site key in
//   firebase-config.js, requests are refused rather than sent unprotected.
if (isLocalhost) {
  self.FIREBASE_APPCHECK_DEBUG_TOKEN = true;
}
const app = initializeApp(firebaseConfig);
initializeAppCheck(app, {
  provider: (!isLocalhost && recaptchaEnterpriseSiteKey)
    ? new ReCaptchaEnterpriseProvider(recaptchaEnterpriseSiteKey)
    : new CustomProvider({
        getToken: () => Promise.reject(new Error("No App Check provider: run on localhost, or add the reCAPTCHA Enterprise site key.")),
      }),
  isTokenAutoRefreshEnabled: true,
});
if (!isLocalhost && !recaptchaEnterpriseSiteKey) {
  showSetupError("This page needs the reCAPTCHA Enterprise site key in web/firebase-config.js (see docs/WEB_SETUP.md).");
}

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

// ---------------------------------------------------------------- Small helpers

function el(tag, cls, text) {
  const e = document.createElement(tag);
  if (cls) e.className = cls;
  if (text != null) e.textContent = text;
  return e;
}

function sourceName(source) {
  if (source === InferenceSource.ON_DEVICE) return "ON_DEVICE";
  if (source === InferenceSource.IN_CLOUD) return "IN_CLOUD";
  return String(source ?? "not reported");
}

function sourceLabel(source) {
  if (source === "ON_DEVICE") return "💻 On this device";
  if (source === "IN_CLOUD") return "☁️ In the cloud";
  return "⚠ " + source;
}

const LIMIT_TEXT = "The free AI limit for NIRA is used up for now. Try again in a few minutes; the daily limit resets at midnight Pacific time.";

/** HTTP 429 / RESOURCE_EXHAUSTED: the project's free AI limit is used up (limits are per project). */
function isLimitReached(e) {
  const text = String(e?.message ?? e).toLowerCase();
  return e?.status === 429 || e?.customErrorData?.status === 429 ||
    text.includes("resource_exhausted") || text.includes("quota") || /\b429\b/.test(text) ||
    text.includes("rate limit") || text.includes("too many requests");
}

$("model-name").textContent = CLOUD_MODEL_NAME;

// ---------------------------------------------------------------- Hybrid AI on this page

const activity = [];
let held = 0;

/** Every AI request on this page: feature, source reported by the SDK, time, and what the checks did. */
function record(feature, source, ms, outcome) {
  if (source === "ON_DEVICE") counts.device++;
  if (source === "IN_CLOUD") counts.cloud++;
  if (outcome === "held") held++;
  activity.unshift({ feature, source, ms, outcome, at: new Date() });
  $("c-device").textContent = counts.device;
  $("c-cloud").textContent = counts.cloud;
  $("c-held").textContent = held;
  const list = $("activity");
  list.textContent = "";
  for (const a of activity.slice(0, 20)) {
    const li = el("li");
    li.append(el("span", "", sourceLabel(a.source)));
    li.append(el("b", "", " " + a.feature));
    li.append(el("span", "muted", ` · ${a.ms} ms · ${a.at.toLocaleTimeString()}`));
    const tag = { passed: "✓ checked", held: "held back", error: "failed" }[a.outcome];
    li.append(el("span", "pill " + a.outcome, tag));
    list.append(li);
  }
}

/** One AI request through Firebase AI Logic, timed. Returns { text, source, ms } or throws. */
async function generate(modeName, prompt) {
  const start = performance.now();
  try {
    const result = await modelFor(modeName).generateContent(prompt);
    return { text: result.response.text(), source: sourceName(result.response.inferenceSource), ms: Math.round(performance.now() - start) };
  } catch (e) {
    e.ms = Math.round(performance.now() - start);
    throw e;
  }
}

// ---------------------------------------------------------------- Chrome on-device status

const chromeVersion = navigator.userAgentData?.brands?.find((b) => b.brand === "Google Chrome")?.version
  ?? (navigator.userAgent.match(/Chrome\/([\d.]+)/)?.[1] ?? "not Chrome");
$("chrome-version").textContent = chromeVersion;

async function refreshAvailability() {
  if (!("LanguageModel" in self)) {
    $("availability").textContent = "not in this browser (cloud answers)";
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

// ---------------------------------------------------------------- Checks (the same as the Android app)

/** Numbers in a text, ignoring colour codes; grouped amounts (1,92,000 / 192,000) read as one number. */
function numbers(text) {
  const cleaned = text.replace(/#[0-9A-Fa-f]{6}/g, " ").replace(/(?<=\d),(?=\d{2,3}(?:,\d{2,3})*(?:\.\d+)?(?!\d))/g, "");
  return [...cleaned.matchAll(/(?<![A-Za-z0-9.])\d+(?:[.,]\d+)?/g)].map((m) => Number(m[0].replace(",", ".")));
}
function unverifiedNumbers(answer, facts) {
  const known = numbers(facts.join("\n"));
  const same = (a, b) => Math.abs(a - b) < 1e-9;
  return [...new Set(numbers(answer).filter((n) =>
    !known.some((k) => same(n, k) || same(n, Math.round(k)) || same(n, Math.round(k * 10) / 10))))];
}
function missingMethodWords(answer, methodName) {
  if (!methodName) return [];
  const words = [...new Set((methodName.split("(")[0].match(/[A-Za-z]+/g) ?? [])
    .map((w) => w.toLowerCase()).filter((w) => w.length >= 4 && w !== "print"))];
  const text = answer.toLowerCase();
  return words.filter((w) => !text.includes(w));
}
const hasCode = (t) => /#[0-9A-Fa-f]{6}/.test(t);
const sameNumbers = (a, b) => JSON.stringify(numbers(a).sort((x, y) => x - y)) === JSON.stringify(numbers(b).sort((x, y) => x - y));

// ---------------------------------------------------------------- A real NIRA job, as a decision card

// The sample card JSON is exported by the Android app (CardExplanation.exportJson): the facts its
// engine and rules files produced, and the exact prompt it sent for "Explain this card".
let job = null;

/** Splits "Label: value" facts into the parts of the card. */
function cardParts(facts) {
  const parts = { brief: [], notes: [], method: null, others: [], rows: [], steps: [], risks: [], disclaimer: null };
  for (const f of facts) {
    const i = f.indexOf(":");
    const label = i > 0 ? f.slice(0, i) : "";
    const value = i > 0 ? f.slice(i + 1).trim() : f;
    if (label === "Brief") parts.brief = value.replace(/\.$/, "").split(";").map((s) => s.trim());
    else if (label === "Contrast warning" || label === "Print vs fabric") parts.notes.push(value);
    else if (label === "Method") parts.method = value;
    else if (label === "Second choice" || label === "For small quantities") parts.others.push([label, value]);
    else if (label === "How it is printed") parts.steps = value.split(/(?<=\.)\s+/);
    else if (label === "Risk") parts.risks.push(value);
    else if (label) parts.rows.push([label, value]);
    else parts.disclaimer = f;
  }
  return parts;
}

function renderDecision() {
  const d = $("decision");
  d.textContent = "";
  if (!job) return;
  const p = cardParts(job.facts);
  const brief = el("div", "chips static");
  p.brief.forEach((b) => brief.append(el("span", "chip", b)));
  d.append(brief);
  p.notes.forEach((n) => d.append(el("p", "note", "⚠ " + n)));
  if (p.method) {
    const m = el("div", "method");
    m.append(el("span", "label", "Method"));
    const name = el("p", "method-name", job.method_name ?? p.method.split("(")[0]);
    m.append(name);
    m.append(el("p", p.method.includes("verified by Ravi") ? "ok small" : "muted small", p.method.includes("verified by Ravi") ? "✓ verified by Ravi on the floor · from the rules file" : "from the rules file · not floor-verified"));
    d.append(m);
  }
  for (const [label, value] of p.rows) {
    const r = el("div", "row");
    r.append(el("span", "label", label));
    r.append(el("span", "value", value));
    d.append(r);
  }
  if (p.steps.length) {
    d.append(el("p", "label", "How it is printed"));
    const ol = el("ol", "steps");
    p.steps.forEach((s) => ol.append(el("li", "", s)));
    d.append(ol);
  }
  if (p.risks.length) {
    d.append(el("p", "label", "Risks (from the rules file)"));
    const ul = el("ul", "plain");
    p.risks.forEach((r) => ul.append(el("li", "", "• " + r)));
    d.append(ul);
  }
  if (p.others.length) {
    const det = el("details", "others");
    det.append(el("summary", "", `Other methods (${p.others.length})`));
    p.others.forEach(([l, v]) => det.append(el("p", "small muted", `${l}: ${v}`)));
    d.append(det);
  }
  if (p.disclaimer) d.append(el("p", "muted small", p.disclaimer));
}

async function loadJob() {
  const path = $("job-select").value;
  $("job-facts").textContent = "";
  $("explain-result").hidden = true;
  $("translate-result").hidden = true;
  chat.length = 0;
  renderChat();
  try {
    job = await (await fetch(path)).json();
  } catch (e) {
    job = null;
    $("job-saved").textContent = "Could not load " + path + ": " + (e?.message ?? e);
    renderDecision();
    return;
  }
  $("job-saved").textContent = `${job.generated_by} · saved ${job.saved_at}`;
  for (const f of job.facts) $("job-facts").append(el("li", "", f));
  renderDecision();
}

// ---------------------------------------------------------------- Tabs

document.querySelectorAll(".tab").forEach((t) => t.addEventListener("click", () => {
  document.querySelectorAll(".tab").forEach((x) => x.classList.toggle("on", x === t));
  for (const name of ["explain", "ask", "translate"]) $("pane-" + name).hidden = name !== t.dataset.tab;
}));

// ---------------------------------------------------------------- Explain

$("explain").addEventListener("click", async () => {
  if (!job) return;
  $("explain").disabled = true;
  $("explain-result").hidden = false;
  $("e-src").textContent = "…";
  $("e-text").textContent = "";
  $("e-check").textContent = "";
  try {
    const r = await generate("PREFER_ON_DEVICE", job.prompt);
    $("e-src").textContent = `${sourceLabel(r.source)} · ${r.ms} ms`;
    const bad = unverifiedNumbers(r.text, job.facts);
    const missing = missingMethodWords(r.text, job.method_name);
    let outcome = "held";
    if (bad.length) $("e-check").textContent = `Held back: the answer has numbers that are not on the card (${bad.join(", ")}).`;
    else if (hasCode(r.text)) $("e-check").textContent = "Held back: the answer has a colour code; the card gives colour names only.";
    else if (missing.length) $("e-check").textContent = `Held back: the answer did not name the card's method (missing: ${missing.join(", ")}).`;
    else {
      outcome = "passed";
      $("e-text").textContent = r.text;
      $("e-check").textContent = "✓ Checked: every number is on the card, the method is named, no colour code.";
    }
    record("Explain this card", r.source, r.ms, outcome);
  } catch (e) {
    $("e-src").textContent = "⚠ Not answered";
    $("e-text").textContent = isLimitReached(e) ? LIMIT_TEXT : (e?.message ?? String(e));
    record("Explain this card", "ERROR", e.ms ?? 0, "error");
  } finally {
    $("explain").disabled = false;
  }
});

// ---------------------------------------------------------------- Ask NIRA

const ASK_TEMPLATE = await (await fetch("prompts/ask_web_v1.txt")).text();
const chat = [];
const SUGGESTIONS = ["Why this method?", "What can go wrong?", "What must I check before bulk?", "What does it cost for 1000 pieces?"];

function renderChat() {
  const box = $("chat");
  box.textContent = "";
  for (const m of chat) {
    box.append(el("p", "q", m.q));
    const a = el("div", "a");
    if (m.pending) a.append(el("p", "muted", "NIRA is thinking…"));
    else {
      a.append(el("p", "src", m.src));
      if (m.text) a.append(el("p", "", m.text));
      if (m.check) a.append(el("p", "check", m.check));
    }
    box.append(a);
  }
  const chips = $("chips");
  chips.textContent = "";
  if (!chat.length) SUGGESTIONS.forEach((s) => {
    const b = el("button", "chip", s);
    b.type = "button";
    b.addEventListener("click", () => ask(s));
    chips.append(b);
  });
}

async function ask(question) {
  const q = question.trim();
  if (!q || !job || chat.some((m) => m.pending)) return;
  const history = chat.filter((m) => m.ok).slice(-3).map((m) => `Q: ${m.q}\nA: ${m.text}`).join("\n") || "(none)";
  const msg = { q, pending: true };
  chat.push(msg);
  renderChat();
  const prompt = ASK_TEMPLATE
    .replace("{facts}", job.facts.map((f) => "- " + f).join("\n"))
    .replace("{conversation}", history)
    .replace("{question}", q);
  try {
    const r = await generate("PREFER_ON_DEVICE", prompt);
    const bad = unverifiedNumbers(r.text, [...job.facts, q]);
    msg.src = `${sourceLabel(r.source)} · ${r.ms} ms`;
    if (bad.length) {
      msg.check = `Held back: numbers not in this job or your question (${bad.join(", ")}). Change the job in the NIRA app and its engine recalculates.`;
      record("Ask NIRA", r.source, r.ms, "held");
    } else if (hasCode(r.text) && !hasCode(q)) {
      msg.check = "Held back: the answer added a colour code that is not in this job.";
      record("Ask NIRA", r.source, r.ms, "held");
    } else {
      msg.text = r.text;
      msg.ok = true;
      msg.check = "✓ Checked against the job's facts.";
      record("Ask NIRA", r.source, r.ms, "passed");
    }
  } catch (e) {
    msg.src = "⚠ Not answered";
    msg.check = isLimitReached(e) ? LIMIT_TEXT : (e?.message ?? String(e));
    record("Ask NIRA", "ERROR", e.ms ?? 0, "error");
  } finally {
    msg.pending = false;
    renderChat();
  }
}

$("ask-form").addEventListener("submit", (ev) => {
  ev.preventDefault();
  const q = $("ask-input").value;
  $("ask-input").value = "";
  ask(q);
});

// ---------------------------------------------------------------- Translate the card

const TRANSLATE_TEMPLATE = await (await fetch("prompts/translate_web_v1.txt")).text();

$("translate").addEventListener("click", async () => {
  if (!job) return;
  const language = $("lang").value;
  const texts = job.facts;
  $("translate").disabled = true;
  $("translate-result").hidden = false;
  $("t-src").textContent = "…";
  $("t-summary").textContent = "";
  $("t-lines").textContent = "";
  try {
    // Chrome's built-in model writes few languages, so translation asks the cloud first.
    const r = await generate("PREFER_IN_CLOUD", TRANSLATE_TEMPLATE.replaceAll("{language}", language).replace("{texts}", JSON.stringify(texts)));
    $("t-src").textContent = `${sourceLabel(r.source)} · ${r.ms} ms`;
    let arr = null;
    try { arr = JSON.parse(r.text.slice(r.text.indexOf("["), r.text.lastIndexOf("]") + 1)); } catch { arr = null; }
    if (!Array.isArray(arr) || arr.length !== texts.length) {
      $("t-summary").textContent = "The AI reply could not be used; the card stays in English.";
      record("Translate the card", r.source, r.ms, "held");
      return;
    }
    let kept = 0;
    texts.forEach((src, i) => {
      const t = String(arr[i] ?? "").trim();
      const ok = t && sameNumbers(src, t) && !(hasCode(t) && !hasCode(src));
      if (!ok) kept++;
      $("t-lines").append(el("li", ok ? "" : "kept", ok ? t : src + "  (kept in English: number check)"));
    });
    $("t-summary").textContent = `✓ ${texts.length - kept} of ${texts.length} lines translated; ${kept} kept in English (number check).`;
    record("Translate the card", r.source, r.ms, kept ? "held" : "passed");
  } catch (e) {
    $("t-src").textContent = "⚠ Not answered";
    $("t-summary").textContent = isLimitReached(e) ? LIMIT_TEXT : (e?.message ?? String(e));
    record("Translate the card", "ERROR", e.ms ?? 0, "error");
  } finally {
    $("translate").disabled = false;
  }
});

$("job-select").addEventListener("change", loadJob);
await loadJob();

// ---------------------------------------------------------------- Developer: free prompt in any mode

$("run").addEventListener("click", async () => {
  const modeName = document.querySelector('input[name="mode"]:checked').value;
  const prompt = $("prompt").value.trim();
  if (!prompt) return;
  $("run").disabled = true;
  $("result").hidden = false;
  $("r-mode").textContent = modeName;
  $("r-model").textContent = CLOUD_MODEL_NAME;
  $("r-source").textContent = "…";
  $("r-duration").textContent = "…";
  $("r-text").textContent = "";
  $("r-error-row").hidden = true;
  try {
    const r = await generate(modeName, prompt);
    $("r-source").textContent = r.source;
    $("r-text").textContent = r.text;
    $("r-duration").textContent = `${r.ms} ms`;
    record("Developer test", r.source, r.ms, "passed");
  } catch (e) {
    $("r-source").textContent = modeName === "ONLY_ON_DEVICE" ? "UNAVAILABLE / ERROR" : "ERROR";
    $("r-error").textContent = isLimitReached(e) ? LIMIT_TEXT : (e?.message ?? String(e));
    $("r-error-row").hidden = false;
    $("r-duration").textContent = `${e.ms ?? 0} ms`;
    record("Developer test", "ERROR", e.ms ?? 0, "error");
  } finally {
    $("run").disabled = false;
    await refreshAvailability();
  }
});
