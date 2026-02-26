# Implementation Plan: Arca Java Backend

**Branch**: `001-java-backend` | **Date**: 2026-02-26 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/001-java-backend/spec.md`

## Summary

Build a non-custodial REST API service (Spring Boot 3.x / Java 21+) that acts as a
"glorified proxy" between the vault UI and two external systems: the deployed EVM policy
smart contract (via a single configured proxy address) and the Lit Protocol network.

The backend has four concrete responsibilities:
1. **ABI calldata encoding** — encode all 11 state-mutating contract functions into
   unsigned payloads; the client wallet signs and submits.
2. **Read/cache layer** — proxy `getPackageStatus()` and `getPackage()` live reads;
   maintain a reorg-safe indexed copy of all 13 contract events for fast paginated queries.
3. **Lit ACC assembly and validation** — generate and validate ACC JSON bound to
   `isReleased(packageKey) == true` on the canonical proxy; no Lit SDK, no Lit network calls.
4. **Operational services** — SIWE/JWT session management, artifact storage (IPFS/S3)
   with sha256 integrity, and best-effort notifications.

The backend **never** computes status, evaluates thresholds, or makes release decisions.
All business logic lives on-chain. See Proxy Boundary Audit — Session 2026-02-27 (round 4)
in spec.md for the full analysis.

---

## Technical Context

**Language/Version**: Java 21+

**Primary Dependencies**:
- Spring Boot 3.x (`spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-security`)
- Web3j 4.x (`org.web3j:core`, `org.web3j:web3j-spring-boot-starter`) — RPC client, ABI encoding, SIWE ecrecover
- jjwt 0.12+ (`io.jsonwebtoken:jjwt-api`, `jjwt-impl`, `jjwt-jackson`) — HS256 JWT session tokens
- Flyway 10.x (`org.flywaydb:flyway-core`) — SQL-first DB migrations
- Jackson (`com.fasterxml.jackson.core`) — JSON serialization/deserialization
- SpringDoc OpenAPI 2.x (`org.springdoc:springdoc-openapi-starter-webmvc-ui`) — OpenAPI UI

**Storage**: PostgreSQL 15+ — sole state store; 7 entities (V1–V8 Flyway migrations)

**Testing**:
- JUnit 5 + Mockito + AssertJ — unit tests for all module boundaries
- Testcontainers (`org.testcontainers:postgresql`) — integration tests against real PostgreSQL
- WireMock — RPC/HTTP stub for EVM and external service tests
- Secret-pattern log scan assertions — required per Constitution Principle I

**Build**: Maven 3.9+ (Maven Wrapper `./mvnw` committed)

**Target Platform**: JVM / Linux container; Docker Compose for local dev; cloud-ready

**Project Type**: web-service (REST API + background indexer thread)

**Performance Goals**:
- Package-status and recovery-kit endpoints: p95 ≤ 500 ms under normal load (NFR-006)
- Indexer polling interval: ≤ 15 seconds, configurable (NFR-005)
- DB-backed read paths must sustain p95 target at ≥ 1 million package rows (NFR-006)

**Constraints**:
- Single proxy address + single chain ID per instance (NFR-007): `ARCA_POLICY_PROXY_ADDRESS`, `ARCA_EVM_CHAIN_ID`
- `api` module MUST NOT import `persistence` directly (Constitution § Quality Bar)
- All data access goes through `policy` or dedicated service interfaces
- No distributed locking or leader election at MVP (NFR-001)
- Indexer runs as a single thread within the service process (NFR-002)

**Scale/Scope**: MVP; designed for millions of packages without on-chain enumeration (NFR-006, Constitution VI)

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Principle | Spec coverage | Status |
|---|-----------|---------------|--------|
| I | **Zero-Secret Custody** | FR-021 (no DEK/key storage/log/transit), FR-034 (no secrets in logs), FR-035 (field-level scrubbing at framework level), SC-003 (automated secret-scan test) | ✅ PASS |
| II | **Recovery Independence** | FR-015–FR-017 (recovery kit with live-read fallback), FR-016 (works with empty DB), SC-001 (beneficiary completes recovery without backend), all 13 events replayable from `ARCA_INDEXER_START_BLOCK` | ✅ PASS |
| III | **On-Chain Authoritativeness** | FR-005 (roles from live chain, DB never sole auth source), FR-006, FR-007, FR-014 (403 on cache-only auth), FR-008/FR-009a (status from `getPackageStatus()`, never computed in Java) | ✅ PASS |
| IV | **Non-Custodial Transactions** | FR-013 (no signing/submission), SC-002 (all payload endpoints return calldata only), covers all 11 contract call types including `rescue()` (FR-012c); `prepareUpdateManifestUri` ABI name corrected | ✅ PASS |
| V | **Policy-Bound Lit Integration** | FR-018–FR-020 (ACC bound to `isReleased(packageKey)==true` on proxy, requester==beneficiary, chainId, packageKey), 3-layer manifest validation, HTTP 400 on any mismatch, no Lit network calls from backend | ✅ PASS |
| VI | **Scale-First Design** | FR-030 (no on-chain enumeration), NFR-006 (p95 ≤ 500ms at 1M rows), FR-026–FR-029 (reorg-safe indexer: confirmation depth, blockHash, idempotency), V8 DB indexes | ✅ PASS |

**Pre-gate result: ALL 6 PASS — proceed.**

---

## Phase 0: Research Summary

Research complete in [research.md](research.md) (10 sections). All NEEDS CLARIFICATION items resolved.

| Decision | Rationale | Section |
|---|---|---|
| Manual SIWE parser + Web3j ecrecover | Avoids unmaintained `siwe-java`; fully auditable | §1 |
| Web3j 4.x | De facto Java EVM library; Spring Boot auto-config; ABI type safety | §2 |
| No Lit Java SDK | ACC is static JSON; no Lit network calls needed from backend | §3 |
| Flyway (SQL-first) over Liquibase | Simpler for this schema; Spring Boot 3.x auto-config | §4 |
| PostgreSQL-only DB | Sole state store; no Redis/secondary cache needed at MVP | §5 |
| Single-instance deployment | No distributed coordination at MVP; horizontal scale deferred | §6 |
| SIWE domain exact-match (`ARCA_SIWE_DOMAIN`) | Blocks cross-origin replay; configured per deployment | §9 |
| Indexer start-block = contract deployment block | Never genesis (wasteful); never service-start (drops history) | §10 |

---

## Phase 1: Design & Contracts

### Documentation

```text
specs/001-java-backend/
├── plan.md              ← this file
├── research.md          ← Phase 0 complete (10 sections)
├── data-model.md        ← Phase 1 complete (7 entities, state-transitions table)
├── quickstart.md        ← Phase 1 complete (.env template, startup steps)
├── contracts/
│   └── openapi.yaml     ← Phase 1 complete (all endpoints through round 4)
└── tasks.md             ← Phase 2 output (/speckit.tasks — NOT created by /speckit.plan)
```

### Data Model

See [data-model.md](data-model.md) — fully specified (7 PostgreSQL entities):

| Entity | Purpose |
|---|---|
| `nonces` | SIWE nonce lifecycle; single-use, expiry-bound |
| `package_cache` | Indexed `PackageView` fields incl. `last_check_in`, `paid_until` (all 13 event types update it) |
| `guardian_cache` | Guardian list per package; reset by `GuardianStateReset`; never used for auth |
| `event_records` | Immutable log of all 13 contract events; `raw_data` JSONB incl. `reason_flags` for `PendingRelease` |
| `processed_blocks` | Block hash per processed block for reorg detection |
| `stored_artifacts` | Pinned manifest + ciphertext blobs with sha256 |
| `notification_targets` | Owner/beneficiary subscriptions per package + event type |

Flyway migrations: `V1__create_nonces.sql` … `V8__create_indexes.sql`

### Interface Contracts

See [contracts/openapi.yaml](contracts/openapi.yaml) — reflects all 4 clarify rounds.

Endpoints added/changed since plan v1:
- `GET /config` — unauthenticated; `{chainId, proxyAddress, fundingEnabled}` (FR-010a)
- `POST /packages/{packageKey}/tx/rescue` — unauthenticated, `security:[]` (FR-012c)
- `PackageStatus` schema — all 15 `PackageView` fields (FR-008)
- 409 responses on all auth-gated tx endpoints where auth read predicts a status-based revert (FR-012d)
- `operationId: prepareUpdateManifestUri` — corrected ABI function name

### Source Tree

```text
src/main/java/com/arcadigitalis/backend/
├── api/
│   ├── controller/
│   │   ├── AuthController.java          # POST /auth/nonce, POST /auth/verify
│   │   ├── ConfigController.java        # GET /config (unauthenticated)
│   │   ├── PackageController.java       # GET /packages/{key}/status, /recovery-kit
│   │   ├── TxController.java            # POST /packages/{key}/tx/*  (11 endpoints)
│   │   ├── LitController.java           # GET /acc-template, POST /validate-manifest
│   │   ├── StorageController.java       # POST /artifacts, GET /artifacts/{id}
│   │   ├── EventController.java         # GET /events (paginated)
│   │   └── NotificationController.java  # CRUD /notification-targets
│   ├── dto/                             # Request/response DTOs (immutable records)
│   └── exception/                       # @ControllerAdvice, ProblemDetail mapping
│
├── auth/
│   ├── SiweParser.java                  # Line-by-line EIP-4361 parser
│   ├── SiweVerifier.java                # ecrecover + domain/nonce/expiry validation
│   ├── JwtService.java                  # HS256 issue + verify (jjwt 0.12+)
│   └── NonceService.java                # Nonce issue + consume (single DB tx)
│
├── evm/
│   ├── Web3jConfig.java                 # HttpService(ARCA_EVM_RPC_URL) bean
│   ├── PolicyReader.java                # getPackage(), getPackageStatus(), isReleased()
│   ├── CalldataBuilder.java             # ABI-encode all 11 mutating functions
│   ├── IndexerPoller.java               # eth_getLogs polling loop, blockHash tracking
│   ├── ReorgHandler.java                # blockHash mismatch detection + DB rewind
│   └── EventDecoder.java                # decode all 13 event types from EthLog
│
├── policy/
│   ├── PackageService.java              # Status reads, PackageView → DTO mapping
│   ├── RoleResolver.java                # Confirm owner/guardian/beneficiary from chain
│   ├── TxPayloadService.java            # Pre-flight 409 logic (FR-012a/d), delegate to CalldataBuilder
│   └── FundingGuard.java                # FR-010a: HTTP 400 when fundingEnabled=false + ETH value
│
├── lit/
│   ├── AccTemplateBuilder.java          # Static ACC JSON for (chainId, proxy, key, beneficiary)
│   ├── ManifestValidator.java           # 3-layer validation (local + RPC + blob guard)
│   └── ChainNameRegistry.java           # chainId → Lit chain name mapping
│
├── storage/
│   ├── ArtifactService.java             # Pin + retrieve; sha256 verified before confirm
│   ├── IpfsAdapter.java                 # Infura/Pinata IPFS gateway
│   └── ObjectStorageAdapter.java        # S3-compatible fallback
│
├── persistence/
│   ├── entity/                          # JPA entities for all 7 tables
│   ├── repository/                      # Spring Data JPA repositories
│   └── migration/
│       ├── V1__create_nonces.sql
│       ├── V2__create_package_cache.sql
│       ├── V3__create_guardian_cache.sql
│       ├── V4__create_event_records.sql
│       ├── V5__create_processed_blocks.sql
│       ├── V6__create_stored_artifacts.sql
│       ├── V7__create_notification_targets.sql
│       └── V8__create_indexes.sql
│
└── notifications/
    ├── NotificationDispatcher.java      # ApplicationEvent listener; fan-out per target
    ├── EmailDelivery.java               # SMTP adapter
    ├── WebhookDelivery.java             # HTTP POST adapter
    └── RetryPolicy.java                 # Bounded retry + dead-letter logging

src/test/java/com/arcadigitalis/backend/
├── auth/
│   ├── SiweVerifierTest.java            # MANDATORY: nonce, sig, replay, domain mismatch
│   └── JwtServiceTest.java              # Expiry, replay rejection
├── evm/
│   ├── CalldataBuilderTest.java         # MANDATORY: ABI round-trips for all 11 call types
│   ├── IndexerPollerTest.java           # MANDATORY: reorg rewind, idempotency
│   └── EventDecoderTest.java            # All 13 event types decoded correctly
├── policy/
│   ├── RoleResolverTest.java            # MANDATORY: chain-based role checks, cache bypass
│   ├── TxPayloadServiceTest.java        # Pre-flight 409 logic (FR-012a/d), funding guard
│   └── PackageServiceTest.java          # All 7 status values, DRAFT for unknown key
├── lit/
│   ├── AccTemplateBuilderTest.java      # MANDATORY: proxy binding, chainId, packageKey, requester
│   └── ManifestValidatorTest.java       # MANDATORY: all 3 layers; fuzz proxy/chainId/encDek
├── integration/
│   ├── AuthFlowIT.java                  # Full SIWE → JWT → protected endpoint flow
│   ├── IndexerReorgIT.java              # MANDATORY: blockHash mismatch → rewind → replay
│   ├── TxPayloadIT.java                 # All 11 payload endpoints; zero submissions
│   └── NotificationIT.java             # Delivery failure does not propagate to indexer
└── security/
    └── SecretLogScanTest.java           # MANDATORY: logs/responses free of secret-pattern fields
```

**Structure Decision**: Single Spring Boot project (Maven multi-module layout above).
No separate frontend. 8 domain modules under `com.arcadigitalis.backend`, each independently
testable. `api` → `policy` → `evm`/`persistence`/`lit`/`storage`/`notifications`; no reverse
dependencies; `api` never imports `persistence` directly per Constitution Quality Bar.

---

## Post-Design Constitution Check

Re-evaluated after 4 clarify rounds (spec + contract changes through 2026-02-27):

| # | Principle | New design elements verified | Status |
|---|-----------|------------------------------|--------|
| I | Zero-Secret Custody | `GET /config` exposes only public deployment constants. `/tx/rescue` returns unsigned calldata only. `fundingEnabled` is `pricePerSecond != 0` — not a secret. | ✅ PASS |
| II | Recovery Independence | `PackageRescued` clears `pending_since` in cache. All 13 events indexed and replayable from `ARCA_INDEXER_START_BLOCK`. Recovery kit live-read fallback unaffected by all additions. | ✅ PASS |
| III | On-Chain Authoritativeness | `fundingEnabled` read from chain at startup. Pre-flight 409s are backed by the same live read already required for owner/guardian auth — no new DB-only decision paths introduced. | ✅ PASS |
| IV | Non-Custodial Transactions | `rescue()` added to `CalldataBuilder`; endpoint `security:[]`; backend never signs. `prepareUpdateManifestUri` naming fix ensures correct ABI function selector in encoded calldata. All 11 functions covered. | ✅ PASS |
| V | Policy-Bound Lit Integration | No change to Lit module from any design round. ACC template and 3-layer manifest validation unchanged. `ManifestValidator` layer 2 still verifies on-chain beneficiary match. | ✅ PASS |
| VI | Scale-First Design | All 13 events indexed (incl. `CheckIn`, `Renewed` updating cache fields). No new enumeration paths. `event_records` indexed on `(chain_id, proxy_address, package_key, block_number)`. Guardian 409s reuse the auth live read, not a new scan. | ✅ PASS |

**Post-design result: ALL 6 PASS — Phase 1 design is constitution-compliant.**

---

## Complexity Tracking

*No constitution violations requiring justification.*
