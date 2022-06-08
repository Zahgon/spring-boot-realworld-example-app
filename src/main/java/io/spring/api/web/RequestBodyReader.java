package io.spring.api.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import jakarta.inject.Singleton;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

/**
 * Reads JSON request bodies into parameter objects. Only {@code application/json} and {@code
 * application/*+json} are accepted; the root object name is unwrapped by the configured mapper.
 */
@Singleton
public class RequestBodyReader {
  private static final Set<String> UNICODE_CHARSETS =
      Set.of(
          "UTF-8", "UTF-16BE", "UTF-16LE", "UTF-32BE", "UTF-32LE", "US-ASCII", "UTF-16", "UTF-32");
  private static final Set<String> BODY_METHODS = Set.of("POST", "PUT", "PATCH");

  private final ObjectMapper objectMapper;
  private final RequestBodyValidator validator;

  public RequestBodyReader(ObjectMapper objectMapper, RequestBodyValidator validator) {
    this.objectMapper = objectMapper;
    this.validator = validator;
  }

  /** Reads the body like {@link #read} and validates its bean constraints. */
  public <T> T readValid(HttpRequest<?> request, byte[] body, Class<T> type) {
    return validator.validate(read(request, body, type));
  }

  public <T> T read(HttpRequest<?> request, byte[] body, Class<T> type) {
    MediaRange contentType = contentType(request);
    boolean hasBody = body != null && body.length > 0;
    boolean readable =
        contentType != null
            && ContentNegotiation.JSON_TYPES.stream().anyMatch(json -> json.covers(contentType));
    if (!readable) {
      if (contentType == null && !hasBody) {
        throw new RequestBodyException.Missing();
      }
      if (!BODY_METHODS.contains(request.getMethodName())) {
        throw new RequestBodyException.Missing();
      }
      throw new RequestBodyException.UnsupportedMediaType(ContentNegotiation.JSON_TYPES);
    }
    if (!hasBody) {
      throw new RequestBodyException.Missing();
    }
    T value;
    try {
      Charset charset = contentType.charset();
      if (charset == null) {
        charset = StandardCharsets.UTF_8;
      }
      if (UNICODE_CHARSETS.contains(charset.name())) {
        value = objectMapper.readValue(body, type);
      } else {
        value =
            objectMapper.readValue(
                new InputStreamReader(new ByteArrayInputStream(body), charset), type);
      }
    } catch (IOException | RuntimeException e) {
      throw new RequestBodyException.Unreadable(e);
    }
    if (value == null) {
      throw new RequestBodyException.Missing();
    }
    return value;
  }

  /**
   * The parsed {@code Content-Type} of a request whose body is read, or null when absent. An
   * unparseable value is unsupported; a wildcard type or subtype is a server error, as no body can
   * be read in it.
   */
  public static MediaRange contentType(HttpRequest<?> request) {
    String header = request.getHeaders().get(HttpHeaders.CONTENT_TYPE);
    if (header == null || header.isEmpty()) {
      return null;
    }
    MediaRange contentType;
    try {
      contentType = MediaRange.parse(header);
    } catch (IllegalArgumentException e) {
      throw new RequestBodyException.UnsupportedMediaType(List.of());
    }
    if (!contentType.isSpecific()) {
      throw new IllegalStateException("Content-Type cannot contain wildcard type '*'");
    }
    return contentType;
  }
}
