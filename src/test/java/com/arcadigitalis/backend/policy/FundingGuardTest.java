package com.arcadigitalis.backend.policy;

import com.arcadigitalis.backend.api.exception.ValidationException;
import com.arcadigitalis.backend.evm.Web3jConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for FundingGuard — complementary to TxPayloadServiceTest.
 */
@ExtendWith(MockitoExtension.class)
class FundingGuardTest {

    @Mock private Web3jConfig config;

    @Test
    @DisplayName("No exception when funding is enabled")
    void fundingEnabled_noException() {
        when(config.isFundingEnabled()).thenReturn(true);
        FundingGuard guard = new FundingGuard(config);
        guard.assertFundingAllowed("0x1000"); // Should not throw
    }

    @Test
    @DisplayName("No exception when ETH value is zero even if funding disabled")
    void fundingDisabled_zeroEth_noException() {
        when(config.isFundingEnabled()).thenReturn(false);
        FundingGuard guard = new FundingGuard(config);
        guard.assertFundingAllowed("0x0"); // Should not throw
    }

    @Test
    @DisplayName("Throws when funding disabled and ETH > 0")
    void fundingDisabled_nonZeroEth_throws() {
        when(config.isFundingEnabled()).thenReturn(false);
        FundingGuard guard = new FundingGuard(config);
        assertThatThrownBy(() -> guard.assertFundingAllowed("0x1000"))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("FundingDisabled");
    }

    @Test
    @DisplayName("No exception when ETH value is null")
    void fundingDisabled_nullEth_noException() {
        when(config.isFundingEnabled()).thenReturn(false);
        FundingGuard guard = new FundingGuard(config);
        guard.assertFundingAllowed(null); // Should not throw
    }
}
