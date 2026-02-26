# Implementation Plan: Arca Java Backend

**Branch**: `001-java-backend` | **Date**: 2026-02-26 | **Spec**: [specs/001-java-backend/spec.md](spec.md)
**Input**: Feature specification from `/specs/001-java-backend/spec.md`

## Summary

Build a Spring Boot 3.x REST API that serves as the operational layer for the ArcaDigitalis Vault:
authenticates wallets via SIWE, provides non-custodial transaction payloads for all 8 contract
operations, generates and validates Lit ACCs, indexes vault events with reorg recovery, pins
artifacts through IPFS/S3 adapters, and dispatches best-effort notifications — all with zero
secret custody and full beneficiary-independent recovery support.

## Technical Context

**Language/Version**: Java 21+
**Framework**: Spring Boot 3.x (Spring MVC, Spring Security, Spring Data JPA, Spring Retry)
**EVM Library**: Web3j 4.x — RPC client, ABI encoding/decoding, log subscription
**Auth**: SIWE / EIP-4361 (manual parse or `com.spruceid:siwe-java` thin wrapper); JWT via `io.jsonwebtoken:jjwt` 0.12+
**Storage**: PostgreSQL 15+; schema migrations via Flyway; persistence layer via Spring Data JPA + Hibernate
**Storage Adapters**: Infura IPFS HTTP API (primary) + AWS SDK v2 S3 (secondary); pluggable via `StoragePort` interface
**Notifications**: Spring `@Async` + `@Scheduled` dispatcher; Spring Mail (SMTP email) + `RestTemplate`/`WebClient` (webhook); Spring Retry for bounded retries
**Testing**: JUnit 5, Spring Boot Test, Testcontainers (PostgreSQL), WireMock (RPC/IPFS mocks), REST Assured or MockMvc
**Target Platform**: Linux container; Docker Compose for local development; Kubernetes-ready
**Project Type**: web-service
**Performance Goals**: p95 ≤ 500 ms on status/recovery-kit endpoints (NFR-006); indexer poll cycle ≤ 15 s (NFR-005)
**Constraints**: Single-instance MVP (NFR-001); no distributed locks or leader election; PostgreSQL is sole state store; no in-process shared state required between instances
**Scale/Scope**: ≥ 1 million packages in DB without degradation (SC-006); ≥ 10 on-chain events per block processed correctly

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Gate | Evidence | Status |
|-----------|------|----------|--------|
| I. Zero-Secret Custody | No secret material stored, logged, or transited | FR-013 (no signing), FR-021/034/035 (no DEK/key in logs or DB), `encDEK` classified as public | ✅ PASS |
| II. Recovery Independence | Beneficiary can recover without backend | SC-001, FR-015/016 (live chain fallback), notifications decoupled from recovery path (FR-033) | ✅ PASS |
| III. On-Chain Authoritativeness | Auth & status from chain, not DB | FR-005/006/007 (live chain for every privileged call), FR-009/009a (7-state enum via `getPackageStatus()`), FR-016 (no false negative from empty cache) | ✅ PASS |
| IV. Non-Custodial Transactions | All write endpoints return unsigned calldata | FR-013 (explicit prohibition on signing/submit), FR-012a/017a (pre-flight guards return 409, not payload) | ✅ PASS |
| V. Policy-Bound Lit Integration | ACC binds to `isReleased(packageKey)` on proxy | FR-019 (ACC spec), FR-020 (3-layer manifest validation), no Lit network call server-side | ✅ PASS |
| VI. Scale-First Design | No on-chain enumeration; reorg-safe indexer | FR-030 (no enumeration), NFR-006 (p95 ≤ 500 ms), FR-027/028/029 (reorg, confirmation depth, idempotency) | ✅ PASS |

**Gate result**: ALL PASS — no violations. Proceed to Phase 0.

## Project Structure

### Documentation (this feature)

```text
specs/001-java-backend/
├── plan.md              # This file
├── research.md          # Phase 0: library decisions + best practices
├── data-model.md        # Phase 1: DB schema entities and relationships
├── quickstart.md        # Phase 1: local dev setup
├── contracts/
│   └── openapi.yaml     # Phase 1: REST API contract (OpenAPI 3.0)
└── tasks.md             # Phase 2 output (NOT created by /speckit.plan)
```

### Source Code (repository root)

```text
src/
├── main/
│   ├── java/com/arcadigitalis/backend/
│   │   ├── ArcaBackendApplication.java
│   │   ├── api/                        # Spring MVC controllers, DTOs, pagination
│   │   │   ├── auth/                   # POST /auth/nonce, POST /auth/verify
│   │   │   ├── packages/               # GET /packages/{key}/status, recovery-kit, tx payloads
│   │   │   ├── guardian/               # Guardian tx payload endpoints
│   │   │   ├── storage/                # POST /artifacts, GET /artifacts/{id}
│   │   │   ├── lit/                    # GET /acc-template, POST /validate-manifest
│   │   │   ├── notifications/          # Subscription CRUD
│   │   │   └── health/                 # GET /health/live, GET /health/ready
│   │   ├── auth/                       # SIWE parse/verify, JWT issue/validate
│   │   ├── evm/
│   │   │   ├── client/                 # Web3j RPC wrapper, retry, circuit-breaker
│   │   │   ├── indexer/                # Block poller, reorg detector, event dispatcher
│   │   │   └── calldata/               # ABI-encoded unsigned tx payload builders
│   │   ├── policy/                     # Domain services: role check, status mapping, pkg queries
│   │   ├── lit/                        # ACC template builder, manifest validator
│   │   ├── storage/
│   │   │   ├── ipfs/                   # Infura/Pinata IPFS HTTP adapter
│   │   │   └── s3/                     # AWS SDK v2 S3 adapter
│   │   ├── notifications/              # Async dispatcher, retry, channel adapters
│   │   └── persistence/
│   │       ├── entity/                 # JPA entities (PackageCache, EventRecord, ...)
│   │       ├── repository/             # Spring Data JPA repositories
│   │       └── migration/              # Flyway SQL (V1__init.sql, V2__...)
│   └── resources/
│       ├── db/migration/               # Flyway migration scripts
│       └── application.yml
└── test/
    ├── java/com/arcadigitalis/backend/
    │   ├── contract/                   # Spring MockMvc HTTP contract tests
    │   ├── integration/                # Testcontainers + WireMock integration tests
    │   └── unit/                       # Per-module unit tests
    └── resources/
        └── application-test.yml

pom.xml                                 # Maven build; or build.gradle if Gradle preferred
docker-compose.yml                      # Local dev: PostgreSQL + optional IPFS node
```

**Structure Decision**: Single-project Spring Boot service. Module boundaries enforced by
package-level conventions (constitution: `api` MUST NOT import `persistence` directly).
All inter-module data flow goes through `policy` service interfaces.


