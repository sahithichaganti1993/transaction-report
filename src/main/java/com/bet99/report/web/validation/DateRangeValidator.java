package com.bet99.report.web.validation;

import com.bet99.report.web.ReportCriteria;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class DateRangeValidator implements ConstraintValidator<ValidDateRange, ReportCriteria> {
    @Override
    public boolean isValid(ReportCriteria criteria, ConstraintValidatorContext context) {
        if (criteria == null || criteria.getStartDateTime() == null || criteria.getEndDateTime() == null) {
            // @NotNull on the individual fields already reports the missing range.
            return true;
        }
        boolean valid = !criteria.getStartDateTime().isAfter(criteria.getEndDateTime());
        if (!valid) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                    .addPropertyNode("endDateTime")
                    .addConstraintViolation();
        }
        return valid;
    }
}
