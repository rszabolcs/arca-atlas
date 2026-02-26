package com.arcadigitalis.backend.auth;

import com.arcadigitalis.backend.api.exception.AuthException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

import java.nio.charset.StandardCharsets;
import java.security.SignatureException;

@Service
public class SiweVerifier {

    @Value("${arca.siwe.domain}")
    private String expectedDomain;

    @Value("${arca.evm.chain-id}")
    private Long expectedChainId;

    private final NonceService nonceService;

    public SiweVerifier(NonceService nonceService) {
        this.nonceService = nonceService;
    }

    public String verify(String siweMessage, String signature) {
        try {
            // Parse the SIWE message
            SiweParser.ParsedSiweMessage parsed = SiweParser.parse(siweMessage);

            // Validate domain
            if (!expectedDomain.equals(parsed.domain)) {
                throw new AuthException("Domain mismatch: expected " + expectedDomain + ", got " + parsed.domain);
            }

            // Validate chain ID
            if (!expectedChainId.equals(parsed.chainId)) {
                throw new AuthException("Chain ID mismatch: expected " + expectedChainId + ", got " + parsed.chainId);
            }

            // Recover address from signature
            String recoveredAddress = recoverAddress(siweMessage, signature);

            // Validate recovered address matches claimed address
            if (!recoveredAddress.equalsIgnoreCase(parsed.address)) {
                throw new AuthException("Signature verification failed: address mismatch");
            }

            // Consume nonce (throws AuthException if invalid/expired/consumed)
            nonceService.consumeNonce(parsed.address, parsed.nonce);

            return recoveredAddress;

        } catch (SiweParser.ParseException e) {
            throw new AuthException("Invalid SIWE message format: " + e.getMessage());
        } catch (SignatureException e) {
            throw new AuthException("Invalid signature: " + e.getMessage());
        }
    }

    private String recoverAddress(String message, String signature) throws SignatureException {
        // EIP-191 personal sign prefix
        String prefix = "\u0019Ethereum Signed Message:\n" + message.length();
        byte[] msgHash = org.web3j.crypto.Hash.sha3((prefix + message).getBytes(StandardCharsets.UTF_8));

        // Parse signature (remove 0x prefix if present)
        String cleanSig = Numeric.cleanHexPrefix(signature);
        if (cleanSig.length() != 130) {
            throw new SignatureException("Invalid signature length");
        }

        byte[] r = Numeric.hexStringToByteArray(cleanSig.substring(0, 64));
        byte[] s = Numeric.hexStringToByteArray(cleanSig.substring(64, 128));
        byte v = (byte) Integer.parseInt(cleanSig.substring(128, 130), 16);

        // Normalize v (27/28 or 0/1)
        if (v < 27) {
            v += 27;
        }

        Sign.SignatureData signatureData = new Sign.SignatureData(v, r, s);

        // Recover public key
        int recId = v - 27;
        org.web3j.crypto.ECKeyPair keyPair = Sign.recoverFromSignature(recId,
            new org.web3j.crypto.ECDSASignature(
                new java.math.BigInteger(1, r),
                new java.math.BigInteger(1, s)
            ),
            msgHash
        );

        if (keyPair == null) {
            throw new SignatureException("Could not recover public key from signature");
        }

        return "0x" + Keys.getAddress(keyPair.getPublicKey());
    }
}
