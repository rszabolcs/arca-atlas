package com.arcadigitalis.backend.evm;

import com.arcadigitalis.backend.evm.EventDecoder.DecodedEvent;
import com.arcadigitalis.backend.evm.EventDecoder.UnknownEventException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.web3j.abi.EventEncoder;
import org.web3j.protocol.core.methods.response.Log;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for EventDecoder — T092.
 * Tests: known event decoding, unknown topic rejection, data decoding.
 */
class EventDecoderTest {

    private EventDecoder decoder;

    private static final String PKG_KEY_TOPIC = "0x" + "ab".repeat(32);

    @BeforeEach
    void setUp() {
        decoder = new EventDecoder();
    }

    @Test
    @DisplayName("Decode CheckIn(bytes32) event — simple event with no data")
    void decodeCheckIn() {
        String topic0 = EventEncoder.buildEventSignature("CheckIn(bytes32)");
        Log log = buildLog(topic0, PKG_KEY_TOPIC, "0x");

        DecodedEvent event = decoder.decode(log);

        assertThat(event.eventType()).isEqualTo("CheckIn");
        assertThat(event.packageKey()).isEqualTo(PKG_KEY_TOPIC);
        assertThat(event.blockNumber()).isEqualTo(100L);
    }

    @Test
    @DisplayName("Decode Released(bytes32) event")
    void decodeReleased() {
        String topic0 = EventEncoder.buildEventSignature("Released(bytes32)");
        Log log = buildLog(topic0, PKG_KEY_TOPIC, "0x");

        DecodedEvent event = decoder.decode(log);
        assertThat(event.eventType()).isEqualTo("Released");
    }

    @Test
    @DisplayName("Decode Revoked(bytes32) event")
    void decodeRevoked() {
        String topic0 = EventEncoder.buildEventSignature("Revoked(bytes32)");
        Log log = buildLog(topic0, PKG_KEY_TOPIC, "0x");

        DecodedEvent event = decoder.decode(log);
        assertThat(event.eventType()).isEqualTo("Revoked");
    }

    @Test
    @DisplayName("Decode GuardianStateReset(bytes32) event")
    void decodeGuardianStateReset() {
        String topic0 = EventEncoder.buildEventSignature("GuardianStateReset(bytes32)");
        Log log = buildLog(topic0, PKG_KEY_TOPIC, "0x");

        DecodedEvent event = decoder.decode(log);
        assertThat(event.eventType()).isEqualTo("GuardianStateReset");
    }

    @Test
    @DisplayName("Decode PackageRescued(bytes32) event")
    void decodePackageRescued() {
        String topic0 = EventEncoder.buildEventSignature("PackageRescued(bytes32)");
        Log log = buildLog(topic0, PKG_KEY_TOPIC, "0x");

        DecodedEvent event = decoder.decode(log);
        assertThat(event.eventType()).isEqualTo("PackageRescued");
    }

    @Test
    @DisplayName("Unknown topic throws UnknownEventException")
    void unknownTopic_throws() {
        Log log = buildLog("0x" + "ff".repeat(32), PKG_KEY_TOPIC, "0x");
        assertThatThrownBy(() -> decoder.decode(log))
            .isInstanceOf(UnknownEventException.class);
    }

    @Test
    @DisplayName("Log with no topics throws UnknownEventException")
    void noTopics_throws() {
        Log log = new Log();
        log.setTopics(Collections.emptyList());
        assertThatThrownBy(() -> decoder.decode(log))
            .isInstanceOf(UnknownEventException.class);
    }

    @Test
    @DisplayName("All 13 event types have unique signatures")
    void allSignatures_unique() {
        List<String> sigs = List.of(
            "PackageActivated(bytes32,address,address,string,address[],uint256,uint256,uint256)",
            "ManifestUpdated(bytes32,string)",
            "CheckIn(bytes32)",
            "Renewed(bytes32,uint256)",
            "GuardianApproved(bytes32,address)",
            "GuardianVetoed(bytes32,address)",
            "GuardianVetoRescinded(bytes32,address)",
            "GuardianApproveRescinded(bytes32,address)",
            "GuardianStateReset(bytes32)",
            "PendingRelease(bytes32,uint256)",
            "Released(bytes32)",
            "Revoked(bytes32)",
            "PackageRescued(bytes32)"
        );

        long uniqueCount = sigs.stream()
            .map(EventEncoder::buildEventSignature)
            .distinct()
            .count();

        assertThat(uniqueCount).isEqualTo(13);
    }

    // ── Helper ─────────────────────────────────────────────────────────────

    private Log buildLog(String topic0, String topic1, String data) {
        Log log = new Log();
        log.setTopics(Arrays.asList(topic0, topic1));
        log.setData(data);
        log.setBlockNumber("0x64"); // 100
        log.setBlockHash("0x" + "cc".repeat(32));
        log.setTransactionHash("0x" + "dd".repeat(32));
        log.setLogIndex("0x0");
        return log;
    }
}
