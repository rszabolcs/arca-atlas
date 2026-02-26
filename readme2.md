# Backend Constitution Seed (Spec-Kit Input) — Arca Java Backend

Use this as a starting “constitution” for generating a backend spec with Spec-Kit.

## Purpose
Build a Java backend that powers the ArcaDigitalis Vault UI (mobile/web) by integrating with:
- an existing EVM policy smart contract (already implemented elsewhere; interact via proxy address),
- Lit Protocol (conditional key release),
- public storage (manifest + ciphertext).

The backend is a **convenience layer**; recovery must remain possible without it.

## Non-negotiables (hard rules)

1) No secrets handled or persisted
- Never store or log plaintext assets, raw DEKs, private keys, seed phrases, passwords, or Lit session secrets.
- Treat manifest URIs and ciphertext URIs as public by design.
- encDEK (Lit encryptedSymmetricKey) is public and may be stored.

2) Backend must not be required for recovery
- Recovery must be possible with chain RPC + manifest + ciphertext + Lit.
- Backend may cache/index, but must not be a single point of failure.

3) On-chain is authoritative
- Permissions and state are derived from on-chain reads (proxy address).
- The DB is a cache only and must be rebuildable from events.

4) Non-custodial transactions
- Default: client-signed tx payloads (calldata, gas hints) returned by the API.
- Backend must not require custody of user wallets.

5) Lit integration is policy-bound
- ACC must be bound to `policyProxy.isReleased(packageKey)==true` and requester = beneficiary.
- Backend provides ACC templates and validation; DEK unwrap and decryption remain client-side.

6) Scale and performance
- Must support millions of packages:
  - no “list all packages on-chain”
  - event indexing + paginated queries
- Reorg-safe indexing; idempotent processing.

## Required capabilities

- REST API for Owner, Guardians, Beneficiary.
- SIWE-based authentication and short-lived sessions.
- Chain integration:
  - read status, manifestUri, releasedAt,
  - build tx payloads for contract functions.
- Lit helpers:
  - ACC template generation,
  - manifest validation for ACC binding correctness.
- Storage:
  - pin/serve manifests and ciphertext (encrypted),
  - verify hashes (sha256) and signatures (optional).
- Notifications (best-effort) driven by chain events.

## Quality bar

- Clear separation between:
  - API layer,
  - chain layer,
  - persistence/indexing,
  - Lit helper layer,
  - storage adapter layer.
- Strict logging hygiene (no sensitive data).
- Comprehensive tests:
  - SIWE auth verification,
  - role checks derived from chain,
  - tx payload correctness (ABI encoding),
  - indexer reorg handling,
  - ACC validation correctness.

## Deliverables expected from the spec
- API contract (endpoints, request/response schemas, auth).
- Data model (DB schema for caches/indexing).
- Event indexing plan (replay, reorg strategy).
- Integration plan for Lit (no secrets server-side).
- Deployment/dev plan (docker-compose friendly).
