package com.arcadigitalis.backend.auth;

import com.arcadigitalis.backend.api.exception.AuthException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.crypto.ECDSASignature;
import org.web3j.crypto.Hash;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Arrays;

/**
 * Verifies SIWE messages using the SiweParser + Web3j ecrecover.
 * Enforces: domain exact-match, nonce single-use, expiry check, address match.
 */
@Service
public class SiweVerifier {

    private static final Logger log = LoggerFactory.getLogger(SiweVerifier.class);
    private static final String ETH_MESSAGE_PREFIX = "\u0019Ethereum Signed Message:\n";

    private final NonceService nonceService;
    private final String configuredDomain;

    public SiweVerifier(NonceService nonceService,
                        @Value("${arca.auth.siwe-domain}") String configuredDomain) {
        this.nonceService = nonceService;
        this.configuredDomain = configuredDomain;
    }

    /**
     * Verify a SIWE signed message. Returns the recovered wallet address on success.
     *
     * @param messageText raw EIP-4361 message text
     * @param signatureHex 0x-prefixed hex signature (65 bytes: r + s + v)
     * @return the verified wallet address (EIP-55 checksummed)
     * @throws AuthException on any verification failure
     */
    public String verify(String messageText, String signatureHex) {
        // Parse the SIWE message
        SiweParser.SiweMessage parsed = SiweParser.parse(messageText);

        // Domain check (FR-002)
        if (!configuredDomain.equals(parsed.domain())) {
            throw new AuthException("SIWE domain mismatch: expected " + configuredDomain + ", got " + parsed.domain());
        }

        // Expiration check
        if (parsed.expirationTime() != null && Instant.now().isAfter(parsed.expirationTime())) {
            throw new AuthException("SIWE message has expired");
        }

        // Recover signer address from signature
        String recoveredAddress = recoverAddress(messageText, signatureHex);

        // Address match check
        if (!parsed.address().equalsIgnoreCase(recoveredAddress)) {
            throw new AuthException("SIWE address mismatch: claimed " + parsed.address() + ", recovered " + recoveredAddress);
        }

        // Consume nonce (single-use, atomic via NonceService)
        nonceService.consumeNonce(parsed.address(), parsed.nonce());

        log.debug("SIWE verification succeeded for {}", parsed.address());
        return Keys.toChecksumAddress(recoveredAddress);
    }

    /**
     * Recover the signer address from a personal_sign-style Ethereum signature.
     */
    private String recoverAddress(String message, String signatureHex) {
        try {
            byte[] messageBytes = message.getBytes();
            // Ethereum personal sign prefix
            String prefix = ETH_MESSAGE_PREFIX + messageBytes.length;
            byte[] prefixedMessage = new byte[prefix.getBytes().length + messageBytes.length];
            System.arraycopy(prefix.getBytes(), 0, prefixedMessage, 0, prefix.getBytes().length);
            System.arraycopy(messageBytes, 0, prefixedMessage, prefix.getBytes().length, messageBytes.length);
            byte[] messageHash = Hash.sha3(prefixedMessage);

            byte[] sigBytes = Numeric.hexStringToByteArray(signatureHex);
            if (sigBytes.length != 65) {
                throw new AuthException("Invalid signature length: expected 65 bytes");
            }

            byte v = sigBytes[64];
            if (v < 27) v += 27;

            byte[] r = Arrays.copyOfRange(sigBytes, 0, 32);
            byte[] s = Arrays.copyOfRange(sigBytes, 32, 64);

            Sign.SignatureData signatureData = new Sign.SignatureData(v, r, s);

            BigInteger publicKey = Sign.signedMessageHashToKey(messageHash, signatureData);
            return "0x" + Keys.getAddress(publicKey);
        } catch (AuthException e) {
            throw e;
        } catch (Exception e) {
            throw new AuthException("Failed to recover address from signature", e);
        }
    }
}
