package io.spring.api.web;

import io.micronaut.http.HttpRequest;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Query parameter access with the API's conversion rules: repeated parameters are joined with
 * {@code ,} for text and use their first value for numbers; numbers ignore whitespace and accept
 * {@code 0x}/{@code #} hexadecimal notation; empty values fall back to the default. Only {@code &}
 * separates parameters; names and values are percent-decoded as UTF-8 with {@code +} as a space,
 * and a parameter that does not decode is ignored.
 */
public final class QueryParameters {
  private QueryParameters() {}

  public static String text(HttpRequest<?> request, String name) {
    List<String> values = values(request, name);
    if (values.isEmpty()) {
      return null;
    }
    return String.join(",", values);
  }

  public static int integer(HttpRequest<?> request, String name, int defaultValue) {
    List<String> values = values(request, name);
    if (values.isEmpty()) {
      return defaultValue;
    }
    String value = values.get(0);
    if (values.size() == 1 && value.isEmpty()) {
      return defaultValue;
    }
    String trimmed = trimAllWhitespace(value);
    try {
      return isHexNumber(trimmed) ? Integer.decode(trimmed) : Integer.valueOf(trimmed);
    } catch (NumberFormatException e) {
      throw new InvalidParameterException(name, value, e);
    }
  }

  private static List<String> values(HttpRequest<?> request, String name) {
    List<String> values = new ArrayList<>();
    String query = request.getUri().getRawQuery();
    if (query == null || query.isEmpty()) {
      return values;
    }
    for (String pair : query.split("&")) {
      int equals = pair.indexOf('=');
      String key = decode(equals < 0 ? pair : pair.substring(0, equals));
      String value = equals < 0 ? "" : decode(pair.substring(equals + 1));
      if (name.equals(key) && value != null) {
        values.add(value);
      }
    }
    return values;
  }

  private static String decode(String encoded) {
    ByteArrayOutputStream out = new ByteArrayOutputStream(encoded.length());
    for (int i = 0; i < encoded.length(); i++) {
      char c = encoded.charAt(i);
      if (c == '+') {
        out.write(' ');
      } else if (c == '%') {
        if (i + 2 >= encoded.length()) {
          return null;
        }
        int high = Character.digit(encoded.charAt(i + 1), 16);
        int low = Character.digit(encoded.charAt(i + 2), 16);
        if (high < 0 || low < 0) {
          return null;
        }
        out.write((high << 4) | low);
        i += 2;
      } else {
        byte[] bytes = String.valueOf(c).getBytes(StandardCharsets.UTF_8);
        out.write(bytes, 0, bytes.length);
      }
    }
    return out.toString(StandardCharsets.UTF_8);
  }

  private static boolean isHexNumber(String value) {
    int index = value.startsWith("-") ? 1 : 0;
    return value.startsWith("0x", index)
        || value.startsWith("0X", index)
        || value.startsWith("#", index);
  }

  private static String trimAllWhitespace(String value) {
    StringBuilder builder = new StringBuilder(value.length());
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (!Character.isWhitespace(c)) {
        builder.append(c);
      }
    }
    return builder.toString();
  }

  /** A query parameter value that cannot be converted to the declared type. */
  public static class InvalidParameterException extends RuntimeException {
    InvalidParameterException(String name, String value, Throwable cause) {
      super("Failed to convert value '" + value + "' of parameter '" + name + "'", cause);
    }
  }
}
