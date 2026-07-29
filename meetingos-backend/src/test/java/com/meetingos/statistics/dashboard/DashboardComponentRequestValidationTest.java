package com.meetingos.statistics.dashboard;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardComponentRequestValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidatorFactory() {
        validatorFactory.close();
    }

    @Test
    void acceptsConfiguredDisplayLimitsFromOneToOneHundred() {
        assertThat(validator.validate(component(1))).isEmpty();
        assertThat(validator.validate(component(100))).isEmpty();
        assertThat(validator.validate(component(null))).isEmpty();
    }

    @Test
    void rejectsDisplayLimitsOutsideConfiguredRange() {
        assertThat(validator.validate(component(0)))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("limit");
        assertThat(validator.validate(component(101)))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("limit");
    }

    @Test
    void defaultsLegacyComponentsToHalfWidthAndAcceptsFullWidth() {
        assertThat(component(10).width()).isEqualTo(DashboardComponentWidth.HALF);
        assertThat(validator.validate(new DashboardComponentRequest(
                "inviter-wide",
                "邀请人分布",
                "tduck:inviter",
                DashboardAggregation.DISTRIBUTION,
                DashboardVisualization.TABLE,
                10,
                List.of(),
                DashboardComponentWidth.FULL
        ))).isEmpty();
    }

    private DashboardComponentRequest component(Integer limit) {
        return new DashboardComponentRequest(
                "inviter",
                "邀请人分布",
                "tduck:inviter",
                DashboardAggregation.DISTRIBUTION,
                DashboardVisualization.TABLE,
                limit,
                List.of()
        );
    }
}
