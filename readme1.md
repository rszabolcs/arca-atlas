# Arca Backend (Java) — Implementation Overview (Smart Contract Already Exists)

This repository implements the **Java backend** for ArcaDigitalis Vault. The EVM smart contract(s) live in a **separate repository and are already implemented**. This backend integrates with that deployed contract (via the **proxy address**) and with **Lit Protocol**, and exposes a REST API for:
- Owner (package setup + maintenance),
- Guardians (approve/veto/rescind),
- Beneficiary (recovery UX + claim flow support).

**Critical requirement:** backend is a **convenience layer only**. If the company/backend disappears, recovery must remain possible with:
- chain RPC + policy proxy address + packageKey,
- manifest URI,
- ciphertext URI,
- Lit network.

The backend must not become a recovery dependency.

---

## 1) What the backend does (responsibilities)

### 1.1 REST API for UI/Mobile apps
- Provides a stable HTTP surface for the mobile app and web UI.
- Returns **public metadata**, computed status, and prepared transaction payloads.
- Supports two modes for transactions:
  1) **Client-signed mode (default):** backend returns unsigned tx data; client wallet signs and submits.
  2) **Relayed mode (optional future):** backend can submit via an external relayer (non-custodial) if later adopted.

### 1.2 EVM integration (read/write + indexing)
- Reads on-chain state from the **policy proxy address** (never the implementation address):
  - `getPackageStatus(packageKey)` (must return DRAFT for unknown keys),
  - `isReleased(packageKey)`, `getReleasedAt(packageKey)`,
  - `getManifestUri(packageKey)`,
  - event streams: Activated/ManifestUpdated/PendingRelease/Released/Revoked, plus guardian events.
- Produces ABI-encoded calldata for contract calls:
  - activate (owner)
  - checkIn / renew (owner)
  - guardianApprove / guardianVeto / guardianRescindVeto (guardian)
  - claim (beneficiary or permissioned actor per contract)
  - revoke (owner)
- Maintains an **optional local index** (DB) for fast queries:
  - latest manifest URI per packageKey,
  - last known status (mirror),
  - guardian set + quorum (mirror),
  - timestamps (pendingSince, releasedAt).
  This index is **replayable** from chain events.

### 1.3 Lit integration (policy-bound, non-custodial)
The contract already defines the release condition. Lit must gate DEK unwrap with an Access Control Condition (ACC) bound to:
- `policyProxy.isReleased(packageKey) == true`,
- requester constraint = beneficiary address.

Backend responsibilities for Lit:
- Generate and validate ACC templates bound to `(chainId, proxy, packageKey, beneficiary)`.
- Validate that a manifest’s Lit configuration is consistent with chain data.
- Provide a “Lit session guidance” endpoint for clients (what to sign, which chainId, which ACC).

**Important:** The backend MUST NOT persist secrets:
- No raw DEKs, no plaintext files, no private keys, no seed phrases.
- If any Lit SDK operation would require a raw DEK, it must be done **client-side** (preferred).
- The backend may optionally run a separate internal “lit-worker” (Node sidecar) for operational convenience, but recovery must not require it.

### 1.4 Storage/pinning convenience (encrypted artifacts)
- Accepts and stores/pins **public** artifacts:
  - manifest JSON (public metadata),
  - ciphertext blobs (already encrypted; treated as public),
  - integrity hashes (sha256).
- Verifies integrity:
  - manifest canonical hash,
  - ciphertext sha256 matches manifest,
  - (optional) owner signature over manifest hash.

### 1.5 Notifications (best-effort)
- Watches chain events and emits notifications (email/webhook/push):
  - PendingRelease materialized,
  - Released (release fact + time),
  - Revoked,
  - guardian approvals/veto/rescind.

Notifications are best-effort; lack of notification must not block recovery.

---

## 2) Security model (non-negotiables)

### 2.1 Data classification
Public (OK to store):
- chainId, policy proxy address, packageKey,
- manifestUri, ciphertextUri, hashes,
- encDEK (Lit encryptedSymmetricKey),
- on-chain status, releasedAt timestamp, events.

Private (must never store):
- raw DEK, plaintext assets,
- wallet private keys / seed phrases,
- guardian/beneficiary keys,
- upgrade authority keys,
- passwords, email credentials.

### 2.2 No custodial signing
- Backend does not hold user wallets.
- Default pattern is **client-signed transactions** and SIWE-based auth for API sessions.

---

## 3) Authentication & authorization

### 3.1 Authentication (recommended)
- **SIWE (Sign-In with Ethereum)** for API sessions:
  - client signs a nonce-bound message,
  - backend verifies signature and issues a short-lived JWT/session token.
- Backend authorization is derived from on-chain roles:
  - owner address for a package,
  - guardian set for a package,
  - beneficiary address for a package.

### 3.2 Authorization rules (examples)
- Owner endpoints require `caller == owner` (validated via signature session + on-chain read).
- Guardian endpoints require `caller in guardians[packageKey]` (validated via on-chain read).
- Beneficiary endpoints require `caller == beneficiary` for “recovery help” endpoints (optional).
- Backend must never “trust its own DB” for permissions; DB is a cache only.

---

## 4) Suggested architecture (Java)

- Spring Boot 3, Java 21+
- Modules:
  - `api`: REST controllers, DTOs, validation, pagination
  - `auth`: SIWE verification, sessions/JWT, nonce store
  - `evm`: chain client (RPC), ABI encoding/decoding, event ingestion, reorg handling
  - `policy`: domain services (status mapping, role checks, tx builders)
  - `lit`: ACC builder + manifest Lit validation utilities (client-guidance)
  - `storage`: IPFS/S3 adapters + hash verification
  - `persistence`: Postgres + Flyway/Liquibase (index cache)
  - `notifications`: dispatcher + templates

### 4.1 Reorg-safe indexing
- Store last processed block and log index.
- Handle reorgs by:
  - using confirmations (e.g., N blocks),
  - recording blockHash and rewinding when mismatch detected.

---

## 5) REST API (high-level shape)

All endpoints should support **client-signed mode**: return calldata + gas suggestions.

### 5.1 Package discovery
- `GET /v1/packages/{chainId}/{proxy}/{packageKey}`
  - returns cached + live status, roles, latest manifestUri, releasedAt.

### 5.2 Owner flows
- `POST /v1/packages/prepare-activation`
  - validates manifest structure (public only) and returns tx payload for `activate(...)`.
- `POST /v1/packages/{...}/check-in/tx`
- `POST /v1/packages/{...}/renew/tx`
- `POST /v1/packages/{...}/manifest/tx` (if contract supports setting manifestUri)
- `POST /v1/packages/{...}/revoke/tx`

### 5.3 Guardian flows
- `POST /v1/packages/{...}/guardian/approve/tx`
- `POST /v1/packages/{...}/guardian/veto/tx`
- `POST /v1/packages/{...}/guardian/rescind-veto/tx`

### 5.4 Beneficiary / recovery flows
- `GET  /v1/packages/{...}/recovery-kit`
  - returns chainId/proxy/packageKey/manifestUri + Lit ACC template parameters.
- `POST /v1/packages/{...}/claim/tx`
- `GET  /v1/packages/{...}/status` (live chain read, no cache reliance)

### 5.5 Lit helper endpoints (no secrets)
- `GET /v1/lit/acc-template?chainId=&proxy=&packageKey=&beneficiary=`
- `POST /v1/lit/validate-manifest`
  - checks that ACC references the proxy and correct packageKey and chainId.

---

## 6) Operational notes

- Target scale: **millions of packages** across users.
  - Never add “enumerate all packages on-chain” features.
  - Index via events and paginate via DB.
- Guardians limited to max 7.

---

## 7) What an agent should implement (summary)
Implement a Spring Boot service with SIWE auth, role checks via on-chain reads, tx payload builders, reorg-safe event indexer, storage adapters, Lit ACC template builder/validator, and best-effort notifications.
