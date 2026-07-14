package com.meetingos.user;

import com.meetingos.security.Role;
import com.meetingos.security.UserStatus;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class UserPasswordRequestValidationTest {

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
    void createRequestAcceptsSixCharacterPassword() {
        var request = new CreateUserRequest(
                "operator",
                "操作员",
                "A1b2c3",
                UserStatus.ACTIVE,
                Set.of(Role.VIEWER)
        );

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void createRequestRejectsFiveCharacterPassword() {
        var request = new CreateUserRequest(
                "operator",
                "操作员",
                "A1b2c",
                UserStatus.ACTIVE,
                Set.of(Role.VIEWER)
        );

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("password");
    }

    @Test
    void resetPasswordRequestAcceptsSixCharacterPassword() {
        assertThat(validator.validate(new ResetPasswordRequest("A1b2c3"))).isEmpty();
    }

    @Test
    void resetPasswordRequestRejectsFiveCharacterPassword() {
        assertThat(validator.validate(new ResetPasswordRequest("A1b2c")))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("password");
    }
}
