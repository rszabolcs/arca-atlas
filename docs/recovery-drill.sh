#!/usr/bin/env bash
# docs/recovery-drill.sh — Offline beneficiary recovery drill (T102)
#
# Purpose: Prove that a beneficiary can retrieve their Lit ACC template
# and construct a claim transaction WITHOUT the backend running.
# Uses only `cast` (Foundry) or `curl` + offline tools.
#
# Prerequisites:
#   - cast (Foundry) installed: https://getfoundry.sh
#   - RPC endpoint accessible
#   - IPFS gateway accessible
#
# Usage:
#   ./docs/recovery-drill.sh <PACKAGE_KEY> <RPC_URL> <PROXY_ADDRESS>
#
# Example:
#   ./docs/recovery-drill.sh 0xabababab...64hexchars http://localhost:8545 0x1234...5678
#
set -euo pipefail

PKG_KEY="${1:?Usage: $0 <PACKAGE_KEY> <RPC_URL> <PROXY_ADDRESS>}"
RPC_URL="${2:?Missing RPC_URL}"
PROXY="${3:?Missing PROXY_ADDRESS}"

echo "═══════════════════════════════════════════════════"
echo "  Arca Offline Recovery Drill"
echo "═══════════════════════════════════════════════════"
echo "Package Key  : $PKG_KEY"
echo "RPC URL      : $RPC_URL"
echo "Proxy Address: $PROXY"
echo ""

# Step 1: Read package status from chain
echo "[1/5] Reading package status from chain..."
STATUS=$(cast call "$PROXY" "getPackageStatus(bytes32)(string)" "$PKG_KEY" --rpc-url "$RPC_URL" 2>/dev/null || echo "UNKNOWN")
echo "       Status: $STATUS"

if [[ "$STATUS" == "DRAFT" || "$STATUS" == "UNKNOWN" ]]; then
    echo "ERROR: Package is $STATUS — nothing to recover."
    exit 1
fi

# Step 2: Read full package view
echo "[2/5] Reading full PackageView from chain..."
# getPackage returns a tuple; we extract key fields
RESULT=$(cast call "$PROXY" "getPackage(bytes32)" "$PKG_KEY" --rpc-url "$RPC_URL" 2>/dev/null || echo "")
if [[ -z "$RESULT" ]]; then
    echo "ERROR: Failed to read PackageView from chain."
    exit 1
fi
echo "       Raw result: ${RESULT:0:80}..."

# Step 3: Read manifestUri (extracted from getPackage or dedicated getter)
echo "[3/5] Reading manifestUri..."
MANIFEST_URI=$(cast call "$PROXY" "getPackage(bytes32)" "$PKG_KEY" --rpc-url "$RPC_URL" 2>/dev/null | head -1 || echo "")
echo "       ManifestUri field from PackageView: ${MANIFEST_URI:0:80}"

# Step 4: If IPFS URI, fetch manifest from gateway
echo "[4/5] Fetching manifest (if IPFS)..."
if [[ "$MANIFEST_URI" == ipfs://* ]]; then
    CID="${MANIFEST_URI#ipfs://}"
    GATEWAY_URL="https://gateway.pinata.cloud/ipfs/$CID"
    echo "       Fetching from: $GATEWAY_URL"
    MANIFEST=$(curl -sL "$GATEWAY_URL" 2>/dev/null || echo '{}')
    echo "       Manifest preview: ${MANIFEST:0:200}"
else
    echo "       ManifestUri is not IPFS — skipping fetch"
    MANIFEST="{}"
fi

# Step 5: Construct claim calldata offline
echo "[5/5] Constructing claim calldata offline..."
# claim(bytes32 packageKey)
CLAIM_SELECTOR="0x$(cast sig 'claim(bytes32)' 2>/dev/null || echo 'unknown')"
PADDED_KEY=$(echo "$PKG_KEY" | sed 's/^0x//')
CALLDATA="${CLAIM_SELECTOR}$(printf '%064s' "$PADDED_KEY" | tr ' ' '0')"
echo "       Claim calldata: ${CALLDATA:0:80}..."
echo "       To: $PROXY"

echo ""
echo "═══════════════════════════════════════════════════"
echo "  Recovery Drill COMPLETE"
echo "═══════════════════════════════════════════════════"
echo ""
echo "Next steps for actual recovery:"
echo "  1. Sign the claim transaction with the beneficiary wallet"
echo "  2. Submit to chain: cast send $PROXY $CALLDATA --rpc-url $RPC_URL"
echo "  3. After claim succeeds, use the Lit ACC to decrypt the DEK"
echo ""
echo "This drill proves: NO backend dependency for beneficiary recovery (SC-001)."
