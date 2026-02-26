# Data Model: ArcaDigitalis Vault — Policy Contract MVP

**Feature**: `001-vault-policy-mvp`
**Date**: 2026-02-20
**Sources**: spec.md §Storage Layout Plan, §Data Classification; research.md §OZ v5 Storage, §Lazy State Pattern

---

## 1. Entities

### 1.1 Package

The central on-chain entity. Each package represents one owner's digital-legacy vault instance.

**Identity**: `bytes32 packageKey` — globally unique per proxy deployment. Derived off-chain by
the owner, recommended derivation: `keccak256(abi.encodePacked(ownerAddress, uint256 nonce))`.

**Stored in**: `mapping(bytes32 => PackageData) _packages` inside `PolicyV1` storage. Because
`PolicyV1` inherits from OZ upgradeable contracts using ERC-7201 namespaced storage, all fields
are isolated in a deterministic storage namespace (`keccak256("arca.policy.storage") - 1`).

---

### 1.2 PackageData Struct

Storage layout using ERC-7201 namespaced storage (OZ v5 pattern). Fields MUST NOT be
reordered across upgrades. New fields MUST only be appended before `__gap`.

```
/// @custom:storage-location erc7201:arca.policy.storage
struct PolicyStorage {
    mapping(bytes32 => PackageData) packages;
    address upgradeAuthority; // set in initialize(); read exclusively by rescue() for caller validation
}

struct PackageData {
    // ── Actors ──────────────────────────────────────────────────────────────────
    address owner;              // PUBLIC  packed with lastCheckIn in slot N+0
    uint64  lastCheckIn;        // PUBLIC  last check-in UNIX timestamp

    address beneficiary;        // PUBLIC  packed with paidUntil in slot N+1
    uint64  paidUntil;          // PUBLIC  funding expiry UNIX timestamp

    // ── Timing ──────────────────────────────────────────────────────────────────
    uint64  pendingSince;       // PUBLIC  UNIX ts of first PENDING entry; 0 = not pending
    uint64  releasedAt;         // PUBLIC  UNIX ts of claim(); 0 = not released (IMMUTABLE once set)
    uint64  warnThreshold;      // PUBLIC  seconds of inactivity before WARNING
    uint64  inactivityThreshold;// PUBLIC  seconds of inactivity before PENDING_RELEASE

    // ── Policy knobs ─────────────────────────────────────────────────────────────
    uint32  gracePeriodSeconds; // PUBLIC  seconds in PENDING_RELEASE before CLAIMABLE
    uint8   guardianQuorum;     // PUBLIC  min guardians required to veto or approve

    // ── Counters (kept in sync with bitmaps) ────────────────────────────────────
    uint8   vetoCount;          // PUBLIC  current active veto count
    uint8   approvalCount;      // PUBLIC  current fast-track approval count

    // ── Stored status (only for terminal / written states) ──────────────────────
    PackageStatus storedStatus; // PUBLIC  RELEASED=5, REVOKED=6 ONLY — DRAFT never written; existence guard is owner == address(0); live states derived lazily

    // ── Dynamic fields ───────────────────────────────────────────────────────────
    string    manifestUri;      // PUBLIC  POINTER — off-chain manifest URI; treated as public
    address[] guardians;        // PUBLIC  ordered list of guardian addresses

    // ── Guardian bitmaps ─────────────────────────────────────────────────────────
    mapping(address => bool) vetoFlags;     // PUBLIC  guardian has active veto
    mapping(address => bool) approvalFlags; // PUBLIC  guardian has fast-track approval

    // ── Future-proofing gap ──────────────────────────────────────────────────────
    // New fields are APPENDED HERE before __gap; never before existing fields.
    uint256[45] __gap; // brings total reserved slots to 50
}
```

**Packing note**: `address` (20 bytes) + `uint64` (8 bytes) = 28 bytes → fit in one 32-byte slot.
The packing shown above is indicative; the Solidity compiler packs adjacent smaller-than-slot
types. Explicit struct packing review is required during implementation.

---

### 1.3 PackageStatus Enum

```
enum PackageStatus {
    DRAFT            = 0,  // Non-existent / never activated (Solidity zero-value default; no storage write required)
    ACTIVE           = 1,  // Owner alive; clocks healthy
    WARNING          = 2,  // Approaching inactivity threshold
    PENDING_RELEASE  = 3,  // Inactivity or funding lapse; grace countdown
    CLAIMABLE        = 4,  // Grace elapsed and not vetoed; OR guardian fast-track
    RELEASED         = 5,  // claim() executed; releasedAt recorded
    REVOKED          = 6   // Owner permanently cancelled
}
```

**Lazy computation**: Values ACTIVE, WARNING, PENDING_RELEASE, and CLAIMABLE are NOT stored in
`storedStatus`. They are derived by `getPackageStatus()` (a `view` function) from timestamps.
DRAFT is **never written** — it is the Solidity zero-value default (`= 0`) for all unmapped keys.
Only REVOKED (owner action via `revoke()`) and RELEASED (`claim()`) ever write `storedStatus`.

**VETOED**: Not a separate enum value. A package in PENDING_RELEASE with `vetoCount >= quorum`
is "veto-blocked pending". `getPackageStatus()` returns PENDING_RELEASE; `claim()` checks the
veto condition separately and reverts with `ReleasedVetoed()`.

---

### 1.4 Manifest (Off-Chain Entity)

The manifest is a public JSON document pointed to by `manifestUri`. It is NOT stored on-chain
beyond the URI pointer. It is the recovery artifact needed by the beneficiary.

**Recommended structure** (stored as JSON at `manifestUri`):

```json
{
  "packageKey": "0x...",
  "policy": {
    "chainId": 1,
    "contract": "0x<policyProxyAddress>"
  },
  "artifacts": [
    {
      "ciphertextUri": "ipfs://...",
      "ciphertextHash": "0x..."
    }
  ],
  "keyRelease": {
    "encryptedSymmetricKey": "0x...",
    "accessControl": {
      "type": "evmContract",
      "chain": "ethereum",
      "contractAddress": "0x<policyProxyAddress>",
      "functionName": "isReleased",
      "functionParams": ["0x<packageKey>"],
      "returnValueTest": { "comparator": "=", "value": "true" }
    },
    "requester": "0x<beneficiaryAddress>"
  },
  "manifestIntegrity": {
    "hash": "0x<keccak256OfCanonicalManifest>"
  },
  "signatures": [
    { "signer": "0x<ownerAddress>", "sig": "0x..." }
  ]
}
```

All manifest fields are **Public** by design — treat as public metadata.

---

## 2. Field Validation Rules

| Field | Rule |
|---|---|
| `packageKey` | Unique per proxy; collision is owner's responsibility |
| `manifestUri` | Non-empty string; max length unconstrained on-chain (gas-bounded in practice) |
| `beneficiary` | `!= address(0)` |
| `warnThreshold` | `> 0` |
| `inactivityThreshold` | `> warnThreshold` |
| `gracePeriodSeconds` | `> 0` (enforced by `activate()` via `InvalidThresholds()` revert; zero is not permitted on-chain — zero grace collapses PENDING_RELEASE into instant CLAIMABLE, denying guardians any veto window) |
| `paidUntil` | `> block.timestamp` at activation time |
| `guardianQuorum` | `<= guardians.length` if `guardians.length > 0`; `0` disables guardian logic |
| `vetoCount` | Must equal exactly the count of `vetoFlags[g] == true` for all `g` in `guardians` |
| `approvalCount` | Must equal exactly the count of `approvalFlags[g] == true` for all `g` in `guardians` |

---

## 3. State Transitions — Written vs Derived

| Transition | What is written to storage |
|---|---|
| `activate()` | All PackageData fields written for the first time; key transitions from DRAFT zero-default to ACTIVE in one step; `lastCheckIn = block.timestamp` |
| `checkIn()` | `lastCheckIn = block.timestamp`; if was PENDING_RELEASE (not CLAIMABLE): clear `pendingSince`; bulk-reset all guardian flags — `vetoFlags[g] = false` for all `g`, `approvalFlags[g] = false` for all `g`, `vetoCount = 0`, `approvalCount = 0` (FR-033) |
| First write while in lazy PENDING state | `pendingSince` materialized via `_materializePendingSince()` |
| `renew()` | `paidUntil` extended; pre-trigger check must prove funding-only pending (`preFundingLapsed && !preInactivityPending`) and post-update status must exit pending (`ACTIVE`/`WARNING`) before clearing `pendingSince`; in that recovery branch, bulk-reset guardian flags/counters and require owner caller (`NotOwner` otherwise) |
| `guardianVeto()` | Cross-switch (FR-032): if caller has active approval, `approvalFlags[msg.sender] = false`; `approvalCount--` first; then `vetoFlags[msg.sender] = true`; `vetoCount++` (if not already set) |
| `guardianRescindVeto()` | `vetoFlags[msg.sender] = false`; `vetoCount--` (if was set) |
| `guardianApprove()` | Cross-switch (FR-031): if caller has active veto, `vetoFlags[msg.sender] = false`; `vetoCount--` first; then `approvalFlags[msg.sender] = true`; `approvalCount++` (if not already set) |
| `guardianRescindApprove()` | `approvalFlags[msg.sender] = false`; `approvalCount--` (if was set) (FR-030) |
| `claim()` | `releasedAt = block.timestamp`; `storedStatus = RELEASED` |
| `revoke()` | `storedStatus = REVOKED` |
| `rescue()` | `pendingSince = 0`; no other fields altered (Invariant 8) |

---

## 4. Status Computation Logic (Pseudocode)

This is the logic for `getPackageStatus(packageKey)` — pure `view`, no storage writes.

```
function _computeStatus(PackageData p, uint256 now, uint256 pricePerSecond) → PackageStatus:
    if p.storedStatus == RELEASED: return RELEASED
    if p.storedStatus == REVOKED:  return REVOKED
    if p.owner == address(0):      return DRAFT   // non-existent key — owner zero-address is the existence sentinel; DRAFT never written to storedStatus

    // Live package — derive from timestamps
    bool fundingLapsed = (pricePerSecond != 0) && (now >= p.paidUntil)  // disabled when pricePerSecond == 0 (FR-027)
    bool inactivityPending = (now >= p.lastCheckIn + p.inactivityThreshold)
    bool inPending = fundingLapsed || inactivityPending

    if inPending:
        if p.pendingSince != 0:
            pendingSince = p.pendingSince    // already materialized
        else:
            // Conditioned formula (FR-012): use only enabled + crossed triggers
            uint64 inactivityTs = p.lastCheckIn + p.inactivityThreshold
            uint64 fundingTs    = p.paidUntil
            // algebraically: disabled/uncrossed trigger uses UINT64_MAX sentinel
            uint64 iVal = inactivityPending ? inactivityTs : UINT64_MAX
            uint64 fVal = (fundingLapsed)   ? fundingTs    : UINT64_MAX
            pendingSince = min(iVal, fVal)  // yields the earliest crossed+enabled ts

        // Guardian fast-track
        if p.guardianQuorum > 0 && p.approvalCount >= p.guardianQuorum:
            return CLAIMABLE

        // Grace elapsed and not veto-blocked
        if now >= pendingSince + p.gracePeriodSeconds:
            if p.guardianQuorum == 0 || p.vetoCount < p.guardianQuorum:
                return CLAIMABLE
            // else: veto-blocked — return PENDING_RELEASE

        return PENDING_RELEASE

    // Not yet in pending window
    if now >= p.lastCheckIn + p.warnThreshold:
        return WARNING

    return ACTIVE
```

**`_materializePendingSince(p, pricePerSecond)` — called at the top of every write function; `pricePerSecond` is the contract-level init parameter (FR-027)**:
```
if p.storedStatus == RELEASED || p.storedStatus == REVOKED: return
bool inactivityPending = (now >= p.lastCheckIn + p.inactivityThreshold)
bool fundingLapsed     = (pricePerSecond != 0) && (now >= p.paidUntil)  // disabled when pricePerSecond == 0
bool inPending = inactivityPending || fundingLapsed
if inPending && p.pendingSince == 0:
    // Conditioned formula (FR-012, Option B): use ONLY enabled AND crossed triggers.
    // fundingLapsed is already false when pricePerSecond == 0 (see guard above),
    // so paidUntil is never used to compute pendingSince in funding-disabled deployments.
    // UINT64_MAX is the sentinel for a disabled or uncrossed trigger; min() yields
    // the earliest crossing timestamp among the triggers that actually fired.
    uint64 inactivityTs = inactivityPending ? (p.lastCheckIn + p.inactivityThreshold) : UINT64_MAX
    uint64 fundingTs    = fundingLapsed     ?  p.paidUntil                             : UINT64_MAX
    p.pendingSince = min(inactivityTs, fundingTs)
    // Invariant: p.pendingSince is NEVER block.timestamp; NEVER paidUntil when funding disabled.
    // Using max() would delay grace start (security bug); using block.timestamp
    // would allow grace extension via write-delay (security bug).
    uint8 reasonFlags = (inactivityPending ? 0x01 : 0) | (fundingLapsed ? 0x02 : 0)
    emit PendingRelease(packageKey, p.pendingSince, reasonFlags)
```

---

## 5. Relationships

```
PolicyProxy (stable address)
    └── delegates to → PolicyV1 (implementation)
                           └── _packages: mapping(bytes32 => PackageData)
                                              └── PackageData
                                                  ├── owner: address
                                                  ├── beneficiary: address
                                                  ├── guardians: address[]
                                                  └── manifestUri: string → Manifest (off-chain)
                                                                              └── ciphertextUri → Ciphertext (off-chain)
                                                                              └── encDEK → Lit Protocol
                                                                              └── accessControl → PolicyProxy.isReleased(packageKey)
```

---

## 6. Data Retention / Finality

- Once `releasedAt != 0`: the package is permanently RELEASED. No write function can change it. `isReleased()` permanently returns `true`.
- Once `storedStatus == REVOKED`: the package is permanently REVOKED. `isReleased()` permanently returns `false`.
- `pendingSince` once set is never updated while the package remains in PENDING_RELEASE (prevents grace extension).
- `manifestUri` can be updated by the owner at any time before RELEASED; the value emitted in `Released(...)` is the value at `claim()` time.
