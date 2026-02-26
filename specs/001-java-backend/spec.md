# Feature Specification: Arca Java Backend

**Feature Branch**: `001-java-backend`
**Created**: 2026-02-26
**Status**: Draft
**Input**: Build the Arca Java Backend — a REST API service for ArcaDigitalis Vault that integrates with an existing deployed EVM policy smart contract (via proxy address) and Lit Protocol, providing Owner, Guardian, and Beneficiary endpoints with SIWE authentication, non-custodial transaction payloads, reorg-safe event indexing, storage adapters, Lit ACC template generation/validation, and best-effort notifications.

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
   encodes the `activate(...)` contract call; submitting it produces an `Activated` event on-chain.

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

**Independent Test**: Emit a sequence of `Activated`, `PendingRelease`, `Released`, and `Revoked`
events on a testnet including a deliberate reorg (mine two competing blocks then resolve). Verify
the indexed state after reorg resolution matches the canonical chain; verify no duplicate events
appear. Query the paginated event feed filtered by owner address; verify results are correct and
complete.

**Acceptance Scenarios**:

1. **Given** the indexer is running, **When** an `Activated`, `ManifestUpdated`, `PendingRelease`,
   `Released`, `Revoked`, or guardian event is emitted on-chain and has reached the confirmation
   depth, **Then** the event is recorded in the local index within a reasonable polling interval.

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
- What happens when the chain RPC is temporarily unavailable? Write endpoints that require live
  authorization checks MUST fail safe (HTTP 503); read endpoints MAY return cached data with a
  staleness indicator.
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

---

## Requirements *(mandatory)*

### Functional Requirements

**Authentication**

- **FR-001**: The service MUST issue a unique, time-limited nonce to any client requesting
  authentication, binding it to the requesting address.
- **FR-002**: The service MUST verify a Sign-In With Ethereum (SIWE / EIP-4361) signed message
  against the issued nonce and the claimed address before issuing a session token.
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

- **FR-008**: The service MUST expose a package-status endpoint that returns: on-chain status,
  owner address, guardian list, beneficiary address, manifest URI, pending-since timestamp, and
  released-at timestamp.
- **FR-009**: The service MUST return status `DRAFT` for any `packageKey` that has no on-chain
  activation record; `DRAFT` is a valid response, not an error.
- **FR-010**: The service MUST support an unauthenticated read of package status for the
  recovery-kit path (the recovery kit endpoint must not require authentication).

**Owner Transaction Payloads**

- **FR-011**: The service MUST provide an endpoint that accepts a manifest reference and returns
  an unsigned `activate(...)` transaction payload (calldata + target address + gas estimate).
- **FR-012**: The service MUST provide endpoints that return unsigned payloads for `checkIn(...)`,
  `renew(...)`, `guardianApprove(...)`, `guardianVeto(...)`, `guardianRescindVeto(...)`,
  `claim(...)`, and `revoke(...)` contract calls.
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

**Lit Protocol Integration**

- **FR-018**: The service MUST expose an ACC-template endpoint that returns a complete, correctly
  formed Lit ACC for a given `(chainId, proxy, packageKey, beneficiary)` tuple.
- **FR-019**: The ACC MUST condition unwrap on `policyProxy.isReleased(packageKey) == true`
  evaluated against the specified proxy address, and MUST restrict the requester to the
  beneficiary address.
- **FR-020**: The service MUST expose a manifest-validation endpoint that checks the manifest's
  embedded ACC binds to the correct proxy address, `chainId`, and `packageKey`; it MUST reject
  manifestos (HTTP 400) that reference a different proxy or are missing required Lit fields
  (`encryptedSymmetricKey`).
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

- **FR-026**: The service MUST index the following on-chain events: `Activated`,
  `ManifestUpdated`, `PendingRelease`, `Released`, `Revoked`, plus all guardian events
  (approve/veto/rescind).
- **FR-027**: The indexer MUST be reorg-safe: it MUST store each processed block's hash, detect
  a mismatch between a stored block hash and the canonical chain, and rewind to the last
  consistent block before replaying.
- **FR-028**: The indexer MUST apply a configurable confirmation depth before treating an event
  as finalized.
- **FR-029**: Event processing MUST be idempotent; re-processing an already-indexed event MUST
  NOT produce duplicate records.
- **FR-030**: The service MUST support paginated queries of indexed events filtered by owner
  address, guardian address, beneficiary address, and/or time range; on-chain enumeration of all
  packages MUST NOT be used for any query.

**Notifications**

- **FR-031**: The service MUST dispatch best-effort notifications for `PendingRelease`,
  `Released`, `Revoked`, and guardian approve/veto/rescind events via at least one configurable
  channel (email, webhook, or push).
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

### Key Entities

- **Package**: A vault unit identified by `(chainId, proxy address, packageKey)`. Carries status,
  owner, guardian list, beneficiary, manifest URI, and lifecycle timestamps. Status values:
  `DRAFT`, `ACTIVE`, `PENDING_RELEASE`, `RELEASED`, `REVOKED`.

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

- **NotificationTarget**: A configured delivery endpoint (email address, webhook URL, push token)
  associated with a package and one or more event types. Carries delivery attempt log and last
  delivery status.

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

- **SC-005**: A manifest with an incorrect proxy address or missing `encryptedSymmetricKey` is
  rejected 100% of the time by the manifest-validation endpoint. Verified by a property-based
  fuzz test.

- **SC-006**: Paginated queries for packages associated with a given address return results within
  a user-perceivable time at a dataset size of at least one million packages, without on-chain
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
  internal contract detail.
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
- The backend does not govern, observe, or respond to proxy-contract upgrade events for
  authorization purposes; proxy upgrades are an infrastructure concern. The backend may optionally
  index upgrade events for informational UX purposes only.
