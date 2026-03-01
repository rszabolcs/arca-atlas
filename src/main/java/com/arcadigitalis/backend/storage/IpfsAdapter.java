package com.arcadigitalis.backend.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;

/**
 * Pins content to an IPFS gateway (Infura/Pinata). Returns an {@code ipfs://} URI.
 */
@Component
public class IpfsAdapter {

    private static final Logger log = LoggerFactory.getLogger(IpfsAdapter.class);

    @Value("${arca.storage.ipfs.enabled:false}")
    private boolean enabled;

    @Value("${arca.storage.ipfs.api-url:https://ipfs.infura.io:5001}")
    private String apiUrl;

    @Value("${arca.storage.ipfs.project-id:}")
    private String projectId;

    @Value("${arca.storage.ipfs.project-secret:}")
    private String projectSecret;

    @Value("${arca.storage.ipfs.timeout-seconds:30}")
    private int timeoutSeconds;

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Pins content bytes to IPFS.
     * @return ipfs:// URI
     * @throws StorageException on failure
     */
    public String pin(byte[] content) {
        if (!enabled) throw new StorageException("IPFS storage is not enabled");

        try {
            String boundary = "----ArcaBoundary" + System.currentTimeMillis();
            byte[] body = buildMultipartBody(boundary, content);

            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl + "/api/v0/add"))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body));

            if (!projectId.isBlank() && !projectSecret.isBlank()) {
                String auth = Base64.getEncoder().encodeToString((projectId + ":" + projectSecret).getBytes());
                requestBuilder.header("Authorization", "Basic " + auth);
            }

            HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new StorageException("IPFS pin failed with status " + response.statusCode() + ": " + response.body());
            }

            // Extract CID from response JSON (Infura returns {"Hash":"Qm...",...})
            String responseBody = response.body();
            String hash = extractJsonField(responseBody, "Hash");
            if (hash == null || hash.isBlank()) {
                throw new StorageException("IPFS response missing Hash field: " + responseBody);
            }

            String uri = "ipfs://" + hash;
            log.info("Pinned content to IPFS: {}", uri);
            return uri;
        } catch (StorageException e) {
            throw e;
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StorageException("IPFS pin failed: " + e.getMessage(), e);
        }
    }

    private byte[] buildMultipartBody(String boundary, byte[] content) {
        String header = "--" + boundary + "\r\n"
            + "Content-Disposition: form-data; name=\"file\"; filename=\"artifact\"\r\n"
            + "Content-Type: application/octet-stream\r\n\r\n";
        String footer = "\r\n--" + boundary + "--\r\n";
        byte[] headerBytes = header.getBytes();
        byte[] footerBytes = footer.getBytes();
        byte[] body = new byte[headerBytes.length + content.length + footerBytes.length];
        System.arraycopy(headerBytes, 0, body, 0, headerBytes.length);
        System.arraycopy(content, 0, body, headerBytes.length, content.length);
        System.arraycopy(footerBytes, 0, body, headerBytes.length + content.length, footerBytes.length);
        return body;
    }

    private static String extractJsonField(String json, String field) {
        // Simple extraction without pulling in a JSON parser for one field
        String key = "\"" + field + "\":\"";
        int start = json.indexOf(key);
        if (start < 0) return null;
        start += key.length();
        int end = json.indexOf("\"", start);
        if (end < 0) return null;
        return json.substring(start, end);
    }
}
