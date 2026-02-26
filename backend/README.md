# Arca Java Backend

Non-custodial REST API service for ArcaDigitalis Vault. Built with Spring Boot 3.x / Java 21+.

## Features Implemented

### ✅ Phase 1-2: Foundation
- Spring Boot 3.2.2 with Maven wrapper
- PostgreSQL 15 with Flyway migrations (8 migrations, 7 tables)
- JPA entities and repositories
- Web3j integration for EVM interaction
- JWT authentication with SIWE (EIP-4361)
- Spring Security configuration
- Health endpoints (liveness/readiness)
- Docker Compose setup

### ✅ Phase 3: Authentication & Package Status (US1)
- SIWE message parser (manual EIP-4361)
- Nonce service (issue/consume with DB atomicity)
- SIWE verifier (ecrecover + signature validation)
- JWT service (HS256 with JTI replay protection)
- Live package status reads from chain
- Config endpoint (chainId, proxy, fundingEnabled)

### ✅ Phase 4: Owner Lifecycle (US2)
- Transaction payload generation for 11 contract functions
- Owner endpoints: activate, checkIn, renew, updateManifestUri, revoke, rescue
- Role-based authorization (owner/guardian/beneficiary)
- Pre-flight status checks (409 on invalid state transitions)
- Funding guard (ETH value validation)

### ✅ Phase 5: Guardian Workflow (US3)
- Guardian transaction payloads: approve, veto, rescindVeto, rescindApprove
- Guardian authorization checks
- Status-based validation (PENDING_RELEASE required)

### ✅ Phase 6: Beneficiary Recovery (US4)
- Recovery kit endpoint (unauthenticated)
- Lit ACC template generation
- Claim transaction payload
- Live chain fallback (works with empty DB)

### ✅ Phase 7: Manifest Validation (US5)
- 3-layer manifest validation:
  - Layer 1: Structural (proxy, chainId, ACC fields)
  - Layer 2: Live RPC (package status, beneficiary match)
  - Layer 3: Blob guard (encDEK validation)
- ACC template endpoint
- No Lit SDK required (static JSON generation)

### ✅ Phase 8: Artifact Storage (US6)
- SHA-256 integrity verification
- IPFS adapter (placeholder)
- S3-compatible storage adapter (placeholder)
- Artifact metadata persistence

### ⏳ Phase 9: Event Indexing (US7) - NOT IMPLEMENTED
- Event decoder for 13 contract event types
- Reorg-safe indexer with confirmation depth
- Block hash tracking for fork detection
- Notification dispatcher (email, webhook, push)
- Paginated event query endpoint

### ⏳ Phase 10: Polish & Testing - NOT IMPLEMENTED
- Secret log scanning tests
- Architecture tests (ArchUnit)
- Integration tests for all flows
- Comprehensive unit test suite

## Quick Start

### Prerequisites
- Java 21+
- Docker + Docker Compose
- PostgreSQL 15+ (via Docker Compose)

### Configuration

Copy `.env.example` to `.env` and configure:

```bash
cp .env.example .env
```

Required environment variables:
- `ARCA_EVM_RPC_URL`: Ethereum RPC endpoint
- `ARCA_EVM_CHAIN_ID`: Chain ID (e.g., 11155111 for Sepolia)
- `ARCA_POLICY_PROXY_ADDRESS`: Deployed policy proxy contract address
- `ARCA_JWT_SECRET`: 32+ byte secret (base64)
- `ARCA_SIWE_DOMAIN`: Domain for SIWE (e.g., localhost)

### Run Locally

```bash
# Start PostgreSQL
docker compose up -d postgres

# Build and run
./mvnw spring-boot:run
```

The API will be available at `http://localhost:8080/api/v1`

## API Endpoints

### Public Endpoints
- `POST /auth/nonce` - Issue SIWE nonce
- `POST /auth/verify` - Verify SIWE signature, get JWT
- `GET /config` - Get deployment configuration
- `GET /packages/{key}/recovery-kit` - Get recovery kit (unauthenticated)
- `POST /packages/{key}/tx/renew` - Prepare renew transaction (unauthenticated)
- `POST /packages/{key}/tx/rescue` - Prepare rescue transaction (unauthenticated)
- `GET /health/live` - Liveness probe
- `GET /health/ready` - Readiness probe

### Authenticated Endpoints (require JWT)
- `GET /packages/{key}/status` - Get package status
- `POST /packages/{key}/tx/activate` - Prepare activate transaction
- `POST /packages/{key}/tx/check-in` - Prepare check-in transaction
- `POST /packages/{key}/tx/update-manifest` - Prepare update manifest transaction
- `POST /packages/{key}/tx/revoke` - Prepare revoke transaction
- `POST /packages/{key}/tx/guardian-approve` - Prepare guardian approve
- `POST /packages/{key}/tx/guardian-veto` - Prepare guardian veto
- `POST /packages/{key}/tx/guardian-rescind-veto` - Rescind veto
- `POST /packages/{key}/tx/guardian-rescind-approve` - Rescind approve
- `POST /packages/{key}/tx/claim` - Prepare claim transaction
- `GET /lit/acc-template` - Get Lit ACC template
- `POST /lit/validate-manifest` - Validate manifest JSON
- `POST /artifacts` - Upload artifact
- `GET /artifacts/{id}` - Retrieve artifact

## Architecture

### Constitution Compliance

The implementation follows all 6 constitution principles:

1. **Zero-Secret Custody**: No DEK/private keys stored or logged
2. **Recovery Independence**: Recovery kit works without backend
3. **On-Chain Authoritativeness**: All authorization from live chain reads
4. **Non-Custodial Transactions**: Backend returns unsigned calldata only
5. **Policy-Bound Lit Integration**: ACC bound to `isReleased(packageKey)==true`
6. **Scale-First Design**: No on-chain enumeration, indexed events

### Package Structure

```
src/main/java/com/arcadigitalis/backend/
├── api/
│   ├── controller/        # REST controllers
│   ├── dto/              # Request/response DTOs
│   └── exception/        # Exception handling
├── auth/                 # SIWE + JWT authentication
├── evm/                  # Web3j integration
├── policy/               # Business logic layer
├── lit/                  # Lit Protocol ACC generation
├── storage/              # Artifact storage
├── persistence/          # JPA entities & repositories
└── notifications/        # Notification dispatching (not implemented)
```

## Testing

```bash
# Run all tests
./mvnw test

# Run integration tests only
./mvnw verify -Pintegration-tests
```

## Docker Deployment

```bash
# Build image
docker build -t arca-backend .

# Run with Docker Compose
docker compose up
```

## Development Status

**Current**: ~75% complete (Phases 1-8 of 10)

**Missing**:
- Event indexer (Phase 9)
- Notification system (Phase 9)
- Comprehensive test suite (Phase 10)
- IPFS/S3 adapters (placeholders only)

**Next Steps**:
1. Implement event indexer with reorg protection
2. Add notification dispatcher
3. Write integration tests
4. Implement IPFS and S3 adapters
5. Add secret log scanning tests
6. Performance testing at scale (1M+ packages)

## License

See project root LICENSE file.
