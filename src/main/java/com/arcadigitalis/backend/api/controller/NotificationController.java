package com.arcadigitalis.backend.api.controller;

import com.arcadigitalis.backend.api.dto.NotificationTargetRequest;
import com.arcadigitalis.backend.api.dto.NotificationTargetResponse;
import com.arcadigitalis.backend.api.dto.NotificationTargetUpdateRequest;
import com.arcadigitalis.backend.api.exception.ValidationException;
import com.arcadigitalis.backend.notifications.NotificationSubscriptionService;
import com.arcadigitalis.backend.notifications.NotificationSubscriptionService.Subscription;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Notification subscription CRUD endpoints (FR-031).
 */
@RestController
@RequestMapping("/notifications/subscriptions")
@Tag(name = "Notifications", description = "Notification subscription management")
public class NotificationController {

    private final NotificationSubscriptionService subscriptionService;

    public NotificationController(NotificationSubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @PostMapping
    @Operation(summary = "Create notification subscription", operationId = "createSubscription")
    @ApiResponses({@ApiResponse(responseCode = "201", description = "Subscription created"),
                   @ApiResponse(responseCode = "400", description = "Invalid channel"),
                   @ApiResponse(responseCode = "403", description = "Not owner or beneficiary")})
    public ResponseEntity<NotificationTargetResponse> create(
            @RequestBody NotificationTargetRequest request,
            Authentication auth) {
        validateChannelType(request.channelType());

        Subscription sub = subscriptionService.create(
            request.packageKey(), auth.getName(),
            request.eventTypes(), request.channelType(), request.channelValue()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(sub));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update notification subscription", operationId = "updateSubscription")
    public ResponseEntity<NotificationTargetResponse> update(
            @PathVariable UUID id,
            @RequestBody NotificationTargetUpdateRequest request,
            Authentication auth) {
        return subscriptionService.update(id, auth.getName(),
                request.eventTypes(), request.channelValue(), request.active())
            .map(sub -> ResponseEntity.ok(toResponse(sub)))
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete notification subscription", operationId = "deleteSubscription")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "Subscription deleted"),
                   @ApiResponse(responseCode = "403", description = "Not subscription owner"),
                   @ApiResponse(responseCode = "404", description = "Subscription not found")})
    public ResponseEntity<Void> delete(@PathVariable UUID id, Authentication auth) {
        boolean deleted = subscriptionService.delete(id, auth.getName());
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    private NotificationTargetResponse toResponse(Subscription sub) {
        return new NotificationTargetResponse(
            sub.id(), sub.chainId(), sub.proxyAddress(), sub.packageKey(),
            sub.subscriberAddress(), sub.eventTypes(), sub.channelType(),
            sub.channelValue(), sub.active(), sub.createdAt(),
            sub.lastDeliveryAttempt(), sub.lastDeliveryStatus()
        );
    }

    private void validateChannelType(String channelType) {
        if (channelType == null || (!channelType.equals("email") && !channelType.equals("webhook") && !channelType.equals("push"))) {
            throw new ValidationException("channelType must be 'email', 'webhook', or 'push'");
        }
    }
}
