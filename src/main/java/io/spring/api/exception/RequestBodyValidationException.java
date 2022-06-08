package io.spring.api.exception;

import java.util.List;

/** A request body failed bean validation; answered with 422 and the field errors. */
@SuppressWarnings("serial")
public class RequestBodyValidationException extends RuntimeException {
  private final List<FieldErrorResource> fieldErrors;

  public RequestBodyValidationException(List<FieldErrorResource> fieldErrors) {
    super("Validation failed for request body");
    this.fieldErrors = fieldErrors;
  }

  public List<FieldErrorResource> getFieldErrors() {
    return fieldErrors;
  }
}
