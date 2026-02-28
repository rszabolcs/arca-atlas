package com.arcadigitalis.backend.evm;

import com.arcadigitalis.backend.api.exception.RpcUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.*;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint8;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Performs live contract reads against the policy proxy. All methods perform
 * a real {@code eth_call} — no caching, no fallback to DB.
 * <p>
 * Contract ABI assumptions (derived from spec):
 * <ul>
 *   <li>{@code getPackageStatus(bytes32) returns (uint8)} — 0=DRAFT,1=ACTIVE,2=WARNING,3=PENDING_RELEASE,4=CLAIMABLE,5=RELEASED,6=REVOKED</li>
 *   <li>{@code getPackage(bytes32) returns (PackageView)} — tuple of all 15+ fields</li>
 *   <li>{@code isReleased(bytes32) returns (bool)}</li>
 * </ul>
 */
@Service
public class PolicyReader {

    private static final Logger log = LoggerFactory.getLogger(PolicyReader.class);

    private static final String[] STATUS_NAMES = {
        "DRAFT", "ACTIVE", "WARNING", "PENDING_RELEASE", "CLAIMABLE", "RELEASED", "REVOKED"
    };

    private final Web3j web3j;
    private final Web3jConfig config;

    public PolicyReader(Web3j web3j, Web3jConfig config) {
        this.web3j = web3j;
        this.config = config;
    }

    // ── getPackageStatus(bytes32) → string status name ───────────────────

    public String getPackageStatus(String packageKey) {
        try {
            Function function = new Function(
                "getPackageStatus",
                List.of(new Bytes32(Numeric.hexStringToByteArray(packageKey))),
                List.of(new TypeReference<Uint8>() {})
            );
            String encoded = FunctionEncoder.encode(function);
            EthCall response = ethCall(encoded);

            List<Type> decoded = FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());
            if (decoded.isEmpty()) {
                // Unknown package → DRAFT per spec (FR-009)
                return "DRAFT";
            }
            int statusIndex = ((Uint8) decoded.get(0)).getValue().intValue();
            if (statusIndex < 0 || statusIndex >= STATUS_NAMES.length) {
                log.warn("Unknown status index {} for packageKey={}", statusIndex, packageKey);
                return "DRAFT";
            }
            return STATUS_NAMES[statusIndex];
        } catch (RpcUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new RpcUnavailableException("Failed to read package status from chain", e);
        }
    }

    // ── getPackage(bytes32) → PackageView ─────────────────────────────────

    @SuppressWarnings("unchecked")
    public PackageView getPackage(String packageKey) {
        try {
            // Build the ABI function for getPackage(bytes32) returning a tuple
            // The returned tuple (PackageView) fields:
            //   uint8 status, address owner, address beneficiary, string manifestUri,
            //   address[] guardians, uint256 guardianQuorum,
            //   uint256 vetoCount, uint256 approvalCount,
            //   uint256 warnThreshold, uint256 inactivityThreshold, uint256 gracePeriodSeconds,
            //   uint256 lastCheckIn, uint256 paidUntil, uint256 pendingSince, uint256 releasedAt
            Function function = new Function(
                "getPackage",
                List.of(new Bytes32(Numeric.hexStringToByteArray(packageKey))),
                List.of(
                    new TypeReference<Uint8>() {},          // status
                    new TypeReference<Address>() {},        // owner
                    new TypeReference<Address>() {},        // beneficiary
                    new TypeReference<Utf8String>() {},     // manifestUri
                    new TypeReference<DynamicArray<Address>>() {},  // guardians
                    new TypeReference<Uint256>() {},        // guardianQuorum
                    new TypeReference<Uint256>() {},        // vetoCount
                    new TypeReference<Uint256>() {},        // approvalCount
                    new TypeReference<Uint256>() {},        // warnThreshold
                    new TypeReference<Uint256>() {},        // inactivityThreshold
                    new TypeReference<Uint256>() {},        // gracePeriodSeconds
                    new TypeReference<Uint256>() {},        // lastCheckIn (unix timestamp)
                    new TypeReference<Uint256>() {},        // paidUntil (unix timestamp)
                    new TypeReference<Uint256>() {},        // pendingSince (unix timestamp)
                    new TypeReference<Uint256>() {}         // releasedAt (unix timestamp)
                )
            );

            String encoded = FunctionEncoder.encode(function);
            EthCall response = ethCall(encoded);

            List<Type> decoded = FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());
            if (decoded.isEmpty()) {
                // Unknown package → return DRAFT view
                return PackageView.draft();
            }

            int idx = 0;
            int statusIdx = ((Uint8) decoded.get(idx++)).getValue().intValue();
            String status = (statusIdx >= 0 && statusIdx < STATUS_NAMES.length)
                ? STATUS_NAMES[statusIdx] : "DRAFT";
            String owner = ((Address) decoded.get(idx++)).getValue();
            String beneficiary = ((Address) decoded.get(idx++)).getValue();
            String manifestUri = ((Utf8String) decoded.get(idx++)).getValue();

            DynamicArray<Address> guardianArray = (DynamicArray<Address>) decoded.get(idx++);
            List<String> guardians = guardianArray.getValue().stream()
                .map(Address::getValue)
                .toList();

            BigInteger guardianQuorum = ((Uint256) decoded.get(idx++)).getValue();
            BigInteger vetoCount = ((Uint256) decoded.get(idx++)).getValue();
            BigInteger approvalCount = ((Uint256) decoded.get(idx++)).getValue();
            BigInteger warnThreshold = ((Uint256) decoded.get(idx++)).getValue();
            BigInteger inactivityThreshold = ((Uint256) decoded.get(idx++)).getValue();
            BigInteger gracePeriodSeconds = ((Uint256) decoded.get(idx++)).getValue();
            BigInteger lastCheckIn = ((Uint256) decoded.get(idx++)).getValue();
            BigInteger paidUntil = ((Uint256) decoded.get(idx++)).getValue();
            BigInteger pendingSince = ((Uint256) decoded.get(idx++)).getValue();
            BigInteger releasedAt = ((Uint256) decoded.get(idx++)).getValue();

            return new PackageView(
                status, owner, beneficiary, manifestUri, guardians,
                guardianQuorum.intValue(),
                vetoCount.intValue(), approvalCount.intValue(),
                warnThreshold.longValue(), inactivityThreshold.longValue(),
                gracePeriodSeconds.intValue(),
                toInstantOrNull(lastCheckIn), toInstantOrNull(paidUntil),
                toInstantOrNull(pendingSince), toInstantOrNull(releasedAt)
            );
        } catch (RpcUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new RpcUnavailableException("Failed to read package from chain", e);
        }
    }

    // ── isReleased(bytes32) → boolean ──────────────────────────────────────

    public boolean isReleased(String packageKey) {
        try {
            Function function = new Function(
                "isReleased",
                List.of(new Bytes32(Numeric.hexStringToByteArray(packageKey))),
                List.of(new TypeReference<Bool>() {})
            );
            String encoded = FunctionEncoder.encode(function);
            EthCall response = ethCall(encoded);

            List<Type> decoded = FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());
            if (decoded.isEmpty()) {
                return false;
            }
            return ((Bool) decoded.get(0)).getValue();
        } catch (RpcUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new RpcUnavailableException("Failed to read isReleased from chain", e);
        }
    }

    // ── Private helpers ────────────────────────────────────────────────────

    private EthCall ethCall(String encodedFunction) {
        try {
            Transaction tx = Transaction.createEthCallTransaction(
                "0x0000000000000000000000000000000000000000",
                config.getProxyAddress(),
                encodedFunction
            );
            EthCall response = web3j.ethCall(tx, DefaultBlockParameterName.LATEST).send();
            if (response.hasError()) {
                throw new RpcUnavailableException("EVM RPC error: " + response.getError().getMessage());
            }
            return response;
        } catch (RpcUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new RpcUnavailableException("EVM RPC call failed", e);
        }
    }

    private static Instant toInstantOrNull(BigInteger unixSeconds) {
        if (unixSeconds == null || unixSeconds.signum() == 0) {
            return null;
        }
        return Instant.ofEpochSecond(unixSeconds.longValue());
    }

    // ── Immutable view record ──────────────────────────────────────────────

    public record PackageView(
        String status,
        String ownerAddress,
        String beneficiaryAddress,
        String manifestUri,
        List<String> guardians,
        int guardianQuorum,
        int vetoCount,
        int approvalCount,
        long warnThreshold,
        long inactivityThreshold,
        int gracePeriodSeconds,
        Instant lastCheckIn,
        Instant paidUntil,
        Instant pendingSince,
        Instant releasedAt
    ) {
        /** Returns a DRAFT PackageView for unknown/unactivated packages */
        public static PackageView draft() {
            return new PackageView(
                "DRAFT", null, null, null, Collections.emptyList(),
                0, 0, 0, 0L, 0L, 0, null, null, null, null
            );
        }
    }
}
