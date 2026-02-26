# Data Model: Arca Java Backend

**Feature**: `001-java-backend`
**Date**: 2026-02-26
**Sources**: spec.md Key Entities; research.md §Reorg-Safe Indexer; constitution.md §Technology Stack

---

## 1. Entities

### 1.1 `nonces`

Stores SIWE nonces pending consumption. Single-use; deleted (or marked consumed) when the
wallet completes SIWE verification.

| Column | Type | Notes |
|---|---|---|
| `id` | `UUID` PK | generated |
| `wallet_address` | `VARCHAR(42)` NOT NULL | EIP-55 checksummed |
| `nonce` | `VARCHAR(64)` UNIQUE NOT NULL | cryptographically random |
| `created_at` | `TIMESTAMPTZ` NOT NULL | for TTL expiry sweeps |
| `expires_at` | `TIMESTAMPTZ` NOT NULL | reject if `now > expires_at` |
| `consumed` | `BOOLEAN` NOT NULL DEFAULT false | set true atomically on JWT issue |

**Indexes**: unique on `nonce`; partial index on `(wallet_address, consumed=false)`.

---

### 1.2 `package_cache`

An event-sourced read cache of on-chain package state. Never authoritative for auth —
live chain reads are always preferred for authorization (Constitution III). Populated
and updated by the indexer from `Activated`, `ManifestUpdated`, `PendingRelease`,
`Released`, `Revoked` events.

| Column | Type | Notes |
|---|---|---|
| `id` | `UUID` PK | generated |
| `chain_id` | `BIGINT` NOT NULL | EVM chain identifier |
| `proxy_address` | `VARCHAR(42)` NOT NULL | policy proxy (EIP-55) |
| `package_key` | `VARCHAR(66)` NOT NULL | `bytes32` hex with `0x` prefix |
| `owner_address` | `VARCHAR(42)` | from `Activated` event |
| `beneficiary_address` | `VARCHAR(42)` | from `Activated` event |
| `manifest_uri` | `TEXT` | latest observed `manifestUri` |
| `cached_status` | `VARCHAR(20)` | last observed status string (informational only) |
| `pending_since` | `TIMESTAMPTZ` | sourced from `PendingRelease` event |
| `released_at` | `TIMESTAMPTZ` | sourced from `Released` event |
| `last_indexed_block` | `BIGINT` | highest block we have processed events for |
| `created_at` | `TIMESTAMPTZ` NOT NULL | |
| `updated_at` | `TIMESTAMPTZ` NOT NULL | updated on every indexer write |

**Unique constraint**: `(chain_id, proxy_address, package_key)`.
**Indexes**: on `owner_address`; on `beneficiary_address`; on `(chain_id, proxy_address, package_key)`.

---

### 1.3 `guardian_cache`

Cached on-chain guardian list per package. Populated from `Activated` events and updated
on guardian state reset events. Used only for UX read paths; never for authorization.

| Column | Type | Notes |
|---|---|---|
| `id` | `UUID` PK | generated |
| `package_cache_id` | `UUID` FK → `package_cache(id)` | cascade delete |
| `guardian_address` | `VARCHAR(42)` NOT NULL | EIP-55 |
| `position` | `SMALLINT` NOT NULL | order in guardian array |

**Unique constraint**: `(package_cache_id, guardian_address)`.

---

### 1.4 `event_records`

Indexed on-chain log entries. Immutable once written. Replayable from contract genesis.
Idempotency enforced by unique constraint on `(tx_hash, log_index)`.

| Column | Type | Notes |
|---|---|---|
| `id` | `UUID` PK | generated |
| `chain_id` | `BIGINT` NOT NULL | |
| `proxy_address` | `VARCHAR(42)` NOT NULL | |
| `package_key` | `VARCHAR(66)` NOT NULL | |
| `event_type` | `VARCHAR(40)` NOT NULL | `Activated`, `ManifestUpdated`, `PendingRelease`, `Released`, `Revoked`, `GuardianVetoed`, `GuardianVetoRescinded`, `GuardianApproved`, `GuardianApproveRescinded`, `GuardianStateReset` |
| `emitting_address` | `VARCHAR(42)` NOT NULL | topic[0] — always proxy |
| `block_number` | `BIGINT` NOT NULL | |
| `block_hash` | `VARCHAR(66)` NOT NULL | for post-insert reorg audit |
| `tx_hash` | `VARCHAR(66)` NOT NULL | |
| `log_index` | `INT` NOT NULL | position within block |
| `block_timestamp` | `TIMESTAMPTZ` NOT NULL | from block header |
| `raw_data` | `JSONB` | decoded event fields (event-type specific) |
| `created_at` | `TIMESTAMPTZ` NOT NULL | |

**Unique constraint**: `(tx_hash, log_index)` — idempotency guard.
**Indexes**: on `(chain_id, proxy_address, package_key, block_number)`; on `emitting_address`;
on `block_timestamp`; on `event_type`.

---

### 1.5 `processed_blocks`

Tracks every block processed by the indexer, storing its hash for reorg detection.

| Column | Type | Notes |
|---|---|---|
| `id` | `BIGSERIAL` PK | |
| `chain_id` | `BIGINT` NOT NULL | |
| `proxy_address` | `VARCHAR(42)` NOT NULL | |
| `block_number` | `BIGINT` NOT NULL | |
| `block_hash` | `VARCHAR(66)` NOT NULL | stored at index time; compared on next pass |

**Unique constraint**: `(chain_id, proxy_address, block_number)`.
**Index**: on `(chain_id, proxy_address, block_number DESC)` for fast "last processed block" lookup.

---

### 1.6 `stored_artifacts`

Metadata for pinned manifest JSON or ciphertext blobs.

| Column | Type | Notes |
|---|---|---|
| `id` | `UUID` PK | generated |
| `chain_id` | `BIGINT` | optional (null if not yet linked to a package) |
| `proxy_address` | `VARCHAR(42)` | optional |
| `package_key` | `VARCHAR(66)` | optional |
| `artifact_type` | `VARCHAR(20)` NOT NULL | `manifest` \| `ciphertext` |
| `sha256_hash` | `VARCHAR(66)` NOT NULL | `0x`-prefixed hex; verified before persist |
| `ipfs_uri` | `TEXT` | `ipfs://` URI; null if not pinned to IPFS |
| `s3_uri` | `TEXT` | `s3://` URI; null if not stored in S3 |
| `size_bytes` | `BIGINT` NOT NULL | |
| `created_at` | `TIMESTAMPTZ` NOT NULL | |
| `storage_confirmed_at` | `TIMESTAMPTZ` | set after at least one backend confirms pin |

**Unique constraint**: `(sha256_hash)` — content-addressed dedup.
**Index**: on `(chain_id, proxy_address, package_key, artifact_type)`.

---

### 1.7 `notification_targets`

Delivery subscriptions registered by a package owner or beneficiary. Channel-type is
extensible; current supported values are `email`, `webhook`.

| Column | Type | Notes |
|---|---|---|
| `id` | `UUID` PK | generated |
| `chain_id` | `BIGINT` NOT NULL | |
| `proxy_address` | `VARCHAR(42)` NOT NULL | |
| `package_key` | `VARCHAR(66)` NOT NULL | |
| `subscriber_address` | `VARCHAR(42)` NOT NULL | registered by this address |
| `event_types` | `VARCHAR(40)[]` NOT NULL | array: subset of event_type values from `event_records` |
| `channel_type` | `VARCHAR(20)` NOT NULL | `email` \| `webhook` |
| `channel_value` | `TEXT` NOT NULL | email address or webhook URL |
| `active` | `BOOLEAN` NOT NULL DEFAULT true | |
| `created_at` | `TIMESTAMPTZ` NOT NULL | |
| `last_delivery_attempt` | `TIMESTAMPTZ` | |
| `last_delivery_status` | `VARCHAR(20)` | `delivered` \| `failed` \| `retrying` |

**Unique constraint**: `(chain_id, proxy_address, package_key, subscriber_address, channel_type, channel_value)`.
**Index**: on `(chain_id, proxy_address, package_key, active=true)` for fast dispatch lookup.

---

## 2. Relationships

```
nonces (1) ─── belongs to ────────────────────── wallet (no entity; address only)

package_cache (1) ─── has many ────────────────── guardian_cache (N)
package_cache (1) ─── has many ────────────────── event_records (N)
package_cache (1) ─── has many ────────────────── notification_targets (N)
package_cache (1) ─── has many ────────────────── stored_artifacts (N)

processed_blocks ─── independent; keyed by (chain_id, proxy_address, block_number)
```

---

## 3. Validation Rules

| Entity | Field | Rule |
|---|---|---|
| `nonces` | `wallet_address` | EIP-55 checksummed; 42 chars; starts with `0x` |
| `nonces` | `nonce` | min 32 random bytes, base64url or hex encoded |
| `package_cache` | `package_key` | `0x`-prefixed, 66 chars, hex |
| `package_cache` | `proxy_address` | EIP-55 checksummed, 42 chars |
| `package_cache` | `cached_status` | one of `DRAFT`, `ACTIVE`, `WARNING`, `PENDING_RELEASE`, `CLAIMABLE`, `RELEASED`, `REVOKED` |
| `guardian_cache` | `position` | 0–6 (max 7 guardians, contract-enforced) |
| `event_records` | `event_type` | one of the 10 named event types |
| `stored_artifacts` | `sha256_hash` | `0x` + 64 hex chars |
| `stored_artifacts` | `artifact_type` | `manifest` or `ciphertext` |
| `notification_targets` | `channel_type` | `email` or `webhook` |
| `notification_targets` | `channel_value` | valid email format (if `email`) or valid URL `https://` (if `webhook`) |

---

## 4. State Transitions Captured in Cache

Cache fields are updated by the indexer from on-chain events only. No API write endpoint
updates `package_cache` directly.

| Event | Cache update |
|---|---|
| `Activated` | INSERT `package_cache` row; INSERT `guardian_cache` rows; set `cached_status = ACTIVE` |
| `ManifestUpdated` | UPDATE `manifest_uri` |
| `PendingRelease` | UPDATE `pending_since`, `cached_status = PENDING_RELEASE` (or `WARNING` if service calls chain for confirmation) |
| `Released` | UPDATE `released_at`, `cached_status = RELEASED` |
| `Revoked` | UPDATE `cached_status = REVOKED` |
| `GuardianStateReset` | (no specific cache column; event record stored for query) |

`WARNING` and `CLAIMABLE` are never received as events — they are lazily derived by the
contract. The backend always calls `getPackageStatus()` live for authoritative status; the
cached value is for UX read-path optimization only.

---

## 5. Flyway Migration Naming

```
V1__create_nonces.sql
V2__create_package_cache.sql
V3__create_guardian_cache.sql
V4__create_event_records.sql
V5__create_processed_blocks.sql
V6__create_stored_artifacts.sql
V7__create_notification_targets.sql
V8__create_indexes.sql
```
