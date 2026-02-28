package com.arcadigitalis.backend.evm;

import com.arcadigitalis.backend.persistence.repository.EventRecordRepository;
import com.arcadigitalis.backend.persistence.repository.ProcessedBlockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ReorgHandler — T093.
 * Tests: no reorg, detected reorg, first-seen block.
 */
@ExtendWith(MockitoExtension.class)
class ReorgHandlerTest {

    @Mock private ProcessedBlockRepository processedBlockRepository;
    @Mock private EventRecordRepository eventRecordRepository;

    private ReorgHandler handler;

    private static final long CHAIN_ID = 11155111L;
    private static final String PROXY = "0x1234567890abcdef1234567890abcdef12345678";

    @BeforeEach
    void setUp() {
        handler = new ReorgHandler(processedBlockRepository, eventRecordRepository);
    }

    @Test
    @DisplayName("No reorg when block hashes match — no deletions")
    void noReorg_whenHashesMatch() {
        var storedBlock = mock(com.arcadigitalis.backend.persistence.entity.ProcessedBlockEntity.class);
        when(storedBlock.getBlockHash()).thenReturn("0xAABB");
        when(processedBlockRepository.findByChainIdAndProxyAddressAndBlockNumber(CHAIN_ID, PROXY, 100L))
            .thenReturn(Optional.of(storedBlock));

        handler.checkAndHandleReorg(CHAIN_ID, PROXY, 100L, "0xAABB");

        verify(eventRecordRepository, never()).deleteByChainIdAndProxyAddressAndBlockNumberGreaterThan(anyLong(), anyString(), anyLong());
    }

    @Test
    @DisplayName("Reorg detected when block hashes differ — deletes from fork point")
    void reorgDetected_deletesFromForkPoint() {
        var storedBlock = mock(com.arcadigitalis.backend.persistence.entity.ProcessedBlockEntity.class);
        when(storedBlock.getBlockHash()).thenReturn("0xOLDHASH");
        when(processedBlockRepository.findByChainIdAndProxyAddressAndBlockNumber(CHAIN_ID, PROXY, 100L))
            .thenReturn(Optional.of(storedBlock));

        handler.checkAndHandleReorg(CHAIN_ID, PROXY, 100L, "0xNEWHASH");

        // Should delete events and blocks from forkPoint-1 = 99
        verify(eventRecordRepository).deleteByChainIdAndProxyAddressAndBlockNumberGreaterThan(
            eq(CHAIN_ID), eq(PROXY), eq(99L));
        verify(processedBlockRepository).deleteByChainIdAndProxyAddressAndBlockNumberGreaterThan(
            eq(CHAIN_ID), eq(PROXY), eq(99L));
    }

    @Test
    @DisplayName("First-seen block (not in DB) — no reorg check")
    void firstSeenBlock_noAction() {
        when(processedBlockRepository.findByChainIdAndProxyAddressAndBlockNumber(CHAIN_ID, PROXY, 100L))
            .thenReturn(Optional.empty());

        handler.checkAndHandleReorg(CHAIN_ID, PROXY, 100L, "0xANYHASH");

        verify(eventRecordRepository, never()).deleteByChainIdAndProxyAddressAndBlockNumberGreaterThan(anyLong(), anyString(), anyLong());
    }
}
