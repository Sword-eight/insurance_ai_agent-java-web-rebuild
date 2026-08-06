package com.insurance.platform.common.trace;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TraceIdContextTests {

    @Test
    void validationMatchesFrozenHeaderContract() {
        assertThat(TraceIdContext.isValid("a".repeat(16))).isTrue();
        assertThat(TraceIdContext.isValid("A0_-".repeat(16))).isTrue();
        assertThat(TraceIdContext.isValid("a".repeat(15))).isFalse();
        assertThat(TraceIdContext.isValid("a".repeat(65))).isFalse();
        assertThat(TraceIdContext.isValid("trace id with space")).isFalse();
        assertThat(TraceIdContext.isValid(null)).isFalse();
    }

    @Test
    void normalizationPreservesValidValueAndReplacesInvalidValue() {
        String valid = "01J4EXAMPLETRACE01";

        assertThat(TraceIdContext.normalizeOrGenerate(valid)).isEqualTo(valid);
        assertThat(TraceIdContext.normalizeOrGenerate("invalid trace"))
                .matches("[A-Za-z0-9_-]{16,64}")
                .isNotEqualTo("invalid trace");
    }
}
