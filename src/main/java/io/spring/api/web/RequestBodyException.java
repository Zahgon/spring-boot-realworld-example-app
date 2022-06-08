package io.spring.api.web;

import java.util.List;

/** A request body that could not be turned into the expected parameter object. */
public abstract class RequestBodyException extends RuntimeException {

  RequestBodyException(String message, Throwable cause) {
    super(message, cause);
  }

  /** The body is absent or empty. */
  public static class Missing extends RequestBodyException {
    Missing() {
      super("Required request body is missing", null);
    }
  }

  /** The body is not valid JSON for the expected type. */
  public static class Unreadable extends RequestBodyException {
    Unreadable(Throwable cause) {
      super("JSON parse error: " + cause.getMessage(), cause);
    }
  }

  /** The {@code Content-Type} of the body is not supported. */
  public static class UnsupportedMediaType extends RequestBodyException {
    private final List<MediaRange> supported;

    public UnsupportedMediaType(List<MediaRange> supported) {
      super("Content type not supported", null);
      this.supported = supported;
    }

    public List<MediaRange> getSupported() {
      return supported;
    }
  }
}
