// Copy this file to firebase-config.js (same folder) and paste the values from
// Firebase console > Project settings > Your apps > NIRA Web > SDK setup and configuration > Config.
// firebase-config.js is git-ignored. Do not commit it.
export const firebaseConfig = {
  apiKey: "",
  authDomain: "nira-90d39.firebaseapp.com",
  projectId: "nira-90d39",
  storageBucket: "",
  messagingSenderId: "",
  appId: "",
};

// App Check for the hosted page: the reCAPTCHA Enterprise site key (Google Cloud console >
// Security > reCAPTCHA Enterprise > Keys). A site key is public; it only works on the domains
// listed for it. Leave empty for localhost, which uses the debug provider.
export const recaptchaEnterpriseSiteKey = "";
