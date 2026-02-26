package com.arcadigitalis.backend.policy;

import com.arcadigitalis.backend.api.exception.ValidationException;
import org.springframework.stereotype.Service;

import java.math.BigInteger;

@Service
public class FundingGuard {

    private final boolean fundingEnabled;

    public FundingGuard(boolean fundingEnabled) {
        this.fundingEnabled = fundingEnabled;
    }

    public void assertFundingAllowed(BigInteger requestEthValue) {
        if (!fundingEnabled && requestEthValue != null && requestEthValue.compareTo(BigInteger.ZERO) > 0) {
            throw new ValidationException("FundingDisabled: this instance does not accept ETH-bearing transactions");
        }
    }
}
