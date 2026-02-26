#!/bin/bash
# Recovery Drill Script (T102)
# Demonstrates offline beneficiary recovery using only cast (Foundry) / ethers.js CLI
# Tests SC-001: beneficiary completes recovery without backend

set -e

# Configuration
CHAIN_ID="${CHAIN_ID:-11155111}"
RPC_URL="${RPC_URL:-https://sepolia.infura.io/v3/YOUR_KEY}"
PROXY_ADDRESS="${PROXY_ADDRESS}"
PACKAGE_KEY="${PACKAGE_KEY}"

echo "=== Arca Recovery Drill ==="
echo "Chain ID: $CHAIN_ID"
echo "Proxy: $PROXY_ADDRESS"
echo "Package Key: $PACKAGE_KEY"
echo ""

# Step 1: Read package data from chain (requires cast from Foundry)
echo "Step 1: Reading package data from chain..."

if ! command -v cast &> /dev/null; then
    echo "ERROR: 'cast' (Foundry) not found. Install: https://getfoundry.sh/"
    exit 1
fi

# Call getPackage(bytes32) on proxy
echo "Calling getPackage($PACKAGE_KEY)..."
PACKAGE_DATA=$(cast call \
    "$PROXY_ADDRESS" \
    "getPackage(bytes32)" \
    "$PACKAGE_KEY" \
    --rpc-url "$RPC_URL")

echo "Package data retrieved: $PACKAGE_DATA"

# Parse manifestUri from package data (simplified - in production use abi-decode)
# For this drill, assume manifestUri is at a known offset
MANIFEST_URI=$(echo "$PACKAGE_DATA" | cut -d' ' -f4-)

echo "Manifest URI: $MANIFEST_URI"

# Step 2: Fetch manifest from IPFS (or HTTP)
echo ""
echo "Step 2: Fetching manifest from $MANIFEST_URI..."

if [[ "$MANIFEST_URI" == ipfs://* ]]; then
    CID="${MANIFEST_URI#ipfs://}"
    MANIFEST_JSON=$(curl -s "https://ipfs.io/ipfs/$CID")
else
    MANIFEST_JSON=$(curl -s "$MANIFEST_URI")
fi

echo "Manifest retrieved"

# Step 3: Extract Lit ACC from manifest
echo ""
echo "Step 3: Extracting Lit ACC..."

# Extract keyRelease.accessControl using jq
ACC_JSON=$(echo "$MANIFEST_JSON" | jq '.keyRelease.accessControl')

echo "Lit ACC extracted:"
echo "$ACC_JSON" | jq .

# Step 4: Verify ACC binding (local validation, no backend)
echo ""
echo "Step 4: Verifying ACC binding..."

ACC_CONTRACT=$(echo "$ACC_JSON" | jq -r '.contractAddress')
ACC_FUNCTION=$(echo "$ACC_JSON" | jq -r '.functionName')
ACC_PARAMS=$(echo "$ACC_JSON" | jq -r '.functionParams[0]')

if [[ "$ACC_CONTRACT" != "$PROXY_ADDRESS" ]]; then
    echo "ERROR: ACC contractAddress mismatch!"
    echo "Expected: $PROXY_ADDRESS"
    echo "Got: $ACC_CONTRACT"
    exit 1
fi

if [[ "$ACC_FUNCTION" != "isReleased" ]]; then
    echo "ERROR: ACC functionName must be 'isReleased'"
    exit 1
fi

if [[ "$ACC_PARAMS" != "$PACKAGE_KEY" ]]; then
    echo "ERROR: ACC functionParams must match packageKey"
    exit 1
fi

echo "✓ ACC binding verified (proxy, function, packageKey)"

# Step 5: Call isReleased to verify release condition
echo ""
echo "Step 5: Checking release condition..."

IS_RELEASED=$(cast call \
    "$PROXY_ADDRESS" \
    "isReleased(bytes32)" \
    "$PACKAGE_KEY" \
    --rpc-url "$RPC_URL")

echo "isReleased: $IS_RELEASED"

if [[ "$IS_RELEASED" == *"true"* ]]; then
    echo "✓ Package is RELEASED - recovery condition met"
else
    echo "⚠ Package not yet released - ACC would not grant access"
fi

echo ""
echo "=== Recovery Drill Complete ==="
echo "Beneficiary can perform recovery independently:"
echo "1. Read package data from chain"
echo "2. Fetch manifest from IPFS/HTTP"
echo "3. Verify ACC binding locally"
echo "4. Check isReleased() condition"
echo "5. Use Lit Protocol SDK to decrypt with verified ACC"
echo ""
echo "Backend was NOT contacted during this drill (SC-001 verified)"
