# Feature Specification: ArcaDigitalis Vault — Policy Contract MVP

**Feature Branch**: `001-vault-policy-mvp`
**Created**: 2026-02-20
**Status**: Draft
**Input**: SPECIFY.md + README.md (ArcaDigitalis Vault smart contracts)

---

## Glossary

| Term | Definition |
|---|---|
| **Package** | A single legacy payload: encrypted files, instructions, or keys uploaded by the owner |
| **packageKey** | `bytes32` identifier for a package; determined off-chain by the owner (e.g., `keccak256(abi.encodePacked(owner, salt))`) |
| **Owner** | The address that created and controls the package; checks in to signal liveness |
| **Beneficiary** | The address entitled to trigger `claim()` once conditions are met |
| **Guardian** | An optional trusted address that can veto or approve release before grace expires |
| **manifestUri** | Public URI pointing to the off-chain manifest JSON (S3 / IPFS / Arweave) |
| **ciphertextUri** | Public URI pointing to the encrypted payload blob |
| **encDEK** | Lit-encrypted Data Encryption Key; stored in the manifest; publicly safe |
| **DEK** | Raw symmetric decryption key; never on-chain; revealed only by Lit under ACC |
| **Lit ACC** | Access Control Condition bound to `policyProxy.isReleased(packageKey) == true` |
| **PolicyProxy** | The `TransparentUpgradeableProxy` — the only stable address for all integrations |
| **ProxyAdmin** | OZ upgrade controller; must be owned by a multisig in production |
| **warnThreshold** | Seconds of inactivity after which the package enters WARNING |
| **inactivityThreshold** | Seconds of inactivity after which the package enters PENDING_RELEASE |
| **gracePeriodSeconds** | Seconds of PENDING_RELEASE before CLAIMABLE is reached |
| **paidUntil** | UNIX timestamp until which retention is funded |
| **releasedAt** | UNIX timestamp recorded permanently on `claim()` |

---

## Clarifications

### Session 2026-02-20

- Q: What does `DRAFT` state mean — is it an explicitly registered pre-activation state tied to a `register()` call, or a sentinel for "non-existent"? → A: `DRAFT` = "non-existent / never activated". No `register()` function exists. `PackageStatus.DRAFT = 0` is the Solidity zero-value default for all unmapped `packageKey`s; no storage write ever sets a key to DRAFT. `getPackageStatus(unknownKey)` returns `DRAFT` as the safe discovery path for off-chain tooling. All state-mutating functions called with a key whose `owner == address(0)` revert with `PackageNotFound()`. `activate()` is the sole entry point, transitioning a key from DRAFT (zero default) to ACTIVE in a single step.
- Q: What is the canonical `initialize()` signature — does it take `address upgradeAuthority_` as an explicit second parameter, or is the upgrade authority derived from `msg.sender` at init time? → A: 2-parameter form is canonical: `initialize(uint256 pricePerSecond, address upgradeAuthority_)`. The implementation contract has no built-in access to the OZ v5 ProxyAdmin's owner; storing `upgradeAuthority_` explicitly in `PolicyStorage` at init time is required for `rescue()` caller validation.
- Q: Does `rescue()` belong on the `IPolicy` interface surface, or on a separate `IPolicyAdmin` interface / implementation-only? → A: `rescue()` is part of `IPolicy` directly — consistent with T008's explicit count of 11 state-mutating functions including `rescue()` and `guardianRescindApprove()`; single interface cast for test code and external callers.
- Q: Is `pendingSince` set to `block.timestamp` of the first write, or to the actual threshold-crossing timestamp? → A: `pendingSince` is ALWAYS set to the **earliest crossing timestamp among enabled and crossed triggers** — NOT `block.timestamp` of the materializing transaction. Using `block.timestamp` would silently delay the grace window start and is a security bug. The conditioned formula (see FR-012) conditions on which triggers are actually active (`fundingEnabled = pricePerSecond != 0`) and have crossed, preventing `paidUntil` from backdating `pendingSince` in funding-disabled deployments. Grace/claimable conditions computed from this value everywhere.
- Q: Should `checkIn()` be callable in CLAIMABLE state to cancel the release? → A: No. `checkIn()` MUST revert with `AlreadyClaimable()` when status is CLAIMABLE. Once a package reaches CLAIMABLE (grace elapsed, no blocking veto), the owner cannot reverse it via check-in. This prevents a last-minute `checkIn()` from denying the beneficiary a valid release claim.
- Q: What is the canonical claimability guard when `guardianQuorum == 0`? → A: The guard MUST be `(guardianQuorum == 0 || vetoCount < guardianQuorum)`. When `guardianQuorum == 0`, no veto mechanism is configured; the grace condition alone determines CLAIMABLE. Using only `vetoCount < guardianQuorum` deadlocks release when quorum is 0 (uint8: `0 < 0` is always false).
- Q: What happens when `pricePerSecond == 0` — can `renew()` be called, and does `paidUntil` still trigger PENDING_RELEASE? → A: When `pricePerSecond == 0`: (1) `renew()` MUST revert with `FundingDisabled()` — ETH-based funding is not configured; (2) the funding lapse trigger MUST be disabled in status computation — `bool fundingLapsed = (pricePerSecond != 0) && (now >= paidUntil)`; packages can only enter PENDING_RELEASE via inactivity.
- Q: What happens when `activate()` is called with `msg.value > 0` and `pricePerSecond == 0`? → A: `activate()` MUST revert with `FundingDisabled()` if `msg.value > 0` and `pricePerSecond == 0`. ETH-based funding extension is fully disabled; no ETH may be trapped in the contract via `activate()`. When `msg.value == 0`, `activate()` proceeds normally with `paidUntil` set from the explicit parameter. This is symmetric with `renew()` (FR-028).
- Q: Is `gracePeriodSeconds == 0` a valid on-chain configuration, or must `activate()` enforce `gracePeriodSeconds > 0`? → A: `activate()` MUST enforce `gracePeriodSeconds > 0`, reverting with `InvalidThresholds()`. A zero-grace window causes PENDING_RELEASE to immediately become CLAIMABLE, denying guardians any veto window and creating an unsafe configuration class. The existing `InvalidThresholds()` error covers this class of precondition violations (FR-002).
- Q: When a write function first detects that a package crossed into PENDING_RELEASE (`pendingSince == 0` in storage, thresholds crossed) yet that same call would cancel the pending state (e.g., `checkIn()`, `renew()`) — should `PendingRelease` still be emitted in that transaction? → A: Yes — emit-then-cancel (Option A). Every state-mutating function MUST call `_materializePendingSince()` first: if `pendingSince == 0` and thresholds are crossed, write `pendingSince` to storage (using the conditioned formula, FR-012) and emit `PendingRelease(packageKey, pendingSince, reasonFlags)`, then proceed with the call's own logic (e.g., `checkIn()` clears `pendingSince` back to 0; `renew()` extends `paidUntil` + clears `pendingSince`). Both events appear in the same transaction. This preserves a complete on-chain audit trail — the `PendingRelease` event proves the package legitimately entered PENDING_RELEASE before the owner recovered, which is critical forensic evidence in beneficiary disputes.
- Q: Is the `pendingSince` formula an unconditional `min(inactivityTs, paidUntil)`, or must it condition on which triggers are enabled and have actually crossed? → A: Conditioned formula (Option B). `pendingSince` MUST equal the earliest crossing timestamp among triggers that are both **(a) enabled** and **(b) crossed**. Definitions: `inactivityTs = lastCheckIn + inactivityThreshold`; `fundingTs = paidUntil`; `fundingEnabled = (pricePerSecond != 0)` (FR-027); `inactivityCrossed = (now >= inactivityTs)`; `fundingCrossed = (fundingEnabled && now >= fundingTs)`. Canonical rule: if both crossed → `min(inactivityTs, fundingTs)`; if only inactivity crossed → `inactivityTs`; if only funding crossed → `fundingTs`; neither → not pending. Using an unconditional `min()` in a `pricePerSecond == 0` deployment would backdate `pendingSince` to `paidUntil` even though no funding trigger fired, silently shortening the grace window. Algebraically equivalent implementation: use `UINT64_MAX` as the sentinel value for a disabled or uncrossed trigger, then `min(inactivityVal, fundingVal)` yields the correct result.
- Q: Are guardian veto and rescind operations strictly per-caller — can a guardian cast more than one vote, and can a guardian rescind another guardian’s active veto? → A: Strictly per-caller. `guardianVeto()` records and counts only the calling guardian’s own vote; calling it a second time is idempotent (`vetoCount` does NOT increment again). `guardianRescindVeto()` clears only the calling guardian’s own veto flag; it MUST NOT affect another guardian’s flag. A call by guardian B where guardian B has no active veto is a silent no-op regardless of whether guardian A has an outstanding veto — `vetoCount` is unchanged, no event is emitted, no error is thrown. This isolation is enforced by the `mapping(address => bool) vetoFlags` data structure: each flag is keyed to the caller’s own address.- Q: When `msg.value > 0` but `uint64(msg.value / pricePerSecond) == 0` (dust ETH below the per-second rate threshold — integer division truncates to 0 seconds), should `renew()` silently absorb the ETH or revert? → A: Revert with a new `InsufficientPayment()` error (error #23). Silently accepting dust ETH would trap irrecoverable funds in the contract with no benefit to `paidUntil`. The guard fires when `msg.value > 0` AND `pricePerSecond != 0` AND `uint64(msg.value / pricePerSecond) == 0`. Applied symmetrically to both `renew()` and the initial ETH deposit path in `activate()` — neither may absorb dust ETH without extending `paidUntil`.
- Q: Should guardians be able to rescind their approval (symmetric to `guardianRescindVeto()`)? Should switching from approve→veto or veto→approve auto-rescind the opposing flag? → A: Yes to both. (1) New function `guardianRescindApprove()`: clears caller's own approval flag; silent no-op if no active approval; decrements `approvalCount`; emits `GuardianApproveRescinded(packageKey, guardian, approvalCount)` only when a flag was actually cleared. (2) Cross-switch: `guardianApprove()` MUST auto-rescind the caller's own active veto before recording the approval — emit `GuardianVetoRescinded` then `GuardianApproved` in same tx; `guardianVeto()` MUST auto-rescind the caller's own active approval before recording the veto — emit `GuardianApproveRescinded` then `GuardianVetoed` in same tx. Counters stay mutually exclusive per caller. Two sequential events per cross-switch (rescind old → record new).
- Q: When `checkIn()` performs a PENDING_RELEASE → ACTIVE recovery, should it bulk-reset all guardian veto and approval flags? What event fires? → A: Yes — on the PENDING_RELEASE→ACTIVE recovery path only (not on routine `checkIn()` from ACTIVE or WARNING). All guardian `vetoFlags` and `approvalFlags` entries are cleared; `vetoCount` and `approvalCount` reset to 0. Emits single summary event `GuardianStateReset(bytes32 indexed packageKey)` — no per-guardian events (avoids variable-N gas loop). Routine `checkIn()` calls from ACTIVE or WARNING state do NOT touch guardian state.

### Session 2026-02-23

- Q: If `checkIn()` updates `lastCheckIn` but a funding lapse remains active (`pricePerSecond != 0 && now >= paidUntil`), should `pendingSince` be cleared and `GuardianStateReset` be emitted? → A: No. Guardian state bulk-reset and `GuardianStateReset` MUST fire only when the package genuinely transitions out of PENDING_RELEASE. Implementation rule: after writing `lastCheckIn`, re-evaluate `_computeStatus()`; if the resulting status is ACTIVE or WARNING (all active triggers cleared), proceed with `pendingSince = 0`, guardian reset, and emit `GuardianStateReset`. If the result is still PENDING_RELEASE (e.g., a funding lapse persists), `pendingSince` MUST NOT be zeroed and `GuardianStateReset` MUST NOT be emitted — the release window remains open and the beneficiary's legitimate claim is preserved. Condition implementation: `if (p.pendingSince != 0 && _computeStatus(p, block.timestamp, pricePerSecond) != PackageStatus.PENDING_RELEASE)`.
- Q: The README state machine diagram uses strict `>` comparisons (e.g., `now > lastCheckIn + warnThreshold`) but PolicyLib uses inclusive `>=`. Which is authoritative? → A: Inclusive `>=` is the on-chain authoritative boundary for every threshold crossing. `PolicyLib._computeStatus()` uses `ts >= lastCheckIn + warnThreshold` (WARNING), `ts >= lastCheckIn + inactivityThreshold` (PENDING_RELEASE via inactivity), `ts >= paidUntil` (PENDING_RELEASE via funding lapse), and `ts >= pendingSince + gracePeriodSeconds` (CLAIMABLE). Using strict `>` understates the threshold: at exactly `ts == threshold`, the package has already crossed, and an integrator polling with `>` would miss the first eligible block. The README diagram, spec threat table, Key Invariant 9, and T3 test prose references have been updated to `>=` throughout. The T3 boundary test assertions (`now == threshold` → transitioned state) are the definitive integration test anchors.
- Q: Should `scripts/export-artifact.ts` silently write empty strings when `findAddress` cannot locate `proxy`, `implementation`, or `proxyAdmin` in `deployed_addresses.json`? → A: No. The script MUST throw a descriptive error and exit non-zero when any required address field resolves to an empty string. Silently writing an empty address produces a structurally valid but functionally unusable artifact; downstream CI pipelines and Lit ACC configuration would accept the artifact without detecting the missing address. Required fields: `proxy`, `implementation`, `proxyAdmin`. `upgradeAuthority` is optional and may remain empty (sourced from env). The error message MUST name the field and list the candidate keys searched (e.g., `"proxy" address not found in deployed_addresses.json — searched for keys matching: #proxy, transparentupgradeableproxy`).
- Q: Should `ignition/modules/PolicyUpgradeModule.ts` default `existingProxyAddress`, `proxyAdminAddress`, and `reinitPackageKey` to zero values when discovery fails and no explicit parameter is supplied? → A: No. The module MUST throw at module-build time when any of these three required parameters cannot be resolved — either via auto-discovery from `deployed_addresses.json` or via an explicit Ignition parameter override. Defaulting to `address(0)` would silently issue an `upgradeAndCall` against the zero address, which is a live-network no-op at best and an irreversible state corruption at worst; Hardhat Ignition does not simulate before broadcasting. `reinitPackageKey = bytes32(0)` is equally invalid: a real package key is always `keccak256(abi.encodePacked(owner, salt))`, so zero-hash can never map to an activated package and passing it to `reinitializeV2` would corrupt state. The module MUST throw with a message naming the missing parameter and the discovery candidates tried. Parameters resolved by auto-discovery are acceptable without an explicit CLI override; the error fires only when both discovery and parameter override fail.
- Q: What specific invariant does zeroing `pendingSince` in `checkIn()` violate when a funding lapse is still active? → A: It breaks **Key Invariant 7** — "`pendingSince` is NEVER updated while the package stays in PENDING_RELEASE". Mechanism: `checkIn()` writes `lastCheckIn = block.timestamp`, which clears the inactivity trigger. If `pendingSince` is then zeroed while the funding trigger is still active (`pricePerSecond != 0 && now >= paidUntil`), the next `_materializePendingSince()` call re-materializes `pendingSince = paidUntil` (the only remaining crossed trigger). If the original `pendingSince` was `inactivityTs` and `inactivityTs < paidUntil` (inactivity crossed first), the new value is later than the original, shifting the grace window start forward by `paidUntil − inactivityTs` and extending the time until CLAIMABLE by the same amount. The owner could exploit this to indefinitely delay the beneficiary by repeatedly checking in just before grace elapses. The FR-033 post-update status re-evaluation guard prevents this: `pendingSince` is zeroed only when all active triggers are clear, ensuring its stored value is never advanced while PENDING_RELEASE persists.
- Q: Should a non-owner be able to reset guardian veto/approval decisions by calling `renew()` during funding-lapse-only recovery? → A: No. `renew()` remains permissionless for pure funding extension, but the recovery branch that clears `pendingSince` and bulk-resets guardian state is owner-only. Recovery/reset eligibility is trigger-accurate: pre-extension requires `preStatus == PENDING_RELEASE && preFundingLapsed && !preInactivityPending`; post-extension requires `postStatus == ACTIVE || postStatus == WARNING`. Only then may `renew()` clear/reset, and only when `msg.sender == owner`; otherwise revert `NotOwner()`. This prevents third parties from erasing guardian decisions and prevents false resets at exact inactivity boundaries.
---

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Owner Creates and Activates a Package (Priority: P1)

An owner encrypts their legacy files client-side, uploads ciphertext to off-chain storage, signs
a manifest binding Lit ACC to the proxy `isReleased()`, and activates an on-chain policy for that
`packageKey`. From this point, the vault is live: the owner can check in periodically and the
beneficiary address is locked in.

**Why this priority**: This is the entry point for the entire system. Nothing else (warns, releases,
guardian votes) exists until activation succeeds. Without this story there is no MVP.

**Independent Test**: Deploy the proxy, call `activate()` with valid params, assert package is in
ACTIVE state, assert `PackageActivated` event emitted with correct `packageKey` and `manifestUri`,
assert `getPackageStatus()` returns ACTIVE.

**Acceptance Scenarios**:

1. **Given** a fresh proxy deployment, **When** the owner calls `activate(packageKey, manifestUri, beneficiary, warnThreshold, inactivityThreshold, gracePeriodSeconds)` with `paidUntil > now` and all required fields set, **Then** `getPackageStatus(packageKey)` returns `ACTIVE`, `PackageActivated(packageKey, manifestUri)` is emitted, `lastCheckIn` is set to `block.timestamp`.

2. **Given** an unactivated (DRAFT) `packageKey`, **When** `activate()` is called with `manifestUri` = empty string, **Then** it reverts with `ManifestUriRequired()`.

3. **Given** an unactivated (DRAFT) `packageKey`, **When** `activate()` is called with `beneficiary` = `address(0)`, **Then** it reverts with `BeneficiaryRequired()`.

4. **Given** an unactivated (DRAFT) `packageKey`, **When** `activate()` is called with `paidUntil` ≤ `block.timestamp`, **Then** it reverts with `FundingRequired()`.

5. **Given** an ACTIVE package, **When** `activate()` is called again by the same owner, **Then** it reverts with `AlreadyActive()`.

6. **Given** an ACTIVE package, **When** a different address calls `activate()` with the same `packageKey`, **Then** it reverts with `AlreadyActive()`. *(Note: `activate()` has no pre-existing owner check — the caller always becomes the owner on a fresh key. This scenario covers re-activation of an already-ACTIVE package by any caller.)*

---

### User Story 2 — Owner Maintains Liveness via Check-In (Priority: P1)

After activation the owner periodically calls `checkIn()` to signal they are alive. This resets
the inactivity clock. If the package reaches WARNING state, a check-in returns it to ACTIVE.
If the package has reached PENDING_RELEASE (but is not yet CLAIMABLE), a check-in can cancel
the pending release and restore ACTIVE.

**Why this priority**: Check-in is the primary liveness mechanism. It determines the entire
lifecycle rhythm and is called most frequently of all owner operations.

**Independent Test**: Activate a package, advance time past `warnThreshold`, call `checkIn()`,
assert status returns to ACTIVE, assert `lastCheckIn` updated, assert `CheckIn` event emitted.

**Acceptance Scenarios**:

1. **Given** an ACTIVE package, **When** the owner calls `checkIn()`, **Then** `lastCheckIn` is updated to `block.timestamp` and `CheckIn(packageKey, lastCheckIn)` is emitted.

2. **Given** a WARNING package, **When** the owner calls `checkIn()`, **Then** `getPackageStatus(packageKey)` returns `ACTIVE` and `CheckIn` event is emitted.

3. **Given** a PENDING_RELEASE package (grace not yet elapsed), **When** the owner calls `checkIn()`, **Then** `getPackageStatus(packageKey)` returns `ACTIVE`, `pendingSince` is cleared, and `CheckIn` event is emitted.

4. **Given** a RELEASED package, **When** any address calls `checkIn()`, **Then** it reverts with `PackageAlreadyReleased()`.

5. **Given** an ACTIVE package, **When** a non-owner address calls `checkIn()`, **Then** it reverts with `NotOwner()`.

---

### User Story 3 — Beneficiary Claims a Released Package (Priority: P1)

After inactivity or funding lapse and the grace period elapses without a veto quorum, the package
enters CLAIMABLE. The beneficiary calls `claim()`, which records `releasedAt`, emits `Released`,
and moves the package to RELEASED. Lit ACC then evaluates `isReleased()` as true and releases the DEK.

**Why this priority**: This is the terminal success path — the reason the vault exists. Without
a working, testable `claim()` the MVP has no value.

**Independent Test**: Activate a package, advance time past `inactivityThreshold + gracePeriodSeconds`,
call `claim()` as beneficiary, assert `isReleased()` returns true, assert `releasedAt` ≠ 0, assert
`Released(packageKey, releasedAt, manifestUri)` event emitted with exact values.

**Acceptance Scenarios**:

1. **Given** a CLAIMABLE package (inactivity path), **When** the beneficiary calls `claim()`, **Then** `isReleased(packageKey)` returns `true`, `releasedAt` equals `block.timestamp` of the `claim()` transaction, `Released(packageKey, releasedAt, manifestUri)` is emitted with exact values, and `getPackageStatus()` returns `RELEASED`.

2. **Given** a CLAIMABLE package (funding lapse path), **When** the beneficiary calls `claim()`, **Then** same assertions as above apply.

3. **Given** a CLAIMABLE package, **When** a non-beneficiary address calls `claim()`, **Then** it reverts with `NotBeneficiary()`.

4. **Given** a PENDING_RELEASE package where `now < pendingSince + gracePeriodSeconds`, **When** the beneficiary calls `claim()`, **Then** it reverts with `GracePeriodNotElapsed()`.

5. **Given** a PENDING_RELEASE package with guardian `vetoCount >= quorum`, **When** grace elapses and the beneficiary calls `claim()`, **Then** it reverts with `ReleasedVetoed()`.

6. **Given** a RELEASED package, **When** `claim()` is called again, **Then** it reverts with `PackageAlreadyReleased()`.

---

### User Story 4 — Guardian Operations: Veto, Approve, Rescind, Cross-Switch (Priority: P2)

Guardians can veto a pending release to block the package from becoming CLAIMABLE. A guardian
can later rescind their veto. If after rescinding `vetoCount < quorum`, the package returns to
normal pending flow and will become CLAIMABLE once grace elapses.

**Why this priority**: Guardian veto is an important safety mechanism but the vault still works
end-to-end (with no guardians configured) without it.

**Independent Test**: Activate a package with 2 guardians and quorum = 2, advance to
PENDING_RELEASE, call `guardianVeto()` from both guardians, assert release blocked after grace,
rescind one veto, assert release still blocked, rescind both, assert CLAIMABLE after grace.

**Acceptance Scenarios**:

1. **Given** a PENDING_RELEASE package with quorum = 2, **When** guardian A calls `guardianVeto()`, **Then** `vetoCount` is 1, `GuardianVetoed(packageKey, guardian, 1)` is emitted, and the package is still PENDING_RELEASE (quorum not met).

2. **Given** a PENDING_RELEASE package with quorum = 2 and `vetoCount` = 2, **When** grace elapses and beneficiary calls `claim()`, **Then** it reverts with `ReleasedVetoed()`.

3. **Given** a vetoed PENDING_RELEASE package, **When** a guardian calls `guardianRescindVeto()`, **Then** `vetoCount` decrements, `GuardianVetoRescinded(packageKey, guardian, newCount)` is emitted.

4. **Given** a PENDING_RELEASE package with `vetoCount` < quorum and grace elapsed, **When** beneficiary calls `claim()`, **Then** it succeeds and package moves to RELEASED.

5. **Given** a RELEASED package, **When** a guardian calls `guardianVeto()`, **Then** it reverts with `PackageAlreadyReleased()`.

6. **Given** a PENDING_RELEASE package, **When** `guardianVeto()` is called twice by the same guardian, **Then** `vetoCount` increments only once (idempotent).

7. **Given** a guardian with no active veto, **When** `guardianRescindVeto()` is called, **Then** `vetoCount` does not change (idempotent, no error).
8. **Given** a PENDING_RELEASE package with quorum = 2, guardian A has vetoed (`vetoCount == 1`), guardian B has NOT vetoed, **When** guardian B calls `guardianRescindVeto()`, **Then** `vetoCount` remains 1, no `GuardianVetoRescinded` event is emitted, no error is thrown — guardian B’s own flag was already false; guardian A’s veto is completely unaffected (per-caller isolation, FR-016).
9. **Given** a PENDING_RELEASE package with guardian A having an active approval (`approvalCount == 1`), **When** guardian A calls `guardianRescindApprove()`, **Then** `approvalCount` is 0, `GuardianApproveRescinded(packageKey, guardian, 0)` is emitted (idempotent: second call is no-op, no event emitted on second call).
10. **Given** a PENDING_RELEASE package where guardian A has an active veto (`vetoCount == 1`), **When** guardian A calls `guardianApprove()`, **Then** the transaction log contains BOTH `GuardianVetoRescinded(packageKey, guardian, 0)` AND `GuardianApproved(packageKey, guardian, 1)` in that order; `vetoCount == 0`, `approvalCount == 1` (cross-switch auto-rescind, FR-031/FR-032).
11. **Given** a PENDING_RELEASE package where guardian A has an active approval (`approvalCount == 1`), **When** guardian A calls `guardianVeto()`, **Then** the transaction log contains BOTH `GuardianApproveRescinded(packageKey, guardian, 0)` AND `GuardianVetoed(packageKey, guardian, 1)` in that order; `approvalCount == 0`, `vetoCount == 1`.
12. **Given** a PENDING_RELEASE package (inactivity-triggered only, funding healthy) with `vetoCount == 2` and `approvalCount == 1`, **When** the owner calls `checkIn()` (grace not elapsed), **Then** `pendingSince` is cleared, `vetoCount == 0`, `approvalCount == 0`, all guardian flags cleared, `GuardianStateReset(packageKey)` is emitted, `CheckIn(packageKey, now)` is emitted in the same transaction (FR-033).
13. **Given** a PENDING_RELEASE package where `pendingSince` was set to `inactivityTs` (inactivity crossed first, `inactivityTs < paidUntil`) and a funding lapse is ALSO active, with active guardian vetoes, **When** the owner calls `checkIn()` (grace not elapsed), **Then** `lastCheckIn` is updated, status re-evaluated returns PENDING_RELEASE (funding lapse still active), `pendingSince` retains its original value (`inactivityTs`), `vetoCount` and guardian flags are NOT changed, `GuardianStateReset` is NOT emitted, only `CheckIn(packageKey, now)` is emitted; subsequent `getPackageStatus()` reads return PENDING_RELEASE with an unchanged grace window (CLAIMABLE at `inactivityTs + gracePeriodSeconds`, NOT at `paidUntil + gracePeriodSeconds`) (FR-033, Key Invariant 7).

---

### User Story 5 — Owner Renews Funding (Priority: P2)

The owner (or anyone) can deposit ETH to extend `paidUntil`. If the funding has lapsed and the
package entered PENDING_RELEASE solely due to funding expiry, renewing restores the package to
ACTIVE.

**Why this priority**: Retention funding is a liveness mechanism but simpler than check-in;
the vault still has a complete inactivity-only path without it.

**Acceptance Scenarios**:

1. **Given** an ACTIVE package with `paidUntil` about to expire, **When** `renew()` is called with `msg.value > 0`, **Then** `paidUntil` is extended, `Renewed(packageKey, paidUntil)` is emitted.

2. **Given** a PENDING_RELEASE package triggered by funding lapse only (inactivity clock still healthy), **When** the owner calls `renew()`, **Then** `getPackageStatus()` returns `ACTIVE` and `pendingSince` is cleared.

3. **Given** a RELEASED package, **When** `renew()` is called, **Then** it reverts with `PackageAlreadyReleased()`.

---

### User Story 6 — Owner Revokes a Package (Priority: P3)

An owner can permanently revoke a package before it reaches RELEASED. This enables recovery
when a beneficiary address changes, the owner decides to cancel, or the vault design becomes
obsolete.

**Acceptance Scenarios**:

1. **Given** an ACTIVE package, **When** the owner calls `revoke(packageKey)`, **Then** `getPackageStatus()` returns `REVOKED` and `Revoked(packageKey)` is emitted.

2. **Given** a PENDING_RELEASE package, **When** the owner calls `revoke()`, **Then** status becomes `REVOKED`.

3. **Given** a RELEASED package, **When** the owner calls `revoke()`, **Then** it reverts with `PackageAlreadyReleased()`.

4. **Given** a REVOKED package, **When** the owner calls `revoke()` again, **Then** it reverts with `AlreadyRevoked()`.

---

### User Story 7 — Guardian Fast-Track Approval (Priority: P3)

Guardians can actively approve release before grace elapses, fast-tracking a PENDING_RELEASE
package to CLAIMABLE when `approvalCount >= quorum`.

**Acceptance Scenarios**:

1. **Given** a PENDING_RELEASE package with quorum = 2, **When** both guardians call `guardianApprove()`, **Then** `approvalCount` reaches 2, `getPackageStatus()` returns CLAIMABLE (fast-track), `GuardianApproved(packageKey, guardian, approvalCount)` emitted.

2. **Given** a CLAIMABLE package via fast-track, **When** the beneficiary calls `claim()`, **Then** it succeeds and package moves to RELEASED with `releasedAt` recorded.

---

### Edge Cases

- **Exact boundary at threshold**: A package with `now == lastCheckIn + inactivityThreshold` must transition; `now == lastCheckIn + inactivityThreshold - 1` must not.
- **Concurrent triggers**: A package can hit both funding lapse AND inactivity simultaneously; either trigger is sufficient to enter PENDING_RELEASE.
- **Veto after grace elapses logically**: If veto quorum is reached after grace has already elapsed, release is still blocked until vetoes are rescinded.
- **Guardian not in list**: A caller not in `guardians[]` calling `guardianVeto()` reverts with `NotGuardian()`.
- **Zero guardians**: If `guardianQuorum == 0` or no guardians are set, guardian functions (`guardianVeto`, `guardianApprove`, `guardianRescindVeto`) revert with `GuardiansNotConfigured()`; veto/approval logic is skipped. CLAIMABLE is reached on grace condition alone — `(guardianQuorum == 0 || vetoCount < guardianQuorum)` is trivially satisfied; no deadlock.
- **`checkIn()` when CLAIMABLE**: Owner calling `checkIn()` after grace has elapsed MUST revert with `AlreadyClaimable()`. Once CLAIMABLE, the release countdown cannot be cancelled by owner activity.
- **`pricePerSecond == 0` (funding disabled)**: `renew()` reverts with `FundingDisabled()`; advancing past `paidUntil` does NOT trigger PENDING_RELEASE; only inactivity (`block.timestamp >= lastCheckIn + inactivityThreshold`) can enter PENDING_RELEASE.
- **`activate()` ETH with funding disabled (`pricePerSecond == 0`)**: If `msg.value > 0`, `activate()` MUST revert with `FundingDisabled()` — symmetric with `renew()`. If `msg.value == 0`, `activate()` proceeds normally with `paidUntil` from the explicit parameter. No ETH can be trapped in the contract via `activate()` in a funding-disabled deployment (FR-028).
- **manifestUri update on ACTIVE package**: Owner calls `updateManifestUri()`, `ManifestUpdated(packageKey, newUri)` emitted; `manifestUri` stored in the Released event reflects the latest value at time of `claim()`.
- **Reentrancy on `claim()`**: If beneficiary is a contract that re-enters on ETH receive, reentrancy guard prevents double-claim.
- **Unknown / never-activated `packageKey`**: `DRAFT = 0` is the Solidity zero-value default for all unmapped keys; no `register()` step exists; `activate()` is the sole creation entry point; existence is detected via `owner == address(0)`. View-function behaviour on unknown keys — all four never revert:
  - `getPackageStatus(unknownKey)` → returns `DRAFT` (safe off-chain discovery path).
  - `isReleased(unknownKey)` → returns `false` (safe for Lit ACC evaluation).
  - `getPackage(unknownKey)` → returns a zero-valued `PackageView` with `status = DRAFT` and all other fields empty/zero. Callers SHOULD check `pkg.owner != address(0)` to detect non-existent packages before relying on other fields.
  - `getManifestUri(unknownKey)` → returns empty string `""`.
  **All state-mutating functions** (`activate()` succeeds only for truly fresh keys; `checkIn()`, `renew()`, `updateManifestUri()`, `guardianVeto()`, `guardianRescindVeto()`, `guardianApprove()`, `guardianRescindApprove()`, `claim()`, `revoke()`, `rescue()`) called with an unknown key MUST revert with `PackageNotFound()` — except `activate()` which succeeds on a fresh key and reverts `AlreadyActive()` if the key already exists.

---

## Requirements *(mandatory)*

### Functional Requirements

**Package lifecycle**:

- **FR-001**: The contract MUST allow an owner to create a package via `activate()`, moving it from non-existent DRAFT (zero-value sentinel; `owner == address(0)`) to ACTIVE in a single step, provided all preconditions are met. Re-activating an existing key (`owner != address(0)`) MUST revert with `AlreadyActive()`.
- **FR-002**: The contract MUST enforce activation preconditions: non-empty `manifestUri`, non-zero `beneficiary`, valid thresholds (`inactivityThreshold > warnThreshold > 0` AND `gracePeriodSeconds > 0`), and `paidUntil > block.timestamp`. A zero `gracePeriodSeconds` MUST revert with `InvalidThresholds()` — zero grace causes PENDING_RELEASE to immediately become CLAIMABLE, denying guardians any veto window.
- **FR-003**: The contract MUST allow the owner to call `checkIn()` to reset `lastCheckIn`, preventing inactivity release.
- **FR-004**: The contract MUST allow the owner (or anyone) to call `renew()` with `msg.value` to extend `paidUntil` via `paidUntil += uint64(msg.value / pricePerSecond)` where `pricePerSecond` is set in `initialize()`. If `pricePerSecond == 0`, `renew()` MUST revert with `FundingDisabled()`. If `msg.value > 0`, `pricePerSecond != 0`, and `uint64(msg.value / pricePerSecond) == 0` (dust payment below rate threshold — integer division truncates to 0 seconds), `renew()` MUST revert with `InsufficientPayment()` — dust ETH MUST NOT be silently absorbed with no `paidUntil` extension. Same guard applies symmetrically to the ETH deposit path in `activate()`.
- **FR-005**: The contract MUST allow the owner to call `updateManifestUri()` at any time before RELEASED to update the manifest pointer, emitting `ManifestUpdated`.
- **FR-006**: The contract MUST allow the owner to call `revoke()` on any package not yet RELEASED, transitioning to REVOKED.
- **FR-007**: The beneficiary MUST be the only address that can call `claim()` (MVP).
- **FR-008**: `claim()` MUST write `releasedAt = block.timestamp` and emit `Released(packageKey, releasedAt, manifestUri)` with exact values.
- **FR-009**: `isReleased(packageKey)` MUST return `true` immediately and permanently after a successful `claim()`.

**State transitions (lazy, no executor)**:

- **FR-010**: STATUS transitions driven by inactivity and funding lapse MUST be computed lazily: `getPackageStatus()` MUST return the correct state based on current `block.timestamp` without requiring an explicit trigger transaction.
- **FR-011**: WARNING state MUST be entered lazily when `block.timestamp >= lastCheckIn + warnThreshold` and the package is ACTIVE.
- **FR-012**: PENDING_RELEASE MUST be entered lazily when `block.timestamp >= lastCheckIn + inactivityThreshold` (inactivity path) OR (`pricePerSecond != 0` AND `block.timestamp >= paidUntil`) (funding lapse). `pendingSince` MUST be set on the first write-transition into PENDING state using the **conditioned formula**: only triggers that are both enabled and crossed participate. Let `inactivityTs = lastCheckIn + inactivityThreshold`, `fundingTs = paidUntil`, `fundingEnabled = (pricePerSecond != 0)`, `inactivityCrossed = (block.timestamp >= inactivityTs)`, `fundingCrossed = (fundingEnabled && block.timestamp >= fundingTs)`. Then: if both triggered → `pendingSince = min(inactivityTs, fundingTs)`; if only inactivity → `pendingSince = inactivityTs`; if only funding → `pendingSince = fundingTs`. `pendingSince` is NEVER set to `block.timestamp` of the materializing write — this would allow grace extension via write-delay. Algebraically equivalent: use `UINT64_MAX` as sentinel for disabled/uncrossed triggers and take `min()`. Never materialize when neither trigger has crossed.
- **FR-013**: CLAIMABLE MUST be reached when `block.timestamp >= pendingSince + gracePeriodSeconds` AND `(guardianQuorum == 0 || vetoCount < guardianQuorum)`. The `guardianQuorum == 0` disjunct is required to prevent deadlock — `uint8 vetoCount < uint8(0)` is always false, making the simpler `vetoCount < guardianQuorum` form permanently blocking when quorum is zero.
- **FR-014**: Guardian fast-track MUST move PENDING_RELEASE to CLAIMABLE immediately when `approvalCount >= guardianQuorum`.

**Guardians**:

- **FR-015**: `guardianVeto()` MUST be idempotent per caller: calling it twice MUST NOT increment `vetoCount` twice. Each guardian holds exactly one vote — their own — tracked by a per-address boolean flag. A guardian MUST NOT cast votes on behalf of, or overwrite the flag of, another guardian. For cross-switch auto-rescind behavior (when the caller holds an active approval flag), see FR-032.
- **FR-016**: `guardianRescindVeto()` MUST only rescind the **caller’s own** veto. If the caller has no active veto, the call MUST be a silent no-op (no error, no event, no `vetoCount` change) — regardless of whether other guardians have active vetoes. A guardian MUST NOT be able to rescind or otherwise alter another guardian’s veto flag.
- **FR-017**: Guardian operations MUST NOT be callable on RELEASED or REVOKED packages.
- **FR-018**: All guardian operations MUST verify `msg.sender` is in the `guardians[]` list for the package.

**Upgradeability**:

- **FR-019**: The implementation MUST use OZ `initialize()` / `reinitializer(n)` patterns; no standard constructor state initialization.
- **FR-020**: New storage fields MUST only be appended at the end of the layout; the implementation MUST include a storage gap.
- **FR-021**: The proxy address MUST remain stable across all upgrades.

**Security**:

- **FR-022**: No plaintext secrets, raw DEKs, emails, credentials, or PII MUST ever appear in any on-chain field or event; only public pointers/addresses/thresholds are permitted. Any secret-like field or event payload is a defect.
- **FR-023**: All ETH-transferring functions MUST follow CEI pattern and be protected by reentrancy guard.
- **FR-024**: All restricted functions MUST revert with a named custom error identifying the violated condition.

**Escape hatch**:

- **FR-025**: The contract MUST provide an upgrade-authority-only `rescue()` escape hatch that clears `pendingSince` on a PENDING_RELEASE package before grace elapses, satisfying constitution Principle VII. Reverts: `PackageNotFound`, `PackageAlreadyReleased`, `PackageRevoked`, `NotAuthorized`, `NotPending`. Emits: `PackageRescued`.
- **FR-026**: `checkIn()` MUST revert with `AlreadyClaimable()` when `getPackageStatus(packageKey)` returns CLAIMABLE. Valid `checkIn()` states: ACTIVE, WARNING, PENDING_RELEASE (grace not yet elapsed). Invalid states: CLAIMABLE (→ `AlreadyClaimable`), RELEASED (→ `PackageAlreadyReleased`), REVOKED (→ `PackageRevoked`).
- **FR-027**: When `pricePerSecond == 0`, the funding lapse trigger MUST be permanently disabled in status computation: `bool fundingLapsed = (pricePerSecond != 0) && (block.timestamp >= paidUntil)`. A zero-pricePerSecond package can only enter PENDING_RELEASE via inactivity (`block.timestamp >= lastCheckIn + inactivityThreshold`).
- **FR-028**: `activate()` MUST revert with `FundingDisabled()` if `msg.value > 0` and `pricePerSecond == 0`. When `msg.value == 0`, `activate()` proceeds with `paidUntil` set from the explicit parameter. This is symmetric with `renew()` (FR-004) and ensures no ETH can be trapped in a funding-disabled deployment.
- **FR-029**: Every state-mutating function MUST invoke `_materializePendingSince()` before any other logic. `_materializePendingSince()` MUST: (a) detect first-entry into PENDING_RELEASE when `pendingSince == 0` and at least one enabled trigger has crossed; (b) write `pendingSince` using the conditioned formula (FR-012); (c) emit `PendingRelease(packageKey, pendingSince, reasonFlags)` in the same transaction. This applies even when the calling function will immediately cancel the pending state (e.g., `checkIn()` or `renew()`) — the emit-then-cancel pattern preserves a complete on-chain audit trail. **Exception**: `renew()` MUST call `_materializePendingSince()` AFTER extending `paidUntil`, to avoid falsely detecting a funding lapse that `renew()` is immediately curing.
- **FR-030**: `guardianRescindApprove()` MUST only rescind the **caller's own** approval. If the caller has no active approval, the call MUST be a silent no-op (no error, no event, no `approvalCount` change) — regardless of other guardians' approval states. Symmetric to `guardianRescindVeto()` (FR-016). Emits `GuardianApproveRescinded(packageKey, msg.sender, approvalCount)` only when a flag was actually cleared.
- **FR-031**: `guardianApprove()` MUST auto-rescind the caller's own active veto before recording the approval. If the caller has `vetoFlags[msg.sender] == true`: decrement `vetoCount`, clear flag, emit `GuardianVetoRescinded(packageKey, msg.sender, vetoCount)` — then proceed to record the approval. This ensures the caller's veto and approve flags are mutually exclusive. Two sequential events emitted in the same tx on cross-switch (rescind old → record new).
- **FR-032**: `guardianVeto()` MUST auto-rescind the caller's own active approval before recording the veto. If the caller has `approvalFlags[msg.sender] == true`: decrement `approvalCount`, clear flag, emit `GuardianApproveRescinded(packageKey, msg.sender, approvalCount)` — then proceed to record the veto. Same mutual-exclusivity guarantee as FR-031. Two sequential events emitted in the same tx on cross-switch.
- **FR-033**: When `checkIn()` successfully clears `pendingSince` (PENDING_RELEASE → ACTIVE recovery), it MUST bulk-reset all guardian state: iterate `p.guardians[]`, clear all `vetoFlags` and `approvalFlags` entries, set `p.vetoCount = 0`, `p.approvalCount = 0`; emit `GuardianStateReset(packageKey)`. The reset MUST fire ONLY when the package genuinely leaves PENDING_RELEASE. **Implementation guard**: after writing `p.lastCheckIn = uint64(block.timestamp)`, re-evaluate `_computeStatus(p, block.timestamp, pricePerSecond)`; proceed with `pendingSince = 0`, guardian reset, and `GuardianStateReset` emit only when the result is ACTIVE or WARNING. If the computed status is still PENDING_RELEASE (e.g., a funding lapse persists despite the inactivity trigger being cleared), `pendingSince` MUST NOT be zeroed and `GuardianStateReset` MUST NOT be emitted. **Rationale (Key Invariant 7)**: zeroing `pendingSince` while any trigger remains active violates the invariant that `pendingSince` is NEVER updated while the package stays in PENDING_RELEASE. Specifically: if the original `pendingSince` was `inactivityTs` (inactivity crossed first, `inactivityTs < paidUntil`), zeroing it would allow the next `_materializePendingSince()` call to re-set `pendingSince = paidUntil`, shifting the grace window start forward by `paidUntil − inactivityTs` and extending the time until CLAIMABLE. An owner could exploit this to indefinitely delay the beneficiary by repeatedly checking in just before grace elapses. This reset MUST NOT fire on routine `checkIn()` calls from ACTIVE or WARNING state (where `pendingSince == 0` before the call).
- **FR-034**: `renew()` recovery/reset MUST be trigger-accurate. Pre-extension, evaluate triggers with `_computeStatus()`-aligned inclusive boundaries: `preInactivityPending = (now >= lastCheckIn + inactivityThreshold)`, `preFundingLapsed = (pricePerSecond != 0 && now >= paidUntil)`. Funding-only recovery is eligible only when `preStatus == PENDING_RELEASE && preFundingLapsed && !preInactivityPending`. After extending `paidUntil`, recompute status; clear `pendingSince` and bulk-reset guardian state only if pending is actually resolved (`postStatus == ACTIVE || postStatus == WARNING`). On this recovery+reset branch, require `msg.sender == owner` or revert `NotOwner()`. `renew()` remains permissionless for pure funding extension outside this branch.

### Key Entities

- **Package**: The central entity. Identified by `packageKey` (`bytes32`). Holds all lifecycle state, thresholds, actors (owner, beneficiary, guardians), and pointers (manifestUri). Lives in the proxy's storage.
- **PackageStatus (enum)**: `DRAFT | ACTIVE | WARNING | PENDING_RELEASE | CLAIMABLE | RELEASED | REVOKED`. `DRAFT = 0` is the Solidity zero-value default for all unmapped keys; it is never explicitly written to storage. Note: VETOED is not a separate storage state; veto-blocking is computed from `vetoCount >= quorum` within the PENDING_RELEASE/CLAIMABLE evaluation.
- **Manifest (off-chain)**: JSON document referenced by `manifestUri`. Contains `ciphertextUri`, `encDEK`, `accessControl` (Lit ACC bound to `policyProxy.isReleased(packageKey) == true`), `requester` (beneficiary address), and `manifestIntegrity.hash`. Treat as public.
- **PolicyProxy**: The `TransparentUpgradeableProxy` with stable address. All on-chain integrations and Lit ACC reference this address.
- **PolicyImpl**: The upgradeable implementation contract. Replaced on upgrade; the proxy address does not change.
- **ProxyAdmin**: OZ upgrade controller. In production, MUST be owned by a multisig or timelocked address.

---

## Domain Model: Data Classification

All on-chain data is public. The table reflects intent and off-chain classification:

| Field / Artifact | Location | Classification | Notes |
|---|---|---|---|
| `packageKey` | On-chain | Public | Derived off-chain; owner chooses |
| `owner` | On-chain | Public | |
| `beneficiary` | On-chain | Public | |
| `guardians[]` | On-chain | Public | |
| `guardianQuorum` | On-chain | Public | |
| `vetoCount` / `vetoFlags` | On-chain | Public | Implemented as `mapping(address => bool)` — per-address flag, not a packed bitfield |
| `approvalCount` / `approvalFlags` | On-chain | Public | Implemented as `mapping(address => bool)` — per-address flag, not a packed bitfield |
| `status` (enum) | On-chain | Public | Lazy-computed from timestamps |
| `manifestUri` | On-chain | Public | Pointer only; content is off-chain |
| `lastCheckIn` | On-chain | Public | |
| `paidUntil` | On-chain | Public | |
| `pendingSince` | On-chain | Public | Set on first write to earliest crossing timestamp among **enabled and crossed** triggers (conditioned formula, FR-012); never `block.timestamp`; `paidUntil` is excluded when `pricePerSecond == 0` |
| `gracePeriodSeconds` | On-chain | Public | |
| `warnThreshold` | On-chain | Public | |
| `inactivityThreshold` | On-chain | Public | |
| `releasedAt` | On-chain | Public | **Must be recorded** |
| All events | On-chain | Public | |
| `ciphertextUri` | Off-chain (manifest) | Public | Pointer to encrypted blob |
| `ciphertext` bytes | Off-chain (storage) | Public | Encrypted; safe to expose |
| `encDEK` | Off-chain (manifest) | Public | Lit-encrypted; useless without ACC |
| DEK (raw) | Never on-chain | **Private** | Revealed only by Lit under ACC |
| Plaintext assets | Never on-chain | **Private** | Client-side only |
| Wallet/guardian private keys | Never on-chain | **Private** | |
| Upgrade authority private keys | Never on-chain | **Private** | |
| Backend user accounts / emails | Never on-chain | **Private, non-critical** | |

---

## State Machine

### States

| State | Meaning |
|---|---|
| `DRAFT` | Non-existent / never activated. `PackageStatus.DRAFT = 0` is the Solidity zero-value default for all unmapped keys — no storage write ever sets a key to DRAFT. `getPackageStatus()` returns DRAFT for unknown keys; all mutating functions revert with `PackageNotFound()`. |
| `ACTIVE` | Owner alive; inactivity and funding clocks healthy |
| `WARNING` | Inactivity clock past `warnThreshold`; release approaching |
| `PENDING_RELEASE` | Inactivity past `inactivityThreshold` OR funding lapsed; grace countdown started |
| `CLAIMABLE` | Grace elapsed AND `vetoCount < quorum`; OR guardian fast-track quorum met |
| `RELEASED` | `claim()` executed; `releasedAt` recorded; DEK accessible via Lit |
| `REVOKED` | Owner permanently cancelled the package |

Note: VETOED is not a separate stored state. When in PENDING_RELEASE, if `vetoCount >= quorum` the computed status is blocked-pending; `claim()` will revert. This avoids an extra state slot while preserving transition semantics.

### Transitions

Note: The `DRAFT` state is the Solidity zero-value default (`PackageStatus = 0`). No `register()` transaction is needed; all unmapped keys implicitly start in DRAFT. `activate()` is the sole entry point that commits a key to storage.

| From | To | Trigger | Guard | Side Effects |
|---|---|---|---|---|
| `DRAFT` (zero default) | `ACTIVE` | `activate()` | `owner == address(0)` (key not yet used); `manifestUri` ≠ "", `beneficiary` ≠ 0, thresholds valid, `paidUntil > now` | `lastCheckIn = now`; all `PackageData` fields written; `PackageActivated(packageKey, manifestUri)` |
| `ACTIVE` | `ACTIVE` | `checkIn()` | `msg.sender == owner`; not RELEASED/REVOKED | `lastCheckIn = now`; `CheckIn(packageKey, now)` |
| `ACTIVE` | `ACTIVE` | `renew()` | `msg.value > 0`; not RELEASED/REVOKED | `paidUntil` extended; `Renewed(packageKey, paidUntil)` |
| `ACTIVE` | `WARNING` | lazy read | `now >= lastCheckIn + warnThreshold` | No storage write on read; stored on next write |
| `WARNING` | `ACTIVE` | `checkIn()` | same as above | same as above |
| `WARNING` | `ACTIVE` | `renew()` | `msg.value > 0` | `paidUntil` extended |
| `WARNING` | `PENDING_RELEASE` | lazy read | `now >= lastCheckIn + inactivityThreshold` | `pendingSince` = conditioned formula (FR-012): `inactivityTs = lastCheckIn + inactivityThreshold`; if funding also crossed and enabled, `min(inactivityTs, paidUntil)` else `inactivityTs`. NEVER `block.timestamp`; `PendingRelease(packageKey, pendingSince, reasonFlags)` |
| `ACTIVE` | `PENDING_RELEASE` | lazy read | `pricePerSecond != 0` AND `now >= paidUntil` (funding lapse; inactivity clock healthy) | `pendingSince = paidUntil` (conditioned formula: funding-only trigger, inactivity not crossed, so result is `fundingTs`); `PendingRelease(packageKey, pendingSince, reasonFlags)` |
| `PENDING_RELEASE` | `ACTIVE` | `checkIn()` | owner; grace not elapsed (or guardian fast-track not triggered) | If `pendingSince == 0` (first write after lazy crossing): materialize `pendingSince`, emit `PendingRelease` — THEN clear `pendingSince = 0`; `lastCheckIn = now`. Both events in same tx (Option A, FR-029). |
| `PENDING_RELEASE` | `ACTIVE` | `renew()` | pre: `preStatus == PENDING_RELEASE && preFundingLapsed && !preInactivityPending`; post: `postStatus` is ACTIVE/WARNING; caller is owner | Extend `paidUntil`; on owner recovery+reset branch, clear `pendingSince = 0`, bulk-reset guardian flags/counters, emit `GuardianStateReset`, then `Renewed`. Because `renew()` materializes pending after extension (FR-029 exception), immediate recoveries typically emit no `PendingRelease` in that same tx. Non-owner caller in this recovery+reset branch reverts `NotOwner()`. |
| `PENDING_RELEASE` | `CLAIMABLE` (fast-track) | `guardianApprove()` | `approvalCount >= quorum` after call | `GuardianApproved` events; `CLAIMABLE` computable |
| `PENDING_RELEASE` | `CLAIMABLE` (grace) | lazy read | `now >= pendingSince + gracePeriodSeconds` AND `(quorum == 0 OR vetoCount < quorum)` | No storage write until `claim()` |
| `CLAIMABLE` | `RELEASED` | `claim()` | `msg.sender == beneficiary`; not vetoed | `releasedAt = now`; `Released(packageKey, releasedAt, manifestUri)` |
| any (pre-RELEASED) | `REVOKED` | `revoke()` | `msg.sender == owner`; not RELEASED | `Revoked(packageKey)` |

---

## Contract Surface (English Interface)

All functions operate on the proxy address. The interface below is implementation-grade.

### State-Mutating Functions

**`activate(bytes32 packageKey, string calldata manifestUri, address beneficiary, address[] calldata guardians, uint8 guardianQuorum, uint64 warnThreshold, uint64 inactivityThreshold, uint32 gracePeriodSeconds, uint64 paidUntil) external payable`**
- Caller becomes `owner`.
- Initial ETH deposit extends `paidUntil` if `msg.value > 0` and `pricePerSecond != 0`. MUST revert with `FundingDisabled()` if `msg.value > 0` and `pricePerSecond == 0` — symmetric with `renew()` (FR-028); no ETH may be trapped in a funding-disabled deployment.
- Reverts: `AlreadyActive`, `ManifestUriRequired`, `BeneficiaryRequired`, `FundingRequired`, `InvalidThresholds` (covers `gracePeriodSeconds == 0`, `warnThreshold == 0`, `inactivityThreshold <= warnThreshold`), `QuorumExceedsGuardians`, `FundingDisabled` (when `msg.value > 0` and `pricePerSecond == 0`), `InsufficientPayment` (when `msg.value > 0`, `pricePerSecond != 0`, and computed extension truncates to 0 seconds).
- Emits: `PackageActivated(packageKey, manifestUri)`.

**`checkIn(bytes32 packageKey) external`**
- Resets `lastCheckIn`. After updating `lastCheckIn`, re-evaluates `_computeStatus()`: if the result is ACTIVE or WARNING (all pending triggers cleared), clears `pendingSince`, bulk-resets all guardian flags (`vetoFlags`, `approvalFlags`, `vetoCount`, `approvalCount` → 0), and emits `GuardianStateReset(packageKey)` (FR-033). If the result is still PENDING_RELEASE (a funding lapse remains active), `pendingSince` is NOT cleared and guardian state is NOT reset. **Invariant preservation**: zeroing `pendingSince` while any trigger remains active would allow `_materializePendingSince()` to re-set it to a later crossing timestamp (e.g., `paidUntil` when inactivity was originally first), shifting the grace window start forward and extending the time until CLAIMABLE — breaking Key Invariant 7 and enabling an owner to delay the beneficiary indefinitely. Guardian reset fires ONLY when the package genuinely transitions out of PENDING_RELEASE, not on routine check-ins from ACTIVE or WARNING.
- MUST revert with `AlreadyClaimable()` if `getPackageStatus()` returns CLAIMABLE.
- Reverts: `NotOwner`, `PackageAlreadyReleased`, `PackageRevoked`, `PackageNotFound`, `AlreadyClaimable`.
- Emits: `[GuardianStateReset(packageKey)]` (only on PENDING → ACTIVE/WARNING recovery) then `CheckIn(packageKey, lastCheckIn)`.

**`renew(bytes32 packageKey) external payable`**
- Extends `paidUntil` by time proportional to `msg.value` and a stored rate (or flat extension; rate is a deployment parameter).
- Owner-initiated recovery/reset runs only when pre-extension pending was funding-only (`preFundingLapsed && !preInactivityPending`) and post-extension status exits pending (`ACTIVE`/`WARNING`). At exact inactivity boundary (`now == lastCheckIn + inactivityThreshold`), inactivity is already pending, so recover-reset MUST NOT run.
- Reverts: `PackageAlreadyReleased`, `PackageRevoked`, `PackageNotFound`, `ZeroValue`, `FundingDisabled` (when `pricePerSecond == 0`), `InsufficientPayment` (when `msg.value > 0`, `pricePerSecond != 0`, and `uint64(msg.value / pricePerSecond) == 0`), `NotOwner` (only on the owner-only recovery branch).
- Emits: `Renewed(packageKey, paidUntil)`.

**`updateManifestUri(bytes32 packageKey, string calldata newUri) external`**
- Updates `manifestUri`. Callable by owner before RELEASED.
- Reverts: `NotOwner`, `PackageAlreadyReleased`, `PackageRevoked`, `PackageNotFound`, `ManifestUriRequired`.
- Emits: `ManifestUpdated(packageKey, newUri)`.

**`guardianApprove(bytes32 packageKey) external`**
- Records caller's approval flag (idempotent). If caller has an active veto, auto-rescinds it first (cross-switch, FR-031): emits `GuardianVetoRescinded` then `GuardianApproved` in same tx. If `approvalCount >= quorum`, CLAIMABLE fast-track is now computable.
- Reverts: `NotGuardian`, `NotPending`, `PackageAlreadyReleased`, `PackageRevoked`, `GuardiansNotConfigured`, `PackageNotFound`.
- Emits: `[GuardianVetoRescinded(packageKey, guardian, vetoCount)]` (only on cross-switch) then `GuardianApproved(packageKey, guardian, approvalCount)`.

**`guardianVeto(bytes32 packageKey) external`**
- Sets caller's veto flag (idempotent). If caller has an active approval, auto-rescinds it first (cross-switch, FR-032): emits `GuardianApproveRescinded` then `GuardianVetoed` in same tx. Increments `vetoCount` only if veto flag was not already set.
- Reverts: `NotGuardian`, `NotPending`, `PackageAlreadyReleased`, `PackageRevoked`, `GuardiansNotConfigured`, `PackageNotFound`.
- Emits: `[GuardianApproveRescinded(packageKey, guardian, approvalCount)]` (only on cross-switch) then `GuardianVetoed(packageKey, guardian, vetoCount)`.

**`guardianRescindVeto(bytes32 packageKey) external`**
- Clears caller's veto flag (idempotent). Decrements `vetoCount` only if flag was set.
- Reverts: `NotGuardian`, `PackageAlreadyReleased`, `PackageRevoked`, `GuardiansNotConfigured`, `PackageNotFound`.
- Emits: `GuardianVetoRescinded(packageKey, guardian, vetoCount)`.

**`guardianRescindApprove(bytes32 packageKey) external`**
- Clears caller's approval flag (idempotent). Decrements `approvalCount` only if flag was set. Silent no-op if no active approval (no error, no event). Symmetric to `guardianRescindVeto()` (FR-030).
- Reverts: `NotGuardian`, `PackageAlreadyReleased`, `PackageRevoked`, `GuardiansNotConfigured`, `PackageNotFound`.
- Emits: `GuardianApproveRescinded(packageKey, guardian, approvalCount)` (only when flag was cleared).

**`claim(bytes32 packageKey) external`**
- Requires package in CLAIMABLE state (`getPackageStatus()` must return CLAIMABLE).
- Sets `releasedAt = block.timestamp`.
- Reverts: `NotBeneficiary`, `NotClaimable`, `GracePeriodNotElapsed`, `ReleasedVetoed`, `PackageAlreadyReleased`, `PackageRevoked`, `PackageNotFound`.
- Emits: `Released(packageKey, releasedAt, manifestUri)`.

**`revoke(bytes32 packageKey) external`**
- Permanently cancels a package. Only callable before RELEASED.
- Reverts: `NotOwner`, `PackageAlreadyReleased`, `AlreadyRevoked`, `PackageNotFound`.
- Emits: `Revoked(packageKey)`.

**`rescue(bytes32 packageKey) external`**
- Emergency escape hatch callable **only by the upgrade authority** (the address that owns `ProxyAdmin`, configured at `initialize()` time).
- If the package is in PENDING_RELEASE and grace has **not yet elapsed** (i.e., not yet CLAIMABLE), clears `pendingSince` to allow the owner to resume normal check-in operations.
- Use case: operator confirms the owner is alive but temporarily unable to call `checkIn()` (key rotation in progress, multi-sig delay), or a misconfigured `paidUntil` caused a false funding lapse. Does **not** alter `lastCheckIn`, thresholds, owner, beneficiary, or any other field.
- Reverts: `PackageNotFound`, `PackageAlreadyReleased`, `PackageRevoked`, `NotAuthorized` (caller is not the stored upgrade authority), `NotPending` (package is not in PENDING_RELEASE, or grace has already elapsed and package is already CLAIMABLE).
- Emits: `PackageRescued(packageKey, msg.sender)`.

### Read-Only Functions

**`getPackageStatus(bytes32 packageKey) external view returns (PackageStatus)`**
- Returns lazily-computed status based on current `block.timestamp`. No state is written.

**`isReleased(bytes32 packageKey) external view returns (bool)`**
- Returns `releasedAt != 0`. This is the Lit ACC target function.

**`getPackage(bytes32 packageKey) external view returns (PackageView memory)`**
- Returns all stored fields as a `PackageView` struct for off-chain tooling.

**`getManifestUri(bytes32 packageKey) external view returns (string memory)`**
- Recovery helper: returns current `manifestUri`.

### Events

```
event PackageActivated(bytes32 indexed packageKey, string manifestUri);
event ManifestUpdated(bytes32 indexed packageKey, string manifestUri);
event CheckIn(bytes32 indexed packageKey, uint64 lastCheckIn);
event Renewed(bytes32 indexed packageKey, uint64 paidUntil);
event GuardianApproved(bytes32 indexed packageKey, address indexed guardian, uint8 approvalCount);
event GuardianVetoed(bytes32 indexed packageKey, address indexed guardian, uint8 vetoCount);
event GuardianVetoRescinded(bytes32 indexed packageKey, address indexed guardian, uint8 vetoCount);
event GuardianApproveRescinded(bytes32 indexed packageKey, address indexed guardian, uint8 approvalCount);
event GuardianStateReset(bytes32 indexed packageKey);
event PendingRelease(bytes32 indexed packageKey, uint64 pendingSince, uint8 reasonFlags);
event Released(bytes32 indexed packageKey, uint64 releasedAt, string manifestUri);
event Revoked(bytes32 indexed packageKey);
event PackageRescued(bytes32 indexed packageKey, address indexed rescuedBy);
```

`reasonFlags` in `PendingRelease`: bit 0 = inactivity, bit 1 = funding lapse.

### Custom Errors

```
error PackageNotFound();
error AlreadyActive();
error AlreadyRevoked();
error NotOwner();
error NotBeneficiary();
error NotGuardian();
error NotPending();
error NotClaimable();
error ManifestUriRequired();
error BeneficiaryRequired();
error FundingRequired();
error InvalidThresholds();
error QuorumExceedsGuardians();
error GuardiansNotConfigured();
error GracePeriodNotElapsed();
error ReleasedVetoed();
error PackageAlreadyReleased();
error PackageRevoked();
error ZeroValue();
error NotAuthorized();
error AlreadyClaimable();
error FundingDisabled();
error InsufficientPayment();
```

---

## Storage Layout Plan

All storage lives in the proxy (delegatecall). Fields MUST NOT be reordered across upgrades; new
fields MUST be appended at the end of `PackageData` and before `__gap`.

Every on-chain storage field MUST carry an inline data-classification comment (`// PUBLIC` or `// POINTER — treated as public`) per Constitution Principle I. Events only expose public data.

```
struct PackageData {
    // slot 0
    address owner;          // 20 bytes
    uint64  lastCheckIn;    //  8 bytes (packed)
    // slot 1
    address beneficiary;    // 20 bytes
    uint64  paidUntil;      //  8 bytes
    // slot 2
    uint64  pendingSince;        //  8 bytes
    uint64  releasedAt;          //  8 bytes
    uint64  warnThreshold;       //  8 bytes
    uint64  inactivityThreshold; //  8 bytes  (fills slot 2 exactly — 4 × 8 = 32 bytes)
    // slot 3
    uint32  gracePeriodSeconds;  //  4 bytes
    uint8   guardianQuorum;      //  1 byte
    uint8   vetoCount;           //  1 byte
   uint8   approvalCount;       //  1 byte
   uint8   storedStatus;        //  1 byte (RELEASED=5 and REVOKED=6 ONLY; DRAFT is zero-value default, never written; live states derived lazily)
    // slot 4+
    string  manifestUri;    // dynamic
    // dynamic arrays
    address[] guardians;
    // bitmaps (guardian index → bool) using nested mapping
    mapping(address => bool) vetoFlags;
    mapping(address => bool) approvalFlags;
}

mapping(bytes32 => PackageData) private _packages;

// Storage gap for future fields — bring total reserved slots to 50
uint256[N] private __gap;
```

Notes:
- `storedStatus` is written ONLY for the two terminal states: RELEASED=5 (written by `claim()`) and REVOKED=6 (written by `revoke()`). DRAFT (= 0) is the Solidity zero-value default — it is **NEVER** written to `storedStatus`. The existence sentinel is `owner == address(0)`. All live states (ACTIVE, WARNING, PENDING_RELEASE, CLAIMABLE) are derived lazily from timestamps.
   Alternatively store a raw `PackageStatus` enum as uint8 to simplify logic — implementation choice.
- Guardian bitmaps use `mapping(address => bool)` per package, not a shared bitmap,
  to avoid index management complexity (no max guardian count constraint).
- `vetoCount` and `approvalCount` are cached counters kept consistent with the bitmaps.

---

## Security & Threat Model

### What the contract prevents

| Threat | Mitigation |
|---|---|
| Secret exposure on-chain | No plaintext/DEK fields exist; `manifestUri` is a pointer only |
| Non-owner check-in spoofing | All owner functions verify `msg.sender == package.owner` |
| Non-beneficiary premature claim | `claim()` checks `msg.sender == package.beneficiary` |
| Premature claim (grace not elapsed) | `claim()` checks `now >= pendingSince + gracePeriodSeconds` |
| Veto bypass | `claim()` checks `(guardianQuorum == 0 || vetoCount < guardianQuorum)` before proceeding — quorum==0 deployments must never deadlock |
| Re-entry on `claim()` (future ETH-distribution path) | CEI pattern + `nonReentrant` on `claim()`; `renew()` and `rescue()` send no ETH outbound |
| CEI / reentrancy on `renew()` | `renew()` follows CEI with no external calls; add `nonReentrant` if outbound transfers are introduced in a future version |
| Storage layout collision on upgrade | CI storage layout diff gate (zero-conflict required) |
| Initializer re-run attack | OZ `Initializable` with `initializer`/`reinitializer(n)` guards |
| Irrecoverable state trap | `revoke()` provides escape; veto is rescindable |

### What is out of scope (MVP)

- Off-chain Lit Protocol failure or censorship.
- Beneficiary private key loss (no key recovery mechanism on-chain).
- Multi-chain consistency (each deployment is independent).
- ZK / Privado ID identity verification for guardians.
- Complex payment models (ETH top-up only; rate model is a deployment parameter).

### Key Invariants

1. Once `releasedAt != 0`, no state-mutating function except view reads is valid.
2. `vetoCount` exactly matches the count of addresses with `vetoFlags[addr] == true` for any package. No address can simultaneously hold both `vetoFlags[addr] == true` and `approvalFlags[addr] == true` (cross-switch mutual exclusivity per caller, FR-031/FR-032).
3. `approvalCount` exactly matches the count of addresses with `approvalFlags[addr] == true`. No address can simultaneously hold both `approvalFlags[addr] == true` and `vetoFlags[addr] == true` (same mutual-exclusivity constraint, symmetric).
4. `isReleased(packageKey) == true` is permanent and monotone (never reverts to false).
5. No on-chain field or event ever contains plaintext secrets, raw DEKs, or PII.
6. The proxy address never changes across upgrades.
7. `pendingSince` is always the earliest crossing timestamp among **enabled and crossed** triggers (conditioned formula, FR-012). Enabled triggers: inactivity always; funding only when `pricePerSecond != 0`. `pendingSince` is NEVER set to `block.timestamp` of the materializing transaction, NEVER includes `paidUntil` when funding is disabled (`pricePerSecond == 0`), NEVER updated while the package stays in PENDING_RELEASE, and NEVER zeroed while any active trigger keeps the package in PENDING_RELEASE. Violation of the last constraint would allow `_materializePendingSince()` to re-set `pendingSince` to a later crossing timestamp, shifting the grace window start forward and enabling an owner to extend the time until CLAIMABLE indefinitely (see FR-033, Session 2026-02-23).
8. `rescue()` MUST only succeed when `getPackageStatus(packageKey) == PENDING_RELEASE` (grace not yet elapsed). CLAIMABLE, RELEASED, or REVOKED packages MUST NOT be rescuable. `rescue()` does not alter `lastCheckIn`, owner, beneficiary, thresholds, or any field other than `pendingSince`.
9. When `guardianQuorum == 0`, grace elapsed (`now >= pendingSince + gracePeriodSeconds`) is the sole requirement for CLAIMABLE. `guardianVeto()` itself reverts with `GuardiansNotConfigured()` — no veto can ever block release. The guard `(guardianQuorum == 0 || vetoCount < guardianQuorum)` must never deadlock a no-guardian deployment.

---

## Testing Plan (Hardhat 3 + TypeScript + viem + Node Test Runner)

### Test file structure

```
test/
├── unit/
│   ├── policy.activate.test.ts
│   ├── policy.checkin.test.ts
│   ├── policy.renew.test.ts
│   ├── policy.guardian.test.ts
│   ├── policy.claim.test.ts
│   ├── policy.revoke.test.ts
│   ├── policy.rescue.test.ts
│   └── policy.stateMachine.test.ts
├── integration/
│   ├── policy.inactivityFlow.test.ts
│   ├── policy.fundingLapseFlow.test.ts
│   ├── policy.guardianVetoFlow.test.ts
│   ├── policy.fastTrackFlow.test.ts
│   └── policy.recoveryFlow.test.ts   ← end-to-end no-backend test
└── upgrade/
    ├── policy.upgrade.v1v2.test.ts
    └── policy.storageLayout.test.ts
```

### T1 — State Machine Transitions

Goal: every valid AND invalid transition is covered; 100% branch coverage on state logic.

- Activate: valid call, each invalid precondition — `manifestUri` empty, `beneficiary` zero, `warnThreshold == 0`, `inactivityThreshold <= warnThreshold`, `gracePeriodSeconds == 0`, `paidUntil <= now`, `quorumExceedsGuardians` (7 separate tests).
- ACTIVE → WARNING: boundary at `warnThreshold`; one second before (stays ACTIVE); one second after (WARNING).
- WARNING → ACTIVE via `checkIn()`.
- WARNING → PENDING_RELEASE: boundary at `inactivityThreshold`.
- ACTIVE → PENDING_RELEASE: funding lapse boundary at `paidUntil`.
- PENDING_RELEASE → ACTIVE via `checkIn()`.
- PENDING_RELEASE → ACTIVE via `renew()` only when pre-trigger checks prove funding-only pending and post-extension status exits pending (owner only, FR-034).
- PENDING_RELEASE → CLAIMABLE: grace boundary (one second before: not CLAIMABLE; one second after: CLAIMABLE).
- CLAIMABLE → RELEASED via `claim()`.
- Any state → REVOKED via `revoke()`.
- RELEASED: all mutating functions revert.
- REVOKED: all mutating functions revert.
- CLAIMABLE: `checkIn()` reverts with `AlreadyClaimable()` (grace cannot be cancelled once claimability threshold is crossed).
- `guardianQuorum == 0` with grace elapsed → CLAIMABLE without guardian interaction; `claim()` succeeds.
- `pricePerSecond == 0`: `renew()` reverts `FundingDisabled()`; advancing past `paidUntil` does NOT enter PENDING_RELEASE; only inactivity applies.
- `pricePerSecond == 0` + `activate()` with `msg.value > 0` → reverts `FundingDisabled()`; assert no ETH deposited to contract.
- `pricePerSecond == 0` + `activate()` with `msg.value == 0` and valid explicit `paidUntil` → succeeds; package moves to ACTIVE.

### T2 — Guardian Quorum, Veto, Rescind

- Veto by one of two guardians: `vetoCount == 1`; still pending.
- Veto by both: `vetoCount == 2 == quorum`; claim reverts after grace.
- Rescind one: `vetoCount == 1`; claim still blocked.
- Rescind both: `vetoCount == 0`; claim succeeds after grace.
- Idempotent veto: two calls from same guardian → `vetoCount` incremented once.
- Idempotent rescind: rescind with no active veto → no change, no revert.
- Non-guardian veto → `NotGuardian()`.
- Fast-track: `approvalCount >= quorum` → immediate CLAIMABLE before grace.
- Approval idempotent: two calls from same guardian → `approvalCount` once.
- Veto does not block revoke.
- Guardian operations on RELEASED package → `PackageAlreadyReleased()`.
- `guardianRescindApprove()` idempotent: no active approval → silent no-op (no error, no event, no `approvalCount` change).
- `guardianRescindApprove()` with active approval → `approvalCount` decremented by 1, `GuardianApproveRescinded` emitted with correct count.
- Cross-switch veto→approve: guardian with active veto calls `guardianApprove()` → tx log contains `GuardianVetoRescinded` then `GuardianApproved` in that order; `vetoCount == 0`, `approvalCount == 1`.
- Cross-switch approve→veto: guardian with active approval calls `guardianVeto()` → tx log contains `GuardianApproveRescinded` then `GuardianVetoed` in that order; `approvalCount == 0`, `vetoCount == 1`.
- `checkIn()` PENDING→ACTIVE recovery (inactivity-only trigger, funding healthy) with non-zero guardian state: `vetoCount == 0`, `approvalCount == 0`, all per-guardian flags cleared, `GuardianStateReset` emitted.
- `checkIn()` during PENDING_RELEASE when funding lapse persists: `pendingSince` unchanged, guardian flags NOT cleared, `GuardianStateReset` NOT emitted; only `CheckIn` emitted (FR-033).
- `checkIn()` from ACTIVE or WARNING state: guardian flags are NOT cleared, no `GuardianStateReset` emitted.

### T3 — Time-Based Logic (Inactivity / Funding / Grace)

All tests use `networkHelpers.time.setNextBlockTimestamp(ts)` (Hardhat 3 API, not raw `evm_*` JSON-RPC) for determinism:

- `now == lastCheckIn + warnThreshold - 1` → ACTIVE.
- `now == lastCheckIn + warnThreshold` → WARNING.
- `now == lastCheckIn + inactivityThreshold - 1` → WARNING (not PENDING_RELEASE).
- `now == lastCheckIn + inactivityThreshold` → PENDING_RELEASE; `pendingSince` set to `lastCheckIn + inactivityThreshold` (the threshold-crossing timestamp, not `block.timestamp`).
- `now == paidUntil` → PENDING_RELEASE (funding lapse).
- `now == pendingSince + gracePeriodSeconds - 1` → PENDING_RELEASE (not yet CLAIMABLE).
- `now == pendingSince + gracePeriodSeconds` → CLAIMABLE.
- `pendingSince` must remain unchanged if package stays in PENDING_RELEASE across multiple reads.
- Concurrent trigger (both inactivity and funding lapse simultaneously): PENDING_RELEASE entered, both bits set in `reasonFlags` of `PendingRelease` event.
- `checkIn()` during PENDING_RELEASE before grace: clears `pendingSince`; subsequent read is ACTIVE.
- `renew()` during PENDING_RELEASE (funding-lapse-only by explicit pre-trigger check, owner only, FR-034): clears `pendingSince`; subsequent read is ACTIVE.
- Exact inactivity boundary (`now == lastCheckIn + inactivityThreshold`) with funding also lapsed: renew extends `paidUntil` but MUST NOT clear/reset; status remains PENDING_RELEASE and guardian state remains unchanged.
- `checkIn()` when CLAIMABLE (grace elapsed, no blocking veto) → reverts `AlreadyClaimable()`.
- `pricePerSecond == 0`: `renew()` \u2192 `FundingDisabled()`; advancing past `paidUntil` does NOT enter PENDING_RELEASE (inactivity path is the only trigger); specifically assert `getPackageStatus()` returns ACTIVE (not PENDING_RELEASE) when only `now >= paidUntil` and `pricePerSecond == 0`.
- **Conditioned `pendingSince` formula**: Activate with `pricePerSecond == 0`; set `paidUntil` in the past. Advance time past `inactivityThreshold` only. Call any write function to materialize `pendingSince`. Assert `pendingSince == lastCheckIn + inactivityThreshold` (NOT `paidUntil`) — verifies that a disabled funding trigger is excluded from the minimum.
- `guardianQuorum == 0`, grace elapsed → `getPackageStatus()` returns CLAIMABLE; `claim()` succeeds.
- **Dual-event on first-write cancellation (Option A)**: Activate package, advance time past `inactivityThreshold` (grace not elapsed), call `checkIn()`. Assert transaction log contains BOTH `PendingRelease(packageKey, pendingSince, 0x01)` (emitted by `_materializePendingSince`) AND `CheckIn(packageKey, now)` — in that order. Assert `pendingSince == 0` after the call (cleared by `checkIn()`). For `renew()`: when extension is insufficient and package stays pending, assert `PendingRelease` then `Renewed`; when owner extension resolves funding-only pending, assert `GuardianStateReset` then `Renewed`. At exact inactivity boundary, assert renew does NOT emit `GuardianStateReset`. Repeat for `rescue()`: assert `PendingRelease` + `PackageRescued` in same tx.

### T4 — Upgrade Tests (v1 → v2)

- Deploy PolicyV1 behind a `TransparentUpgradeableProxy`.
- Activate a package; record all storage values.
- Deploy PolicyV2 (adds a new field `extraField` at end of `PackageData`).
- Use ProxyAdmin to upgrade.
- Assert all V1 fields are unchanged at same storage slots.
- Assert `extraField` reads as zero (default).
- Assert V2 `reinitializer(2)` ran: new field initialized correctly.
- Assert proxy address unchanged.
- Assert `isReleased()` still works.
- Run storage layout diff: assert no existing slot positions changed (CI gate).

### T5 — Event Assertions

Every event must have a dedicated assertion test using viem `getLogs` / `parseEventLogs`:

- `PackageActivated(packageKey, manifestUri)`: assert exact `packageKey` and `manifestUri`.
- `CheckIn(packageKey, lastCheckIn)`: assert `lastCheckIn == block.timestamp`.
- `Renewed(packageKey, paidUntil)`: assert extended `paidUntil` value.
- `GuardianVetoed(packageKey, guardian, vetoCount)`: assert all 3 values.
- `GuardianVetoRescinded(packageKey, guardian, vetoCount)`: assert decremented count.
- `GuardianApproved(packageKey, guardian, approvalCount)`: assert all 3 values.
- `GuardianApproveRescinded(packageKey, guardian, approvalCount)`: assert decremented count; assert directly after `guardianRescindApprove()` and as first event in cross-switch veto→approve tx.
- `GuardianStateReset(packageKey)`: assert emitted on PENDING_RELEASE → ACTIVE/WARNING recovery `checkIn()` call (inactivity-only trigger cleared, funding healthy); assert `vetoCount == 0` and `approvalCount == 0` post-tx; assert NOT emitted on routine `checkIn()` from ACTIVE or WARNING; assert NOT emitted when `checkIn()` is called during dual-trigger PENDING_RELEASE (inactivity cleared but funding lapse persists) — `pendingSince` must remain non-zero and guardian state must be unchanged.
- `PendingRelease(packageKey, pendingSince, reasonFlags)`: assert `reasonFlags` bits correct.
- `Released(packageKey, releasedAt, manifestUri)`: assert `releasedAt == block.timestamp` of claim tx; assert `manifestUri` is the current stored value.
- `Revoked(packageKey)`: assert emitted.
- `ManifestUpdated(packageKey, newUri)`: assert new URI in event and in `getManifestUri()`.
- `PackageRescued(packageKey, rescuedBy)`: assert `rescuedBy == upgradeAuthority`; assert `pendingSince == 0` post-call; assert no other storage fields altered.

### T6 — Access Control

For every restricted function, test with an unauthorized caller and assert the exact custom error:

- `activate()` called by non-owner → N/A (caller IS the new owner).
- `checkIn()` by non-owner → `NotOwner()`.
- `renew()` is permissionless (anyone can fund) → no access error on the funding extension path; on the owner-only recovery+reset branch (pre-trigger: funding-only pending; post-extension: exits pending), a non-owner caller reverts `NotOwner()` (FR-034).
- `updateManifestUri()` by non-owner → `NotOwner()`.
- `guardianVeto()` by non-guardian → `NotGuardian()`.
- `guardianRescindVeto()` by non-guardian → `NotGuardian()`.
- `guardianApprove()` by non-guardian → `NotGuardian()`.
- `guardianRescindApprove()` by non-guardian → `NotGuardian()`.
- `claim()` by non-beneficiary → `NotBeneficiary()`.
- `revoke()` by non-owner → `NotOwner()`.
- `rescue()` by non-upgrade-authority → `NotAuthorized()`.
- **Unknown key — mutating functions**: `checkIn()`, `renew()`, `updateManifestUri()`, `guardianVeto()`, `guardianRescindVeto()`, `guardianApprove()`, `guardianRescindApprove()`, `claim()`, `revoke()`, `rescue()` each called with a never-activated `packageKey` MUST revert `PackageNotFound()` (10 separate assertions).
- **Unknown key — view functions**: `getPackageStatus(unknownKey)` → returns `DRAFT`, does NOT revert; `isReleased(unknownKey)` → returns `false`, does NOT revert; `getPackage(unknownKey)` → returns zero-valued `PackageView` with `status == DRAFT`, does NOT revert; `getManifestUri(unknownKey)` → returns `""`, does NOT revert (4 separate assertions).

### T7 — Reentrancy

- Deploy `DoubleClaimAttacker.sol` as the package beneficiary; `simulateDoubleEntry()` calls `claim()` twice in the same transaction.
- Assert the second inner `claim()` reverts with `PackageAlreadyReleased()`; `releasedAt` is written exactly once.
- Assert `Released` event emitted exactly once via `getLogs`.
- Note: `claim()` sends no ETH in v1 — `receive()` never fires. The `nonReentrant` guard is forward-proofing for future v2+ ETH-distribution-on-claim scenarios.

### T8 — End-to-End Recovery Path (no external mocks)

Single test demonstrating complete no-backend recovery:

1. Deploy proxy + admin via Ignition module. *(In the test suite, the `deployPolicy()` fixture substitutes for the Ignition module for cross-phase decoupling — see T021. T047 performs the Ignition-based smoke validation.)*
2. Activate package with `warnThreshold = 30 days`, `inactivityThreshold = 60 days`, `gracePeriodSeconds = 7 days`.
3. Advance time past `inactivityThreshold + gracePeriodSeconds`.
4. Assert `getPackageStatus()` == CLAIMABLE.
5. Call `claim()` as beneficiary.
6. Assert `isReleased(packageKey)` == true.
7. Assert `releasedAt` != 0 and == block.timestamp of claim tx.
8. Assert `Released` event emitted with correct values.
9. Call `getManifestUri()` — assert returns the URI set at activation (or last update).

---

## Deployment Plan (Hardhat Ignition)

### Module: `PolicyModule`

**Step 1 — Deploy implementation**
```
const impl = m.contract("PolicyV1");
```

**Step 2 — Resolve init parameters and encode initializer call data**
```
const pricePerSecond  = m.getParameter("pricePerSecond");                         // required; no default
const upgradeAuthority = m.getParameter("upgradeAuthority", m.getAccount(0));     // defaults to deployer; override with multisig in prod
const initData = m.encodeFunctionCall(impl, "initialize", [pricePerSecond, upgradeAuthority]);
```

**Step 3 — Deploy TransparentUpgradeableProxy** *(OZ v5 — `ProxyAdmin` is auto-created inside the constructor)*
```
const proxy = m.contract("TransparentUpgradeableProxy", [
    impl,
    upgradeAuthority,   // becomes ProxyAdmin's initialOwner; ProxyAdmin auto-created by OZ v5
    initData,           // encodes initialize(pricePerSecond, upgradeAuthority)
]);
```
Both `pricePerSecond` and `upgradeAuthority` are Ignition parameters resolved at deploy time. `upgradeAuthority` defaults to deployer; override with multisig in production. Do **NOT** deploy `ProxyAdmin` separately — OZ v5 creates it automatically in the proxy constructor.

**Step 4 — Export deployment artifact**

The Ignition module MUST write (or the CI post-process script MUST extract) a JSON artifact:
```json
{
  "chainId": <number>,
  "deployedAt": "<ISO8601 UTC>",
  "gitCommit": "<40-char SHA>",
  "proxy": "<checksummed address>",
  "implementation": "<checksummed address>",
  "proxyAdmin": "<checksummed address>",
  "upgradeAuthority": "<checksummed address>"
}
```

Artifact is committed to `deployments/<chainId>/<gitCommit>.json`.

**Validation requirement**: `scripts/export-artifact.ts` MUST validate that `proxy`, `implementation`, and `proxyAdmin` are non-empty after resolving against `deployed_addresses.json`. If any required field is empty (no matching key found), the script MUST throw with a descriptive error naming the missing field and the candidate keys that were searched, and exit with code 1. `upgradeAuthority` is optional (sourced from `UPGRADE_AUTHORITY` env var) and may be empty. This prevents CI and deploy pipelines from silently committing unusable artifacts.

### Upgrade Module: `PolicyUpgradeModule`

```
const implV2 = m.contract("PolicyV2");
const proxy = m.contractAt("TransparentUpgradeableProxy", existingProxyAddress);
m.call(proxyAdmin, "upgradeAndCall", [proxy, implV2, reinitCallData]);
```

All upgrade modules MUST emit the updated artifact with the new `implementation` address.

**Validation requirement**: `PolicyUpgradeModule` MUST throw at module-build time if any of `existingProxyAddress`, `proxyAdminAddress`, or `reinitPackageKey` resolves to its zero value after both auto-discovery and explicit parameter override have been attempted. Zero-address targets are invalid upgrade recipients; zero-hash package keys cannot correspond to any activated package. The error MUST name the unresolved parameter and the discovery candidates searched. This prevents silent no-op upgrades or state corruption on live networks.

---

## Assumptions

1. **ETH renewal rate**: A flat rate (e.g., `1 ETH = 365 days`) or fixed per-second price is a
   deployment constructor parameter. The spec does not fix the value; tests use a configurable fixture.
2. **Guardian count limit**: No hard cap in MVP. A reasonable soft advisory (e.g., ≤ 10) may be
   documented but is not enforced on-chain.
3. **`packageKey` uniqueness**: Assumed to be globally unique per proxy deployment. Collision
   is the owner's responsibility (use standard derivation: `keccak256(abi.encodePacked(owner, nonce))`).
4. **Lazy vs stored status**: `getPackageStatus()` is a pure view function computing from timestamps.
   The only stored enum values are REVOKED (set by `revoke()`) and RELEASED (set by `claim()`). DRAFT
   (`PackageStatus = 0`) is the Solidity zero-value default — it is NEVER written to storage. All live
   states (ACTIVE, WARNING, PENDING_RELEASE, CLAIMABLE) are derived lazily from timestamps. This avoids
   state-write transactions for time-based transitions.
5. **ProxyAdmin ownership**: Defaults to deployer address in dev; in production a multisig address
   is passed as deployment parameter. This spec does not mandate a specific multisig product.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A beneficiary can complete the full recovery flow (find manifest → verify release → obtain DEK via Lit → decrypt) without any backend service, using only a chain RPC endpoint and the manifest URI.
- **SC-002**: All 8 mandatory test categories pass with ≥ 90% statement and branch coverage; state machine module achieves 100% branch coverage.
- **SC-003**: An upgrade from PolicyV1 to PolicyV2 completes without corrupting any pre-existing package state, verified by the T4 upgrade test suite.
- **SC-004**: An adversarial caller (non-beneficiary, non-owner, non-guardian) cannot trigger any state transition; every unauthorized call reverts with the correct named error, verified by the T6 access-control test suite.
- **SC-005**: A reentrancy attack on `claim()` is prevented; `releasedAt` is written exactly once per package, verified by the T7 reentrancy test suite.
- **SC-006**: Every deployment produces a versioned artifact with proxy address, implementation address, ProxyAdmin address, chainId, git commit SHA, and ISO 8601 timestamp.
- **SC-007**: The proxy address remains identical before and after any upgrade, verified by the T4 upgrade test and confirmed in deployment artifacts.
