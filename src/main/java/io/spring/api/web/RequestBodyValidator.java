package io.spring.api.web;

import io.spring.api.exception.FieldErrorResource;
import io.spring.api.exception.RequestBodyValidationException;
import jakarta.inject.Singleton;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ElementKind;
import jakarta.validation.Path;
import jakarta.validation.Validator;
import java.beans.Introspector;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Bean validation of request bodies; violations become field errors of the body object. */
@Singleton
public class RequestBodyValidator {
  private final Validator validator;

  public RequestBodyValidator(Validator validator) {
    this.validator = validator;
  }

  public <T> T validate(T body) {
    Set<ConstraintViolation<T>> violations = validator.validate(body);
    if (violations.isEmpty()) {
      return body;
    }
    String objectName = Introspector.decapitalize(body.getClass().getSimpleName());
    List<FieldErrorResource> errors = new ArrayList<>();
    for (ConstraintViolation<T> violation : violations) {
      String field = fieldOf(violation.getPropertyPath());
      if (field.isEmpty()) {
        continue;
      }
      errors.add(
          new FieldErrorResource(
              objectName,
              field,
              violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName(),
              violation.getMessage()));
    }
    throw new RequestBodyValidationException(errors);
  }

  private static String fieldOf(Path path) {
    StringBuilder field = new StringBuilder();
    boolean first = true;
    for (Path.Node node : path) {
      if (node.isInIterable()) {
        field.append('[');
        Object index = node.getIndex();
        if (index == null) {
          index = node.getKey();
        }
        if (index != null) {
          field.append(index);
        }
        field.append(']');
      }
      String name = node.getName();
      if (name != null && node.getKind() == ElementKind.PROPERTY && !name.startsWith("<")) {
        if (!first) {
          field.append('.');
        }
        first = false;
        field.append(name);
      }
    }
    return field.toString();
  }
}
