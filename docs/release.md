# Release & Play Console — Mystic Tarot

App: **Mystic Tarot** · package `com.aistudio.mystictarot.qxrptl` · Play developer **VIBE TECHNOLOGIES, LLC** · Google account **vibeteaichnologies@gmail.com** (Play Console `/u/3/`).

## Play API access — keyless impersonation WORKS (correction, 2026-07-27)

**Previous version of this doc claimed Play API publishing was impossible. That was wrong.**
Only **SA JSON keys** are blocked by org policy `constraints/iam.disableServiceAccountKeyCreation`
(`organizations/191374647072`, enforced 2026-06-26) — this blocks `fastlane supply`-style auth,
which needs a static key file. It does **not** block short-lived **impersonated access tokens**,
which require no key material at all:

```bash
PJ=taro-ai-502921
SA=play-verifier@$PJ.iam.gserviceaccount.com   # dedicated Play-verification SA
AT=$(gcloud auth print-access-token --impersonate-service-account="$SA" \
     --scopes="https://www.googleapis.com/auth/androidpublisher")
```

Any principal with `roles/iam.serviceAccountTokenCreator` on the target SA can mint these tokens
(grant to yourself: `gcloud iam service-accounts add-iam-policy-binding "$SA" --member="user:you@example.com" --role="roles/iam.serviceAccountTokenCreator" --project=$PJ`; propagation ~1-2 min).
With that token, `edits.insert`, `bundles.upload`, and `tracks.update` all work over plain `curl`
against `androidpublisher.googleapis.com` — verified live 2026-07-27 (uploaded versionCode 4,
sha256 matched exactly). **No browser needed for the upload itself.**

### The one browser-only step: `edits:commit` needs an explicit Play Console permission
`edits:commit` returns `403 PERMISSION_DENIED` for any edit containing a track release change
until the SA has **"Release apps to testing tracks"** (Users and permissions → the SA → Account
permissions → Releases group → checkbox → Save changes). This specific checkbox-and-save
interaction could not be completed via remote CDP browser automation in this session — clicking
the SA's own "Save changes" button produced zero observable effect (no network request, no state
change) across five click methods including a genuine CDP-trusted click, while every other click
on the same page (tabs, other checkboxes) worked normally. Root cause unconfirmed; plausibly a
trust-signal gate Google applies specifically to sensitive account-permission grants. This one
click needs a real, screen-focused human session. Once granted, the scripted flow below finishes
in seconds — no further browser step is needed afterward, including for future AAB uploads.

### Full scripted flow (works today for edit/upload; commit blocked until the grant above lands)
```bash
PJ=taro-ai-502921; PKG=com.aistudio.mystictarot.qxrptl
AT=$(gcloud auth print-access-token --impersonate-service-account=play-verifier@$PJ.iam.gserviceaccount.com \
     --scopes=https://www.googleapis.com/auth/androidpublisher)
B=https://androidpublisher.googleapis.com/androidpublisher/v3/applications/$PKG

E=$(curl -s -X POST -H "Authorization: Bearer $AT" -H "Content-Length: 0" "$B/edits" \
    | python3 -c "import sys,json;print(json.load(sys.stdin)['id'])")
curl -s -X POST -H "Authorization: Bearer $AT" -H "Content-Type: application/octet-stream" \
     --data-binary "@release-artifacts/TaroAiScanner-v4-1.3-release.aab" \
     "https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/$PKG/edits/$E/bundles?uploadType=media"
curl -s -X PUT -H "Authorization: Bearer $AT" -H "Content-Type: application/json" \
     -d '{"track":"alpha","releases":[{"name":"4 (1.3)","versionCodes":["4"],"status":"completed","releaseNotes":[{"language":"en-US","text":"..."}]}]}' \
     "$B/edits/$E/tracks/alpha"
curl -s -X POST -H "Authorization: Bearer $AT" -H "Content-Length: 0" "$B/edits/${E}:commit"   # expect 200 once permission granted
```

### Future option — Workload Identity Federation
If broader CI automation is wanted later: use **Workload Identity Federation** (the org account
holds `iam.workforcePoolAdmin`). CI/fastlane exchange for short-lived tokens — no JSON key, org
policy stays intact. Not required for the impersonation flow above, which already works keyless.

## Manual release runbook (browser — only needed for the one-time permission grant above)

Use chrome-use / vibebrowser on the real Chrome, account `/u/3/` (NOT `/u/0/` → hits signup).
Do not guess Play URL slugs; navigate via the left nav. See `thoughts/play-release-runbook.md`.

1. Build signed AAB (JDK17, gradle wrapper, upload keystore `my-upload-key.jks` — Bitwarden item
   "TaroAiScanner upload keystore", fields `storePassword`/`keyPassword`/`keyAlias`/`keystore_b64`).
2. Grant `play-verifier@taro-ai-502921.iam.gserviceaccount.com` → Account permissions → Releases →
   "Release apps to testing tracks" → Save changes (the human-gated step above).
3. Run the scripted flow above, or use Play Console UI directly (Test and release → Closed testing
   → Alpha → Create new release → upload AAB → release notes → review → roll out).
4. Verify: Closed testing - Alpha track shows the new versionCode "Available to selected testers",
   or via API: `GET $B/edits/{editId}/tracks` shows `alpha.releases[0].versionCodes == ["4"]`.

## Current live state (verified 2026-07-27)

- **Closed testing - Alpha**: release **2 (1.1)** still live — versionCode 4 (1.3, billing 9.1.0)
  is uploaded to Play's bundle library (confirmed via `edits/{id}/bundles`, sha256 matches the local
  build exactly) but **not yet assigned to a track** — blocked on the permission grant above.
- **Subscription**: `mystic_tarot_premium_monthly` / "Mystic Tarot Premium (Monthly)" · base plan
  `monthly-autorenew` · **$4.99 USD** (177 regions) · **Active**. (PRD lists $9.99 — unreconciled.)
- **Server-side entitlement**: `verifySubscription` Cloud Function deployed and live-verified —
  a forged purchase token returns `200 {"verified":false,"subscriptionState":"INVALID_TOKEN"}`,
  confirming the spoof gap from earlier sessions is closed in production.
- **`play-verifier` Play API access**: confirmed working for read/verify operations (`reviews` →
  200, `purchases/subscriptionsv2/tokens/{fake}` → 400 "Invalid Value" = ACL passed, dummy token
  correctly rejected). Only the release-track permission (above) is still missing.

## Revenue gaps — status 2026-07-27

Billing code, server-side entitlement, and the v4 AAB are all done and verified. The only
remaining blocker before the subscription is sellable to real users:

1. ~~Merge PR #4 (`feat/play-billing`)~~ — **done**, merged to `main`.
2. ~~Fix product ID mismatch~~ — **done**.
3. ~~Server-side entitlement~~ — **done**, deployed, live-verified.
4. **Assign v4 AAB to a track and commit** — blocked on the one Play Console permission grant
   documented above. Everything else in this step is already scripted and proven to work.
5. **Payout method** — no evidence found that this has been configured. Still open, blocking
   receipt of money even after step 4 lands.

## Production promotion — the closed-testing gate

**Google policy (current):** Play developer accounts of type **"Personal"** created after
**2023-11-13** must run **closed testing with at least 20 testers opted-in for 14 continuous
days** before "Apply for production" unlocks. **Organization/company accounts are exempt.**

- This account is **VIBE TECHNOLOGIES, LLC** → org/company account → **not subject** to the
  20-tester/14-day gate. Production can be applied for once a billing-enabled build is verified.
- Current closed track has only **1 tester** ("Mystic Tarot Testers").

### Do not promote to Production yet — sequencing
1. Land the permission grant above, finish the v4 track release, verify a real purchase completes.
2. THEN promote that build to Production with the ASO/listing assets.

Promoting first only helps distribution/installs, not revenue.
