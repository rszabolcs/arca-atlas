package com.arcadigitalis.backend.evm;

import org.springframework.stereotype.Component;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.*;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint8;
import org.web3j.protocol.core.methods.response.Log;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

@Component
public class EventDecoder {

    // Event signatures
    private static final String PACKAGE_ACTIVATED = EventEncoder.encode(new Event("PackageActivated",
        List.of(new TypeReference<Bytes32>(true) {}, new TypeReference<Address>(true) {},
                new TypeReference<Address>() {}, new TypeReference<Utf8String>() {})));

    private static final String MANIFEST_UPDATED = EventEncoder.encode(new Event("ManifestUpdated",
        List.of(new TypeReference<Bytes32>(true) {}, new TypeReference<Utf8String>() {})));

    private static final String CHECK_IN = EventEncoder.encode(new Event("CheckIn",
        List.of(new TypeReference<Bytes32>(true) {}, new TypeReference<Uint256>() {})));

    private static final String RENEWED = EventEncoder.encode(new Event("Renewed",
        List.of(new TypeReference<Bytes32>(true) {}, new TypeReference<Uint256>() {}, new TypeReference<Uint256>() {})));

    private static final String GUARDIAN_APPROVED = EventEncoder.encode(new Event("GuardianApproved",
        List.of(new TypeReference<Bytes32>(true) {}, new TypeReference<Address>(true) {})));

    private static final String GUARDIAN_VETOED = EventEncoder.encode(new Event("GuardianVetoed",
        List.of(new TypeReference<Bytes32>(true) {}, new TypeReference<Address>(true) {})));

    private static final String GUARDIAN_VETO_RESCINDED = EventEncoder.encode(new Event("GuardianVetoRescinded",
        List.of(new TypeReference<Bytes32>(true) {}, new TypeReference<Address>(true) {})));

    private static final String GUARDIAN_APPROVE_RESCINDED = EventEncoder.encode(new Event("GuardianApproveRescinded",
        List.of(new TypeReference<Bytes32>(true) {}, new TypeReference<Address>(true) {})));

    private static final String GUARDIAN_STATE_RESET = EventEncoder.encode(new Event("GuardianStateReset",
        List.of(new TypeReference<Bytes32>(true) {})));

    private static final String PENDING_RELEASE = EventEncoder.encode(new Event("PendingRelease",
        List.of(new TypeReference<Bytes32>(true) {}, new TypeReference<Uint8>() {})));

    private static final String RELEASED = EventEncoder.encode(new Event("Released",
        List.of(new TypeReference<Bytes32>(true) {}, new TypeReference<Uint256>() {})));

    private static final String REVOKED = EventEncoder.encode(new Event("Revoked",
        List.of(new TypeReference<Bytes32>(true) {})));

    private static final String PACKAGE_RESCUED = EventEncoder.encode(new Event("PackageRescued",
        List.of(new TypeReference<Bytes32>(true) {})));

    public DecodedEvent decode(Log log) {
        String topic0 = log.getTopics().get(0);

        if (PACKAGE_ACTIVATED.equals(topic0)) {
            return decodePackageActivated(log);
        } else if (MANIFEST_UPDATED.equals(topic0)) {
            return decodeManifestUpdated(log);
        } else if (CHECK_IN.equals(topic0)) {
            return decodeCheckIn(log);
        } else if (RENEWED.equals(topic0)) {
            return decodeRenewed(log);
        } else if (GUARDIAN_APPROVED.equals(topic0)) {
            return decodeGuardianApproved(log);
        } else if (GUARDIAN_VETOED.equals(topic0)) {
            return decodeGuardianVetoed(log);
        } else if (GUARDIAN_VETO_RESCINDED.equals(topic0)) {
            return decodeGuardianVetoRescinded(log);
        } else if (GUARDIAN_APPROVE_RESCINDED.equals(topic0)) {
            return decodeGuardianApproveRescinded(log);
        } else if (GUARDIAN_STATE_RESET.equals(topic0)) {
            return decodeGuardianStateReset(log);
        } else if (PENDING_RELEASE.equals(topic0)) {
            return decodePendingRelease(log);
        } else if (RELEASED.equals(topic0)) {
            return decodeReleased(log);
        } else if (REVOKED.equals(topic0)) {
            return decodeRevoked(log);
        } else if (PACKAGE_RESCUED.equals(topic0)) {
            return decodePackageRescued(log);
        } else {
            throw new UnknownEventException("Unknown event topic: " + topic0);
        }
    }

    private DecodedEvent decodePackageActivated(Log log) {
        String packageKey = log.getTopics().get(1);
        String owner = "0x" + log.getTopics().get(2).substring(26);

        List<Type> nonIndexed = FunctionReturnDecoder.decode(log.getData(),
            List.of(new TypeReference<Address>() {}, new TypeReference<Utf8String>() {}));

        String beneficiary = ((Address) nonIndexed.get(0)).getValue();
        String manifestUri = ((Utf8String) nonIndexed.get(1)).getValue();

        return new DecodedEvent("PackageActivated", packageKey,
            Map.of("owner", owner, "beneficiary", beneficiary, "manifestUri", manifestUri));
    }

    private DecodedEvent decodeManifestUpdated(Log log) {
        String packageKey = log.getTopics().get(1);
        List<Type> nonIndexed = FunctionReturnDecoder.decode(log.getData(),
            List.of(new TypeReference<Utf8String>() {}));
        String newManifestUri = ((Utf8String) nonIndexed.get(0)).getValue();

        return new DecodedEvent("ManifestUpdated", packageKey,
            Map.of("manifestUri", newManifestUri));
    }

    private DecodedEvent decodeCheckIn(Log log) {
        String packageKey = log.getTopics().get(1);
        List<Type> nonIndexed = FunctionReturnDecoder.decode(log.getData(),
            List.of(new TypeReference<Uint256>() {}));
        BigInteger timestamp = ((Uint256) nonIndexed.get(0)).getValue();

        return new DecodedEvent("CheckIn", packageKey,
            Map.of("timestamp", timestamp.toString()));
    }

    private DecodedEvent decodeRenewed(Log log) {
        String packageKey = log.getTopics().get(1);
        List<Type> nonIndexed = FunctionReturnDecoder.decode(log.getData(),
            List.of(new TypeReference<Uint256>() {}, new TypeReference<Uint256>() {}));
        BigInteger amount = ((Uint256) nonIndexed.get(0)).getValue();
        BigInteger paidUntil = ((Uint256) nonIndexed.get(1)).getValue();

        return new DecodedEvent("Renewed", packageKey,
            Map.of("amount", amount.toString(), "paidUntil", paidUntil.toString()));
    }

    private DecodedEvent decodeGuardianApproved(Log log) {
        String packageKey = log.getTopics().get(1);
        String guardian = "0x" + log.getTopics().get(2).substring(26);

        return new DecodedEvent("GuardianApproved", packageKey,
            Map.of("guardian", guardian));
    }

    private DecodedEvent decodeGuardianVetoed(Log log) {
        String packageKey = log.getTopics().get(1);
        String guardian = "0x" + log.getTopics().get(2).substring(26);

        return new DecodedEvent("GuardianVetoed", packageKey,
            Map.of("guardian", guardian));
    }

    private DecodedEvent decodeGuardianVetoRescinded(Log log) {
        String packageKey = log.getTopics().get(1);
        String guardian = "0x" + log.getTopics().get(2).substring(26);

        return new DecodedEvent("GuardianVetoRescinded", packageKey,
            Map.of("guardian", guardian));
    }

    private DecodedEvent decodeGuardianApproveRescinded(Log log) {
        String packageKey = log.getTopics().get(1);
        String guardian = "0x" + log.getTopics().get(2).substring(26);

        return new DecodedEvent("GuardianApproveRescinded", packageKey,
            Map.of("guardian", guardian));
    }

    private DecodedEvent decodeGuardianStateReset(Log log) {
        String packageKey = log.getTopics().get(1);
        return new DecodedEvent("GuardianStateReset", packageKey, Map.of());
    }

    private DecodedEvent decodePendingRelease(Log log) {
        String packageKey = log.getTopics().get(1);
        List<Type> nonIndexed = FunctionReturnDecoder.decode(log.getData(),
            List.of(new TypeReference<Uint8>() {}));
        int reasonFlags = ((Uint8) nonIndexed.get(0)).getValue().intValue();

        return new DecodedEvent("PendingRelease", packageKey,
            Map.of("reasonFlags", reasonFlags));
    }

    private DecodedEvent decodeReleased(Log log) {
        String packageKey = log.getTopics().get(1);
        List<Type> nonIndexed = FunctionReturnDecoder.decode(log.getData(),
            List.of(new TypeReference<Uint256>() {}));
        BigInteger releasedAt = ((Uint256) nonIndexed.get(0)).getValue();

        return new DecodedEvent("Released", packageKey,
            Map.of("releasedAt", releasedAt.toString()));
    }

    private DecodedEvent decodeRevoked(Log log) {
        String packageKey = log.getTopics().get(1);
        return new DecodedEvent("Revoked", packageKey, Map.of());
    }

    private DecodedEvent decodePackageRescued(Log log) {
        String packageKey = log.getTopics().get(1);
        return new DecodedEvent("PackageRescued", packageKey, Map.of());
    }

    public record DecodedEvent(String eventType, String packageKey, Map<String, Object> data) {}

    public static class UnknownEventException extends RuntimeException {
        public UnknownEventException(String message) {
            super(message);
        }
    }
}
