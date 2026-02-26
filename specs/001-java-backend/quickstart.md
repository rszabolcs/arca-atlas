# Quickstart: Arca Java Backend

**Feature**: `001-java-backend`
**Date**: 2026-02-26

---

## Prerequisites

| Tool | Version | Purpose |
|---|---|---|
| Java JDK | 21+ | Runtime |
| Maven | 3.9+ (or use `./mvnw`) | Build tool |
| Docker + Docker Compose | 24+ | Local PostgreSQL + optional IPFS |
| git | any | Source control |

Optional but recommended:
- An Ethereum testnet RPC URL (Sepolia) — e.g., Infura or Alchemy free tier.
- An Infura IPFS project ID (for artifact pinning tests).

---

## 1. Clone and Configure

```bash
git clone <repo-url>
cd <repo-root>
git checkout 001-java-backend
```

Copy the environment template:

```bash
cp .env.example .env
```

Edit `.env` and populate the required values:

```dotenv
# ── Ethereum RPC ──────────────────────────────────────────────
ARCA_EVM_RPC_URL=https://sepolia.infura.io/v3/<your-key>
ARCA_EVM_CHAIN_ID=11155111
ARCA_POLICY_PROXY_ADDRESS=0x<deployed-proxy-address>

# ── JWT ───────────────────────────────────────────────────────
# 32+ random bytes, base64-encoded
ARCA_JWT_SECRET=<generate-with: openssl rand -base64 32>
ARCA_JWT_TTL_SECONDS=3600
# ── SIWE ─────────────────────────────────────────────────────────────
# Domain that must exactly match the 'domain' field in SIWE signed messages.
# Set to your frontend's host (e.g. app.arcadigitalis.com). Mismatch → HTTP 401.
ARCA_SIWE_DOMAIN=localhost
# ── Indexer ───────────────────────────────────────────────────
ARCA_INDEXER_CONFIRMATION_DEPTH=1        # use 1 for testnet, 12 for mainnet
ARCA_INDEXER_POLL_INTERVAL_SECONDS=15
ARCA_INDEXER_START_BLOCK=<contract-deployment-block>  # REQUIRED: block at which the proxy was deployed

# ── IPFS (optional for local dev) ─────────────────────────────
ARCA_IPFS_ENABLED=false
ARCA_IPFS_API_URL=https://ipfs.infura.io:5001
ARCA_IPFS_PROJECT_ID=<infura-ipfs-project-id>
ARCA_IPFS_PROJECT_SECRET=<infura-ipfs-secret>

# ── S3 (optional for local dev) ───────────────────────────────
ARCA_S3_ENABLED=false
ARCA_S3_BUCKET=arca-artifacts
ARCA_S3_REGION=us-east-1
AWS_ACCESS_KEY_ID=<key>
AWS_SECRET_ACCESS_KEY=<secret>

# ── Notifications (optional for local dev) ────────────────────
ARCA_NOTIFICATIONS_ENABLED=false
SPRING_MAIL_HOST=smtp.example.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=<user>
SPRING_MAIL_PASSWORD=<pass>
```

---

## 2. Start Local Infrastructure

```bash
docker compose up -d
```

This starts:
- **PostgreSQL 15** on `localhost:5432` (DB: `arca`, user: `arca`, pass: `arca_dev`)

Flyway migrations run automatically on application startup (Spring Boot auto-configuration).

To also run a local IPFS node (optional):

```bash
docker compose --profile ipfs up -d
```

---

## 3. Build

```bash
./mvnw clean package -DskipTests
```

Or with tests (requires Docker for Testcontainers):

```bash
./mvnw clean verify
```

---

## 4. Run

```bash
./mvnw spring-boot:run
```

Or run the packaged JAR:

```bash
java -jar target/arca-backend-*.jar
```

The service starts on `http://localhost:8080` by default.

---

## 5. Verify the Service

Check health:

```bash
curl http://localhost:8080/api/v1/health/ready
# → 200 OK  (DB + RPC reachable)
```

Request a SIWE nonce:

```bash
curl -X POST http://localhost:8080/api/v1/auth/nonce \
  -H 'Content-Type: application/json' \
  -d '{"walletAddress": "0xYourAddress"}'
# → {"nonce":"...", "expiresAt":"..."}
```

Query package status (DRAFT for an unactivated key):

```bash
curl "http://localhost:8080/api/v1/packages/0x$(head -c 32 /dev/urandom | xxd -p)/status?chainId=11155111&proxyAddress=0x<proxy>"
# → {"status":"DRAFT", ...}
```

---

## 6. Run Tests

Unit tests only (no Docker required):

```bash
./mvnw test -pl src/test/unit
```

Integration tests (Testcontainers + WireMock, Docker required):

```bash
./mvnw verify -Pintegration
```

Contract tests (Spring MockMvc, no external dependencies):

```bash
./mvnw test -pl src/test/contract
```

---

## 7. Explore the API

The OpenAPI spec is at:
- [specs/001-java-backend/contracts/openapi.yaml](contracts/openapi.yaml)

Swagger UI is served when the `swagger-ui` Spring profile is active:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=swagger-ui
# → http://localhost:8080/swagger-ui.html
```

---

## 8. Configuration Reference

All settings are in `src/main/resources/application.yml` and can be overridden via:
1. Environment variables (`.env` file or shell exports)
2. `application-local.yml` (gitignored)

Key application properties:

| Property | Env var | Default |
|---|---|---|
| `arca.evm.rpc-url` | `ARCA_EVM_RPC_URL` | *(required)* |
| `arca.evm.chain-id` | `ARCA_EVM_CHAIN_ID` | *(required)* |
| `arca.policy.proxy-address` | `ARCA_POLICY_PROXY_ADDRESS` | *(required)* |
| `arca.auth.siwe-domain` | `ARCA_SIWE_DOMAIN` | *(required)* — must match frontend host |
| `arca.jwt.secret` | `ARCA_JWT_SECRET` | *(required)* |
| `arca.jwt.ttl-seconds` | `ARCA_JWT_TTL_SECONDS` | `3600` |
| `arca.indexer.confirmation-depth` | `ARCA_INDEXER_CONFIRMATION_DEPTH` | `12` |
| `arca.indexer.poll-interval-seconds` | `ARCA_INDEXER_POLL_INTERVAL_SECONDS` | `15` |
| `arca.indexer.start-block` | `ARCA_INDEXER_START_BLOCK` | *(required)* — contract deployment block |
| `arca.storage.ipfs.enabled` | `ARCA_IPFS_ENABLED` | `false` |
| `arca.storage.s3.enabled` | `ARCA_S3_ENABLED` | `false` |
| `arca.notifications.enabled` | `ARCA_NOTIFICATIONS_ENABLED` | `false` |

---

## 9. Secret Hygiene Checklist

Before committing or sharing any config:

- [ ] `.env` is in `.gitignore` — never commit it.
- [ ] `ARCA_JWT_SECRET` is ≥ 32 bytes of random data.
- [ ] `ARCA_SIWE_DOMAIN` matches the exact host of the production frontend (e.g., `app.arcadigitalis.com`); do not leave as `localhost` in production.
- [ ] `ARCA_INDEXER_START_BLOCK` is set to the contract deployment block number, not `0`.
- [ ] No wallet private keys appear anywhere in config or logs.
- [ ] IPFS project secret is set via env var only, never hardcoded.
- [ ] Log output checked: `grep -i 'key\|secret\|password\|dek\|seed\|private' app.log` returns only scrubbed entries.
