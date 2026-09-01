package com.bet99.report.web.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/** Class-level constraint enforcing "start &lt;= end" on the search form. */
@Documented
@Constraint(validatedBy = DateRangeValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidDateRange {

    String message() default "Start date/time must be earlier than or equal to end date/time";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
