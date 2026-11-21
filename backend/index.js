require("dotenv").config();
const express = require("express");
const cors = require("cors");
const admin = require("firebase-admin");

if (!admin.apps.length) {
  admin.initializeApp({
    projectId: process.env.FIREBASE_PROJECT_ID || undefined,
  });
}

const app = express();
app.use(cors({ origin: false }));
app.use(express.json({ limit: "12mb" }));

/**
 * Verifies the Firebase Auth ID token sent by the Android client
 * in Authorization: Bearer <idToken> before issuing TURN credentials or uploading images.
 */
async function requireFirebaseAuth(req, res, next) {
  const authHeader = req.headers.authorization || "";
  if (!authHeader.startsWith("Bearer ")) {
    return res.status(401).json({ error: "Missing Bearer authorization token." });
  }
  const idToken = authHeader.slice(7).trim();
  if (!idToken) {
    return res.status(401).json({ error: "Empty authorization token." });
  }
  try {
    const decoded = await admin.auth().verifyIdToken(idToken);
    req.user = decoded;
    return next();
  } catch (err) {
    console.error("Token verification failed:", err.message);
    return res.status(403).json({ error: "Invalid or expired Firebase ID token." });
  }
}

app.get("/health", (_req, res) => {
  res.json({ status: "ok", service: "connect-live-backend" });
});

app.get("/api/turn-credentials", requireFirebaseAuth, async (req, res) => {
  const domain = process.env.METERED_DOMAIN;
  const apiKey = process.env.METERED_API_KEY;
  const region = process.env.METERED_REGION || "";

  if (!domain || !apiKey) {
    return res.status(503).json({
      error: "TURN server is not configured on the backend.",
    });
  }

  try {
    const url = new URL(`https://${domain}/api/v1/turn/credentials`);
    url.searchParams.set("apiKey", apiKey);
    if (region && region !== "global") {
      url.searchParams.set("region", region);
    }

    const response = await fetch(url.toString(), {
      method: "GET",
      headers: { Accept: "application/json" },
    });

    if (!response.ok) {
      const text = await response.text();
      console.error("Metered API error:", response.status, text);
      return res.status(502).json({ error: "Upstream TURN provider error." });
    }

    const iceServers = await response.json();
    if (!Array.isArray(iceServers)) {
      return res.status(502).json({ error: "Unexpected TURN response format." });
    }

    return res.json({
      iceServers,
      uid: req.user.uid,
      fetchedAt: new Date().toISOString(),
    });
  } catch (err) {
    console.error("Failed to fetch Metered TURN credentials:", err);
    return res.status(500).json({ error: "Failed to retrieve TURN credentials." });
  }
});

app.post("/api/upload-image", requireFirebaseAuth, async (req, res) => {
  const apiKey = process.env.IMGBB_API_KEY;
  if (!apiKey) {
    return res.status(503).json({ error: "ImgBB API key is not configured on the backend." });
  }

  const imageBase64 = typeof req.body?.imageBase64 === "string" ? req.body.imageBase64.trim() : "";
  const name = typeof req.body?.name === "string" ? req.body.name.trim().slice(0, 64) : "connect_live_upload";
  if (!imageBase64) {
    return res.status(400).json({ error: "Missing base64 image payload." });
  }

  try {
    const body = new URLSearchParams();
    body.set("key", apiKey);
    body.set("image", imageBase64);
    if (name) body.set("name", name);

    const response = await fetch("https://api.imgbb.com/1/upload", {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: body.toString(),
    });
    const payload = await response.json();
    if (!response.ok || !payload?.success) {
      return res.status(502).json({ error: payload?.error?.message || "ImgBB upload failed." });
    }
    return res.json({
      url: payload.data?.display_url || payload.data?.url,
      thumbUrl: payload.data?.thumb?.url || payload.data?.display_url || payload.data?.url,
    });
  } catch (err) {
    console.error("ImgBB proxy upload error:", err);
    return res.status(500).json({ error: "Failed to upload image to ImgBB." });
  }
});

const port = process.env.PORT || 3001;
if (require.main === module) {
  app.listen(port, () => {
    console.log(`Connect Live backend listening on port ${port}`);
  });
}

module.exports = app;
