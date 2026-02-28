package com.arcadigitalis.backend.api.dto;

import java.util.List;

/**
 * Paginated response for event records.
 */
public record EventPageResponse(
    List<EventRecordResponse> items,
    long total,
    String cursor
) {}
