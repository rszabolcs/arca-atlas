<!--
SYNC IMPACT REPORT
==================
Version change:  0.0.0 (uninitialized template) → 1.0.0
Bump rationale:  MINOR — initial population of all sections from template placeholders;
                 no prior governed version existed.

Principles added (new):
  I.   Zero-Secret Custody
  II.  Recovery Independence
  III. On-Chain Authoritativeness
  IV.  Non-Custodial Transactions
  V.   Policy-Bound Lit Integration
  VI.  Scale-First Design

Sections added:
  - Core Principles (6 principles derived from readme2.md non-negotiables)
  - Technology Stack & Architecture
  - Quality Bar & Testing Discipline
  - Governance

Sections removed:  n/a (first real version)

Templates reviewed:
  ✅ .specify/templates/plan-template.md   — Constitution Check gate is principle-agnostic;
                                             no structural change required.
  ✅ .specify/templates/spec-template.md   — No mandatory sections added by this constitution
                                             that require template amendment.
  ✅ .specify/templates/tasks-template.md  — Principle-driven task types (secret hygiene,
                                             non-custodial tx, reorg-safe indexing, ACC
                                             validation) are standard named phases; no
                                             structural change required.
  ✅ .github/prompts/*.prompt.md           — No agent-specific (CLAUDE-only) naming found;
                                             all references are generic.
  ✅ .github/agents/*.agent.md             — Same review; no outdated principle references.

Deferred TODOs:  none — all fields resolved from workspace context and current date.
-->

# Arca Java Backend Constitution

## Core Principles

### I. Zero-Secret Custody (NON-NEGOTIABLE)

The backend MUST NOT store, log, transit, or otherwise handle any secret material. This
prohibition is absolute and covers:

- Raw Data Encryption Keys (DEKs) or any plaintext asset content.
- User wallet private keys, seed phrases, or derived signing secrets.
- Guardian, beneficiary, or owner private keys.
- Lit Protocol session keys or session signatures.
- Application credentials (passwords, API secrets) beyond encrypted config.

Permitted storage: `chainId`, policy proxy address, `packageKey`, `manifestUri`,
`ciphertextUri`, content hashes (sha256), `encDEK` (Lit `encryptedSymmetricKey` — public
ciphertext, not a secret), on-chain status mirrors, timestamps, and event records.

Logging pipelines MUST sanitize output at the framework level; no field named `key`,
`secret`, `password`, `seed`, `dek`, or `private` may appear in any log line.

**Rationale:** A compromised backend must not compromise any user's assets. Secrets never
arriving at the server cannot be exfiltrated from it.

### II. Recovery Independence (NON-NEGOTIABLE)

The backend MUST NOT be a required component of the beneficiary recovery path. A
beneficiary MUST be able to complete recovery using only:

- An EVM-compatible RPC endpoint,
- The policy proxy address and `packageKey`,
- The manifest URI (retrievable from chain or IPFS),
- The ciphertext URI,
- The Lit Protocol network.

The backend MAY cache and index data to improve UX but MUST treat its own DB as a
convenience replica, not an authority. All indexed state MUST be fully replayable from
on-chain events. The service MUST expose and document a recovery path that does not
depend on backend availability.

**Rationale:** Company shutdown, infrastructure failure, or service discontinuation must
never strand beneficiaries. Resilience is a product promise, not an afterthought.

### III. On-Chain Authoritativeness (NON-NEGOTIABLE)

Authorization and canonical state MUST be derived from on-chain reads against the
**proxy address** (never the implementation address). The local DB is a read-cache only.

Specific rules:

- Permissions (owner, guardian membership, beneficiary) MUST be re-verified via live
  contract reads before any privileged action or write endpoint responds.
- The DB MUST NOT be the sole source of truth for any authorization decision.
- Contract calls MUST target the proxy address; direct implementation calls are
  forbidden.
- `getPackageStatus(packageKey)` MUST return `DRAFT` for unknown keys (contract
  behaviour the backend must honor in its own status mapping).

**Rationale:** An attacker who corrupts the cache must not gain elevated permissions.
The chain is the immutable ledger; the backend is a lens, not an oracle.

### IV. Non-Custodial Transactions (NON-NEGOTIABLE)

The default and required transaction mode is **client-signed**: all write endpoints MUST
return ABI-encoded unsigned calldata plus gas hints. The client wallet signs and
submits. The backend MUST NOT hold or use any user wallet.

Optional future relayed mode (external, non-custodial relayer) is permissible as an
additive feature and MUST NOT weaken client-signed defaults.

Covered operations: `activate`, `checkIn`, `renew`, `guardianApprove`,
`guardianVeto`, `guardianRescindVeto`, `claim`, `revoke`.

**Rationale:** Custody creates liability and is a single point of failure. Non-custodial
design is architecturally simpler and aligned with the sovereignty model of the product.

### V. Policy-Bound Lit Integration (NON-NEGOTIABLE)

All Lit Protocol Access Control Conditions (ACCs) generated or validated by the backend
MUST be bound to:

- `policyProxy.isReleased(packageKey) == true` evaluated on the canonical proxy address,
- requester identity = beneficiary address,
- correct `chainId` and `packageKey`.

The backend MUST reject (HTTP 400) any manifest whose embedded ACC references a different
proxy address, incorrect `chainId`, or missing `packageKey`. DEK unwrapping and asset
decryption MUST remain entirely client-side. The backend MAY run an optional internal
`lit-worker` sidecar for operational convenience, but recovery MUST NOT require it
(Principle II applies).

**Rationale:** An ACC that does not precisely bind to the on-chain release condition would
allow premature decryption. Precision here is a security invariant, not a preference.

### VI. Scale-First Design

The backend MUST be designed for operation at millions of packages from the outset.
Concretely:

- There MUST be no endpoint or code path that enumerates all packages on-chain.
- Package queries MUST be addressed by `(chainId, proxy, packageKey)` or paginated DB
  queries filtered by address (owner/guardian/beneficiary) or time range.
- The indexer MUST be reorg-safe:
  - maintain confirmation depth (configurable `N` blocks),
  - record `blockHash` per processed block,
  - detect and rewind on `blockHash` mismatch,
  - process events idempotently.
- Guardian set size MUST be enforced at ≤ 7 per package (mirrors contract constraint).

**Rationale:** Retrofitting scale into an existing service is expensive and risky. The
contract design already avoids on-chain enumeration; the backend must honor that discipline.

## Technology Stack & Architecture

**Runtime**: Java 21+, Spring Boot 3.x.

**Module boundaries** (each module is independently testable):

| Module          | Responsibility                                                            |
|-----------------|---------------------------------------------------------------------------|
| `api`           | REST controllers, DTOs, request validation, pagination                    |
| `auth`          | SIWE nonce issuance / verification, JWT / session management              |
| `evm`           | RPC client, ABI encoding/decoding, event ingestion, reorg handling        |
| `policy`        | Domain services: status mapping, role checks, tx calldata builders        |
| `lit`           | ACC template generation, manifest Lit-binding validation, client guidance |
| `storage`       | IPFS / S3 adapters, sha256 verification, optional manifest signature check|
| `persistence`   | PostgreSQL schema, Flyway / Liquibase migrations, repository layer        |
| `notifications` | Event-driven dispatcher (email / webhook / push), best-effort only       |

Cross-cutting concerns (logging hygiene, error handling, secret-field scrubbing) MUST
be enforced at the framework / middleware level, not per endpoint.

**Authentication**: SIWE — client signs a nonce-bound EIP-4361 message; backend issues a
short-lived JWT. Authorization is re-confirmed from chain on every privileged call.

**Persistence**: PostgreSQL is the canonical backing store for the event index / cache.
Schema MUST be managed via migration tooling (Flyway or Liquibase) and MUST be fully
replayable from chain events from genesis block.

**Deployment target**: docker-compose friendly for local development; container-ready for
cloud deployment.

## Quality Bar & Testing Discipline

Automated tests are REQUIRED for every module boundary and every security-relevant
decision path. The following test categories are MANDATORY before any feature ships:

1. **SIWE auth verification** — nonce issuance, signature verification, replay rejection.
2. **Role checks derived from chain** — owner / guardian / beneficiary resolution,
   cache-bypass validation.
3. **Tx payload correctness** — ABI encoding round-trips for all contract call types.
4. **Indexer reorg handling** — confirmation-depth gating, blockHash mismatch rewind,
   idempotent re-processing.
5. **ACC validation correctness** — proxy binding, chainId, packageKey, beneficiary
   constraint; rejection of malformed ACCs.

Logging output in tests MUST be asserted free of secret-pattern fields (Principle I).

Layer separation MUST be verifiable: `api` MUST NOT import `persistence` directly; all
data access goes through `policy` or dedicated service interfaces. Violations are treated
as constitution breaches in code review.

## Governance

This constitution supersedes all other written practices for the Arca Java Backend
repository. Conflicting conventions in READMEs, ADRs, or contribution guides MUST be
updated to align with this document.

**Amendment procedure**:

1. Propose changes in a dedicated PR with a summary of motivation.
2. Identify which principle(s) are affected and whether the change is MAJOR, MINOR, or
   PATCH per the versioning policy below.
3. Update `LAST_AMENDED_DATE` and `CONSTITUTION_VERSION` in the footer.
4. Append a new Sync Impact Report comment block at the top of this file.
5. Update any dependent templates (`.specify/templates/`) in the same PR if required.
6. PR MUST receive explicit approval before merge.

**Versioning policy**:

- MAJOR: removal or redefinition of a NON-NEGOTIABLE principle or section.
- MINOR: new principle or section added, or materially expanded guidance.
- PATCH: clarification, wording improvement, typo fix, non-semantic refinement.

**Compliance review**: Every PR MUST verify the Constitution Check gate defined in
`.specify/templates/plan-template.md`. The reviewer MUST confirm no change violates
Principles I–VI.

**Version**: 1.0.0 | **Ratified**: 2026-02-26 | **Last Amended**: 2026-02-26
