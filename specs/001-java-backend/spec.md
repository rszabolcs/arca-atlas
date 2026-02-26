# Feature Specification: Arca Java Backend

**Feature Branch**: `001-java-backend`
**Created**: 2026-02-26
**Status**: Draft
**Input**: Build the Arca Java Backend — a REST API service for ArcaDigitalis Vault that integrates with an existing deployed EVM policy smart contract (via proxy address) and Lit Protocol, providing Owner, Guardian, and Beneficiary endpoints with SIWE authentication, non-custodial transaction payloads, reorg-safe event indexing, storage adapters, Lit ACC template generation/validation, and best-effort notifications.

## Clarifications

### Session 2026-02-27 (round 4 — proxy boundary audit)

- Q: Should the backend refuse to encode `/tx/renew` and ETH-bearing `/tx/activate` calldata when `fundingEnabled: false`, or always return calldata and let the chain revert with `FundingDisabled()`? → A: Enforce at the backend layer (HTTP 400). `pricePerSecond` is a deployment constant; refusing to encode saves the user a wasted funded tx with no correctness loss. The chain still enforces it independently. FR-010a confirmed as-is.
- Q: General pre-flight principle for tx endpoints: should the backend add live status reads to all tx endpoints to prevent predictable reverts, or only surface 409s when a live read is already required? → A: Option A — pre-flight only when a live status read is already required (for auth or an existing check). No extra RPC calls are added solely to pre-flight. Endpoints that already perform a live read (owner-auth endpoints, guardian-auth endpoints, claim) MUST surface 409 for predictable status-based reverts. Unauthenticated endpoints (`/tx/renew`, `/tx/rescue`) return calldata unconditionally. Added as FR-012d.

### Session 2026-02-27 The spec used `Activated` as the event name throughout; the contract emits `PackageActivated`. → A: Corrected to `PackageActivated` everywhere (spec.md, data-model.md, openapi.yaml). FR-026 expanded to all 13 contract events with cache-update rules.
- Q: Data model completeness (no question, self-evident): `package_cache` lacked `last_check_in` and `paid_until` columns; `event_records` lacked `reason_flags` documentation for `PendingRelease`; state-transitions table was missing `CheckIn`, `Renewed`, `PackageRescued` rows. → A: `last_check_in` and `paid_until` added to `package_cache`; `raw_data` annotated for `PendingRelease`; state-transitions table completed; event type enum updated to 13 types.
- Q: Naming fix (no question, self-evident): openapi.yaml operationId `prepareUpdateManifest` / summary `updateManifest()` did not match the contract function `updateManifestUri(bytes32,string)`. → A: Corrected to `prepareUpdateManifestUri` / `updateManifestUri()` so ABI encoding aligns with the on-chain function selector.
- Q: Should the backend provide a `/tx/rescue` unsigned payload endpoint for the upgrade authority's `rescue()` call? → A: Yes — unauthenticated at the payload layer (same pattern as `/tx/renew`; the contract enforces `NotAuthorized()` on-chain). Added to FR-012 and `openapi.yaml`.
- Q: How should the backend surface `pricePerSecond`/funding-enabled status so clients can gate `/tx/renew` and ETH-bearing `/tx/activate` flows? → A: Expose `fundingEnabled` on a new unauthenticated `GET /config` endpoint returning `{chainId, proxyAddress, fundingEnabled}`. `pricePerSecond` is a deployment constant; one endpoint serves all callers. Added as FR-010a.
- Q: Should the package-status endpoint return all fields from the contract's `PackageView` struct, or only the current 7? → A: All fields — including `vetoCount`, `approvalCount`, `guardianQuorum`, `warnThreshold`, `inactivityThreshold`, `gracePeriodSeconds`, `lastCheckIn`, and `paidUntil`. These are all public on-chain data already fetched by the backend; omitting them forces clients to make a redundant `eth_call`. FR-008 and the `PackageStatus` schema updated.

### Session 2026-02-26

- Q: How are notification targets registered and managed? → A: Dedicated authenticated API endpoints (per package, per event type), managed by the package owner or beneficiary; `NotificationTarget` persisted in the backend DB.
- Q: What is the deployment availability model for the MVP? → A: Single-instance primary; no distributed coordination or leader election required at MVP; horizontal scale is achievable later without redesign, because event processing is idempotent (FR-029) and DB is the sole state store.
- Q: What are the latency and polling-interval targets? → A: API status endpoints p95 ≤ 500 ms; event indexer polling interval ≤ 15 seconds.
- Q: How deep is `/validate-manifest` validation — local only, or does it include live network reads? → A: Three-layer validation: (1) structural field presence + ACC address/value correctness (proxy address, chainId, packageKey, ACC `functionName` == `isReleased`, ACC `functionParams[0]` == packageKey, `requester` == beneficiary) — fully local; (2) one live Ethereum RPC read confirming the packageKey is activated and the on-chain beneficiary matches the manifest `requester`; (3) non-empty, non-whitespace, plausible-length guard on `encryptedSymmetricKey` — no cryptographic or Lit-network check.
- Q: What is the canonical package status model the backend must surface, and do WARNING and CLAIMABLE require distinct handling? → A: Full 7-state enum mirroring the contract: `DRAFT`, `ACTIVE`, `WARNING`, `PENDING_RELEASE`, `CLAIMABLE`, `RELEASED`, `REVOKED`. WARNING and CLAIMABLE are lazily computed by the contract's `getPackageStatus()` view — never written to `storedStatus`. The backend must expose all 7 as first-class values. CLAIMABLE is distinct from PENDING_RELEASE: only a CLAIMABLE package allows `claim()` to succeed; `checkIn()` reverts on-chain with `AlreadyClaimable()` when the package is CLAIMABLE. Derived from: `spec-with-data-model.md §1.3`, `spec-with-transitions.md` clarification sessions.
- Q: Single-proxy vs multi-proxy architecture — should the backend support routing requests across multiple proxy addresses or chains? → A: Single proxy + single chain per instance. The contract is inherently single-proxy per deployment; there is no on-chain multi-tenancy to serve. Any request supplying a `proxyAddress` or `chainId` that does not match the service's configured values MUST be rejected with HTTP 400. Multi-chain deployments are an explicit out-of-scope future extension, not MVP.
- Q: When the RPC node is unreachable, which read endpoints may serve a stale cached response, and which must return HTTP 503? → A: Auth-gated reads (any endpoint that performs a live chain authorization check — guardian access verification, beneficiary validation, notification-target registration) MUST return HTTP 503 if the RPC is unreachable; serving stale auth data could grant access to a revoked guardian. Pure indexed-data reads (package status from DB cache, event history, stored artifacts, paginated package lists) MAY serve cached data and MUST include an `X-Data-Staleness-Seconds` response header indicating seconds since last successful sync.
- Q: Should the backend validate the `domain` field in the SIWE signed message against the service's own configured host? → A: Yes — Option B. The backend MUST validate the `domain` field exactly against a configured value (`ARCA_SIWE_DOMAIN`). A mismatch MUST be rejected with HTTP 401. This blocks cross-origin replay attacks where a message signed for a different site is submitted to this service.
- Q: Should `/tx/renew` require JWT authentication? → A: No. The endpoint is unauthenticated; any caller (including anonymous) may request the unsigned `renew()` calldata. The contract enforces no access control on `renew()` — it is a pure ETH-payment extension callable by anyone. Backend state reset (clearing `pendingSince`) is driven by indexing the on-chain event after the tx mines, not by who requested the payload.
- Q: What should the indexer's starting block be on first run (before any sync state is persisted)? → A: The contract deployment block number, supplied via `ARCA_INDEXER_START_BLOCK`. Syncing from genesis wastes resources; syncing from service-start silently drops historical events needed for notification subscribers and event history. On subsequent starts the indexer resumes from the last processed block stored in `processed_blocks`.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Package Discovery & Wallet Authentication (Priority: P1)

Any party (owner, guardian, or beneficiary) connects their Ethereum wallet to the vault UI, signs
a challenge message, and receives an authenticated session. From that session, they can retrieve
the live status, roles, latest manifest reference, and release timestamp for any vault package,
without depending on a cached copy being correct.

**Why this priority**: Authentication and package-status read are foundational. No other user
story can deliver value without them. The package-status endpoint is also the first step in every
recovery scenario, making it the highest-risk path to get right.

**Independent Test**: Deploy the service pointed at a testnet. Call the nonce endpoint, sign the
challenge with a test wallet, exchange for a session token, then call the package-status endpoint
with a known package key. Verify the response contains correct on-chain status, roles, manifest
URI, and release timestamp — and that the DB cache does not override a live chain mismatch.

**Acceptance Scenarios**:

1. **Given** a valid Ethereum wallet address, **When** the client requests a nonce and returns a
   correctly signed EIP-4361 message, **Then** the service issues a short-lived session token
   valid for subsequent API calls.

2. **Given** an active session, **When** the client queries a known package key, **Then** the
   service returns: current on-chain status, owner address, guardian list, beneficiary address,
   manifest URI, pending-since and released-at timestamps.

3. **Given** a package key not known to the chain, **When** the service reads contract state,
   **Then** the status field equals `DRAFT` — not an error, not a cached stale value.

4. **Given** a DB cache containing `ACTIVE` for a package that the chain now shows as `REVOKED`,
   **When** the client queries status, **Then** the live chain value (`REVOKED`) is returned;
   the service MUST NOT return the stale cached status as authoritative.

5. **Given** an expired or replayed session token, **When** the client calls any endpoint,
   **Then** the service responds with HTTP 401 and a clear error message.

---

### User Story 2 — Owner Package Lifecycle (Priority: P2)

A vault owner activates a new package by submitting a prepared activation payload. Over time they
perform check-ins to reset the inactivity clock, renew the package with optional deposit, update
the manifest URI, and — if needed — revoke the package entirely. For every operation the backend
returns an unsigned transaction payload; the owner wallet signs and submits it independently.

**Why this priority**: Owners create and maintain all packages. Without this story there are no
packages for guardians or beneficiaries to act on.

**Independent Test**: Using a test owner wallet and testnet: call each of the five owner endpoints
(`prepare-activation`, `check-in`, `renew`, `update-manifest`, `revoke`); sign and submit the
returned payload to the testnet; verify the chain state reflects the expected transition after each
operation.

**Acceptance Scenarios**:

1. **Given** an authenticated owner and a valid manifest reference, **When** the owner calls the
   activation endpoint, **Then** the service returns an unsigned transaction payload that correctly
   encodes the `activate(...)` contract call; submitting it produces a `PackageActivated` event on-chain.

2. **Given** an active package owned by the caller, **When** the owner calls check-in, **Then**
   the service returns an unsigned payload for `checkIn(...)`; submitting it resets the inactivity
   timer.

3. **Given** an authenticated session where the caller is NOT the package owner, **When** they
   call any owner endpoint, **Then** the service responds with HTTP 403; no payload is returned.

4. **Given** an owner calls `revoke`, **When** the payload is submitted on-chain, **Then** a
   `Revoked` event is emitted and subsequent status queries return `REVOKED`.

5. **Given** any owner write endpoint, the returned payload MUST contain: `to` (proxy address),
   ABI-encoded `data`, and gas estimate; the service MUST NOT sign or submit the transaction.

---

### User Story 3 — Guardian Approval Workflow (Priority: P3)

A guardian reviews a pending release and either approves, vetoes, or rescinds their prior veto.
Each action is non-custodial: the backend provides an unsigned transaction payload; the guardian's
wallet signs and broadcasts it.

**Why this priority**: Guardians are the quorum-control layer. Their actions directly gate whether
a release proceeds or is blocked. This story is a core security path.

**Independent Test**: Place a package in `PENDING_RELEASE` on testnet. Call approve/veto/rescind
endpoints from a guardian wallet session; sign and submit each payload; verify the on-chain
guardian state reflects the correct approval/veto count.

**Acceptance Scenarios**:

1. **Given** an authenticated guardian for a package in `PENDING_RELEASE`, **When** they request
   an approve payload, **Then** the service returns an unsigned `guardianApprove(...)` payload;
   submitting it emits the appropriate guardian-approval event on-chain.

2. **Given** an authenticated guardian, **When** they request a veto or rescind-veto payload,
   **Then** the service returns the correct unsigned payload for `guardianVeto(...)` or
   `guardianRescindVeto(...)` respectively.

3. **Given** a caller who is NOT in the guardian set for the package, **When** they call any
   guardian endpoint, **Then** the service responds with HTTP 403; authorization is confirmed by
   reading the guardian set from the proxy contract, not from the DB cache.

---

### User Story 4 — Beneficiary Recovery Support (Priority: P4)

A beneficiary needs to recover their inherited assets. They query the backend for a complete
recovery kit containing everything required: chain coordinates, the manifest URI, and Lit ACC
parameters. They can also obtain a claim transaction payload. Critically, recovery must remain
possible even if the backend is unavailable or its data is stale.

**Why this priority**: Recovery is the product's core promise to beneficiaries. A regression here
is a product failure, not just a bug.

**Independent Test**: Bring a package to `RELEASED` on testnet. Query the recovery-kit endpoint
and verify all required fields are present and match chain state. Separately, verify that a
beneficiary can reconstruct the full recovery path using only the fields returned (chain RPC +
proxy + packageKey + manifest + Lit) without calling the backend again.

**Acceptance Scenarios**:

1. **Given** an authenticated session (beneficiary or unauthenticated for public recovery path),
   **When** the recovery-kit endpoint is called for a released package, **Then** the service
   returns: `chainId`, proxy address, `packageKey`, `manifestUri`, `releasedAt`, and the Lit ACC
   parameters bound to the release condition.

2. **Given** a package in `RELEASED` state, **When** a beneficiary requests a claim payload,
   **Then** the service returns an unsigned `claim(...)` transaction payload; submitting it
   on-chain completes the beneficiary claim.

3. **Given** the backend's DB cache is empty or stale, **When** the beneficiary calls the
   recovery-kit endpoint, **Then** the service still returns correct data by performing a live
   chain read; a stale cache MUST NOT cause an error response.

4. **Given** a package that is NOT yet in `RELEASED` state, **When** a beneficiary calls the
   recovery-kit endpoint, **Then** the service returns a response indicating the release condition
   has not been met, along with whatever identifying data is publicly available.

---

### User Story 5 — Lit ACC Template Generation & Manifest Validation (Priority: P5)

The backend generates Access Control Condition templates that precisely bind vault-key unwrapping
to the on-chain release signal. It also validates that any manifest submitted by an owner
correctly references the right chain, proxy address, package key, and beneficiary — rejecting
manifests with mismatched or missing ACC fields before they are stored.

**Why this priority**: An incorrectly bound ACC would allow premature decryption of a beneficiary's
assets. Correctness here is a hard security requirement.

**Independent Test**: Call the ACC-template endpoint for a known `(chainId, proxy, packageKey,
beneficiary)` tuple; verify the returned ACC evaluates `policyProxy.isReleased(packageKey)==true`
against the correct proxy address and constrains the requester to the beneficiary address. Then
submit a manifest with an intentionally wrong proxy address to the validation endpoint; verify it
is rejected with HTTP 400 and a clear error.

**Acceptance Scenarios**:

1. **Given** valid `chainId`, proxy address, `packageKey`, and beneficiary address, **When** the
   ACC-template endpoint is called, **Then** the service returns a complete ACC JSON whose
   condition evaluates `policyProxy.isReleased(packageKey)==true` on the specified proxy, and
   whose requester constraint equals the beneficiary address.

2. **Given** a manifest whose embedded ACC correctly references the proxy, chainId, and packageKey,
   **When** the manifest-validation endpoint is called, **Then** the service returns a success
   response confirming the manifest is consistent with chain data.

3. **Given** a manifest whose embedded ACC references a proxy address different from the canonical
   one for the package, **When** the manifest-validation endpoint is called, **Then** the service
   returns HTTP 400 with a clear error identifying the mismatch.

4. **Given** a manifest that is missing the `encryptedSymmetricKey` (encDEK) field, **When** the
   manifest-validation endpoint is called, **Then** the service returns HTTP 400.

5. **Given** any Lit-related endpoint, the service MUST NOT return, log, or store raw DEKs,
   plaintext assets, or Lit session secrets at any point in the request/response cycle.

---

### User Story 6 — Encrypted Artifact Storage & Integrity (Priority: P6)

An owner or an authorized party pins manifest JSON and ciphertext blobs through the backend. The
backend verifies the sha256 hash of each artifact against the manifest's declared hash before
confirming storage, and optionally verifies an owner signature over the manifest hash.

**Why this priority**: Integrity guarantees protect beneficiaries from tampered artifacts.
Storage must be a verified, not a blind, accept.

**Independent Test**: Upload a manifest and a ciphertext blob whose sha256 matches the manifest
declaration; verify storage succeeds. Then submit a ciphertext whose hash does not match; verify
the backend rejects it with HTTP 422.

**Acceptance Scenarios**:

1. **Given** a manifest JSON and a ciphertext blob whose sha256 matches the hash declared in the
   manifest, **When** the storage upload endpoint is called, **Then** the service pins both
   artifacts and returns their confirmed storage URIs.

2. **Given** a ciphertext blob whose sha256 does NOT match the manifest declaration, **When** the
   upload endpoint is called, **Then** the service returns HTTP 422 (Unprocessable Entity) and
   does not persist the artifact.

3. **Given** a storage operation completes, **When** the returned storage URIs are later resolved,
   **Then** the retrieved content matches the originally uploaded bytes (integrity preserved).

4. **Given** any storage operation, the service MUST treat all artifacts as public (encrypted
   content is already opaque); it MUST NOT attempt to decrypt or inspect blob contents.

---

### User Story 7 — Event Indexing & Notifications (Priority: P7)

The backend continuously indexes vault-related on-chain events into a queryable local store,
enabling fast paginated queries by owner, guardian, beneficiary, and time range. It also sends
best-effort notifications (email, webhook, or push) when significant vault events occur, without
blocking or delaying the recovery path.

**Why this priority**: Indexing powers the UX for all other stories. Notifications improve
responsiveness but are not on the critical path. Both can be iterated after higher-priority
stories are stable.

**Independent Test**: Emit a sequence of `PackageActivated`, `PendingRelease`, `Released`, and `Revoked`
events on a testnet including a deliberate reorg (mine two competing blocks then resolve). Verify
the indexed state after reorg resolution matches the canonical chain; verify no duplicate events
appear. Query the paginated event feed filtered by owner address; verify results are correct and
complete.

**Acceptance Scenarios**:

1. **Given** the indexer is running, **When** a `PackageActivated`, `ManifestUpdated`, `PendingRelease`,
   `CheckIn`, `Renewed`, `GuardianStateReset`, `PackageRescued`,
   `Released`, `Revoked`, or guardian event is emitted on-chain and has reached the confirmation
   depth, **Then** the event is recorded in the local index within **≤ 15 seconds** (one indexer polling cycle).

2. **Given** a chain reorganization occurs — i.e., a previously indexed block is superseded by a
   competing chain — **When** the indexer detects a `blockHash` mismatch, **Then** it rewinds the
   index to the last consistent block and replays events from there; no orphaned events remain.

3. **Given** the paginated event-feed endpoint is called with an owner address filter, **When** the
   query is issued, **Then** results contain only events for packages owned by that address,
   ordered by block/log index, with correct pagination cursors.

4. **Given** a `PendingRelease` or `Released` event is indexed, **When** a notification target
   (email/webhook) is configured for the affected package, **Then** a notification is dispatched
   within a best-effort window; a delivery failure MUST NOT block the indexer or any recovery
   flow.

5. **Given** the event stream is replayed from the genesis block of the contract, **When** replay
   completes, **Then** the resulting index state is identical to the state produced by live
   incremental processing.

---

### Edge Cases

- What happens when a `packageKey` has never been activated? Status MUST be `DRAFT` (not an
  error, not null) — read from the contract, not from a missing DB row.
- What happens when the chain RPC is temporarily unavailable? **Auth-gated read endpoints** (those
  performing a live chain authorization check: guardian access verification, beneficiary validation,
  notification-target registration) MUST return HTTP 503. **Pure indexed-data read endpoints**
  (package status from DB cache, event history, stored artifacts, paginated package lists) MAY
  return cached data and MUST include an `X-Data-Staleness-Seconds` response header indicating
  seconds since last successful sync. All write/payload endpoints that require live authorization
  MUST return HTTP 503.
- What happens if two concurrent requests attempt to process the same event during replay? Event
  processing MUST be idempotent; duplicate processing MUST produce the same state as a single
  processing.
- What happens when a guardian is removed from the guardian set on-chain? Subsequent guardian
  endpoint calls must re-verify the guardian set from the chain; a cached guardian set MUST NOT
  grant access after removal.
- What happens if the manifest validation endpoint receives a manifest referencing a `packageKey`
  that the backend has not indexed yet? The service MUST perform a live chain read to validate
  rather than returning a false negative based on missing DB records.
- What happens when a notification delivery fails (network error, invalid email, webhook timeout)?
  The failure MUST be logged, retried a bounded number of times, and then recorded as undelivered;
  it MUST NOT propagate as an error to the event indexer or any user-facing endpoint.
- What happens when more than 7 guardians are submitted in a request? The service MUST reject the
  request with HTTP 400 — this mirrors the contract-level constraint.
- What happens when the `domain` field in a SIWE signed message does not match `ARCA_SIWE_DOMAIN`?
  The service MUST reject the authentication attempt with HTTP 401 and MUST NOT issue a session
  token; this prevents replay of messages signed for other origins.
- What happens when a notification subscription is created for a package where the caller is
  neither owner nor beneficiary? The service MUST reject the request with HTTP 403; subscription
  registration is restricted to authenticated owners and beneficiaries of the specific package.
- What happens when the beneficiary calls the claim transaction-payload endpoint for a package
  in `PENDING_RELEASE` (grace not yet elapsed, status not yet `CLAIMABLE`)? The service MUST
  detect the status via a live chain read and return HTTP 409 indicating the package is not yet
  CLAIMABLE; it MUST NOT return a payload that will revert on-chain with `GracePeriodNotElapsed()`.
- What happens when the owner calls the checkIn transaction-payload endpoint for a package in
  `CLAIMABLE` state? The service MUST detect the status via a live chain read and return HTTP 409;
  it MUST NOT return a checkIn payload that will revert on-chain with `AlreadyClaimable()`.
- What happens when a request supplies a `proxyAddress` or `chainId` that does not match the
  service's configured values? The service MUST reject with HTTP 400 and a message identifying
  the mismatch (e.g., `"proxyAddress does not match configured proxy"`).
- What happens when a non-owner submits a `renew()` payload for a package where the owner-only
  recovery+reset branch would trigger (pre-extension status was funding-lapse-only `PENDING_RELEASE`
  and the extension would exit pending)? The contract enforces `NotOwner()` at execution time;
  the backend does not restrict who may request the `renew` payload, but callers should be aware
  that this branch requires the submitting wallet to be the package owner.

---

## Requirements *(mandatory)*

### Functional Requirements

**Authentication**

- **FR-001**: The service MUST issue a unique, time-limited nonce to any client requesting
  authentication, binding it to the requesting address.
- **FR-002**: The service MUST verify a Sign-In With Ethereum (SIWE / EIP-4361) signed message
  against the issued nonce, the claimed address, and the configured service domain before issuing
  a session token. The `domain` field in the signed message MUST exactly match the value of
  `ARCA_SIWE_DOMAIN`; a mismatch MUST be rejected with HTTP 401.
- **FR-002a**: The service MUST reject SIWE verification attempts where the signed message `domain`
  does not exactly match `ARCA_SIWE_DOMAIN`, preventing cross-origin replay of signed messages.
- **FR-003**: The service MUST reject replayed SIWE signatures (nonce already consumed).
- **FR-004**: The service MUST issue session tokens with a configurable short lifetime; expired
  tokens MUST be rejected on all protected endpoints.

**Authorization**

- **FR-005**: The service MUST determine owner, guardian, and beneficiary roles exclusively from
  live on-chain reads against the proxy contract; the DB cache MUST NOT be the sole authorization
  source.
- **FR-006**: Owner-only endpoints MUST return HTTP 403 when the session address does not match
  the on-chain owner address for the specified package.
- **FR-007**: Guardian endpoints MUST return HTTP 403 when the session address is not present in
  the on-chain guardian set for the specified package.

**Package Discovery**

- **FR-008**: The service MUST expose a package-status endpoint that returns all fields from
  the contract's `PackageView` struct: on-chain status (from `getPackageStatus()`), owner
  address, beneficiary address, manifest URI, guardian list, guardian quorum, veto count,
  approval count, warn threshold, inactivity threshold, grace period (seconds), last check-in
  timestamp, paid-until timestamp, pending-since timestamp, and released-at timestamp.
- **FR-009**: The service MUST return status `DRAFT` for any `packageKey` that has no on-chain
  activation record; `DRAFT` is a valid response, not an error.
- **FR-009a**: The service MUST surface `WARNING` and `CLAIMABLE` as distinct, first-class status
  values in all package-status responses. These values are lazily derived by the contract's
  `getPackageStatus()` view call; the backend MUST NOT substitute or collapse them to adjacent
  statuses (e.g., treating `WARNING` as `ACTIVE`, or treating `CLAIMABLE` as `PENDING_RELEASE`).
- **FR-010**: The service MUST support an unauthenticated read of package status for the
  recovery-kit path (the recovery kit endpoint must not require authentication).
- **FR-010a**: The service MUST expose an unauthenticated `GET /config` endpoint that returns
  `{chainId, proxyAddress, fundingEnabled}`. `fundingEnabled` MUST be `true` when the
  configured instance has `pricePerSecond != 0`, and `false` otherwise. Clients MUST use this
  endpoint to gate UI flows for `/tx/renew` and ETH-bearing `/tx/activate` requests; a
  `fundingEnabled: false` instance MUST NOT allow these flows to proceed to calldata generation
  (doing so would produce calldata that reverts with `FundingDisabled()` on-chain).

**Owner Transaction Payloads**

- **FR-011**: The service MUST provide an endpoint that accepts a manifest reference and returns
  an unsigned `activate(...)` transaction payload (calldata + target address + gas estimate).
- **FR-012**: The service MUST provide endpoints that return unsigned payloads for `checkIn(...)`,
  `renew(...)`, `guardianApprove(...)`, `guardianVeto(...)`, `guardianRescindVeto(...)`,
  `guardianRescindApprove(...)`, `claim(...)`, `revoke(...)`, and `rescue(...)` contract calls.
- **FR-012c**: The `rescue` payload endpoint is **unauthenticated** at the backend level; the
  contract enforces `NotAuthorized()` if the caller is not the stored upgrade authority. The
  backend only encodes `rescue(packageKey)` calldata; access control is fully on-chain.
- **FR-012a**: The `checkIn` payload endpoint MUST perform a live chain status read before
  returning a payload; if the package status is `CLAIMABLE`, the endpoint MUST return HTTP 409
  with a clear error message rather than a payload that will revert on-chain with
  `AlreadyClaimable()`.
- **FR-012b**: The `renew` payload endpoint is **unauthenticated**; any caller (including
  unauthenticated anonymous callers) MUST be able to request the unsigned `renew()` calldata.
  The contract enforces no access control on `renew()` — it is a pure ETH-payment extension
  callable by anyone. The backend MUST NOT require a session token on this endpoint.
- **FR-012d**: **General pre-flight principle**: any tx endpoint that already performs a live
  chain status read (for auth or an existing guard) MUST additionally return HTTP 409 for any
  predictable status-based on-chain revert derivable from that status read. No extra RPC call
  MUST be added to an endpoint solely for pre-flight. Specifically:
  - Owner endpoints (`checkIn`, `updateManifestUri`, `revoke`) MUST return 409 when status is
    `RELEASED` or `REVOKED` (the live owner-auth read already provides the status).
  - `checkIn` additionally MUST return 409 when status is `CLAIMABLE` (FR-012a).
  - Guardian endpoints (`guardianApprove`, `guardianVeto`, `guardianRescindVeto`,
    `guardianRescindApprove`) MUST return 409 when status is not `PENDING_RELEASE` (`NotPending()`)
    or when status is `RELEASED` or `REVOKED` (the live guardian-auth read already provides status).
  - `claim` MUST return 409 when status is not `CLAIMABLE` (FR-017a).
  - Unauthenticated endpoints (`/tx/renew`, `/tx/rescue`) MUST NOT add a live status read solely
    for pre-flight; they return calldata unconditionally (the chain enforces correctness).
- **FR-013**: No write endpoint MUST sign a transaction or submit it to the network on behalf of
  any user; the service returns calldata only.

**Guardian Transaction Payloads**

- **FR-014**: The service MUST validate that the requesting session address is in the on-chain
  guardian set before returning any guardian transaction payload.

**Beneficiary Recovery**

- **FR-015**: The service MUST provide a recovery-kit endpoint that returns: `chainId`, proxy
  address, `packageKey`, `manifestUri`, `releasedAt`, and Lit ACC parameters (bound condition +
  beneficiary constraint).
- **FR-016**: The recovery-kit endpoint MUST perform a live chain read when the local index is
  absent or stale; it MUST NOT return an error solely because the DB has no record of the package.
- **FR-017**: The service MUST provide a claim transaction payload endpoint for beneficiaries.
- **FR-017a**: The claim payload endpoint MUST perform a live chain status read; if the package
  status is not `CLAIMABLE`, the endpoint MUST return HTTP 409 with a message indicating the
  current status; it MUST NOT return a claim payload for a package in `PENDING_RELEASE` or any
  earlier state.

**Lit Protocol Integration**

- **FR-018**: The service MUST expose an ACC-template endpoint that returns a complete, correctly
  formed Lit ACC for a given `(chainId, proxy, packageKey, beneficiary)` tuple.
- **FR-019**: The ACC MUST condition unwrap on `policyProxy.isReleased(packageKey) == true`
  evaluated against the specified proxy address, and MUST restrict the requester to the
  beneficiary address.
- **FR-020**: The service MUST expose a manifest-validation endpoint (`POST /validate-manifest`)
  implementing three validation layers:
  1. **Structural + address correctness (local)**: all required fields present (`packageKey`,
     `policy.chainId`, `policy.contract`, `keyRelease.accessControl`, `keyRelease.requester`,
     `keyRelease.encryptedSymmetricKey`); ACC `contractAddress` matches the known proxy address;
     `chainId` matches the configured chain; ACC `functionName` == `isReleased`; ACC
     `functionParams[0]` == `packageKey`; `requester` == manifest `beneficiary` field.
  2. **Live Ethereum RPC read**: confirm `packageKey` is activated on-chain (status != `DRAFT`)
     and the on-chain beneficiary address matches the manifest `requester` field.
  3. **`encryptedSymmetricKey` blob guard**: the value MUST be a non-empty, non-whitespace
     string of plausible minimum length (> 0 bytes after trimming); no cryptographic or
     Lit-network check is performed.
  Any layer failure MUST return HTTP 400 with a structured error identifying the failing check.
  No Lit network call is ever made by this endpoint.
- **FR-021**: The service MUST NOT store, log, or transmit raw DEKs, plaintext assets, or Lit
  session secrets at any point.

**Storage & Integrity**

- **FR-022**: The service MUST accept manifest JSON and ciphertext blobs and pin them to at least
  one configured storage backend (IPFS gateway and/or object storage).
- **FR-023**: Before confirming storage, the service MUST verify the sha256 hash of the ciphertext
  blob against the hash declared in the manifest; mismatches MUST result in HTTP 422.
- **FR-024**: The service MUST provide retrieval of stored artifacts by their storage URI.
- **FR-025**: The service MUST NOT attempt to decrypt or inspect either the manifest (treat its
  Lit fields as opaque) or the ciphertext content.

**Event Indexing**

- **FR-026**: The service MUST index the following on-chain events: `PackageActivated`,
  `ManifestUpdated`, `CheckIn`, `Renewed`, `GuardianApproved`, `GuardianVetoed`,
  `GuardianVetoRescinded`, `GuardianApproveRescinded`, `GuardianStateReset`,
  `PendingRelease`, `Released`, `Revoked`, `PackageRescued`.
  - `CheckIn` MUST update `package_cache.lastCheckIn`.
  - `Renewed` MUST update `package_cache.paidUntil`.
  - `GuardianStateReset` MUST bulk-clear the guardian flags in `guardian_cache` for the package.
  - `PackageRescued` MUST clear `package_cache.pendingSince`.
- **FR-027**: The indexer MUST be reorg-safe: it MUST store each processed block's hash, detect
  a mismatch between a stored block hash and the canonical chain, and rewind to the last
  consistent block before replaying.
- **FR-028**: The indexer MUST apply a configurable confirmation depth before treating an event
  as finalized.
- **FR-028a**: On first run (no sync state in `processed_blocks`), the indexer MUST begin
  scanning from the block number configured in `ARCA_INDEXER_START_BLOCK`. This value MUST be
  set to the contract deployment block; syncing from before deployment is wasted work, and
  syncing from service-start silently drops historical events. On subsequent starts the indexer
  MUST resume from the last processed block stored in `processed_blocks`, ignoring
  `ARCA_INDEXER_START_BLOCK`.
- **FR-029**: Event processing MUST be idempotent; re-processing an already-indexed event MUST
  NOT produce duplicate records.
- **FR-030**: The service MUST support paginated queries of indexed events filtered by owner
  address, guardian address, beneficiary address, and/or time range; on-chain enumeration of all
  packages MUST NOT be used for any query.

**Notifications**

- **FR-031**: The service MUST dispatch best-effort notifications for `PendingRelease`,
  `Released`, `Revoked`, and guardian approve/veto/rescind events via at least one configurable
  channel (email, webhook, or push).
- **FR-031a**: The service MUST provide authenticated REST endpoints for a package owner or
  beneficiary to create, update, and delete a `NotificationTarget` (subscription) for a specific
  package and one or more event types; no other role may register or modify subscriptions for a
  package.
- **FR-031b**: The service MUST support at least one of: email address, webhook URL, or push
  token as a notification target type per subscription record.
- **FR-031c**: The service MUST validate that the address registering a subscription is the
  on-chain owner or beneficiary of the referenced package before persisting the target.
- **FR-032**: Notification delivery failures MUST be logged and retried a bounded number of
  times; failures MUST NOT cause errors in the indexer or any API endpoint.
- **FR-033**: Notification delivery MUST NOT be on the critical path of recovery; a beneficiary
  MUST be able to complete recovery without ever having received a notification.

**Logging & Secret Hygiene**

- **FR-034**: No log entry, error message, or API response body MUST contain raw DEKs, wallet
  private keys, seed phrases, or Lit session secrets.
- **FR-035**: Fields named `key`, `secret`, `password`, `seed`, `dek`, or `private` MUST be
  scrubbed or omitted from all log output at the framework level.

---

### Non-Functional Requirements

**Deployment Model**

- **NFR-001**: The MVP deployment model is a **single-instance primary**. The service MUST NOT
  require distributed locking, leader election, or consensus machinery to operate correctly.
- **NFR-002**: The event indexer MUST run as a single goroutine/thread within the service
  process. Multiple concurrent indexer instances MUST NOT run against the same database; the
  idempotency guarantee (FR-029) is the safety net for accidental overlap, not the intended
  operating mode.
- **NFR-003**: The service MUST be horizontally scalable in a future phase without architectural
  redesign: all mutable state lives exclusively in the PostgreSQL database; no in-process shared
  state is required between instances. API instances may be load-balanced freely; the indexer
  must continue to run as a singleton.
- **NFR-004**: The service MUST expose a liveness and readiness health endpoint (`/health/live`
  and `/health/ready`) suitable for container orchestrator probes. Readiness MUST reflect DB
  connectivity and RPC node reachability.
- **NFR-005**: The event indexer MUST poll for new blocks at a configurable interval with a
  default of **15 seconds**. Events confirmed to the configured depth MUST appear in the local
  index within one polling cycle of block confirmation.
- **NFR-006**: Package-status and recovery-kit read endpoints MUST achieve **p95 latency ≤ 500 ms**
  under normal load, measured from request receipt to response sent, inclusive of any live RPC
  call required for authorization. DB-backed read paths MUST be indexed appropriately to sustain
  this target at ≥ 1 million package rows.
- **NFR-007**: The service is configured at startup for exactly **one policy proxy address** and
  **one chain ID** (`ARCA_POLICY_PROXY_ADDRESS`, `ARCA_EVM_CHAIN_ID`). Any request body or query
  parameter supplying a different `proxyAddress` or `chainId` MUST be rejected with HTTP 400 and
  an error message identifying the mismatch. Multi-proxy and multi-chain operation are explicit
  out-of-scope future extensions; the MVP does not support routing across multiple proxies or
  chains.

---

### Key Entities

- **Package**: A vault unit identified by `(chainId, proxy address, packageKey)`. Carries status,
  owner, guardian list, beneficiary, manifest URI, and lifecycle timestamps. Status values:
  `DRAFT`, `ACTIVE`, `WARNING`, `PENDING_RELEASE`, `CLAIMABLE`, `RELEASED`, `REVOKED`.
  `WARNING` and `CLAIMABLE` are lazily derived by the contract's `getPackageStatus()` view
  function and are never written to on-chain storage; the backend treats them as first-class
  statuses and MUST NOT collapse them to adjacent values.

- **Guardian**: An address authorized (on-chain) to approve or veto a pending release for a
  specific package. Maximum 7 per package. Carries their current approval/veto state for a
  pending release.

- **Event Record**: An indexed on-chain log entry. Carries: event type, package reference,
  emitting address, block number, block hash, log index, timestamp. Used for paginated queries
  and notification dispatch. Replayable from contract genesis.

- **Session / Nonce**: A short-lived, single-use authentication artifact. Nonce binds to a
  wallet address; a consumed nonce cannot be replayed. Session token carries address and
  expiration.

- **Manifest**: A structured public metadata document. Carries: vault package reference, manifest
  hash, ciphertext URI, ciphertext hash, Lit ACC parameters (including `encryptedSymmetricKey`),
  and any additional vault metadata. Treated as entirely public.

- **StoredArtifact**: A pinned binary or JSON document. Carries: storage URI(s), sha256 hash,
  artifact type (manifest | ciphertext), creation timestamp, and storage backend confirmation.

- **NotificationTarget**: A delivery subscription registered by a package owner or beneficiary
  via the authenticated API. Associates a specific package with one or more event types and a
  delivery channel (email address, webhook URL, or push token). Carries: subscriber address,
  package reference, subscribed event types, channel type, channel value, creation timestamp,
  last delivery attempt timestamp, and last delivery status. Restricted to on-chain owners and
  beneficiaries; enforced by live chain read at registration time.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A beneficiary can obtain a complete, correct recovery kit (chain coordinates + Lit
  ACC) and complete asset recovery without any backend availability — using only the chain, the
  manifest, and the Lit network. This is verifiable in a controlled drill exercise.

- **SC-002**: All write (transaction-payload) endpoints return unsigned calldata; zero write
  operations submit transactions on behalf of users. Verified by automated test suite covering
  all 8 contract call types; zero submissions allowed.

- **SC-003**: No secrets (raw DEKs, private keys, seed phrases, Lit session secrets) appear in
  any log line, API response, or database record. Verified by automated secret-pattern scanning
  of logs and database snapshots in the test suite.

- **SC-004**: The event indexer correctly recovers from a deliberate chain reorganization in a
  testnet environment: post-reorg indexed state matches canonical state with zero orphaned or
  missing events. Verified as an automated integration test.

- **SC-005**: A manifest with an incorrect proxy address, a beneficiary mismatch against the
  on-chain record, or a missing/empty `encryptedSymmetricKey` is rejected 100% of the time by
  the manifest-validation endpoint. Verified by a property-based fuzz test covering all three
  validation layers.

- **SC-006**: Paginated queries for packages associated with a given address return results at
  **p95 ≤ 500 ms** at a dataset size of at least one million packages, without on-chain
  enumeration. Verified by a load test against a seeded local database.

- **SC-007**: A notification delivery failure for one package does not produce any error response
  on any API endpoint and does not stall the event indexer. Verified by injecting delivery
  failures in the test suite and asserting no propagation.

- **SC-008**: Session tokens that have expired or been replayed are rejected with HTTP 401 on
  100% of calls. Verified by targeted auth regression tests.

---

## Assumptions

- The EVM policy smart contract is already deployed and its ABI is available to the backend team.
  Backend integration uses the proxy address exclusively; the implementation address is an
  internal contract detail. The contract deployment block number is a known value at deploy time
  and MUST be supplied as `ARCA_INDEXER_START_BLOCK`.
- `getPackageStatus(packageKey)` returns `DRAFT` for any package key not yet activated; the
  backend mirrors this as a first-class status, not an error.
- Guardian set size is bounded at 7 per package, enforced by the contract; the backend validates
  this constraint on input.
- Lit Protocol is used for conditional symmetric-key access gating. The backend generates and
  validates ACC templates but never performs DEK unwrapping; that step is always client-side.
- `encDEK` (Lit `encryptedSymmetricKey`) is considered public data — it is the ciphertext of the
  DEK under the Lit network's threshold keys, not a plaintext secret — and may be stored and
  returned in API responses.
- Public storage (IPFS and/or object storage) is the canonical home for manifest JSON and
  ciphertext blobs. The backend acts as a pinning/integrity-checking intermediary, not the
  ultimate archive.
- Notification delivery channels and credentials are configured via environment/infrastructure
  config outside the backend's own data store; the backend does not manage notification-channel
  secrets at runtime.
- The MVP runs as a single process instance. No distributed coordination (distributed locks,
  consensus, leader election) is required at MVP. Horizontal scaling is deferred; the design
  MUST NOT depend on shared in-process state to achieve correctness.
- The service operates against a **single configured policy proxy address** on a **single
  configured chain**. The contract is inherently single-proxy per deployment; there is no
  on-chain multi-tenancy to serve. Multi-chain deployments (running multiple instances against
  different proxies/chains) are a future extension; the MVP does not support routing across
  multiple proxies or chains.
- The backend does not govern, observe, or respond to proxy-contract upgrade events for
  authorization purposes; proxy upgrades are an infrastructure concern. The backend may optionally
  index upgrade events for informational UX purposes only.
