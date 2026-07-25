# Park-or-Proceed — Mystic Tarot (TaroAiScanner)
**2026-07-25 · as of main@e3d5ae8 · $0 revenue, $0 installs (Play listing 404, not promoted to Production)**

## Recommendation: **PROCEED — one bounded ~90-min Play Console session, then hard stop on further engineering.**
Not "explore options." Ship what's built, find out if it earns a dollar, then decide with real data instead of more agent-hours.

## What's genuinely done and merged (11 PRs, 6 days, main@e3d5ae8)
- Native Android app: Compose UI, camera card scan, virtual draw, Firebase auth, Room history.
- Secure AI proxy: deployed, Firebase-token-gated, Vertex-backed, quota-metered — **live-verified**.
- Play Billing wiring + server-side entitlement (Play `subscriptionsv2` verify, forged-token hardening #11) — **merged, NOT deployed** (Cloud Run still on the pre-#5 revision from 07-23).
- Play Console: app exists, Closed testing track live (1 tester), subscription product `mystic_tarot_premium_monthly` configured ($4.99/mo — PRD says $9.99, unreconciled).
- Account type (org, VIBE TECHNOLOGIES LLC) is exempt from the 20-tester/14-day closed-testing gate — production is unlocked whenever we choose.

## What remains before a single dollar is possible (founder-only, browser/banking — not agent-executable)
| # | Action | Min |
|---|---|---|
| 1 | Play Console → link runtime SA under API access, grant subscription-read | 10 |
| 2 | Upload/confirm the billing AAB (v1.2/versionCode 3) to Closed testing | 15 |
| 3 | Attach a payout/merchant profile (never done — no evidence found) | 30 |
| 4 | Live purchase test with a funded account | 15 |
| 5 | Promote to Production (org account, gate exempt) | 5 |
| 6 | Confirm deploy of entitlement code to Cloud Run went live | 5 |
| | **Total founder-minutes** | **≈80–90** |

(Deploying the already-merged entitlement code itself is a 5-min agent task, not counted as founder time — but it must happen before step 4.)

## The contradiction that must be named, not buried
The project's **own PRD v0.1** (Notion, 2026-07-16) argued explicitly: *"Taro ships as a Telegram bot, not a native app"* — citing a native build costs **~10x more** to build/iterate, and Apple's 4.3(b) fortune-telling rejection risk. Its own same-day critical review flagged the plan **SHAKY on market-fit** (target demo is US women 18–34; Telegram is 56.8% male, ~9% US penetration) and **SHAKY on monetization/targets**.
Three days later a **second PRD (v0.2)** and a TDD appeared, silently reversing to native Android — **no decision doc reconciles the two**, none of v0.1's own objections (cost, distribution ceiling, market-fit mismatch) are addressed. Everything in the repo is native Android; zero Telegram code exists. We are ~90 minutes from testing a monetization hypothesis the founding document argued was 10x more expensive than the alternative and never re-validated.

## Why proceed anyway
Sunk-cost bias is real, but the counter-cost of parking now is throwing away 11 merged PRs of working engineering to test nothing. ~90 minutes is cheap enough to buy a real signal (does anyone pay $4.99/mo) instead of arguing architecture on priors. **Condition attached: after step 5, no further engineering work on this repo until install/revenue data comes back — this memo is the stop sign for agent-hours, not a green light for more feature work.**
