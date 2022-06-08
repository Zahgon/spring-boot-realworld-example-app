package io.spring.api.exception;

import java.util.List;

@SuppressWarnings("serial")
public class InvalidRequestException extends RuntimeException {
  private final List<FieldErrorResource> errors;

  public InvalidRequestException(List<FieldErrorResource> errors) {
    super("");
    this.errors = errors;
  }

  public List<FieldErrorResource> getErrors() {
    return errors;
  }
}
