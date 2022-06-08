package io.spring.application;

import jakarta.inject.Singleton;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import jakarta.validation.executable.ExecutableValidator;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;

/**
 * Checks the constrained ({@code @Valid}, ...) arguments of an application service method with Bean
 * Validation before the method changes anything. A violation raises {@link
 * ConstraintViolationException}, whose property paths start with the method and parameter names.
 */
@Singleton
public class ServiceArgumentValidator {
  private final ExecutableValidator validator;

  public ServiceArgumentValidator(Validator validator) {
    this.validator = validator.forExecutables();
  }

  /** Validates {@code arguments} against the parameters of the service's public method. */
  public void validate(Object service, String methodName, Object... arguments) {
    Method method =
        Arrays.stream(service.getClass().getMethods())
            .filter(m -> m.getName().equals(methodName))
            .filter(m -> m.getParameterCount() == arguments.length)
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        service.getClass().getName() + " has no method " + methodName));
    Set<ConstraintViolation<Object>> violations =
        validator.validateParameters(service, method, arguments);
    if (!violations.isEmpty()) {
      throw new ConstraintViolationException(violations);
    }
  }
}
