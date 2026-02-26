package com.arcadigitalis.backend.evm;

import org.springframework.stereotype.Component;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.*;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class CalldataBuilder {

    public String encodeActivate(String packageKey, String manifestUri, List<String> guardians,
                                   BigInteger guardianQuorum, BigInteger warnThreshold,
                                   BigInteger inactivityThreshold) {
        Function function = new Function(
            "activate",
            Arrays.asList(
                new Bytes32(hexToBytes32(packageKey)),
                new Utf8String(manifestUri),
                new DynamicArray<>(Address.class, guardians.stream().map(Address::new).collect(Collectors.toList())),
                new Uint256(guardianQuorum),
                new Uint256(warnThreshold),
                new Uint256(inactivityThreshold)
            ),
            List.of()
        );
        return FunctionEncoder.encode(function);
    }

    public String encodeCheckIn(String packageKey) {
        Function function = new Function(
            "checkIn",
            List.of(new Bytes32(hexToBytes32(packageKey))),
            List.of()
        );
        return FunctionEncoder.encode(function);
    }

    public String encodeRenew(String packageKey) {
        Function function = new Function(
            "renew",
            List.of(new Bytes32(hexToBytes32(packageKey))),
            List.of()
        );
        return FunctionEncoder.encode(function);
    }

    public String encodeUpdateManifestUri(String packageKey, String newManifestUri) {
        Function function = new Function(
            "updateManifestUri",
            Arrays.asList(
                new Bytes32(hexToBytes32(packageKey)),
                new Utf8String(newManifestUri)
            ),
            List.of()
        );
        return FunctionEncoder.encode(function);
    }

    public String encodeGuardianApprove(String packageKey) {
        Function function = new Function(
            "guardianApprove",
            List.of(new Bytes32(hexToBytes32(packageKey))),
            List.of()
        );
        return FunctionEncoder.encode(function);
    }

    public String encodeGuardianVeto(String packageKey) {
        Function function = new Function(
            "guardianVeto",
            List.of(new Bytes32(hexToBytes32(packageKey))),
            List.of()
        );
        return FunctionEncoder.encode(function);
    }

    public String encodeGuardianRescindVeto(String packageKey) {
        Function function = new Function(
            "guardianRescindVeto",
            List.of(new Bytes32(hexToBytes32(packageKey))),
            List.of()
        );
        return FunctionEncoder.encode(function);
    }

    public String encodeGuardianRescindApprove(String packageKey) {
        Function function = new Function(
            "guardianRescindApprove",
            List.of(new Bytes32(hexToBytes32(packageKey))),
            List.of()
        );
        return FunctionEncoder.encode(function);
    }

    public String encodeClaim(String packageKey) {
        Function function = new Function(
            "claim",
            List.of(new Bytes32(hexToBytes32(packageKey))),
            List.of()
        );
        return FunctionEncoder.encode(function);
    }

    public String encodeRevoke(String packageKey) {
        Function function = new Function(
            "revoke",
            List.of(new Bytes32(hexToBytes32(packageKey))),
            List.of()
        );
        return FunctionEncoder.encode(function);
    }

    public String encodeRescue(String packageKey) {
        Function function = new Function(
            "rescue",
            List.of(new Bytes32(hexToBytes32(packageKey))),
            List.of()
        );
        return FunctionEncoder.encode(function);
    }

    private byte[] hexToBytes32(String hex) {
        String clean = hex.startsWith("0x") ? hex.substring(2) : hex;
        byte[] bytes = new byte[32];
        byte[] hexBytes = org.web3j.utils.Numeric.hexStringToByteArray(clean);
        System.arraycopy(hexBytes, 0, bytes, 0, Math.min(hexBytes.length, 32));
        return bytes;
    }
}
