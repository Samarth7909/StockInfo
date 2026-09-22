package com.indira.opsconsole.detection;

import lombok.Builder;
import lombok.Getter;

/**
 * Summary returned by CaseDetectionService after running all rules for a cut.
 */
@Getter
@Builder
public class DetectionResult {
    private final int casesCreated;
    private final int casesReopened;
    private final int casesMatched;   // existing cases confirmed as MATCHED (no change)
}
