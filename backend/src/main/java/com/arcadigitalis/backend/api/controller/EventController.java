package com.arcadigitalis.backend.api.controller;

import com.arcadigitalis.backend.api.dto.EventRecordResponse;
import com.arcadigitalis.backend.persistence.repository.EventRecordRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/events")
public class EventController {

    private final EventRecordRepository eventRecordRepository;

    public EventController(EventRecordRepository eventRecordRepository) {
        this.eventRecordRepository = eventRecordRepository;
    }

    @GetMapping
    public Page<EventRecordResponse> getEvents(
        @RequestParam(required = false) String packageKey,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);

        // TODO: Add filtering by owner/guardian/beneficiary/timestamp
        // For now, simple pagination by package key

        return eventRecordRepository.findAll(pageable)
            .map(entity -> new EventRecordResponse(
                entity.getId(),
                entity.getEventType(),
                entity.getPackageKey(),
                entity.getBlockNumber(),
                entity.getBlockTimestamp(),
                entity.getTxHash(),
                entity.getRawData()
            ));
    }
}
