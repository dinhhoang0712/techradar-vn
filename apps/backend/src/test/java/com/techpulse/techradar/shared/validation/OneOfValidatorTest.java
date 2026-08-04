package com.techpulse.techradar.shared.validation;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.annotation.Annotation;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class OneOfValidatorTest {

    private enum Sample { ALPHA, BETA }

    @Mock
    private ConstraintValidatorContext context;

    private OneOfValidator validator;

    @BeforeEach
    void setUp() {
        validator = new OneOfValidator();
    }

    private void initialize(Class<? extends Enum<?>> enumClass, boolean allowBlank) {
        validator.initialize(new OneOf() {
            @Override
            public Class<? extends Enum<?>> value() {
                return enumClass;
            }

            @Override
            public String message() {
                return "must be one of the allowed values";
            }

            @Override
            public boolean allowBlank() {
                return allowBlank;
            }

            @Override
            public Class<?>[] groups() {
                return new Class<?>[0];
            }

            @Override
            public Class<? extends jakarta.validation.Payload>[] payload() {
                return new Class[0];
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return OneOf.class;
            }
        });
    }

    @Test
    void isValid_acceptsAnEnumConstantName() {
        initialize(Sample.class, true);

        assertThat(validator.isValid("ALPHA", context)).isTrue();
        assertThat(validator.isValid("BETA", context)).isTrue();
    }

    @Test
    void isValid_rejectsAValueNotInTheEnum() {
        initialize(Sample.class, true);

        assertThat(validator.isValid("GAMMA", context)).isFalse();
    }

    @Test
    void isValid_isCaseSensitive() {
        initialize(Sample.class, true);

        assertThat(validator.isValid("alpha", context)).isFalse();
    }

    @Test
    void isValid_allowsBlank_whenAllowBlankIsTrue() {
        initialize(Sample.class, true);

        assertThat(validator.isValid(null, context)).isTrue();
        assertThat(validator.isValid("", context)).isTrue();
        assertThat(validator.isValid("   ", context)).isTrue();
    }

    @Test
    void isValid_rejectsBlank_whenAllowBlankIsFalse() {
        initialize(Sample.class, false);

        assertThat(validator.isValid(null, context)).isFalse();
        assertThat(validator.isValid("", context)).isFalse();
    }
}
