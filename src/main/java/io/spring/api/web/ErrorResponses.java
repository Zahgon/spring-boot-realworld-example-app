package io.spring.api.web;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the API's error responses. Status errors carry a JSON body with {@code timestamp}, {@code
 * status}, {@code error} and {@code path}.
 */
public final class ErrorResponses {
  /** Response attribute: status to answer with when the body is not acceptable to the client. */
  public static final String NOT_ACCEPTABLE_STATUS =
      ErrorResponses.class.getName() + ".NOT_ACCEPTABLE_STATUS";

  /** Response attribute: the body is a status error that has an HTML rendering. */
  public static final String ERROR_PAGE = ErrorResponses.class.getName() + ".ERROR_PAGE";

  /** Response attribute: the body is always written as {@code application/json}. */
  public static final String PRESET_JSON = ErrorResponses.class.getName() + ".PRESET_JSON";

  private static final DateTimeFormatter TIMESTAMP =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSxxx");

  private ErrorResponses() {}

  public static MutableHttpResponse<Map<String, Object>> error(
      HttpRequest<?> request, HttpStatus status) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("timestamp", ZonedDateTime.now(ZoneId.systemDefault()).format(TIMESTAMP));
    body.put("status", status.getCode());
    body.put("error", reasonPhrase(status));
    body.put("path", RequestPaths.rawPath(request));
    MutableHttpResponse<Map<String, Object>> response =
        HttpResponse.<Map<String, Object>>status(status).body(body);
    response.setAttribute(NOT_ACCEPTABLE_STATUS, status.getCode());
    response.setAttribute(ERROR_PAGE, Boolean.TRUE);
    return response;
  }

  /** A body whose response falls back to {@code status} without a body when not acceptable. */
  public static <T> MutableHttpResponse<T> withFallback(
      MutableHttpResponse<T> response, HttpStatus status) {
    response.setAttribute(NOT_ACCEPTABLE_STATUS, status.getCode());
    return response;
  }

  public static <T> MutableHttpResponse<T> presetJson(MutableHttpResponse<T> response) {
    response.setAttribute(PRESET_JSON, Boolean.TRUE);
    return response;
  }

  static String reasonPhrase(HttpStatus status) {
    switch (status) {
      case BAD_REQUEST:
        return "Bad Request";
      case UNAUTHORIZED:
        return "Unauthorized";
      case FORBIDDEN:
        return "Forbidden";
      case NOT_FOUND:
        return "Not Found";
      case METHOD_NOT_ALLOWED:
        return "Method Not Allowed";
      case NOT_ACCEPTABLE:
        return "Not Acceptable";
      case UNSUPPORTED_MEDIA_TYPE:
        return "Unsupported Media Type";
      case UNPROCESSABLE_ENTITY:
        return "Unprocessable Entity";
      case INTERNAL_SERVER_ERROR:
        return "Internal Server Error";
      default:
        return status.getReason();
    }
  }
}
