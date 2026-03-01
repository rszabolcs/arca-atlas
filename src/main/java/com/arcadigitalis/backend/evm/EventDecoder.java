package com.arcadigitalis.backend.evm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.Utils;
import org.web3j.abi.datatypes.*;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Decodes all 13 contract event types from raw EthLog entries.
 * Uses Web3j ABI decoding and topic matching.
 */
@Component
public class EventDecoder {

    private static final Logger log = LoggerFactory.getLogger(EventDecoder.class);

    // Event signatures (keccak256 hashes of event signatures)
    private static final String SIG_PACKAGE_ACTIVATED = eventSignature("PackageActivated(bytes32,address,address,string,address[],uint256,uint256,uint256)");
    private static final String SIG_MANIFEST_UPDATED = eventSignature("ManifestUpdated(bytes32,string)");
    private static final String SIG_CHECK_IN = eventSignature("CheckIn(bytes32)");
    private static final String SIG_RENEWED = eventSignature("Renewed(bytes32,uint256)");
    private static final String SIG_GUARDIAN_APPROVED = eventSignature("GuardianApproved(bytes32,address)");
    private static final String SIG_GUARDIAN_VETOED = eventSignature("GuardianVetoed(bytes32,address)");
    private static final String SIG_GUARDIAN_VETO_RESCINDED = eventSignature("GuardianVetoRescinded(bytes32,address)");
    private static final String SIG_GUARDIAN_APPROVE_RESCINDED = eventSignature("GuardianApproveRescinded(bytes32,address)");
    private static final String SIG_GUARDIAN_STATE_RESET = eventSignature("GuardianStateReset(bytes32)");
    private static final String SIG_PENDING_RELEASE = eventSignature("PendingRelease(bytes32,uint256)");
    private static final String SIG_RELEASED = eventSignature("Released(bytes32)");
    private static final String SIG_REVOKED = eventSignature("Revoked(bytes32)");
    private static final String SIG_PACKAGE_RESCUED = eventSignature("PackageRescued(bytes32)");

    /**
     * Decodes a raw log entry into a typed DecodedEvent.
     * @throws UnknownEventException if the topic0 doesn't match any known event
     */
    public DecodedEvent decode(Log logEntry) {
        if (logEntry.getTopics() == null || logEntry.getTopics().isEmpty()) {
            throw new UnknownEventException("Log entry has no topics");
        }

        String topic0 = logEntry.getTopics().get(0);
        String eventType = resolveEventType(topic0);

        if (eventType == null) {
            throw new UnknownEventException("Unrecognized event topic: " + topic0);
        }

        String packageKey = extractPackageKeyFromTopic(logEntry);
        Map<String, Object> rawData = decodeEventData(eventType, logEntry);

        return new DecodedEvent(
            eventType,
            packageKey,
            logEntry.getBlockNumber().longValue(),
            logEntry.getBlockHash(),
            logEntry.getTransactionHash(),
            logEntry.getLogIndex().intValue(),
            rawData
        );
    }

    private String resolveEventType(String topic0) {
        if (topic0.equalsIgnoreCase(SIG_PACKAGE_ACTIVATED)) return "PackageActivated";
        if (topic0.equalsIgnoreCase(SIG_MANIFEST_UPDATED)) return "ManifestUpdated";
        if (topic0.equalsIgnoreCase(SIG_CHECK_IN)) return "CheckIn";
        if (topic0.equalsIgnoreCase(SIG_RENEWED)) return "Renewed";
        if (topic0.equalsIgnoreCase(SIG_GUARDIAN_APPROVED)) return "GuardianApproved";
        if (topic0.equalsIgnoreCase(SIG_GUARDIAN_VETOED)) return "GuardianVetoed";
        if (topic0.equalsIgnoreCase(SIG_GUARDIAN_VETO_RESCINDED)) return "GuardianVetoRescinded";
        if (topic0.equalsIgnoreCase(SIG_GUARDIAN_APPROVE_RESCINDED)) return "GuardianApproveRescinded";
        if (topic0.equalsIgnoreCase(SIG_GUARDIAN_STATE_RESET)) return "GuardianStateReset";
        if (topic0.equalsIgnoreCase(SIG_PENDING_RELEASE)) return "PendingRelease";
        if (topic0.equalsIgnoreCase(SIG_RELEASED)) return "Released";
        if (topic0.equalsIgnoreCase(SIG_REVOKED)) return "Revoked";
        if (topic0.equalsIgnoreCase(SIG_PACKAGE_RESCUED)) return "PackageRescued";
        return null;
    }

    private String extractPackageKeyFromTopic(Log logEntry) {
        // Most events have packageKey as indexed topic[1]
        if (logEntry.getTopics().size() > 1) {
            return logEntry.getTopics().get(1);
        }
        return "0x" + "0".repeat(64); // fallback
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> decodeEventData(String eventType, Log logEntry) {
        String data = logEntry.getData();
        if (data == null || data.equals("0x") || data.isEmpty()) {
            return Collections.emptyMap();
        }

        try {
            return switch (eventType) {
                case "PackageActivated" -> {
                    // Non-indexed: owner(address), beneficiary(address), manifestUri(string),
                    // guardians(address[]), guardianQuorum(uint256), warnThreshold(uint256), inactivityThreshold(uint256)
                    List<Type> decoded = FunctionReturnDecoder.decode(data, Utils.convert(Arrays.asList(
                        new TypeReference<Address>() {},
                        new TypeReference<Address>() {},
                        new TypeReference<Utf8String>() {},
                        new TypeReference<DynamicArray<Address>>() {},
                        new TypeReference<Uint256>() {},
                        new TypeReference<Uint256>() {},
                        new TypeReference<Uint256>() {}
                    )));
                    yield Map.of(
                        "owner", decoded.get(0).getValue().toString(),
                        "beneficiary", decoded.get(1).getValue().toString(),
                        "manifestUri", decoded.get(2).getValue().toString(),
                        "guardians", ((DynamicArray<Address>) decoded.get(3)).getValue().stream()
                            .map(a -> a.getValue()).toList(),
                        "guardianQuorum", ((Uint256) decoded.get(4)).getValue().intValue(),
                        "warnThreshold", ((Uint256) decoded.get(5)).getValue().longValue(),
                        "inactivityThreshold", ((Uint256) decoded.get(6)).getValue().longValue()
                    );
                }
                case "ManifestUpdated" -> {
                    List<Type> decoded = FunctionReturnDecoder.decode(data, Utils.convert(List.of(
                        new TypeReference<Utf8String>() {}
                    )));
                    yield Map.of("manifestUri", decoded.get(0).getValue().toString());
                }
                case "Renewed" -> {
                    List<Type> decoded = FunctionReturnDecoder.decode(data, Utils.convert(List.of(
                        new TypeReference<Uint256>() {}
                    )));
                    yield Map.of("paidUntil", ((Uint256) decoded.get(0)).getValue().longValue());
                }
                case "GuardianApproved", "GuardianVetoed", "GuardianVetoRescinded", "GuardianApproveRescinded" -> {
                    // guardian address may be in topic[2] or data
                    if (logEntry.getTopics().size() > 2) {
                        String guardian = "0x" + logEntry.getTopics().get(2).substring(26);
                        yield Map.of("guardian", guardian);
                    }
                    List<Type> decoded = FunctionReturnDecoder.decode(data, Utils.convert(List.of(
                        new TypeReference<Address>() {}
                    )));
                    yield Map.of("guardian", decoded.get(0).getValue().toString());
                }
                case "PendingRelease" -> {
                    List<Type> decoded = FunctionReturnDecoder.decode(data, Utils.convert(List.of(
                        new TypeReference<Uint256>() {}
                    )));
                    BigInteger flags = ((Uint256) decoded.get(0)).getValue();
                    yield Map.of(
                        "reason_flags", flags.intValue(),
                        "inactivity", (flags.intValue() & 1) != 0,
                        "funding_lapse", (flags.intValue() & 2) != 0
                    );
                }
                default -> Collections.emptyMap();
            };
        } catch (Exception e) {
            log.warn("Failed to decode event data for {}: {}", eventType, e.getMessage());
            return Collections.emptyMap();
        }
    }

    private static String eventSignature(String sig) {
        return EventEncoder.buildEventSignature(sig);
    }

    // ── Types ──────────────────────────────────────────────────────────────

    public record DecodedEvent(
        String eventType,
        String packageKey,
        long blockNumber,
        String blockHash,
        String txHash,
        int logIndex,
        Map<String, Object> rawData
    ) {}

    public static class UnknownEventException extends RuntimeException {
        public UnknownEventException(String message) { super(message); }
    }
}
