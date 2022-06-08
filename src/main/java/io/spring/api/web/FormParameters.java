package io.spring.api.web;

import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Form ({@code application/x-www-form-urlencoded}) request handling. Form parameters from the query
 * string and a {@code POST} body are merged; a raw body read from a form {@code POST} is the
 * re-encoded parameter map, and form bodies of {@code PUT}, {@code PATCH} and {@code DELETE}
 * requests are consumed as parameters and read as empty.
 */
public final class FormParameters {
  private static final String FORM = "application/x-www-form-urlencoded";
  private static final Set<HttpMethod> PARSED_BODY_METHODS =
      Set.of(HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE);

  private FormParameters() {}

  /** The body as seen by a reader of the raw request body. */
  public static byte[] rawBody(HttpRequest<?> request, byte[] body) {
    String contentType = request.getHeaders().get(HttpHeaders.CONTENT_TYPE);
    if (contentType == null) {
      return body;
    }
    if (request.getMethod() == HttpMethod.POST && contentType.contains(FORM)) {
      return encode(parameters(request, body));
    }
    if (PARSED_BODY_METHODS.contains(request.getMethod()) && isForm(contentType)) {
      return new byte[0];
    }
    return body;
  }

  private static boolean isForm(String contentType) {
    try {
      return MediaRange.parse(FORM).covers(MediaRange.parse(contentType));
    } catch (IllegalArgumentException e) {
      // A Content-Type that does not parse is not a form; the body reader reports it as such.
      return false;
    }
  }

  /** The request parameters, query string first, then a form {@code POST} body. */
  static Map<String, List<String>> parameters(HttpRequest<?> request, byte[] body) {
    Map<String, List<String>> parameters = new LinkedHashMap<>();
    String rawQuery = request.getUri().getRawQuery();
    if (rawQuery != null) {
      parse(rawQuery, parameters);
    }
    String contentType = request.getHeaders().get(HttpHeaders.CONTENT_TYPE);
    if (request.getMethod() == HttpMethod.POST
        && contentType != null
        && FORM.equalsIgnoreCase(baseType(contentType))
        && body != null) {
      parse(new String(body, StandardCharsets.ISO_8859_1), parameters);
    }
    return parameters;
  }

  private static String baseType(String contentType) {
    int semicolon = contentType.indexOf(';');
    return (semicolon < 0 ? contentType : contentType.substring(0, semicolon)).trim();
  }

  private static void parse(String encoded, Map<String, List<String>> into) {
    for (String pair : encoded.split("&")) {
      if (pair.isEmpty()) {
        continue;
      }
      int equals = pair.indexOf('=');
      String name = equals < 0 ? pair : pair.substring(0, equals);
      String value = equals < 0 ? "" : pair.substring(equals + 1);
      if (name.isEmpty()) {
        continue;
      }
      String decodedName = decode(name);
      String decodedValue = decode(value);
      if (decodedName == null || decodedValue == null) {
        continue;
      }
      into.computeIfAbsent(decodedName, n -> new ArrayList<>()).add(decodedValue);
    }
  }

  /** Percent-decodes {@code s} as UTF-8 with {@code +} as space, or null if it is malformed. */
  private static String decode(String s) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == '+') {
        out.write(' ');
      } else if (c == '%') {
        if (i + 2 >= s.length()) {
          return null;
        }
        int hi = Character.digit(s.charAt(i + 1), 16);
        int lo = Character.digit(s.charAt(i + 2), 16);
        if (hi < 0 || lo < 0) {
          return null;
        }
        out.write((hi << 4) + lo);
        i += 2;
      } else {
        out.write((byte) c);
      }
    }
    return out.toString(StandardCharsets.UTF_8);
  }

  private static byte[] encode(Map<String, List<String>> parameters) {
    StringBuilder body = new StringBuilder();
    for (Map.Entry<String, List<String>> entry : parameters.entrySet()) {
      for (String value : entry.getValue()) {
        if (body.length() > 0) {
          body.append('&');
        }
        body.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
        body.append('=').append(URLEncoder.encode(value, StandardCharsets.UTF_8));
      }
    }
    return body.toString().getBytes(StandardCharsets.UTF_8);
  }
}
