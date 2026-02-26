# Tasks: Arca Java Backend

**Input**: Design documents from `/specs/001-java-backend/`
**Prerequisites**: plan.md ✅, spec.md ✅, data-model.md ✅, contracts/openapi.yaml ✅, research.md ✅, quickstart.md ✅

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story?] Description`

- **[P]**: Can run in parallel (different files, no unresolved dependencies)
- **[Story]**: Which user story this task belongs to (US1–US7); setup/foundational/polish phases omit this
- Exact Java file paths use `src/main/java/com/arcadigitalis/backend/` prefix (abbreviated as `src/…/` in descriptions for brevity — expand to full path in implementation)

---

## Phase 1: Setup (Project Initialization)

**Purpose**: Bootstrap the Maven project, establish dependency graph, Docker Compose, and repository skeleton.
No user-story work can begin before this phase is complete.

- [ ] T001 Initialize Spring Boot 3.x Maven project with `./mvnw` wrapper at repository root; base package `com.arcadigitalis.backend`
- [ ] T002 [P] Configure `pom.xml` with all required dependencies: `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-security`, `web3j-spring-boot-starter` (4.x), `jjwt-api/impl/jackson` (0.12+), `flyway-core` (10.x), `springdoc-openapi-starter-webmvc-ui` (2.x), `testcontainers:postgresql`, `wiremock`, `assertj-core`, `junit-jupiter`
- [ ] T003 [P] Create main source tree skeleton: directories for `api/controller/`, `api/dto/`, `api/exception/`, `auth/`, `evm/`, `policy/`, `lit/`, `storage/`, `persistence/entity/`, `persistence/repository/`, `persistence/migration/`, `notifications/` under `src/main/java/com/arcadigitalis/backend/`
- [ ] T004 [P] Create test source tree skeleton: directories for `auth/`, `evm/`, `policy/`, `lit/`, `integration/`, `security/` under `src/test/java/com/arcadigitalis/backend/`
- [ ] T005 [P] Create `src/main/resources/application.yml` declaring all required env-var bindings: `ARCA_EVM_RPC_URL`, `ARCA_POLICY_PROXY_ADDRESS`, `ARCA_EVM_CHAIN_ID`, `ARCA_JWT_SECRET`, `ARCA_JWT_TTL_SECONDS` (default 3600), `ARCA_SIWE_DOMAIN`, `ARCA_INDEXER_START_BLOCK`, `ARCA_INDEXER_CONFIRMATION_DEPTH` (default 12), `ARCA_INDEXER_POLL_INTERVAL_SECONDS` (default 15), `ARCA_INDEXER_ENABLED` (default true), `ARCA_INDEXER_LOCK_ID` (default derived from proxy address hash)
- [ ] T006 [P] Create `docker-compose.yml` for local dev: PostgreSQL 15+ service (port 5432, named `arcadb`), backend service with all env-var placeholders sourced from `.env`; create `.env.example` matching `quickstart.md` template
- [ ] T007 Commit `./mvnw` and `./mvnw.cmd` Maven wrapper scripts to repository root; verify `./mvnw clean compile` succeeds on a clean checkout

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Database schema, JPA layer, Web3j bean, security filter chain, error handling, and health
endpoints that ALL user stories depend on. **No user story work begins until this phase is complete.**

⚠️ **CRITICAL: Blocks all user story phases (3–9)**

- [ ] T008 Create `src/main/resources/db/migration/V1__create_nonces.sql`: `nonces` table (id UUID PK, wallet_address VARCHAR(42) NOT NULL, nonce VARCHAR(64) UNIQUE NOT NULL, created_at TIMESTAMPTZ NOT NULL, expires_at TIMESTAMPTZ NOT NULL, consumed BOOLEAN NOT NULL DEFAULT false); unique index on `nonce`; partial index on `(wallet_address, consumed=false)`
- [ ] T009 [P] Create `V2__create_package_cache.sql`: `package_cache` table (all columns per data-model.md §1.2 incl. last_check_in, paid_until); unique constraint `(chain_id, proxy_address, package_key)`; indexes on `owner_address`, `beneficiary_address`
- [ ] T010 [P] Create `V3__create_guardian_cache.sql`: `guardian_cache` table (id UUID PK, package_cache_id UUID FK → package_cache(id) CASCADE DELETE, guardian_address VARCHAR(42) NOT NULL, position SMALLINT NOT NULL); unique constraint `(package_cache_id, guardian_address)` — *[P] applies to file authoring; Flyway applies migrations in version-ascending order, so V3 always executes after V2 regardless of authoring parallelism*
- [ ] T011 [P] Create `V4__create_event_records.sql`: `event_records` table (all columns per data-model.md §1.4 incl. raw_data JSONB, block_hash VARCHAR(66)); unique constraint `(tx_hash, log_index)` for idempotency; indexes on `(chain_id, proxy_address, package_key, block_number)`, `emitting_address`, `block_timestamp`, `event_type`
- [ ] T012 [P] Create `V5__create_processed_blocks.sql`: `processed_blocks` table (id BIGSERIAL PK, chain_id BIGINT NOT NULL, proxy_address VARCHAR(42) NOT NULL, block_number BIGINT NOT NULL, block_hash VARCHAR(66) NOT NULL); unique constraint `(chain_id, proxy_address, block_number)`; index on `(chain_id, proxy_address, block_number DESC)`
- [ ] T013 [P] Create `V6__create_stored_artifacts.sql`: `stored_artifacts` table (all columns per data-model.md §1.6 incl. ipfs_uri, s3_uri, storage_confirmed_at); unique constraint `(sha256_hash)`; index on `(chain_id, proxy_address, package_key, artifact_type)`
- [ ] T014 [P] Create `V7__create_notification_targets.sql`: `notification_targets` table (all columns per data-model.md §1.7 incl. event_types VARCHAR(40)[] NOT NULL, channel_type, channel_value, last_delivery_status); unique constraint `(chain_id, proxy_address, package_key, subscriber_address, channel_type, channel_value)`; index on `(chain_id, proxy_address, package_key, active=true)`
- [ ] T015 Create `V8__create_indexes.sql`: additional composite indexes for query performance per NFR-006 (1M-row targets): covering index on `package_cache(owner_address, chain_id, proxy_address)`, covering index on `event_records(package_key, block_number, log_index)`, partial index on `notification_targets` where `active = true`
- [ ] T016 Create all 7 JPA `@Entity` classes in `src/main/java/com/arcadigitalis/backend/persistence/entity/`: `NonceEntity`, `PackageCacheEntity`, `GuardianCacheEntity`, `EventRecordEntity`, `ProcessedBlockEntity`, `StoredArtifactEntity`, `NotificationTargetEntity` — with appropriate `@Column`, `@Table(uniqueConstraints=…)`, and `@OneToMany` / `@ManyToOne` mappings
- [ ] T017 [P] Create Spring Data JPA `@Repository` interfaces in `src/main/java/com/arcadigitalis/backend/persistence/repository/`: one per entity with custom query methods needed for each story (e.g. `findByPackageCacheId`, `findByChainIdAndProxyAddressAndBlockNumberOrderByBlockNumberDesc`, `existsByTxHashAndLogIndex`)
- [ ] T018 Create `src/main/java/com/arcadigitalis/backend/evm/Web3jConfig.java`: `@Configuration` bean building `Web3j` from `HttpService(ARCA_EVM_RPC_URL)`; read `pricePerSecond` from contract at startup and expose as `@Bean boolean fundingEnabled`; single chain ID + proxy address validation on startup (reject mis-configured instances)
- [ ] T019 [P] Create `src/main/java/com/arcadigitalis/backend/api/exception/GlobalExceptionHandler.java`: `@ControllerAdvice` mapping to RFC-7807 `ProblemDetail`; handlers for `AuthException→401`, `AccessDeniedException→403`, `ConflictException→409`, `ValidationException→400`, `IntegrityException→422`, `RpcUnavailableException→503`
- [ ] T020 [P] Configure Spring Security in `src/main/java/com/arcadigitalis/backend/api/`: `SecurityConfig.java` with JWT `OncePerRequestFilter`; public endpoints: `POST /auth/nonce`, `POST /auth/verify`, `GET /config`, `POST /packages/*/tx/renew`, `POST /packages/*/tx/rescue`, `GET /packages/*/recovery-kit`, `GET /health/**`; all others require valid JWT
- [ ] T021 [P] Configure Logback field-level scrubbing in `src/main/resources/logback-spring.xml`: mask fields named `key`, `secret`, `password`, `seed`, `dek`, `private` in all structured log output (FR-035); apply as a `MessageConverter` or `MaskingPatternLayout`
- [ ] T022 Create `src/main/java/com/arcadigitalis/backend/api/controller/HealthController.java`: `GET /health/live` (liveness — always 200 if process is running); `GET /health/ready` (readiness — 200 only when PostgreSQL connection available AND EVM RPC reachable; 503 otherwise) (NFR-004)
- [ ] T023 [P] Create `src/main/java/com/arcadigitalis/backend/ArcaBackendApplication.java`: `@SpringBootApplication` main class; startup validation asserting `ARCA_POLICY_PROXY_ADDRESS` and `ARCA_EVM_CHAIN_ID` are configured

**Checkpoint**: Foundation complete — user story phases can now begin (independently or in parallel)

---

## Phase 3: User Story 1 — Package Discovery & Wallet Authentication (Priority: P1) 🎯 MVP

**Goal**: Any party connects their wallet, signs a SIWE challenge, receives a session token, and retrieves
live on-chain package status (all 15 `PackageView` fields). The DB cache never overrides a live chain read.

**Independent Test**: Deploy against a testnet. Call `POST /auth/nonce`, sign EIP-4361 message, call
`POST /auth/verify` → get session token. Call `GET /packages/{key}/status` with a known package key.
Verify live `REVOKED` overrides a stale `ACTIVE` in the cache. Verify `DRAFT` for an unknown key.

- [ ] T024 [P] [US1] Create `src/main/java/com/arcadigitalis/backend/auth/SiweParser.java`: line-by-line EIP-4361 parser extracting `domain`, `address`, `statement`, `uri`, `nonce`, `issuedAt`, `expirationTime`; `ParseException` on malformed input
- [ ] T025 [P] [US1] Create `src/main/java/com/arcadigitalis/backend/auth/NonceService.java`: `issueNonce(walletAddress)` — INSERT nonce row in single DB tx; `consumeNonce(walletAddress, nonce)` — atomic read-and-mark-consumed; `AuthException(401)` if nonce not found, expired (`now > expires_at`), or already consumed
- [ ] T026 [US1] Create `src/main/java/com/arcadigitalis/backend/auth/SiweVerifier.java`: verify SIWE message using `SiweParser` + Web3j `Sign.signedMessageToKey` (ecrecover); enforce: `domain` exact-match vs `ARCA_SIWE_DOMAIN` → `AuthException(401)` on mismatch; nonce single-use via `NonceService.consumeNonce`; expiry check; address match
- [ ] T027 [US1] Create `src/main/java/com/arcadigitalis/backend/auth/JwtService.java`: `issueToken(walletAddress)` — HS256 JWT with `sub`=wallet, `jti`=UUID, `iat`=now, `exp`=now+`ARCA_JWT_TTL_SECONDS`; `verifyToken(token)` — signature + expiry + jti replay check; maintain an in-memory `ConcurrentHashMap<jti, expiresAt>` (MVP); on each `verifyToken()` reject with `AuthException(401)` if `jti` already present, then record it with its expiry; schedule a periodic TTL-pruning task to evict entries past their expiry; `AuthException(401)` on any failure (FR-004, SC-008)
- [ ] T028 [P] [US1] Create `src/main/java/com/arcadigitalis/backend/api/dto/` auth records: `NonceRequest(walletAddress)`, `NonceResponse(nonce, expiresAt)`, `VerifyRequest(walletAddress, signedMessage)`, `VerifyResponse(sessionToken, expiresAt)` — all `record` types (immutable)
- [ ] T029 [US1] Create `src/main/java/com/arcadigitalis/backend/api/controller/AuthController.java`: `POST /auth/nonce` → `NonceService.issueNonce()`; `POST /auth/verify` → `SiweVerifier.verify()` + `JwtService.issueToken()`; both endpoints in public security chain
- [ ] T030 [P] [US1] Create `src/main/java/com/arcadigitalis/backend/evm/PolicyReader.java`: `getPackageStatus(packageKey)` → live `eth_call` to `getPackageStatus(bytes32)` on proxy; `getPackage(packageKey)` → live `eth_call` to `getPackage(bytes32)` returning full `PackageView` struct; `isReleased(packageKey)` → live `eth_call`; `RpcUnavailableException` on connection failure
- [ ] T031 [US1] Create `src/main/java/com/arcadigitalis/backend/policy/PackageService.java`: `getPackageView(packageKey)` — call `PolicyReader.getPackage()`, map `PackageView` struct to `PackageStatusResponse` DTO; `DRAFT` for unknown key (never error); WARNING/CLAIMABLE surfaced as-is (never collapsed); support live-only mode and DB-cache mode with staleness tracking
- [ ] T032 [P] [US1] Create `src/main/java/com/arcadigitalis/backend/api/dto/PackageStatusResponse.java`: a `record` carrying all 15 `PackageView` fields: `status`, `ownerAddress`, `beneficiaryAddress`, `manifestUri`, `guardianList`, `guardianQuorum`, `vetoCount`, `approvalCount`, `warnThreshold`, `inactivityThreshold`, `gracePeriodSeconds`, `lastCheckIn`, `paidUntil`, `pendingSince`, `releasedAt`
- [ ] T033 [P] [US1] Create `src/main/java/com/arcadigitalis/backend/api/dto/ConfigResponse.java`: a `record` carrying `chainId` (long), `proxyAddress` (String), `fundingEnabled` (boolean)
- [ ] T034 [US1] Create `src/main/java/com/arcadigitalis/backend/api/controller/ConfigController.java`: `GET /config` (unauthenticated, `security: []`); reads `fundingEnabled` from `Web3jConfig` bean; returns `ConfigResponse`; no auth required
- [ ] T035 [US1] Add `GET /packages/{packageKey}/status` to `src/main/java/com/arcadigitalis/backend/api/controller/PackageController.java`: call `PackageService.getPackageView()`; validate `packageKey` format (0x-prefixed, 66 chars hex); return `PackageStatusResponse`; live chain read; requires valid JWT
- [ ] T036 [P] [US1] Create `src/test/java/com/arcadigitalis/backend/auth/SiweVerifierTest.java` (MANDATORY): valid sig → token issued; wrong domain → `AuthException(401)`; expired nonce → `AuthException(401)`; replayed nonce → `AuthException(401)`; wrong address → `AuthException(401)`; all assertions with WireMock ecrecover stubs
- [ ] T037 [P] [US1] Create `src/test/java/com/arcadigitalis/backend/auth/JwtServiceTest.java`: expired token rejected; valid token accepted; `sub` claim equals wallet address; `jti` is unique per issuance; replayed `jti` → `AuthException(401)` on second `verifyToken()` call (first call accepts, second call rejects) (SC-008)
- [ ] T038 [P] [US1] Create `src/test/java/com/arcadigitalis/backend/policy/PackageServiceTest.java`: `DRAFT` returned for unknown key; all 7 status values round-trip correctly; `WARNING` not collapsed to `ACTIVE`; `CLAIMABLE` not collapsed to `PENDING_RELEASE`; live chain value overrides stale DB value
- [ ] T039 [US1] Create `src/test/java/com/arcadigitalis/backend/integration/AuthFlowIT.java`: full SIWE → JWT → `GET /packages/{key}/status` flow using Testcontainers PostgreSQL + WireMock EVM stub; expired token → 401; domain mismatch → 401; valid flow → 200 with correct status
- [ ] T040 [P] Create `src/main/java/com/arcadigitalis/backend/lit/ChainNameRegistry.java`: `getChainName(chainId)` → Lit chain name string (e.g. 1→`"ethereum"`, 11155111→`"sepolia"`); `ValidationException(400)` for unknown chain IDs
- [ ] T041 [P] Create `src/main/java/com/arcadigitalis/backend/lit/AccTemplateBuilder.java`: `buildAccTemplate(chainId, proxyAddress, packageKey, beneficiaryAddress)` → complete ACC JSON `ObjectNode`; condition: `contractAddress`=proxy, `functionName`="isReleased", `functionParams`=[packageKey], `returnValueTest.value`="true"; `requester`=beneficiary; uses `ChainNameRegistry` for chain name; no Lit SDK, no network calls

**Checkpoint**: User Story 1 independently testable — auth + package-status complete; `AccTemplateBuilder` + `ChainNameRegistry` (T040–T041) available for Phase 6 (US4)

---

## Phase 4: User Story 2 — Owner Package Lifecycle (Priority: P2)

**Goal**: An authenticated owner calls any of the five owner endpoints and receives an unsigned transaction
payload (calldata + to + gas estimate) ready for wallet signing. No payload is ever signed or submitted
by the backend. Funding guard rejects calldata when `fundingEnabled=false`.

**Independent Test**: Call `activate`, `checkIn`, `renew`, `updateManifestUri`, `revoke`, `rescue` endpoints;
sign and submit each returned calldata on testnet; verify correct chain state transitions after each.
Verify `checkIn` returns 409 for `CLAIMABLE`. Verify `renew` and `rescue` work unauthenticated.

- [ ] T042 [P] [US2] Create `src/main/java/com/arcadigitalis/backend/evm/CalldataBuilder.java`: ABI-encode all 11 mutating contract functions using Web3j `Function` + `FunctionEncoder.encode()`: `activate(bytes32,string,address[],uint256,uint256,uint256)`, `checkIn(bytes32)`, `renew(bytes32)`, `updateManifestUri(bytes32,string)`, `guardianApprove(bytes32)`, `guardianVeto(bytes32)`, `guardianRescindVeto(bytes32)`, `guardianRescindApprove(bytes32)`, `claim(bytes32)`, `revoke(bytes32)`, `rescue(bytes32)`; return hex-encoded calldata string
- [ ] T043 [P] [US2] Create `src/main/java/com/arcadigitalis/backend/policy/RoleResolver.java`: `confirmOwner(packageKey, sessionAddress)` — call `PolicyReader.getPackage()`, compare ownerAddress → `AccessDeniedException(403)` on mismatch; `confirmGuardian(packageKey, sessionAddress)` — check guardian list from chain; `confirmBeneficiary(packageKey, sessionAddress)` — check on-chain beneficiary; NEVER use `guardian_cache` or `package_cache` for authorization
- [ ] T044 [P] [US2] Create `src/main/java/com/arcadigitalis/backend/policy/FundingGuard.java`: `assertFundingAllowed(requestEthValue)` — if `fundingEnabled=false` (from `Web3jConfig` bean) AND `requestEthValue > 0` → throw `ValidationException(400, "FundingDisabled: this instance does not accept ETH-bearing transactions")`
- [ ] T045 [US2] Create `src/main/java/com/arcadigitalis/backend/policy/TxPayloadService.java`: `prepareActivate`, `prepareCheckIn`, `prepareRenew`, `prepareUpdateManifestUri`, `prepareRevoke`, `prepareRescue` methods; owner methods call `RoleResolver.confirmOwner()` first; status from the same live read (from `PolicyReader`) used for pre-flight 409 per FR-012d (`checkIn` → 409 on CLAIMABLE/RELEASED/REVOKED; `updateManifestUri`/`revoke` → 409 on RELEASED/REVOKED); delegates ABI encoding to `CalldataBuilder`; returns `TxPayloadResponse`
- [ ] T046 [P] [US2] Create `src/main/java/com/arcadigitalis/backend/api/dto/` tx records: `TxPayloadResponse(to, data, gasEstimate)`, `ActivateRequest(manifestUri, guardians, guardianQuorum, warnThreshold, inactivityThreshold)`, `CheckInRequest`, `RenewRequest(ethValue)`, `UpdateManifestRequest(newManifestUri)`, `RevokeRequest` — all immutable `record` types; validate guardian list ≤ 7 entries
- [ ] T047 [US2] Create `src/main/java/com/arcadigitalis/backend/api/controller/TxController.java` — `POST /packages/{key}/tx/activate`: auth required; validate ≤7 guardians (400 on violation); `FundingGuard.assertFundingAllowed(ethValue)`; `TxPayloadService.prepareActivate()`; return `TxPayloadResponse`
- [ ] T048 [US2] Add `POST /packages/{key}/tx/check-in` to `TxController.java`: auth required; `TxPayloadService.prepareCheckIn()` (pre-flight: 409 on CLAIMABLE; 409 on RELEASED/REVOKED); return `TxPayloadResponse`
- [ ] T049 [US2] Add `POST /packages/{key}/tx/renew` to `TxController.java`: **unauthenticated** (FR-012b); `FundingGuard.assertFundingAllowed(ethValue)` if ethValue present; `TxPayloadService.prepareRenew()` — calldata returned unconditionally (no status pre-flight per FR-012d); return `TxPayloadResponse`
- [ ] T050 [US2] Add `POST /packages/{key}/tx/update-manifest` to `TxController.java`: auth required; `TxPayloadService.prepareUpdateManifestUri()` (pre-flight: 409 on RELEASED/REVOKED); return `TxPayloadResponse`
- [ ] T051 [US2] Add `POST /packages/{key}/tx/revoke` to `TxController.java`: auth required; `TxPayloadService.prepareRevoke()` (pre-flight: 409 on RELEASED/REVOKED); return `TxPayloadResponse`
- [ ] T052 [US2] Add `POST /packages/{key}/tx/rescue` to `TxController.java`: **unauthenticated**, `security: []` (FR-012c); `TxPayloadService.prepareRescue()` — calldata unconditional; return `TxPayloadResponse`
- [ ] T053 [P] [US2] Create `src/test/java/com/arcadigitalis/backend/evm/CalldataBuilderTest.java` (MANDATORY): ABI round-trip for all 11 functions — encode then decode and verify function selector matches keccak256 of function signature, and each argument is correctly encoded; no nulls; no submissions
- [ ] T054 [P] [US2] Create `src/test/java/com/arcadigitalis/backend/policy/RoleResolverTest.java` (MANDATORY): owner confirmed on matching address; `AccessDeniedException(403)` on non-owner; guardian confirmed from on-chain list; beneficiary confirmed; cache bypass — `guardian_cache` NEVER consulted for auth decision (verified by asserting no repository call via Mockito)
- [ ] T055 [P] [US2] Create `src/test/java/com/arcadigitalis/backend/policy/TxPayloadServiceTest.java` — owner pre-flight cases: 409 on RELEASED for `updateManifestUri`; 409 on REVOKED for `revoke`; 409 on CLAIMABLE for `checkIn`; valid ACTIVE path returns `TxPayloadResponse`; `FundingGuard` rejects ETH value when `fundingEnabled=false`; verify `CalldataBuilder` is called with correct args
- [ ] T056 [US2] Create `src/test/java/com/arcadigitalis/backend/integration/TxPayloadIT.java`: owner tx paths for all 6 owner+rescue endpoints using Testcontainers + WireMock; verify each response contains non-empty `data` (calldata), `to` = configured proxy address; zero on-chain submissions; 409 for pre-flight triggers; 403 for non-owner

**Checkpoint**: User Story 2 independently testable — owner lifecycle payloads complete

---

## Phase 5: User Story 3 — Guardian Approval Workflow (Priority: P3)

**Goal**: An authenticated guardian receives unsigned payloads for approve, veto, rescind-veto, and
rescind-approve. Non-guardians receive 403. Wrong status (non-PENDING_RELEASE / already RELEASED or
REVOKED) returns 409.

**Independent Test**: Put a package in `PENDING_RELEASE` on testnet. Call each guardian endpoint from
a guardian wallet session; sign and submit; verify on-chain guardian state. Verify 403 from non-guardian.
Verify 409 for non-`PENDING_RELEASE` status.

- [ ] T057 [US3] Add `prepareGuardianApprove`, `prepareGuardianVeto`, `prepareGuardianRescindVeto`, `prepareGuardianRescindApprove` to `src/main/java/com/arcadigitalis/backend/policy/TxPayloadService.java`: each calls `RoleResolver.confirmGuardian()` first; status from same live read used for pre-flight 409 (status != PENDING_RELEASE → 409 `NotPending()`; status == RELEASED/REVOKED → 409); delegate to `CalldataBuilder`
- [ ] T058 [US3] Add `POST /packages/{key}/tx/guardian-approve` to `src/main/java/com/arcadigitalis/backend/api/controller/TxController.java`: auth required; `TxPayloadService.prepareGuardianApprove()`; 409 on wrong status; 403 on non-guardian
- [ ] T059 [US3] Add `POST /packages/{key}/tx/guardian-veto` to `TxController.java`: auth required; `TxPayloadService.prepareGuardianVeto()`; 409 on wrong status; 403 on non-guardian
- [ ] T060 [US3] Add `POST /packages/{key}/tx/guardian-rescind-veto` to `TxController.java`: auth required; `TxPayloadService.prepareGuardianRescindVeto()`; 409 on wrong status; 403 on non-guardian
- [ ] T061 [US3] Add `POST /packages/{key}/tx/guardian-rescind-approve` to `TxController.java`: auth required; `TxPayloadService.prepareGuardianRescindApprove()`; 409 on wrong status; 403 on non-guardian
- [ ] T062 [P] [US3] Add guardian pre-flight test cases to `src/test/java/com/arcadigitalis/backend/policy/TxPayloadServiceTest.java`: 409 for non-PENDING_RELEASE on approve; 409 for RELEASED/REVOKED on veto; 403 for non-guardian on each operation; valid guardian path returns `TxPayloadResponse`
- [ ] T063 [US3] Add guardian paths to `src/test/java/com/arcadigitalis/backend/integration/TxPayloadIT.java`: 4 guardian endpoints return unsigned calldata; 403 for non-guardian; 409 for ACTIVE/RELEASED/REVOKED status on approve endpoint; valid PENDING_RELEASE path succeeds

**Checkpoint**: User Story 3 independently testable — guardian workflow payloads complete

---

## Phase 6: User Story 4 — Beneficiary Recovery Support (Priority: P4)

**Goal**: A beneficiary calls the recovery-kit endpoint and gets all fields required for independent
recovery (chain coordinates + Lit ACC). The backend falls back to a live chain read when DB is empty or
stale. A claim tx payload is available for CLAIMABLE packages; 409 for non-CLAIMABLE.

**Independent Test**: Bring a package to `RELEASED` on testnet. Query `GET /packages/{key}/recovery-kit`
with an empty DB; verify all required fields present and matching chain state. Verify claim payload
returns 409 for PENDING_RELEASE. Verify recovery-kit works unauthenticated.

- [ ] T064 [P] [US4] Add `getRecoveryKit(packageKey)` to `src/main/java/com/arcadigitalis/backend/policy/PackageService.java`: live chain read via `PolicyReader.getPackage()` as primary; DB cache as performance hint only; returns `RecoveryKitResponse` even when DB has no row for the package (FR-016); non-RELEASED packages return response with current status and available identifying data (no error)
- [ ] T065 [P] [US4] Create `src/main/java/com/arcadigitalis/backend/api/dto/RecoveryKitResponse.java`: a `record` carrying `chainId`, `proxyAddress`, `packageKey`, `manifestUri`, `releasedAt`, `accCondition` (full ACC JSON string from `AccTemplateBuilder`), `beneficiaryAddress`, `currentStatus`
- [ ] T066 [US4] Add `GET /packages/{packageKey}/recovery-kit` to `src/main/java/com/arcadigitalis/backend/api/controller/PackageController.java`: **unauthenticated** (FR-010); call `PackageService.getRecoveryKit()`; `503` on RPC unavailable; live chain read always performed
- [ ] T067 [US4] Add `prepareClaimTx(packageKey, sessionAddress)` to `src/main/java/com/arcadigitalis/backend/policy/TxPayloadService.java`: `RoleResolver.confirmBeneficiary()` first; live status read — 409 if status != CLAIMABLE (FR-017a) with current status in error body; delegate to `CalldataBuilder.encodeClaim()`
- [ ] T068 [US4] Add `POST /packages/{key}/tx/claim` to `src/main/java/com/arcadigitalis/backend/api/controller/TxController.java`: auth required; `TxPayloadService.prepareClaimTx()`; 409 on non-CLAIMABLE; 403 on non-beneficiary; return `TxPayloadResponse`
- [ ] T069 [P] [US4] Add claim pre-flight cases to `src/test/java/com/arcadigitalis/backend/policy/TxPayloadServiceTest.java`: 409 for PENDING_RELEASE; 409 for ACTIVE; 409 for RELEASED (already claimed); 403 for non-beneficiary; valid CLAIMABLE path returns `TxPayloadResponse`
- [ ] T070 [US4] Add recovery-kit + claim paths to `src/test/java/com/arcadigitalis/backend/integration/TxPayloadIT.java`: recovery-kit returns all 8 required fields; claim 409 on PENDING_RELEASE; recovery-kit works with empty DB (live chain fallback verified); unauthenticated access succeeds for recovery-kit

**Checkpoint**: User Story 4 independently testable — beneficiary recovery path complete

---

## Phase 7: User Story 5 — Lit ACC Template Generation & Manifest Validation (Priority: P5)

**Goal**: `GET /acc-template` returns a correctly bound ACC JSON. `POST /validate-manifest` runs three
validation layers (structural, live RPC, blob guard); any failure returns HTTP 400. No Lit network
calls ever.

**Independent Test**: Call `GET /acc-template` for `(chainId, proxy, packageKey, beneficiary)`; verify
`policyProxy.isReleased(packageKey)==true` condition bound to correct proxy; requester==beneficiary.
Submit manifest with wrong proxy → 400. Submit manifest with missing encDEK → 400.

- [ ] T071 [US5] Create `src/main/java/com/arcadigitalis/backend/lit/ManifestValidator.java`: three-layer validation per FR-020: *Layer 1 (local structural)* — assert all required fields present (`packageKey`, `policy.chainId`, `policy.contract`, `keyRelease.accessControl`, `keyRelease.requester`, `keyRelease.encryptedSymmetricKey`), `contractAddress`==configured proxy, `chainId`==`ARCA_EVM_CHAIN_ID`, `functionName`=="isReleased", `functionParams[0]`==`packageKey`, `requester`==manifest `beneficiary`; *Layer 2 (live RPC)* — `PolicyReader.getPackageStatus(packageKey)` != DRAFT + on-chain `beneficiary`==manifest `requester`; *Layer 3 (blob guard)* — `encryptedSymmetricKey` non-empty, non-whitespace, length > 10 chars; any layer failure → `ValidationException(400)` identifying the failing check
- [ ] T072 [P] [US5] Create `src/main/java/com/arcadigitalis/backend/api/dto/` lit records: `AccTemplateRequest(chainId, proxyAddress, packageKey, beneficiaryAddress)`, `AccTemplateResponse(accJson)`, `ValidateManifestRequest(manifestJson)`, `ValidateManifestResponse(valid, failedLayer, details)` — all `record` types
- [ ] T073 [US5] Create `src/main/java/com/arcadigitalis/backend/api/controller/LitController.java`: `GET /acc-template` (auth required) → `AccTemplateBuilder.buildAccTemplate()`; `POST /validate-manifest` (auth required) → `ManifestValidator.validate()`; 400 on any validation failure; zero Lit network calls
- [ ] T074 [P] [US5] Create `src/test/java/com/arcadigitalis/backend/lit/AccTemplateBuilderTest.java` (MANDATORY): proxy address in `contractAddress`; `chainId` maps correctly via `ChainNameRegistry`; `packageKey` in `functionParams[0]`; `requester`==beneficiaryAddress; `functionName`=="isReleased"; `returnValueTest.value`=="true"; 400 on unknown chainId
- [ ] T075 [P] [US5] Create `src/test/java/com/arcadigitalis/backend/lit/ManifestValidatorTest.java` (MANDATORY): layer 1 rejects wrong `contractAddress`; layer 1 rejects wrong `chainId`; layer 1 rejects missing `encryptedSymmetricKey` field; layer 2 rejects DRAFT package (WireMock stub); layer 2 rejects beneficiary mismatch (WireMock stub); layer 3 rejects empty encDEK string; valid manifest passes all 3 layers; no Lit network calls in any test

**Checkpoint**: User Story 5 independently testable — Lit ACC + manifest validation complete

---

## Phase 8: User Story 6 — Encrypted Artifact Storage & Integrity (Priority: P6)

**Goal**: Upload manifest + ciphertext; backend verifies sha256 before confirming storage. Mismatched
hash returns 422. Artifacts are pinned to at least one configured backend (IPFS and/or S3). No
decryption or content inspection ever.

**Independent Test**: Upload manifest + matching-hash ciphertext → storage succeeds with confirmed URIs.
Upload ciphertext with wrong hash → 422. Retrieve artifact by ID → bytes match original.

- [ ] T076 [P] [US6] Create `src/main/java/com/arcadigitalis/backend/storage/IpfsAdapter.java`: `pin(byte[] content)` → `ipfs://` URI via configured IPFS gateway (Infura/Pinata); HTTP POST multipart; configurable timeout; `StorageException` on failure
- [ ] T077 [P] [US6] Create `src/main/java/com/arcadigitalis/backend/storage/ObjectStorageAdapter.java`: `put(byte[] content, String sha256Key)` → `s3://` URI; configurable S3-compatible endpoint + bucket + credentials; `StorageException` on failure; used as fallback if IPFS not configured
- [ ] T078 [US6] Create `src/main/java/com/arcadigitalis/backend/storage/ArtifactService.java`: `pin(ArtifactUploadRequest)` — compute sha256 of received bytes; compare against `declaredHash` → `IntegrityException(422)` on mismatch; delegate to `IpfsAdapter` and/or `ObjectStorageAdapter` based on config; persist `StoredArtifactEntity`; return confirmed storage URIs; `retrieve(id)` — fetch by UUID, stream bytes; MUST NOT attempt decryption or content inspection
- [ ] T079 [P] [US6] Create `src/main/java/com/arcadigitalis/backend/api/dto/` storage records: `ArtifactUploadRequest(artifactType, declaredHash, content)`, `ArtifactUploadResponse(id, confirmedUris, sha256Hash, sizeBytes)` — `record` types
- [ ] T080 [US6] Create `src/main/java/com/arcadigitalis/backend/api/controller/StorageController.java`: `POST /artifacts` (auth required) → `ArtifactService.pin()`; 422 on hash mismatch; `GET /artifacts/{id}` (auth required) → `ArtifactService.retrieve()`; never decrypt or inspect blob content; return raw bytes with `Content-Type: application/octet-stream`

**Checkpoint**: User Story 6 independently testable — artifact storage + integrity complete

---

## Phase 9: User Story 7 — Event Indexing & Notifications (Priority: P7)

**Goal**: The indexer continuously polls for new blocks, decodes all 13 event types, updates
`package_cache`, persists events idempotently, handles reorgs by rewinding to the last consistent
block, and dispatches best-effort notifications without blocking recovery or API paths.

**Independent Test**: Emit `PackageActivated`, `PendingRelease`, `Released`, and `Revoked` events on
testnet including a deliberate reorg. Verify post-reorg indexed state matches canonical chain with zero
orphaned events. Query paginated `GET /events?ownerAddress=…`; verify correct filtered results.
Inject delivery failure; verify indexer continues unaffected.

- [ ] T081 [P] [US7] Create `src/main/java/com/arcadigitalis/backend/evm/EventDecoder.java`: decode all 13 event types from raw `EthLog` using Web3j ABI decoding and topic matching: `PackageActivated`, `ManifestUpdated`, `CheckIn`, `Renewed`, `GuardianApproved`, `GuardianVetoed`, `GuardianVetoRescinded`, `GuardianApproveRescinded`, `GuardianStateReset`, `PendingRelease` (with `reason_flags` bits 0+1), `Released`, `Revoked`, `PackageRescued`; return typed `DecodedEvent` union; `UnknownEventException` for unrecognized topics
- [ ] T082 [P] [US7] Create `src/main/java/com/arcadigitalis/backend/evm/ReorgHandler.java`: `checkAndHandleReorg(blockNumber, observedHash)` — query `processed_blocks` for stored hash at that block; if mismatch: DELETE `event_records` and `processed_blocks` rows above fork point in a single transaction; return rewind target block number to `IndexerPoller`
- [ ] T083 [US7] Create `src/main/java/com/arcadigitalis/backend/evm/IndexerPoller.java`: `@Scheduled(fixedDelayString="${ARCA_INDEXER_POLL_INTERVAL_SECONDS}000")` background thread; on first run start from `ARCA_INDEXER_START_BLOCK`; subsequent runs resume from last `processed_blocks` row (ignore start-block config per FR-028a); batch `eth_getLogs` with `ARCA_INDEXER_CONFIRMATION_DEPTH` lag; for each confirmed event: call `ReorgHandler.checkAndHandleReorg()`, then decode via `EventDecoder`, then apply cache-update rules (per data-model.md §4 state-transitions table), then persist `EventRecordEntity` idempotently (upsert on unique constraint), then update `processed_blocks`; publish `ApplicationEvent` per indexed event for notification dispatch; single thread — no concurrent indexer instances
- [ ] T084 [P] [US7] Create `src/main/java/com/arcadigitalis/backend/api/dto/` event records: `EventRecordResponse(id, eventType, packageKey, blockNumber, blockTimestamp, rawData)`, `EventPageResponse(events, nextCursor, totalCount)` — `record` types
- [ ] T085 [US7] Create `src/main/java/com/arcadigitalis/backend/api/controller/EventController.java`: `GET /events` paginated (auth required); filter params: `ownerAddress`, `guardianAddress`, `beneficiaryAddress`, `fromTimestamp`, `toTimestamp`; ordered by `(block_number, log_index)`; no on-chain enumeration (FR-030); returns `EventPageResponse` with `X-Data-Staleness-Seconds` header
- [ ] T086 [P] [US7] Create `src/main/java/com/arcadigitalis/backend/notifications/NotificationDispatcher.java`: `@EventListener` on `ApplicationEvent` published by `IndexerPoller`; look up active `notification_targets` for `(packageKey, eventType)`; invoke `EmailDelivery` or `WebhookDelivery` per channel type via `RetryPolicy`; MUST NOT block indexer thread; MUST NOT propagate delivery exception to caller
- [ ] T087 [P] [US7] Create `src/main/java/com/arcadigitalis/backend/notifications/EmailDelivery.java`: SMTP adapter; `send(target, eventPayload)` — compose message body from event data; configurable SMTP host/port/credentials from env; per-attempt timeout; throw `DeliveryException` on failure
- [ ] T088 [P] [US7] Create `src/main/java/com/arcadigitalis/backend/notifications/WebhookDelivery.java`: HTTP POST adapter; `post(webhookUrl, eventPayload)` — serialize event as JSON body; configurable timeout; `DeliveryException` on 4xx/5xx or timeout
- [ ] T089 [P] [US7] Create `src/main/java/com/arcadigitalis/backend/notifications/RetryPolicy.java`: bounded exponential-backoff retry (configurable max attempts, default 3); after final failure: log `WARN` with undelivered details, call `update last_delivery_status='failed'`; MUST catch all `DeliveryException` and MUST NOT rethrow; dead-letter record preserved for observability
- [ ] T090 [P] [US7] Create `src/main/java/com/arcadigitalis/backend/api/dto/` notification target records: `NotificationTargetRequest(packageKey, eventTypes, channelType, channelValue)`, `NotificationTargetResponse(id, packageKey, eventTypes, channelType, active, createdAt)` — `record` types
- [ ] T091 [US7] Create `src/main/java/com/arcadigitalis/backend/api/controller/NotificationController.java`: `POST /notification-targets` (auth; live chain check: caller must be on-chain owner or beneficiary per FR-031c; 403 otherwise); `GET /notification-targets/{id}`, `PUT /notification-targets/{id}`, `DELETE /notification-targets/{id}` (auth; own subscription only); `RpcUnavailableException→503` if chain read fails
- [ ] T092 [P] [US7] Create `src/test/java/com/arcadigitalis/backend/evm/EventDecoderTest.java`: all 13 event types decoded correctly from synthetic raw log data; `PendingRelease` `reason_flags` bit 0 (inactivity) and bit 1 (funding lapse) parsed into `raw_data` JSON correctly; `UnknownEventException` for unrecognized topic0
- [ ] T093 [P] [US7] Create `src/test/java/com/arcadigitalis/backend/evm/IndexerPollerTest.java` (MANDATORY): reorg detected on blockHash mismatch → `ReorgHandler.checkAndHandleReorg()` invoked → DB rewound to fork point; replay from same block produces identical `event_records` (idempotency); second pass with same events produces no duplicate rows (unique constraint guard); first run starts from `ARCA_INDEXER_START_BLOCK`; subsequent run resumes from last `processed_blocks` row
- [ ] T094 [US7] Create `src/test/java/com/arcadigitalis/backend/integration/IndexerReorgIT.java` (MANDATORY): Testcontainers PostgreSQL + WireMock simulating two competing block sequences; indexer processes chain A, then chain B with diverging block at N; verify post-resolution `event_records` match chain B exactly; zero orphaned chain-A events; no duplicates
- [ ] T095 [US7] Create `src/test/java/com/arcadigitalis/backend/integration/NotificationIT.java`: inject `EmailDelivery` stub that always throws `DeliveryException`; index events; verify indexer completes its polling cycle without error; verify no `500` on `GET /events`; verify `last_delivery_status='failed'` recorded in DB; verify `RetryPolicy` invoked exactly `maxAttempts` times

**Checkpoint**: User Story 7 independently testable — indexer + notifications complete

---

## Phase 10: Polish & Cross-Cutting Concerns

**Purpose**: Security hardening, constitution compliance verification, and quickstart validation.
Cuts across all user stories.

- [ ] T096 Create `src/test/java/com/arcadigitalis/backend/security/SecretLogScanTest.java` (MANDATORY per SC-003): exercise all API endpoints with payloads containing synthetic secret-pattern values; assert no log line (Logback appender capture) and no response body contains substrings matching patterns: `privateKey`, `rawDek`, `encryptedKey` raw value, `seedPhrase`; also scan DB `event_records.raw_data` for secret-pattern fields; FR-034 + FR-035
- [ ] T097 [P] Add HTTP 400 input validation for `proxyAddress` + `chainId` mismatch against configured values (NFR-007) to `GlobalExceptionHandler.java` and a new `ConfigGuard.java` `@Component` invoked at controller entry; test: wrong `proxyAddress` → 400 with `"proxyAddress does not match configured proxy"` message
- [ ] T098 [P] Add `X-Data-Staleness-Seconds` response header to cached live-state read endpoints (`GET /packages/{key}/status` when served from DB cache, `GET /events`) — value = seconds since `IndexerPoller` last successful sync; expose last-sync timestamp as `@ApplicationScope` bean updated by indexer; MUST NOT be added to immutable content-addressed endpoints such as `GET /artifacts/{id}`
- [ ] T099 [P] Add HTTP 503 handling for `RpcUnavailableException` on all auth-gated endpoints performing live RPC reads (guardian verification, beneficiary validation, notification-target registration) in `GlobalExceptionHandler.java`; add `GET /health/ready` dependency on RPC reachability check
- [ ] T100 [P] Configure SpringDoc OpenAPI 2.x in `src/main/java/com/arcadigitalis/backend/api/` to align generated schema with `contracts/openapi.yaml`: all `operationId` values, response codes (including 409 on 6 endpoints), security schemes (`bearerAuth`), and `PackageStatus` schema with all 15 fields; add `@Operation` and `@ApiResponse` annotations to all controllers
- [ ] T101 Run end-to-end quickstart validation per `quickstart.md`: local Docker Compose up → all 7 user-story acceptance scenarios passing against the running service; fix any deviations discovered
- [ ] T102 [P] Create `docs/recovery-drill.sh`: self-contained offline beneficiary recovery script using only `cast` (Foundry) / `ethers.js` CLI — reads `packageKey` + `manifestUri` from chain via configured RPC, fetches manifest from IPFS, constructs the Lit ACC locally; add an automated smoke test asserting the script completes correctly with the backend process stopped (SC-001)
- [ ] T103 [P] Create `src/main/java/com/arcadigitalis/backend/notifications/PushDelivery.java`: FCM/APNs push adapter; `send(target, eventPayload)` — compose push notification from event data; configurable credentials via env; throw `DeliveryException` on failure; integrate with `NotificationDispatcher` and `RetryPolicy` (FR-031b)
- [ ] T104 Update `NotificationTargetEntity.java` push channel support: extend `channel_type` accepted values to include `push`; add JPA validation asserting `channel_type` ∈ `{email, webhook, push}`; add `channel_value` format guard (non-empty for `push`, valid URL for `webhook`, valid email for `email`); no migration DDL change required (`channel_type VARCHAR(20)` already accommodates the value; update V7 migration comment to document all three values)
- [ ] T105 [P] Add PostgreSQL advisory lock to `src/main/java/com/arcadigitalis/backend/evm/IndexerPoller.java` startup: check `ARCA_INDEXER_ENABLED` first (skip indexer entirely if `false`); acquire `pg_try_advisory_lock(ARCA_INDEXER_LOCK_ID)` — if not acquired log `WARN "Indexer advisory lock held by another instance — skipping indexer startup"` and return without scheduling the poll; add unit test asserting second-instance lock contention skips the poll thread (NFR-002)
- [ ] T106 [P] Queue PATCH amendment to `.specify/memory/constitution.md` per governance procedure: add `updateManifestUri`, `guardianRescindApprove`, `rescue` to Principle IV covered-operations list; bump version `1.0.0 → 1.0.1`; update `LAST_AMENDED_DATE`; prepend Sync Impact Report — submit as a dedicated PR per amendment procedure (A1)
- [ ] T107 [P] Add `src/test/java/com/arcadigitalis/backend/ArchitectureTest.java`: ArchUnit assertions — (1) `api` package has zero imports from `persistence` package (Constitution Quality Bar); (2) no `static` mutable fields in service-layer classes across `policy`, `evm`, `storage`, `notifications` modules (NFR-003 horizontal-scale invariant); run as part of standard Maven test phase via JUnit 5 `@AnalyzeClasses`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No dependencies — start immediately
- **Phase 2 (Foundational)**: Depends on Phase 1 — **BLOCKS all user story phases**
- **Phase 3 (US1, P1)**: Depends on Phase 2 — **MVP entry point**
- **Phase 4 (US2, P2)**: Depends on **Phase 2 and T030** (`PolicyReader`, Phase 3) — T030 MUST be complete before Phase 4 begins
- **Phase 5 (US3, P3)**: Depends on Phase 4 (extends `TxPayloadService` + `TxController`)
- **Phase 6 (US4, P4)**: Depends on Phase 3 (extends `PackageService` + `PackageController`; `AccTemplateBuilder`/`ChainNameRegistry` T040–T041 are in Phase 3)
- **Phase 7 (US5, P5)**: Depends on Phase 2 + Phase 3 (`PolicyReader` in ManifestValidator layer 2)
- **Phase 8 (US6, P6)**: Depends on Phase 2 (persistence layer only)
- **Phase 9 (US7, P7)**: Depends on Phase 2 + Phase 3 (`PolicyReader`); enhances `PackageService`
- **Phase 10 (Polish)**: Depends on all story phases complete

### User Story Dependencies

| Story | Depends on | Independently testable? |
|---|---|---|
| US1 (P1) | Foundational only | ✅ Yes — auth + status read |
| US2 (P2) | Foundational + `PolicyReader` (T030 from US1) | ✅ Yes — owner payloads |
| US3 (P3) | US2 (`TxPayloadService`, `TxController` skeleton) | ✅ Yes — guardian endpoints |
| US4 (P4) | Foundational + `PolicyReader`/`PackageService` + `AccTemplateBuilder` (T040–T041, Phase 3) | ✅ Yes — recovery kit |
| US5 (P5) | Foundational + `PolicyReader` (US1) | ✅ Yes — Lit ACC + validation |
| US6 (P6) | Foundational + persistence only | ✅ Yes — storage endpoints |
| US7 (P7) | Foundational + `PolicyReader`/cache entities | ✅ Yes — indexer + events |

### Within Each User Story

- JPA entities/repositories (Phase 2) → Service layer → Controllers → Integration tests
- For US2/US3: `CalldataBuilder` → `RoleResolver` → `TxPayloadService` → `TxController`
- For US7: `EventDecoder` → `ReorgHandler` → `IndexerPoller` → `NotificationDispatcher`

### Parallel Opportunities

- All Phase 1 tasks marked `[P]` run in parallel (T002–T007)
- All foundational migration files (T008–T015) run in parallel after T008
- Within US1: `SiweParser` (T024), `NonceService` (T025), `PolicyReader` (T030), auth DTOs (T028), package DTOs (T032, T033) all run in parallel
- Within US2: `CalldataBuilder` (T042), `RoleResolver` (T043), `FundingGuard` (T044), tx DTOs (T046) run in parallel
- Within US7: `EventDecoder` (T081), `ReorgHandler` (T082) run in parallel before `IndexerPoller` (T083)

---

## Parallel Example: User Story 1

```
# Phase A — Launch together (no inter-dependencies):
Task T024: SiweParser.java
Task T025: NonceService.java
Task T028: auth DTOs (NonceRequest, NonceResponse, VerifyRequest, VerifyResponse)
Task T030: PolicyReader.java
Task T032: PackageStatusResponse.java
Task T033: ConfigResponse.java
Task T040: ChainNameRegistry.java
Task T041: AccTemplateBuilder.java

# Phase B — After T024+T025 complete:
Task T026: SiweVerifier.java (needs SiweParser + NonceService)
Task T027: JwtService.java (no inter-dependency within US1)

# Phase C — After T026+T027+T028+T030+T031 complete:
Task T029: AuthController.java
Task T034: ConfigController.java
Task T035: PackageController.java (status endpoint)

# Phase D — Tests in parallel:
Task T036: SiweVerifierTest.java
Task T037: JwtServiceTest.java
Task T038: PackageServiceTest.java

# Phase E — After all implementation tasks complete:
Task T039: AuthFlowIT.java (integration)
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete **Phase 1**: Setup (T001–T007)
2. Complete **Phase 2**: Foundational (T008–T023) — critical blocker
3. Complete **Phase 3**: User Story 1 + Lit utilities (T024–T041) — auth + package status + AccTemplateBuilder
4. **STOP and VALIDATE**: run `AuthFlowIT.java`; test against testnet
5. Deploy/demo if ready — this is the minimal deployable increment

### Incremental Delivery

1. **Foundation** (Phase 1+2) → skeleton deployable
2. **+US1** → auth + package status queryable (deploy/demo)
3. **+US2** → owner tx payloads live (deploy/demo)
4. **+US3** → guardian workflow live (deploy/demo)
5. **+US4** → beneficiary recovery kit live (deploy/demo)
6. **+US5** → Lit ACC + manifest validation live (deploy/demo)
7. **+US6** → artifact storage + integrity live (deploy/demo)
8. **+US7** → event indexing + notifications live (deploy/demo)
9. **+Phase 10** → polish + SC compliance (final release)

---

## Summary

| Phase | Tasks | Description |
|---|---|---|
| Phase 1 | T001–T007 | Setup (7 tasks) |
| Phase 2 | T008–T023 | Foundational / DB / security (16 tasks) |
| Phase 3 | T024–T041 | US1 Auth + Package Status + Lit utilities (18 tasks) |
| Phase 4 | T042–T056 | US2 Owner Lifecycle (15 tasks) |
| Phase 5 | T057–T063 | US3 Guardian Workflow (7 tasks) |
| Phase 6 | T064–T070 | US4 Beneficiary Recovery (7 tasks) |
| Phase 7 | T071–T075 | US5 Manifest Validation (5 tasks) |
| Phase 8 | T076–T080 | US6 Artifact Storage (5 tasks) |
| Phase 9 | T081–T095 | US7 Event Indexing + Notifications (15 tasks) |
| Phase 10 | T096–T107 | Polish + Constitution compliance (12 tasks) |
| **Total** | **T001–T107** | **107 tasks** |

**MANDATORY tests** (must not be skipped):
- T036 `SiweVerifierTest` — nonce/sig/replay/domain
- T051 `CalldataBuilderTest` — all 11 ABI round-trips
- T052 `RoleResolverTest` — chain-based auth, cache bypass
- T074 `AccTemplateBuilderTest` — proxy/chainId/packageKey binding
- T075 `ManifestValidatorTest` — all 3 layers + fuzz
- T093 `IndexerPollerTest` — reorg rewind + idempotency
- T094 `IndexerReorgIT` — end-to-end reorg recovery
- T096 `SecretLogScanTest` — SC-003 secret hygiene
- T107 `ArchitectureTest` — api→persistence import ban + no static mutable state

**Parallel opportunities**: 53 of 107 tasks marked `[P]` — significant concurrent execution possible once Phase 2 foundational work is complete.

**Suggested MVP scope**: Phases 1–3 (39 tasks total) — delivers authentication + live package-status, which is the prerequisite for every other story.
