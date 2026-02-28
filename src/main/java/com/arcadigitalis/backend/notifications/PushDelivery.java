package com.arcadigitalis.backend.notifications;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * FCM/APNs push notification adapter.
 */
@Component
public class PushDelivery {

    private static final Logger log = LoggerFactory.getLogger(PushDelivery.class);

    /**
     * Sends a push notification to the device token.
     * @throws DeliveryException on failure
     */
    public void send(String deviceToken, String packageKey, String eventType, Map<String, Object> eventData) {
        // Placeholder — FCM/APNs integration to be configured via env
        // For MVP, log and skip
        log.info("Push notification requested for token={} event={} package={} (not implemented in MVP)",
            truncateToken(deviceToken), eventType, truncateKey(packageKey));
        throw new DeliveryException("Push delivery not yet implemented");
    }

    private static String truncateToken(String token) {
        if (token == null || token.length() <= 8) return token;
        return token.substring(0, 8) + "...";
    }

    private static String truncateKey(String key) {
        if (key == null || key.length() <= 10) return key;
        return key.substring(0, 10) + "...";
    }
}
