package com.quant.backtest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DataCoverageValidatorTest {

    @Test
    void rejectsIncompleteRangeWhenPartialDataIsNotAllowed() {
        DataCoverageValidator validator = new DataCoverageValidator();

        assertThrows(IllegalArgumentException.class, () -> validator.validate(
                "1m",
                1_767_196_800_000L,
                1_782_230_400_000L,
                1_767_196_800_000L,
                1_780_271_999_999L,
                false
        ));
    }

    @Test
    void reportsMissingBarsWhenPartialDataIsAllowed() {
        DataCoverageValidator validator = new DataCoverageValidator();

        DataCoverageValidator.Coverage coverage = validator.validate(
                "1m",
                1_767_196_800_000L,
                1_782_230_400_000L,
                1_767_196_800_000L,
                1_780_271_999_999L,
                true
        );

        assertFalse(coverage.coverageComplete());
        assertEquals(32_640L, coverage.missingBars());
    }
}
