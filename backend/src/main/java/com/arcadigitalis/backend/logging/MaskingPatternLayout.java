package com.arcadigitalis.backend.logging;

import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Custom Logback layout that masks sensitive fields in log output.
 * Implements FR-035: Field-level scrubbing at framework level.
 */
public class MaskingPatternLayout extends PatternLayout {

    private static final Pattern[] SENSITIVE_PATTERNS = {
        Pattern.compile("(\"key\"\\s*:\\s*\")([^\"]+)(\")", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(\"secret\"\\s*:\\s*\")([^\"]+)(\")", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(\"password\"\\s*:\\s*\")([^\"]+)(\")", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(\"seed\"\\s*:\\s*\")([^\"]+)(\")", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(\"dek\"\\s*:\\s*\")([^\"]+)(\")", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(\"private\"\\s*:\\s*\")([^\"]+)(\")", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(privateKey[\"']?\\s*[:=]\\s*[\"']?)([^\"',\\s}]+)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(encryptedKey[\"']?\\s*[:=]\\s*[\"']?)([^\"',\\s}]+)", Pattern.CASE_INSENSITIVE)
    };

    private static final String MASK = "***REDACTED***";

    @Override
    public String doLayout(ILoggingEvent event) {
        String message = super.doLayout(event);
        return maskSensitiveData(message);
    }

    private String maskSensitiveData(String message) {
        String masked = message;
        for (Pattern pattern : SENSITIVE_PATTERNS) {
            Matcher matcher = pattern.matcher(masked);
            masked = matcher.replaceAll("$1" + MASK + "$3");
        }
        return masked;
    }
}
