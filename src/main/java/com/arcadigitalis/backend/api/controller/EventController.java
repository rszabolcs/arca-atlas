package com.arcadigitalis.backend.api.controller;

import com.arcadigitalis.backend.api.dto.EventPageResponse;
import com.arcadigitalis.backend.api.dto.EventRecordResponse;
import com.arcadigitalis.backend.api.exception.ValidationException;
import com.arcadigitalis.backend.evm.EventQueryService;
import com.arcadigitalis.backend.evm.EventQueryService.EventPage;
import com.arcadigitalis.backend.evm.EventQueryService.EventRecord;
import com.arcadigitalis.backend.evm.IndexerPoller;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Paginated event index query endpoint (FR-030).
 * No on-chain enumeration — all queries served from indexed DB.
 */
@RestController
@RequestMapping("/events")
@Tag(name = "Events", description = "Paginated indexed event queries")
public class EventController {

    private final EventQueryService eventQueryService;
    private final IndexerPoller indexerPoller;

    public EventController(EventQueryService eventQueryService, IndexerPoller indexerPoller) {
        this.eventQueryService = eventQueryService;
        this.indexerPoller = indexerPoller;
    }

    @GetMapping
    @Operation(summary = "Paginated indexed event query", operationId = "listEvents")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Event page returned"),
                   @ApiResponse(responseCode = "400", description = "Invalid query parameters")})
    public ResponseEntity<EventPageResponse> listEvents(
            @RequestParam long chainId,
            @RequestParam String proxyAddress,
            @RequestParam(required = false) String packageKey,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") int limit) {

        if (limit < 1 || limit > 200) {
            throw new ValidationException("limit must be between 1 and 200");
        }

        int page = 0;
        if (cursor != null && !cursor.isBlank()) {
            try { page = Integer.parseInt(cursor); } catch (NumberFormatException e) {
                throw new ValidationException("Invalid cursor format");
            }
        }

        EventPage result = eventQueryService.query(chainId, proxyAddress, packageKey, page, limit);

        var items = result.items().stream().map(this::toResponse).toList();

        // X-Data-Staleness-Seconds header (T098)
        long stalenessSeconds = computeStalenessSeconds();
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Data-Staleness-Seconds", String.valueOf(stalenessSeconds));

        return ResponseEntity.ok()
            .headers(headers)
            .body(new EventPageResponse(items, result.totalElements(), result.nextCursor()));
    }

    private long computeStalenessSeconds() {
        long lastSync = indexerPoller.getLastSyncTimestamp();
        if (lastSync <= 0) return -1; // Never synced
        return (System.currentTimeMillis() - lastSync) / 1000;
    }

    private EventRecordResponse toResponse(EventRecord record) {
        return new EventRecordResponse(
            record.id(), record.chainId(), record.proxyAddress(),
            record.packageKey(), record.eventType(), record.emittingAddress(),
            record.blockNumber(), record.txHash(), record.logIndex(),
            record.blockTimestamp(), record.data()
        );
    }
}
