package io.spring.graphql.exception;

public class MissingHeaderException extends RuntimeException {
  public MissingHeaderException(String header) {
    super("Required header '" + header + "' was not provided");
  }
}
