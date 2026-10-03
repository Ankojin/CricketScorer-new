# React Web Staging Release & Cutover Plan

**Status:** Ready for Staging Release Review (No deployment executed).
**React App Source:** `CricketScorer/web`
**Legacy Vanilla App Source:** `CricketScorer-web/public`
**API Base URL:** `https://cricleagueapi.nrkmart.in`

---

## 1. Quality & Parity Audit Summary

> [!NOTE]
> All build, compilation, and automated test checks have been run and verified locally before staging preparation.

| Check | Tool / Command | Result | Details |
| :--- | :--- | :--- | :--- |
| **Static Analysis / Type Check** | `npx tsc --noEmit` | **PASS (0 Errors)** | Strict TypeScript validation passed across all views, contexts, and domain layers. |
| **Automated Test Suite** | `npm test` (`vitest run`) | **PASS (65/65 Passed)** | 100% pass rate across 10 test suites covering engine golden fixtures, auth, persistence, spectator links, and Option B cloud writes. |
| **Production Bundle Build** | `npm run build` (`tsc && vite build`) | **PASS** | Bundle output in `D:/CricketScorer-new/web/dist`: `index.html` (0.9 kB), `index-DAB7n_-t.css` (35.9 kB), `index-DzeYk0tM.js` (368.5 kB). |
| **Spectator URL Parity** | `URLSearchParams` | **PASS** | Both `?st=<token>` and `?matchId=<id>&st=<token>` URL contracts are 100% backward-compatible with legacy Vanilla links. |

---

## 2. Infrastructure & Hosting Architecture

### **SPA Client Routing & CloudFront Custom Error Response**
- **Issue**: Single Page Application (SPA) routes like `/matches`, `/stats`, or direct links return `403` or `404` from S3 if refreshed directly.
- **Solution**: Configure CloudFront Custom Error Responses:
  - Error Code: `403` & `404`
  - Response Page Path: `/index.html`
  - HTTP Response Code: `200 OK`

### **CloudFront Cache Control Strategy**
- **Hashed Assets (`/assets/*`)**:
  - `Cache-Control: public, max-age=31536000, immutable`
- **Root Entry Point (`/index.html`)**:
  - `Cache-Control: no-cache, no-store, must-revalidate`

### **API CORS & Platform Headers**
- **CORS Allowed Origins**: `https://cricleague.nrkmart.in` and Staging CloudFront domain.
- **CORS Allowed Headers**: `Authorization`, `X-Client-Platform`, `Content-Type`, `X-Guest-Mode`.
- **Lambda Platform Enforcement**: Option B policy gate allows `X-Client-Platform: web`.

---

## 3. Step-by-Step Staging Deployment Instructions (AWS CLI)

> [!WARNING]
> **Do not execute AWS commands below until explicit release approval is granted.**

### **Step 1: Sync Hashed Static Assets to Staging S3**
```bash
aws s3 sync D:/CricketScorer-new/web/dist/assets s3://cricleague-staging-web/assets \
  --cache-control "public, max-age=31536000, immutable"
```

### **Step 2: Sync Root SPA Entry (`index.html`) to Staging S3**
```bash
aws s3 sync D:/CricketScorer-new/web/dist s3://cricleague-staging-web \
  --exclude "assets/*" \
  --cache-control "no-cache, no-store, must-revalidate"
```

### **Step 3: Invalidate CloudFront Staging Edge Cache**
```bash
aws cloudfront create-invalidation \
  --distribution-id E1STAGINGDIST \
  --paths "/*"
```

---

## 4. Rollback Procedure

If any unexpected regression or critical issue is discovered during staging acceptance testing:

1. **Vanilla Legacy Backup Source**: `D:/CricketScorer-web/public/` remains completely intact and deployable.
2. **Revert S3 Bucket Contents**:
   ```bash
   aws s3 sync D:/CricketScorer-web/public/ s3://cricleague-staging-web/ \
     --cache-control "no-cache, no-store, must-revalidate"
   ```
3. **Invalidate CloudFront Cache**:
   ```bash
   aws cloudfront create-invalidation --distribution-id E1STAGINGDIST --paths "/*"
   ```

---

## 5. React Acceptance Checklist

- [x] App root mounts AuthProvider, TournamentProvider, and MatchProvider in correct order.
- [x] Production auth uses real AWS Lambda contract (`POST /auth/login` and `POST /auth/register`); no fake JWT tokens or plaintext passwords stored.
- [x] Guest and signed-in browser data are strictly partitioned by stable `userId`.
- [x] React scoring engine (`web/src/domain/scoringEngine.ts`) is canonical and proven equivalent against reference outputs via golden fixtures.
- [x] All cricket scoring workflows (runs, extras, wickets, retired hurt, strike rotation, innings transitions) pass parity tests.
- [x] React uses only verified API routes (`/auth/*`, `/matches`, `/matches/{id}`, `/share-token`, `/revoke-share`).
- [x] Option B cloud scoring policy gate implemented with `409 STALE_REVISION` optimistic locking protection.
- [x] Spectator links (`?st=token`) enforce read-only controls, live polling, and auto-stop behavior on expiry/revocation (`410`).
- [x] Vanilla legacy codebase in `CricketScorer-web/public` remains untouched and ready for rollback if needed.
- [x] No AWS deployment, SAM deployment, S3 sync, or CloudFront invalidation executed without explicit approval.
