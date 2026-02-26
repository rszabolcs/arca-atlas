# Research: Arca Java Backend

**Feature**: `001-java-backend`
**Date**: 2026-02-26
**Resolves**: All NEEDS CLARIFICATION items from Technical Context in `plan.md`

---

## 1. SIWE / EIP-4361 Verification in Java

**Decision**: Manual EIP-4361 message parser + `web3j` signature recovery (via `Sign.signedMessageToKey`).

**Rationale**: The `com.spruceid:siwe-java` library exists but is minimally maintained and adds a
transitive dependency. EIP-4361 message parsing is straightforward (structured text format with
known fields). Signature recovery (`ecrecover`) is already available via Web3j's `Sign` utility.
A self-contained implementation in the `auth` module is testable in isolation, avoids version
drift, and makes all validation logic auditable without dependency archaeology.

**Implementation outline**:
1. Parse the raw SIWE message string into fields (`domain`, `address`, `nonce`, `issued-at`,
   `expiration-time`, `chain-id`, `uri`) using a line-by-line parser.
2. Recover the signer address with `web3j Sign.signedMessageToKey(messageBytes, signature)`.
3. Validate: recovered address == claimed address; `nonce` matches issued nonce; `issued-at` +
   expiry window is valid; `domain` matches configured host.
4. Consume the nonce in the same DB transaction as issuing the JWT to prevent replay.

**Alternatives considered**:
- `com.spruceid:siwe-java` — thin wrapper; last release 2022; no benefit over direct implementation.
- `ethers4j` — no significant advantage over Web3j for this use case.

---

## 2. EVM Library: Web3j

**Decision**: Web3j 4.x (`org.web3j:core`).

**Rationale**: De facto Java/Spring EVM library. Mature, Spring Boot auto-configuration available
(`org.web3j:web3j-spring-boot-starter`), supports JSON-RPC, ABI encoding/decoding, log filtering,
and raw `eth_getLogs` + `eth_getBlockByNumber` polling needed for the reorg-safe indexer.
Native type generation from ABI JSON eliminates manual ABI encoding errors.

**Key usage points**:
- `Web3j.build(HttpService(...))` for RPC connection.
- `EthFilter` + `EthLog` for `eth_getLogs` polling.
- `ABI.encode(...)` / generated type wrappers for tx calldata construction.
- `Sign.signedMessageToKey(...)` for SIWE address recovery.
- `Web3j.ethGetBlockByNumber(DefaultBlockParameterNumber, false)` for blockHash verification.

**Alternatives considered**:
- `ethers4j` — incomplete API, less community support.
- Nethereum — .NET port, not native Java.
- Raw `OkHttp` + manual JSON-RPC — too low-level, no ABI type safety.

---

## 3. Lit Protocol — No Server-Side SDK Required

**Decision**: Pure JSON template builder in the `lit` module. No Lit Java SDK. No Lit network
calls from the backend.

**Rationale**: The backend's Lit responsibilities are limited to:
1. Generating a correct ACC JSON structure (static template parameterised by `chainId`, `proxy`,
   `packageKey`, `beneficiary`).
2. Validating that a user-submitted manifest's embedded ACC matches expected values.

Neither requires calling the Lit network. The ACC is a JSON document; its correctness is
verifiable by field inspection plus one live Ethereum RPC read (packageKey activation + beneficiary
match). Lit SDK interaction belongs entirely on the client. Introducing a server-side Lit
dependency would violate Constitution Principle II (recovery independence) and Principle I
(session key risk surface).

**ACC JSON template** (canonical structure the `lit` module produces):
```json
{
  "conditionType": "evmContract",
  "contractAddress": "<proxy>",
  "functionName": "isReleased",
  "functionParams": ["<packageKey>"],
  "functionAbi": { "name": "isReleased", "inputs": [...], "outputs": [{"type": "bool"}] },
  "chain": "<litChainName>",
  "returnValueTest": { "key": "", "comparator": "=", "value": "true" }
}
```
Combined with a requester constraint (`unifiedAccessControlConditions` or top-level `requester`
field depending on Lit SDK version) bound to `beneficiary`.

**ChainId → Lit chain name mapping**: maintained as an internal config map
(`ethereum`=1, `sepolia`=11155111, etc.); extensible via `application.yml`.

---

## 4. Database Migration Tooling: Flyway

**Decision**: Flyway (`org.flywaydb:flyway-core`), managed via Spring Boot auto-configuration.

**Rationale**: Flyway is simpler than Liquibase for this project:
- SQL-first (no XML/YAML change-log); all migrations are readable `.sql` files.
- Spring Boot 3.x auto-configures Flyway from `spring.flyway.*` properties.
- Naming convention (`V1__init.sql`, `V2__add_notification_targets.sql`) is self-documenting.
- The schema is relational but not complex; Liquibase's diff and rollback features are not needed
  for this scale.

**Migration location**: `src/main/resources/db/migration/`

**Alternatives considered**:
- Liquibase — richer rollback tooling, but XML/YAML format adds friction; no rollback needed at MVP.
- Manual schema scripts — unversioned, no idempotency guarantee.

---

## 5. IPFS Adapter: Infura IPFS HTTP API (primary) + S3 (secondary)

**Decision**: HTTP-based adapters implementing a `StoragePort` interface; Infura IPFS as default
primary; AWS S3 SDK v2 as secondary. Both pluggable via configuration.

**Rationale**:
- Infura IPFS provides a simple REST API (`/api/v0/add`, `/api/v0/cat`) with API-key auth;
  no local node required for development or production.
- AWS SDK v2 (`software.amazon.awssdk:s3`) is the standard Java S3 client; supports
  LocalStack for test environments.
- `StoragePort` abstraction (`pin(bytes, hash) → uri`, `retrieve(uri) → bytes`) keeps the domain
  logic independent of the provider; swapping providers requires only a new adapter bean.

**sha256 verification**: computed from uploaded bytes before persisting; compared against
manifest-declared hash; mismatch → HTTP 422 without persisting.

**Alternatives considered**:
- Pinata API — functionally equivalent to Infura; Infura preferred due to existing ArcaDigitalis
  infrastructure context; adapter pattern makes future swap trivial.
- Running a local Kubo (go-ipfs) node — operationally heavy; not needed for MVP.

---

## 6. Notification Dispatch: Spring @Async + Spring Retry

**Decision**: Spring `@Async` executor for fire-and-forget dispatch; Spring Retry
(`@Retryable`) for bounded retry with exponential backoff; Spring Mail for email;
`RestTemplate`/`WebClient` for webhook HTTP calls.

**Rationale**:
- Delivery must be off the critical path (FR-033); `@Async` achieves this trivially in
  Spring Boot without a message queue.
- `@Retryable` (Spring Retry) handles bounded retries (`maxAttempts=3`, exponential backoff)
  without manual retry loops.
- Spring Mail (`spring-boot-starter-mail`) supports SMTP with minimal configuration.
- Push token delivery is deferred to a future phase; the `notifications` module is designed
  with a `NotificationChannel` interface (`send(target, event)`) so adding push requires only
  a new implementation.
- An external message queue (Kafka, RabbitMQ) would be a stronger guarantee but is
  over-engineered for MVP (single instance, best-effort semantics per FR-031/032).

**Retry policy**: 3 attempts, 5 s / 15 s / 45 s backoff; failure recorded in `notification_targets`
`last_delivery_status` + `last_delivery_attempt` columns.

**Alternatives considered**:
- Kafka/RabbitMQ — durable delivery, but contradicts MVP single-instance constraint and
  best-effort guarantee; deferred to future scaling phase.
- Quartz Scheduler — unnecessary complexity for a simple async dispatcher.

---

## 7. Reorg-Safe Indexer Design

**Decision**: Poll-based indexer using a `@Scheduled` Spring task; store `(block_number, block_hash)`
per processed block; detect mismatch; rewind by deleting events from diverged blocks and
re-polling from the last consistent block.

**Key algorithm**:
1. On each poll cycle: fetch the latest finalized block number (`eth_blockNumber` minus
   `confirmation_depth`).
2. For each new block N: fetch `eth_getBlockByNumber(N, false)` to get `blockHash`.
3. Lookup stored `(N, expected_hash)`: if mismatch → reorg detected.
4. Reorg recovery: find the last block where stored hash == chain hash (walk backwards);
   bulk-delete all `event_records` with `block_number > last_consistent_block`;
   bulk-delete `processed_blocks` rows > `last_consistent_block`; resume polling from
   `last_consistent_block + 1`.
5. For clean blocks: fetch `eth_getLogs(fromBlock=N, toBlock=N, address=proxy)`;
   insert `event_records` idempotently (unique index on `(tx_hash, log_index)`);
   insert `processed_blocks(N, blockHash)`.

**Confirmation depth**: configurable `arca.indexer.confirmation-depth` (default 12 for mainnet,
1 for testnet). Events only indexed after N+depth.

**Idempotency**: unique DB constraint on `(tx_hash, log_index)` in `event_records` ensures
`INSERT ... ON CONFLICT DO NOTHING` idempotency.

---

## 8. JWT Session Management

**Decision**: `io.jsonwebtoken:jjwt-api` 0.12+ with `HS256` symmetric signing (configurable
secret via `ARCA_JWT_SECRET` env var). Short TTL of 1 hour (configurable). Nonces stored in
`nonces` table; consumed atomically on JWT issue.

**Rationale**: `jjwt` is the most widely used Java JWT library; Spring Security integrates
cleanly. Symmetric signing is adequate for a single-service JWT issuer/validator. RS256 is
available as a future upgrade if JWT validation must be delegated to other services.

**Claims**: `sub` = wallet address (checksummed EIP-55), `jti` = UUID, `iat` + `exp`, `chainId`.
