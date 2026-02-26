# Implementation Plan: Arca Java Backend

**Branch**: `001-java-backend` | **Date**: 2026-02-26 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/001-java-backend/spec.md`

## Summary

Build a Spring Boot 3.x REST API service (Java 21+) that: authenticates wallets via SIWE/EIP-4361
with strict domain validation, resolves roles exclusively from live on-chain reads, returns
unsigned EVM transaction payloads (non-custodial), indexes contract events with a reorg-safe
poll-based indexer, pins manifests/ciphertexts to IPFS + S3, generates and validates Lit Protocol
ACC templates, and dispatches best-effort notifications. Single-instance MVP targeting a single
configured proxy address and chain ID. p95 ≤ 500 ms for status reads; indexer interval ≤ 15 s.

## Technical Context

**Language/Version**: Java 21+ (LTS)
**Framework**: Spring Boot 3.x
**Primary Dependencies**:
- `org.web3j:core` 4.x — EVM RPC, ABI encoding, `eth_getLogs`, SIWE ecrecover
- `org.web3j:web3j-spring-boot-starter` — auto-configuration
- `io.jsonwebtoken:jjwt-api` 0.12+ — JWT issuance / verification (HS256)
- `org.flywaydb:flyway-core` — DB schema migrations
- `software.amazon.awssdk:s3` (AWS SDK v2) — S3 storage adapter
- `org.springframework:spring-retry` — notification delivery retry with exponential back-off

**Storage**: PostgreSQL 15+ (sole state store); Flyway migrations V1–V8
**Testing**: JUnit 5, Spring Boot Test, Testcontainers (PostgreSQL), WireMock (RPC mocking)
**Target Platform**: Linux container (Docker Compose for local dev; Kubernetes-ready)
**Project Type**: web-service
**Performance Goals**: API status endpoints p95 ≤ 500 ms; indexer polling ≤ 15 s; throughput sufficient for ≥ 1 M package rows
**Constraints**:
- Single-instance MVP; no distributed locks or leader election
- Exactly one proxy address + one chain ID per instance (`ARCA_POLICY_PROXY_ADDRESS`, `ARCA_EVM_CHAIN_ID`)
- SIWE `domain` must match `ARCA_SIWE_DOMAIN` exactly (anti-replay)
- `api` module MUST NOT import `persistence` directly
- Zero transaction submissions; calldata only
- No secret material (DEKs, private keys) stored or logged

**Scale/Scope**: MVP ≥ 1 M package rows; single chain, single proxy; horizontal scale deferred

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Check | Status |
|---|---|---|
| I. Zero-Secret Custody | No DEKs, private keys, or Lit session keys stored or logged; `encDEK` is public ciphertext and treated as safe to store; FR-034/FR-035 enforce log scrubbing | ✅ |
| II. Recovery Independence | Recovery kit endpoint returns all data a beneficiary needs to self-recover without the backend; ACC + manifest are stored on public IPFS; SC-001 verifies end-to-end recovery without backend availability | ✅ |
| III. On-Chain Authoritativeness | FR-005: all role checks use live chain reads, never DB-only; FR-014: guardian membership re-verified per request; cache is read-only convenience replica | ✅ |
| IV. Non-Custodial Transactions | FR-012/FR-013: all write endpoints return unsigned calldata; zero tx submissions; SC-002 verified by automated test covering all 8 call types | ✅ |
| V. Policy-Bound Lit Integration | FR-018/FR-019/FR-020: ACC bound to `policyProxy.isReleased(packageKey)`, correct `chainId`, `packageKey`, `beneficiary`; incorrect proxy/chainId/packageKey → HTTP 400 | ✅ |
| VI. Scale-First Design | No on-chain enumeration; DB queries paginated and address-indexed; indexer reorg-safe (confirmation depth, blockHash mismatch rewind, idempotent processing); guardian set enforced ≤ 7 | ✅ |

No violations. Complexity Tracking section not required.

## Project Structure

### Documentation (this feature)

```text
specs/001-java-backend/
├── plan.md              # This file
├── research.md          # Phase 0 — technology decisions (updated 2026-02-26)
├── data-model.md        # Phase 1 — DB schema, 7 entities, Flyway V1–V8
├── quickstart.md        # Phase 1 — local dev setup, env config, build/run/test
├── contracts/
│   └── openapi.yaml     # Phase 1 — OpenAPI 3.0, 24 endpoints (updated 2026-02-26)
└── tasks.md             # Phase 2 output (/speckit.tasks — NOT created here)
```

### Source Code (repository root)

```text
src/
├── main/
│   ├── java/com/arcadigitalis/backend/
│   │   ├── api/                        # REST controllers, DTOs, request validation, pagination
│   │   │   ├── auth/                   # POST /auth/nonce, POST /auth/verify
│   │   │   ├── package/                # GET /packages/{packageKey}/status, recovery-kit
│   │   │   ├── tx/                     # Owner + guardian + beneficiary payload endpoints
│   │   │   ├── lit/                    # GET /lit/acc-template, POST /lit/validate-manifest
│   │   │   ├── storage/                # POST /artifacts, GET /artifacts/{id}
│   │   │   ├── events/                 # GET /events
│   │   │   ├── notifications/          # CRUD /notifications/targets
│   │   │   └── health/                 # GET /health/live, GET /health/ready
│   │   ├── auth/                       # SIWE parser, domain validation, JWT issue/verify, nonce mgmt
│   │   ├── contract/                   # Web3j wrappers, ABI type gen, calldata builders, RPC client
│   │   ├── indexer/                    # Reorg-safe poll loop, blockHash tracking, event dispatcher
│   │   ├── lit/                        # ACC template builder, manifest Lit-binding validator
│   │   ├── notification/               # @Async dispatcher, @Retryable, channel adapters
│   │   ├── policy/                     # Domain services: status mapping, role resolution, proxy guard
│   │   ├── storage/                    # StoragePort interface, IPFS adapter, S3 adapter
│   │   └── persistence/                # Spring Data JPA repositories, Flyway migrations
│   │       └── migration/
│   │           ├── V1__create_nonces.sql
│   │           ├── V2__create_package_cache.sql
│   │           ├── V3__create_guardian_cache.sql
│   │           ├── V4__create_event_records.sql
│   │           ├── V5__create_processed_blocks.sql
│   │           ├── V6__create_stored_artifacts.sql
│   │           ├── V7__create_notification_targets.sql
│   │           └── V8__add_indexes.sql
│   └── resources/
│       └── application.yml
└── test/
    ├── java/com/arcadigitalis/backend/
    │   ├── auth/                       # SIWE nonce, sig verify, domain check, replay rejection
    │   ├── contract/                   # ABI encoding round-trips, calldata correctness
    │   ├── indexer/                    # Reorg handling, confirmation depth, idempotency
    │   ├── lit/                        # ACC template correctness, manifest validation (fuzz)
    │   ├── policy/                     # Status mapping, role checks, proxy/chainId guard
    │   ├── storage/                    # IPFS + S3 adapter tests (WireMock)
    │   └── api/                        # Integration tests (Testcontainers + Spring Boot Test)
    └── resources/
        └── application-test.yml
```

**Structure Decision**: Single Spring Boot project. Module separation enforced by package
boundaries and architecture tests (ArchUnit), not Maven/Gradle subprojects — keeps build
tooling simple for MVP while making `api → persistence` violations detectable at CI.

## Post-Design Constitution Check

*Re-checked after Phase 1 design artifacts (research.md, data-model.md, openapi.yaml).*

All 6 gates remain ✅. New clarifications from Session 2026-02-26 (Q1–Q5) strengthen — not
weaken — existing gates:
- NFR-007 (single proxy/chain per instance) tightens Principle III.
- FR-002a (SIWE domain validation) closes a replay-attack surface against Principle III auth.
- FR-012b (renew unauthenticated) is consistent with Principle IV — the contract has no access
  control on `renew()`; requiring auth would add backend-only friction without security value.
- FR-028a (indexer start from deployment block) ensures complete event history, strengthening
  Principle II (full replayability from chain events).
