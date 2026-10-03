# React Web Migration Gemini Prompt Plan

**Status:** Planning only. No application code or AWS resources are changed by this document.
**Last reviewed:** 2026-10-03

## Purpose

Use these staged prompts to migrate the existing Vanilla Web scoring experience to the existing React/Vite app at `CricketScorer/web`. The React app is a fresh frontend target; there is no React-owned match history to migrate. Keep the current Vanilla app at `CricketScorer-web/public/` available until React passes the release gates.

The AWS API, Lambda, and DynamoDB remain the backend. This is a client migration, not a backend replacement. The React source currently contains an engine, views, state contexts, repositories, and a `CloudApiAdapter`, but the AWS adapter/auth wiring does not yet match the deployed Lambda contract.

## Non-Negotiable Guardrails

Include these in every Gemini implementation prompt:

- Work only in `C:\Users\AnkojiRaoNagisetty\StudioProjects\CricketScorer\web` unless a phase explicitly authorizes changes to `CricketScorer-web` backend or Vanilla code.
- Do not edit `CricketScorer-web/public/` or deploy anything until parity is approved.
- Do not change AWS resources, deploy SAM, upload to S3, invalidate CloudFront, or commit/push without explicit approval.
- Do not set `ENFORCE_ANDROID_MATCH_WRITES=false` as a shortcut. Ask for a scorer authorization decision before enabling React cloud match writes.
- Do not implement or call `/api/series/active/snapshot`, `/matches/{id}/balls`, `/matches/{id}/claim`, or `/matches/{id}/share`; these routes do not exist.
- Do not port a simplified scoring engine or alter scoring rules without shared regression fixtures. Select one existing engine as canonical after comparing the React copies with Vanilla/Android behavior.
- Preserve stable player IDs. Names are not unique identity keys; never auto-merge players solely by normalized name.
- Treat `409 STALE_REVISION` as a conflict. Do not blindly replace local match data or replay balls unless a server-supported idempotent ball-event contract is implemented.
- Keep spectator tokens read-only, match-bound, expiring, versioned, and revocable.
- Keep React cloud matches read-only in the browser unless the scorer-policy decision explicitly enables React scoring.
- Make each phase a small, independently buildable change. Report files changed and test output after each phase.

## Verified Current Backend Contract

API base: `https://cricleagueapi.nrkmart.in` (configure with `VITE_API_BASE_URL` for non-production environments).

Current routes, with no `/api` prefix:

- `POST /auth/register` and `POST /auth/login`, response `{ token, user: { userId, email, name } }`.
- `GET /matches` returns authenticated owner-scoped full match snapshots.
- `GET /matches/{id}` returns an owner match or a LIVE spectator match when given `?st=<spectatorToken>`.
- `POST /matches` and `PUT /matches/{id}` create/update full match snapshots. Match properties, including top-level `revision`, are not wrapped in `{ expectedRevision, match }`.
- `DELETE /matches/{id}` deletes an owned match.
- `POST /matches/{id}/share-token` issues a spectator token. Allowed TTLs: 15, 60, 360 minutes.
- `POST /matches/{id}/revoke-share` revokes spectator links.
- `GET /tournaments`, `POST /tournaments`, `DELETE /tournaments/{id}` manage owner-scoped series metadata.
- `GET /players`, `POST /players`, `DELETE /players/{id}` manage owner-scoped player records.

Deployment currently sets `ENFORCE_ANDROID_MATCH_WRITES=true`. DynamoDB `CricMatches` uses `matchId` as its only key; list routes scan/filter and do not currently provide cursor pagination. Match writes are conditional on the snapshot revision and spectator version; stale/concurrent writes return `409 STALE_REVISION`.

## Prompt Sequence

### Prompt 0: Audit Only

```text
Review the existing React app in CricketScorer/web and the Vanilla Web app in CricketScorer-web/public without editing files. Report the React routes/screens/components, state providers, repositories, test coverage, and duplicated scoring engines. Compare CloudApiAdapter.ts with the verified API contract in Doc/COMPREHENSIVE_ARCHITECTURE_AND_MIGRATION_PLAN.md. Identify blockers and propose a minimal vertical-slice order. Do not scaffold, edit, deploy, commit, or push.
```

**Exit gate:** Confirm the app path, existing React features, canonical engine candidate, and exact API mismatches before implementation.

### Prompt 1: Auth and API Contract

```text
Implement only the React authentication/API adapter slice in CricketScorer/web. Mount AuthProvider at the app root. Replace LocalAuthRepository as the production auth path with the real AWS auth endpoints and exact response shape { token, user: { userId, email, name } }. Remove fake JWT-like tokens and plaintext password persistence from production flows. Align CloudApiAdapter to the actual routes (no /api prefix), current request/response bodies, and current share-token/revoke-share paths. Keep guest/local scoring available. Do not enable cloud match writes, change ENFORCE_ANDROID_MATCH_WRITES, modify the Lambda, or deploy. Add focused Vitest tests for login, register, session restore, logout, and HTTP errors.
```

**Exit gate:** Auth flow uses a real backend token, local guest operation still works, and tests prove there is no fake-token cloud request.

### Prompt 2: Account-Scoped Local Storage and Cloud Bootstrap

```text
Implement React local-data isolation and read-only cloud bootstrap in CricketScorer/web. Scope LocalStorage/IndexedDB records by guest or stable userId, including tournaments, players, active match, and pending operations. Do not expose one account's cached data after account switching. Add a safe, explicit migration path for legacy unscoped browser data; never silently assign it to an account. Use only existing GET /matches, GET /tournaments, and GET /players routes. Handle partial API failure and server pagination requirements explicitly. Keep cloud match reads read-only and do not send score writes. Add tests for guest/account isolation and account switching.
```

**Exit gate:** Login/logout changes the active local namespace, and an account cannot see another namespace's local records.

### Prompt 3: Engine Parity and Local Match Setup

```text
Implement React local match setup using the existing scoring engine and model contracts. First compare CricketScorer/web/src/domain/scoringEngine.ts, web/src/engine/ScoringEngine.ts, web/src_legacy_engine, CricketScorer-web/src/engine/ScoringEngine.ts, and Android ScoringEngine.kt. Recommend one canonical React engine and do not delete duplicates until equivalence is demonstrated. Build the setup flow for teams, roster, overs/settings, toss, and a local-only match. Add golden fixtures for runs, wides, no-balls, byes, leg-byes, granted runs, every supported wicket, retired hurt, strike rotation, pending actions, innings transition, target/chase completion, and history edits. Do not change rules to fit a sample implementation.
```

**Exit gate:** The same fixtures produce equivalent totals, player/bowler stats, wickets, and match status in React and reference clients.

### Prompt 4: Live Scoring React UI

```text
Migrate the live scoring screen into React components and context/reducer actions in CricketScorer/web. Cover fast run controls, extras, wicket/run-out/catch/fielder workflows, striker/bowler selection, pending actions, undo, over summary, scorecard, overs, and stats. Preserve local-only scoring for signed-out users and signed-in local-only matches. Keep cloud-owned match data read-only in the browser. Use the canonical engine for all calculations; components must not duplicate cricket rules. Add component/interaction tests for the primary scoring flows and responsive mobile layout.
```

**Exit gate:** Existing scorecard, overs, stats, and spectator UI remain consistent with the same match history; controls are keyboard/accessibility usable and cannot mutate cloud-owned match data.

### Prompt 5: Cloud Reads, Spectator Links, and Conflict UX

```text
Integrate owner-scoped cloud reads and live spectator links in CricketScorer/web using only current AWS routes. Use GET /matches/{id} with st for spectator links, force spectator read-only mode regardless of login state, stop polling when the API returns revoked/expired/non-LIVE, and retain the current matchId/st URL contract. Use POST /matches/{id}/share-token and POST /matches/{id}/revoke-share only when the signed-in user owns the match. Do not call the nonexistent /share endpoints or /api/public/spectate path. Handle 401, 403, 409, and 410 distinctly. Keep cloud scoring writes disabled and do not auto-rebase a full snapshot.
```

**Exit gate:** Owner can create/revoke a share; spectators can only read; expired/revoked links stop polling; unauthorized users cannot access private match data.

### Prompt 6: Cloud Scoring Policy Gate (Do Not Execute Without Approval)

```text
Before implementing React cloud match writes, stop and present the choices and security implications. The current Lambda deployment requires X-Client-Platform: android, but that header is client-supplied and not strong app attestation. If the product decision is that any authenticated owner may score from either client, retain backend owner checks and DynamoDB revision conditions, adjust CORS only for required headers, and test two-client conflict behavior. Do not change ENFORCE_ANDROID_MATCH_WRITES or deploy until I approve the policy. If multi-scorer scoring is required, propose a durable idempotent event endpoint and transaction design separately; do not fake event merging client-side.
```

**Exit gate:** Written product/security decision and backend/API tests before any React cloud score-write implementation.

### Prompt 7: Staging Release and Cutover Plan

```text
Prepare a release plan only; do not deploy. Run npm run lint, npm test, npm run build, React E2E tests, and API contract tests. Compare the React build with the current Vanilla production flows and verify the existing spectator URL. Specify the staging hostname/distribution, Vite base path, SPA fallback, API CORS origin, CloudFront cache behavior, rollback procedure, and exact AWS resources/commands. The React app is under CricketScorer/web; the Vanilla app is under CricketScorer-web/public. Uploading a directory to an S3 prefix alone does not configure routing. Do not use aws s3 sync --delete against production without reviewing its deletion set. Wait for explicit release approval.
```

**Exit gate:** Staging acceptance checklist complete, rollback tested, and explicit production approval received.

## React Acceptance Checklist

- [ ] App root mounts auth and required providers in a documented order.
- [ ] Production auth uses the real Lambda contract; no fake token or plaintext password storage.
- [ ] Guest and signed-in browser data are partitioned by stable profile ID.
- [ ] React scoring engine is canonical or proven equivalent with shared fixtures.
- [ ] All current scoring workflows pass parity tests.
- [ ] React uses only verified routes unless a proposed route is implemented/tested first.
- [ ] Cloud-owned match scores remain read-only until the scorer policy is approved.
- [ ] Spectator links retain token binding, expiry, revocation, polling stop behavior, and read-only controls.
- [ ] Backend list scaling, pagination, and DynamoDB indexes have an explicit plan before production scale.
- [ ] Vanilla remains deployable until React staging is accepted.
- [ ] No SAM deployment, S3 upload, CloudFront invalidation, commit, or push occurs without approval.
