package com.arcadigitalis.backend.notifications;

import com.arcadigitalis.backend.persistence.entity.EventRecordEntity;
import com.arcadigitalis.backend.persistence.entity.NotificationTargetEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class WebhookDelivery {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void post(NotificationTargetEntity target, EventRecordEntity event) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("eventType", event.getEventType());
            payload.put("packageKey", event.getPackageKey());
            payload.put("chainId", event.getChainId());
            payload.put("proxyAddress", event.getProxyAddress());
            payload.put("blockNumber", event.getBlockNumber());
            payload.put("txHash", event.getTxHash());
            payload.put("timestamp", event.getBlockTimestamp());
            payload.put("data", event.getRawData());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            restTemplate.postForEntity(target.getChannelValue(), request, String.class);

        } catch (Exception e) {
            throw new NotificationDispatcher.DeliveryException("Webhook delivery failed", e);
        }
    }
}
