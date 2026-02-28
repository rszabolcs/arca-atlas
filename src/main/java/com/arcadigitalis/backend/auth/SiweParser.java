package com.arcadigitalis.backend.auth;

import com.arcadigitalis.backend.api.exception.AuthException;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Line-by-line EIP-4361 (SIWE) message parser. Extracts domain, address, nonce,
 * issuedAt, expirationTime from the structured text format.
 *
 * Message format (EIP-4361):
 * <pre>
 * {domain} wants you to sign in with your Ethereum account:
 * {address}
 *
 * {statement}
 *
 * URI: {uri}
 * Version: {version}
 * Chain ID: {chain-id}
 * Nonce: {nonce}
 * Issued At: {issued-at}
 * Expiration Time: {expiration-time}  (optional)
 * </pre>
 */
public class SiweParser {

    public record SiweMessage(
            String domain,
            String address,
            String statement,
            String uri,
            String version,
            long chainId,
            String nonce,
            Instant issuedAt,
            Instant expirationTime
    ) {}

    private static final Pattern HEADER_PATTERN =
            Pattern.compile("^(.+) wants you to sign in with your Ethereum account:$");
    private static final Pattern ADDRESS_PATTERN =
            Pattern.compile("^(0x[0-9a-fA-F]{40})$");

    public static SiweMessage parse(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) {
            throw new AuthException("SIWE message is empty");
        }

        String[] lines = rawMessage.split("\n");
        int idx = 0;

        // Line 1: "{domain} wants you to sign in with your Ethereum account:"
        if (idx >= lines.length) throw new AuthException("SIWE message too short");
        Matcher headerMatcher = HEADER_PATTERN.matcher(lines[idx].trim());
        if (!headerMatcher.matches()) {
            throw new AuthException("Invalid SIWE header line");
        }
        String domain = headerMatcher.group(1);
        idx++;

        // Line 2: address
        if (idx >= lines.length) throw new AuthException("Missing address in SIWE message");
        String addressLine = lines[idx].trim();
        Matcher addressMatcher = ADDRESS_PATTERN.matcher(addressLine);
        if (!addressMatcher.matches()) {
            throw new AuthException("Invalid Ethereum address in SIWE message");
        }
        String address = addressMatcher.group(1);
        idx++;

        // Blank line separator
        if (idx < lines.length && lines[idx].trim().isEmpty()) idx++;

        // Statement (optional, collect lines until next blank line)
        StringBuilder statementBuilder = new StringBuilder();
        while (idx < lines.length && !lines[idx].trim().isEmpty() && !lines[idx].trim().startsWith("URI:")) {
            if (!statementBuilder.isEmpty()) statementBuilder.append("\n");
            statementBuilder.append(lines[idx].trim());
            idx++;
        }
        String statement = statementBuilder.toString();

        // Skip blank line before fields
        if (idx < lines.length && lines[idx].trim().isEmpty()) idx++;

        // Parse key-value fields
        String uri = null;
        String version = null;
        Long chainId = null;
        String nonce = null;
        Instant issuedAt = null;
        Instant expirationTime = null;

        while (idx < lines.length) {
            String line = lines[idx].trim();
            if (line.isEmpty()) { idx++; continue; }

            if (line.startsWith("URI: ")) {
                uri = line.substring(5);
            } else if (line.startsWith("Version: ")) {
                version = line.substring(9);
            } else if (line.startsWith("Chain ID: ")) {
                try {
                    chainId = Long.parseLong(line.substring(10));
                } catch (NumberFormatException e) {
                    throw new AuthException("Invalid Chain ID in SIWE message");
                }
            } else if (line.startsWith("Nonce: ")) {
                nonce = line.substring(7);
            } else if (line.startsWith("Issued At: ")) {
                issuedAt = parseTimestamp(line.substring(11));
            } else if (line.startsWith("Expiration Time: ")) {
                expirationTime = parseTimestamp(line.substring(17));
            }
            idx++;
        }

        if (nonce == null || nonce.isBlank()) {
            throw new AuthException("Missing nonce in SIWE message");
        }

        return new SiweMessage(domain, address, statement, uri, version,
                chainId != null ? chainId : 0, nonce, issuedAt, expirationTime);
    }

    private static Instant parseTimestamp(String ts) {
        try {
            return ZonedDateTime.parse(ts.trim(), DateTimeFormatter.ISO_DATE_TIME).toInstant();
        } catch (Exception e) {
            throw new AuthException("Invalid timestamp in SIWE message: " + ts);
        }
    }
}
