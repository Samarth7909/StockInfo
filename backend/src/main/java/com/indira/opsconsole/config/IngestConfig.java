package com.indira.opsconsole.config;

import com.indira.opsconsole.detection.CaseDetectionService;
import com.indira.opsconsole.ingest.IngestService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;

/**
 * Wires CaseDetectionService into IngestService via setter injection,
 * breaking the IngestService → CaseDetectionService → repositories cycle.
 */
@Configuration
@RequiredArgsConstructor
public class IngestConfig {

    private final IngestService         ingestService;
    private final CaseDetectionService  caseDetectionService;

    @PostConstruct
    void wire() {
        ingestService.setCaseDetectionService(caseDetectionService);
    }
}
