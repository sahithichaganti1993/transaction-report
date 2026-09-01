package com.bet99.report.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class ReportCriteriaValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    private ReportCriteria criteria(LocalDateTime start, LocalDateTime end) {
        ReportCriteria c = new ReportCriteria();
        c.setStartDateTime(start);
        c.setEndDateTime(end);
        return c;
    }

    @Test
    @DisplayName("a range where start is before end is valid")
    void validRangePasses() {
        Set<ConstraintViolation<ReportCriteria>> violations = validator.validate(
                criteria(LocalDateTime.of(2025, 7, 1, 0, 0), LocalDateTime.of(2025, 7, 31, 0, 0)));

        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("start after end is rejected, and the error lands on endDateTime")
    void startAfterEndIsRejected() {
        Set<ConstraintViolation<ReportCriteria>> violations = validator.validate(
                criteria(LocalDateTime.of(2025, 7, 31, 0, 0), LocalDateTime.of(2025, 7, 1, 0, 0)));

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath())
                .hasToString("endDateTime");
    }

    @Test
    @DisplayName("equal start and end is allowed")
    void equalDatesAreAllowed() {
        LocalDateTime same = LocalDateTime.of(2025, 7, 1, 12, 0);

        assertThat(validator.validate(criteria(same, same))).isEmpty();
    }

    @Test
    @DisplayName("a missing date reports only @NotNull, not the range error")
    void missingDateReportsOnlyNotNull() {
        Set<ConstraintViolation<ReportCriteria>> violations =
                validator.validate(criteria(null, LocalDateTime.of(2025, 7, 1, 0, 0)));

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath())
                .hasToString("startDateTime");
    }
}