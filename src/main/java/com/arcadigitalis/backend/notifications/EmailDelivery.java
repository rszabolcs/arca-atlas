package com.arcadigitalis.backend.notifications;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * SMTP email delivery adapter for notifications.
 */
@Component
public class EmailDelivery {

    private static final Logger log = LoggerFactory.getLogger(EmailDelivery.class);

    private final JavaMailSender mailSender;

    @Value("${arca.notifications.email.from:no-reply@arcadigitalis.com}")
    private String fromAddress;

    public EmailDelivery(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * Sends a notification email.
     * @throws DeliveryException on failure
     */
    public void send(String toEmail, String packageKey, String eventType, Map<String, Object> eventData) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(toEmail);
            message.setSubject("[Arca] " + eventType + " — Package " + truncateKey(packageKey));
            message.setText(buildBody(packageKey, eventType, eventData));

            mailSender.send(message);
            log.debug("Email sent to {} for event {} on package {}", toEmail, eventType, packageKey);
        } catch (Exception e) {
            throw new DeliveryException("Email delivery failed to " + toEmail + ": " + e.getMessage(), e);
        }
    }

    private String buildBody(String packageKey, String eventType, Map<String, Object> eventData) {
        StringBuilder sb = new StringBuilder();
        sb.append("Event: ").append(eventType).append("\n");
        sb.append("Package: ").append(packageKey).append("\n\n");
        if (eventData != null && !eventData.isEmpty()) {
            sb.append("Details:\n");
            eventData.forEach((k, v) -> sb.append("  ").append(k).append(": ").append(v).append("\n"));
        }
        return sb.toString();
    }

    private static String truncateKey(String key) {
        if (key == null || key.length() <= 10) return key;
        return key.substring(0, 10) + "...";
    }
}
