package com.arcadigitalis.backend.evm;

import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.*;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.utils.Numeric;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * ABI-encodes all 11 mutating contract functions into hex calldata strings.
 * Never signs or submits — Constitution Principle IV.
 */
@Component
public class CalldataBuilder {

    /**
     * activate(bytes32 packageKey, string manifestUri, address beneficiary,
     *          address[] guardians, uint256 guardianQuorum,
     *          uint256 warnThreshold, uint256 inactivityThreshold,
     *          uint256 gracePeriodSeconds, uint256 paidUntil)
     */
    public String encodeActivate(String packageKey, String manifestUri, String beneficiary,
                                  List<String> guardians, int guardianQuorum,
                                  long warnThreshold, long inactivityThreshold,
                                  long gracePeriodSeconds, long paidUntil) {
        Function function = new Function(
            "activate",
            Arrays.asList(
                toBytes32(packageKey),
                new Utf8String(manifestUri),
                new Address(beneficiary),
                new DynamicArray<>(Address.class,
                    guardians.stream().map(Address::new).toList()),
                new Uint256(BigInteger.valueOf(guardianQuorum)),
                new Uint256(BigInteger.valueOf(warnThreshold)),
                new Uint256(BigInteger.valueOf(inactivityThreshold)),
                new Uint256(BigInteger.valueOf(gracePeriodSeconds)),
                new Uint256(BigInteger.valueOf(paidUntil))
            ),
            Collections.emptyList()
        );
        return FunctionEncoder.encode(function);
    }

    /** checkIn(bytes32 packageKey) */
    public String encodeCheckIn(String packageKey) {
        Function function = new Function(
            "checkIn",
            List.of(toBytes32(packageKey)),
            Collections.emptyList()
        );
        return FunctionEncoder.encode(function);
    }

    /** renew(bytes32 packageKey) */
    public String encodeRenew(String packageKey) {
        Function function = new Function(
            "renew",
            List.of(toBytes32(packageKey)),
            Collections.emptyList()
        );
        return FunctionEncoder.encode(function);
    }

    /** updateManifestUri(bytes32 packageKey, string newManifestUri) */
    public String encodeUpdateManifestUri(String packageKey, String newManifestUri) {
        Function function = new Function(
            "updateManifestUri",
            Arrays.asList(toBytes32(packageKey), new Utf8String(newManifestUri)),
            Collections.emptyList()
        );
        return FunctionEncoder.encode(function);
    }

    /** revoke(bytes32 packageKey) */
    public String encodeRevoke(String packageKey) {
        Function function = new Function(
            "revoke",
            List.of(toBytes32(packageKey)),
            Collections.emptyList()
        );
        return FunctionEncoder.encode(function);
    }

    /** rescue(bytes32 packageKey) */
    public String encodeRescue(String packageKey) {
        Function function = new Function(
            "rescue",
            List.of(toBytes32(packageKey)),
            Collections.emptyList()
        );
        return FunctionEncoder.encode(function);
    }

    /** guardianApprove(bytes32 packageKey) */
    public String encodeGuardianApprove(String packageKey) {
        Function function = new Function(
            "guardianApprove",
            List.of(toBytes32(packageKey)),
            Collections.emptyList()
        );
        return FunctionEncoder.encode(function);
    }

    /** guardianVeto(bytes32 packageKey) */
    public String encodeGuardianVeto(String packageKey) {
        Function function = new Function(
            "guardianVeto",
            List.of(toBytes32(packageKey)),
            Collections.emptyList()
        );
        return FunctionEncoder.encode(function);
    }

    /** guardianRescindVeto(bytes32 packageKey) */
    public String encodeGuardianRescindVeto(String packageKey) {
        Function function = new Function(
            "guardianRescindVeto",
            List.of(toBytes32(packageKey)),
            Collections.emptyList()
        );
        return FunctionEncoder.encode(function);
    }

    /** guardianRescindApprove(bytes32 packageKey) */
    public String encodeGuardianRescindApprove(String packageKey) {
        Function function = new Function(
            "guardianRescindApprove",
            List.of(toBytes32(packageKey)),
            Collections.emptyList()
        );
        return FunctionEncoder.encode(function);
    }

    /** claim(bytes32 packageKey) */
    public String encodeClaim(String packageKey) {
        Function function = new Function(
            "claim",
            List.of(toBytes32(packageKey)),
            Collections.emptyList()
        );
        return FunctionEncoder.encode(function);
    }

    // ── Helper ─────────────────────────────────────────────

    private static Bytes32 toBytes32(String hexKey) {
        return new Bytes32(Numeric.hexStringToByteArray(hexKey));
    }
}
