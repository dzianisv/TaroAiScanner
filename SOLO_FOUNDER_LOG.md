# 🌌 Mystic Tarot — Solo Founder's Strategic Log
This document tracks the strategic decisions, architectural refactoring, customer support mitigations, and growth/virality iterations executed to stabilize, scale, and monetize Mystic Tarot toward **$10k MRR**.

---

## 📅 Log Entry: July 21, 2026
**Founder Action:** Core Application Stabilization, Virality Refactoring, and Self-Service Offline fallback implementation.

### 1. 🛑 Problem: Request Stuck on "Consulting the Stars"
- **Symptom:** Customers on the virtual draw screen reported the app freezing or hanging indefinitely on the "Consulting the Stars" screen.
- **Root Cause Analysis:** 
  1. The proxy URL was configured to run through secure endpoints, but in standard local runs or when proxy services are slow, the underlying network socket blocks.
  2. The OkHttpClient connect, read, and write timeouts were globally hardcoded to **60 seconds**.
  3. While `withTimeoutOrNull(10000)` was wrapping the coroutine execution, OkHttp's blocking `newCall().execute()` is **non-cooperative with coroutine cancellation**. The thread stayed blocked for up to a full minute, causing a perceived "infinite hang" to the user.
- **Sentry/Support Ticket Impact:** Critical support tickets on `support.agentlabs.cc` regarding screen freezes and cosmic timeouts.

### 2. 🧠 Reasoning & Strategy
To reach $10k MRR, the product must be **bulletproof** and work seamlessly regardless of API key availability or network speed. We cannot afford bad App Store reviews from timed-out readings.
- **Decision A (Multi-Client Timeout Partitioning):**
  - Create separate OkHttp clients inside `GeminiTarotService.kt` tuned for specific workloads.
  - **Text Client (`textClient`):** 5s connect, 8s read, 5s write. Virtual draw and chatbot queries are lightweight text tasks. They should succeed almost instantly or time out extremely fast to trigger fallbacks.
  - **Scan Client (`scanClient`):** 8s connect, 15s read, 12s write. Image uploads require slightly more breathing room, but should never hang for a minute.
- **Decision B (Offline / Local Mode Toggle):**
  - Save an explicit `offlineMode` Boolean in Room's `TarotSettingsEntity`.
  - Let seekers toggle "Offline Mode" in the dashboard's settings. If enabled, the app completely bypasses the network and delivers high-fidelity simulated readings instantly (using tailored mappings from `TarotDeck.kt` which look and feel identical to Gemini responses). This is a game-changer for offline users and completely eliminates "stuck" complaints.
- **Decision C (Custom Gemini API Key & Proxy Setup):**
  - Give users direct inputs for **Custom Gemini API Key** and **Custom Proxy URL** in the settings dialog. This transforms the app from a locked prototype into an enterprise-grade self-hosted software package for power users.

### 3. 🎨 Virality & Beauty Refactoring
- **Symptom:** Virtual card draws felt generic and lacked the mystical, premium visual punch required to go viral on social media (e.g., TikTok, Instagram).
- **Refactoring Action:**
  1. Used `generate_image` to create a beautiful, symmetrical, gold-accented celestial tarot card back (`img_tarot_card_back.jpg`) featuring luxury moon phases and intricate filigree.
  2. Applied this high-fidelity image as the default background of `CardBackDesign` in `TarotVirtualDrawScreen.kt`.
  3. Layered a dynamic linear gradient holographic gold `.shimmer()` modifier directly on top of the image so that the card glints and shines mystically as the user hovers, drags, or fans them.

### 4. 🛠️ Execution & Implementation Details
- **Database Schema Upgraded:** Added `offlineMode: Boolean` and `customApiKey: String` to `TarotSettingsEntity.kt`. Incremented database schema `version` from 3 to 4 in `TarotDatabase.kt` to trigger Room destructive migration on start.
- **Repository Refactored:** Added `updateOfflineMode()` and `updateCustomApiKey()` to `TarotRepository.kt`.
- **ViewModel Refactored:** Added `saveOfflineMode()`, `saveCustomApiKey()`, and mapped current settings into `startReading`, `drawVirtualCard`, and `chatWithTarotMaster` in `TarotViewModel.kt`.
- **UI Settings Upgraded:** Replaced the static, informational "About" dialog on the dashboard with a full-fledged, beautiful **M3 Oracle Settings & Info Panel**.
- **Build Verified:** Successfully built the APK. All compilation checks are green.

---

## 📈 Next Steps (Founder's Marketing & Growth Plan)
1. **TikTok & Reels Organic Marketing:** Record screen captures of the stunning shimmering virtual card fan and the cosmic 3D flip animation. Post with mystical ambient audio to drive viral attention.
2. **App Store Optimization (ASO):** Rebrand descriptions to highlight the "Offline-First Self-Keyed" features, appealing to privacy-conscious esoteric practitioners.
3. **Monetization Funnel:** Implement a premium subscription tier ($9.99/mo) for unlimited Gemini-3.5-pro detailed multi-card spreads and custom AI decks, pushing toward our $10k MRR milestone.
