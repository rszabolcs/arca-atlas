# Arca Java Backend — Feature List (Agreed Scope)

This file enumerates the backend features we want to implement. The smart contract is already implemented in a separate repo; this backend integrates with it.

## A) REST API (for mobile/web UI)

### Roles supported
- Owner (create/maintain package)
- Guardians (approve/veto/rescind)
- Beneficiary (recovery assistance + claim flow)

### Endpoints (conceptual)
- Package discovery:
  - get live/cached status, roles, latest manifestUri, releasedAt
- Owner operations:
  - prepare activation tx payload
  - check-in tx payload
  - renew/deposit tx payload
  - update-manifest tx payload (if supported)
  - revoke tx payload
- Guardian operations:
  - approve / veto / rescind-veto tx payloads
- Beneficiary operations:
  - recovery kit endpoint (chainId/proxy/packageKey/manifestUri + Lit ACC parameters)
  - claim tx payload
- Event feed (optional convenience):
  - paginated event stream for UI (from indexed DB)

All write endpoints must support **client-signed mode** (return calldata + gas hints).

## B) Authentication & authorization

- SIWE authentication:
  - nonce issuance, signature verification, short-lived session token (JWT).
- Authorization is derived from chain reads:
  - owner/guardian/beneficiary addresses read from the policy proxy.
- DB must not be trusted for authorization (cache only).

## C) Ethereum / EVM integration

- Read-only:
  - contract calls on the **proxy address** (never implementation)
  - event indexing (Activated, ManifestUpdated, PendingRelease, Released, Revoked, guardian events)
- Write support:
  - ABI encoding for all contract calls used by the UI
  - optional relay integration later (non-custodial, external relayer)

## D) Lit integration (no secret custody)

- ACC template builder:
  - binds to `policyProxy.isReleased(packageKey)==true`
  - requester = beneficiary address
- Manifest validation:
  - confirms ACC references proxy address + correct chainId/packageKey
  - validates presence/format of encryptedSymmetricKey (encDEK)
- Client guidance:
  - provide “how to obtain Lit session and unwrap DEK” instructions (no raw DEK on backend)

## E) Storage & integrity

- Storage adapters:
  - IPFS pinning gateway and/or S3 bucket support
- Integrity checks:
  - sha256 verification for ciphertext and manifest
  - optional signature verification of manifest hash

## F) Notifications (best-effort)

- Event-driven notifications for:
  - PendingRelease
  - Released (release fact + releasedAt)
  - Revoked
  - guardian approve/veto/rescind
- Notification failures must not block recovery.

## G) Indexing and scale

- Reorg-safe indexer:
  - confirmation depth,
  - rewind on blockHash mismatch,
  - idempotent processing.
- Scale goal: millions of packages
  - no on-chain enumeration
  - DB pagination, filtering by address (owner/guardian/beneficiary), and time ranges.

## H) Non-features / constraints

- Backend stores no plaintext secrets and cannot decrypt assets.
- Backend is not required for beneficiary recovery.
- Proxy upgrade governance is out of scope for backend control; backend may only observe upgrade events for UX.
