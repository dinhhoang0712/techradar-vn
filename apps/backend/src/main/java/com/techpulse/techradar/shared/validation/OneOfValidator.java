package com.techpulse.techradar.shared.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public class OneOfValidator implements ConstraintValidator<OneOf, String> {

    private Set<String> allowed;
    private boolean allowBlank;

    @Override
    public void initialize(OneOf annotation) {
        this.allowed = Arrays.stream(annotation.value().getEnumConstants())
                .map(Enum::name)
                .collect(Collectors.toSet());
        this.allowBlank = annotation.allowBlank();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (!StringUtils.hasText(value)) {
            return allowBlank;
        }
        return allowed.contains(value);
    }
}
