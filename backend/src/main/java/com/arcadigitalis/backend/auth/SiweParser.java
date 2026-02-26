package com.arcadigitalis.backend.auth;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manual EIP-4361 SIWE message parser.
 * Parses the structured text format without external dependencies.
 */
public class SiweParser {

    private static final Pattern DOMAIN_PATTERN = Pattern.compile("^([^ ]+) wants you to sign in");
    private static final Pattern ADDRESS_PATTERN = Pattern.compile("URI: (.+)");
    private static final Pattern NONCE_PATTERN = Pattern.compile("Nonce: (.+)");
    private static final Pattern ISSUED_AT_PATTERN = Pattern.compile("Issued At: (.+)");
    private static final Pattern EXPIRATION_PATTERN = Pattern.compile("Expiration Time: (.+)");
    private static final Pattern CHAIN_ID_PATTERN = Pattern.compile("Chain ID: (\\d+)");
    private static final Pattern WALLET_ADDRESS_PATTERN = Pattern.compile("^(0x[a-fA-F0-9]{40})$");

    public static class ParsedSiweMessage {
        public final String domain;
        public final String address;
        public final String nonce;
        public final String uri;
        public final Instant issuedAt;
        public final Instant expirationTime;
        public final Long chainId;

        public ParsedSiweMessage(String domain, String address, String nonce, String uri,
                                  Instant issuedAt, Instant expirationTime, Long chainId) {
            this.domain = domain;
            this.address = address;
            this.nonce = nonce;
            this.uri = uri;
            this.issuedAt = issuedAt;
            this.expirationTime = expirationTime;
            this.chainId = chainId;
        }
    }

    public static class ParseException extends Exception {
        public ParseException(String message) {
            super(message);
        }
    }

    public static ParsedSiweMessage parse(String message) throws ParseException {
        if (message == null || message.isBlank()) {
            throw new ParseException("SIWE message is empty");
        }

        String[] lines = message.split("\\r?\\n");

        String domain = extractDomain(lines);
        String address = extractAddress(lines);
        String nonce = extractField(lines, NONCE_PATTERN, "Nonce");
        String uri = extractField(lines, ADDRESS_PATTERN, "URI");
        Instant issuedAt = extractTimestamp(lines, ISSUED_AT_PATTERN, "Issued At");
        Instant expirationTime = extractTimestamp(lines, EXPIRATION_PATTERN, "Expiration Time");
        Long chainId = extractChainId(lines);

        return new ParsedSiweMessage(domain, address, nonce, uri, issuedAt, expirationTime, chainId);
    }

    private static String extractDomain(String[] lines) throws ParseException {
        if (lines.length < 1) {
            throw new ParseException("Missing domain line");
        }
        Matcher matcher = DOMAIN_PATTERN.matcher(lines[0]);
        if (matcher.find()) {
            return matcher.group(1);
        }
        throw new ParseException("Invalid domain format");
    }

    private static String extractAddress(String[] lines) throws ParseException {
        // Address is on the second line in EIP-4361 format
        if (lines.length < 2) {
            throw new ParseException("Missing address line");
        }
        String addressLine = lines[1].trim();
        Matcher matcher = WALLET_ADDRESS_PATTERN.matcher(addressLine);
        if (matcher.matches()) {
            return addressLine;
        }
        throw new ParseException("Invalid wallet address format");
    }

    private static String extractField(String[] lines, Pattern pattern, String fieldName) throws ParseException {
        for (String line : lines) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        throw new ParseException("Missing field: " + fieldName);
    }

    private static Instant extractTimestamp(String[] lines, Pattern pattern, String fieldName) throws ParseException {
        String timestamp = extractField(lines, pattern, fieldName);
        try {
            return Instant.from(DateTimeFormatter.ISO_INSTANT.parse(timestamp));
        } catch (Exception e) {
            throw new ParseException("Invalid timestamp format for " + fieldName + ": " + timestamp);
        }
    }

    private static Long extractChainId(String[] lines) throws ParseException {
        String chainIdStr = extractField(lines, CHAIN_ID_PATTERN, "Chain ID");
        try {
            return Long.parseLong(chainIdStr);
        } catch (NumberFormatException e) {
            throw new ParseException("Invalid Chain ID: " + chainIdStr);
        }
    }
}
