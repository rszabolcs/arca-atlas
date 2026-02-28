package com.arcadigitalis.backend.notifications;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * HTTP POST webhook delivery adapter for notifications.
 */
@Component
public class WebhookDelivery {

    private static final Logger log = LoggerFactory.getLogger(WebhookDelivery.class);

    @Value("${arca.notifications.webhook.timeout-seconds:10}")
    private int timeoutSeconds;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Posts event data as JSON to the webhook URL.
     * @throws DeliveryException on 4xx/5xx or timeout
     */
    public void post(String webhookUrl, String packageKey, String eventType, Map<String, Object> eventData) {
        try {
            Map<String, Object> payload = Map.of(
                "packageKey", packageKey,
                "eventType", eventType,
                "data", eventData != null ? eventData : Map.of()
            );

            String body = objectMapper.writeValueAsString(payload);

            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(webhookUrl))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new DeliveryException("Webhook returned " + response.statusCode() + ": " + response.body());
            }

            log.debug("Webhook delivered to {} for event {} on package {}", webhookUrl, eventType, packageKey);
        } catch (DeliveryException e) {
            throw e;
        } catch (Exception e) {
            throw new DeliveryException("Webhook delivery failed to " + webhookUrl + ": " + e.getMessage(), e);
        }
    }
}
