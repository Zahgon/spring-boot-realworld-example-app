package io.spring.api.security;

import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cross-origin policy of the API: any origin, the methods {@code HEAD, GET, POST, PUT, DELETE,
 * PATCH} and the request headers {@code Authorization, Cache-Control, Content-Type}; no
 * credentials, no exposed headers and no max-age.
 */
@Singleton
public class CorsProcessor {
  static final String ALL = "*";
  static final List<String> ALLOWED_METHODS =
      List.of("HEAD", "GET", "POST", "PUT", "DELETE", "PATCH");
  static final List<String> ALLOWED_HEADERS =
      List.of("Authorization", "Cache-Control", "Content-Type");
  static final List<String> KNOWN_METHODS =
      List.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "TRACE");

  private static final Pattern ORIGIN_PATTERN =
      Pattern.compile("^(?:([^:/?#]+):)?(?://(\\[[^]]*]|[^/?#:]*)(?::(\\d*))?)?.*$");

  /** The outcome of applying the policy to a request. */
  public static final class Decision {
    private final boolean corsRequest;
    private final boolean preflight;
    private final boolean rejected;
    private final List<String> allowMethods;
    private final List<String> allowHeaders;

    private Decision(
        boolean corsRequest,
        boolean preflight,
        boolean rejected,
        List<String> allowMethods,
        List<String> allowHeaders) {
      this.corsRequest = corsRequest;
      this.preflight = preflight;
      this.rejected = rejected;
      this.allowMethods = allowMethods;
      this.allowHeaders = allowHeaders;
    }

    static Decision notCors() {
      return new Decision(false, false, false, List.of(), List.of());
    }

    static Decision reject(boolean preflight) {
      return new Decision(true, preflight, true, List.of(), List.of());
    }

    static Decision accept(boolean preflight, List<String> methods, List<String> headers) {
      return new Decision(true, preflight, false, methods, headers);
    }

    public boolean isCorsRequest() {
      return corsRequest;
    }

    public boolean isPreflight() {
      return preflight;
    }

    public boolean isRejected() {
      return rejected;
    }

    public List<String> getAllowMethods() {
      return allowMethods;
    }

    public List<String> getAllowHeaders() {
      return allowHeaders;
    }
  }

  public Decision process(HttpRequest<?> request) {
    HttpHeaders headers = request.getHeaders();
    String origin = headers.get(HttpHeaders.ORIGIN);
    if (origin == null || !isCrossOrigin(request, origin)) {
      return Decision.notCors();
    }
    boolean preflight = isPreflight(request);
    if (origin.isBlank()) {
      return Decision.reject(preflight);
    }
    String requestMethod =
        preflight
            ? headers.get(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD)
            : request.getMethodName();
    if (requestMethod == null
        || !KNOWN_METHODS.contains(requestMethod)
        || !ALLOWED_METHODS.contains(requestMethod)) {
      return Decision.reject(preflight);
    }
    List<String> requestHeaders =
        preflight ? requestedHeaders(headers) : new ArrayList<>(headers.names());
    List<String> allowHeaders = checkHeaders(requestHeaders);
    if (preflight && allowHeaders == null) {
      return Decision.reject(true);
    }
    return Decision.accept(
        preflight, ALLOWED_METHODS, allowHeaders == null ? List.of() : allowHeaders);
  }

  public static boolean isPreflight(HttpRequest<?> request) {
    HttpHeaders headers = request.getHeaders();
    return HttpMethod.OPTIONS.name().equals(request.getMethodName())
        && headers.contains(HttpHeaders.ORIGIN)
        && headers.contains(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD);
  }

  private static List<String> requestedHeaders(HttpHeaders headers) {
    List<String> result = new ArrayList<>();
    for (String value : headers.getAll(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS)) {
      if (value == null) {
        continue;
      }
      Arrays.stream(value.split(","))
          .map(String::trim)
          .filter(token -> !token.isEmpty())
          .forEach(result::add);
    }
    return result;
  }

  private static List<String> checkHeaders(List<String> requestHeaders) {
    if (requestHeaders.isEmpty()) {
      return Collections.emptyList();
    }
    List<String> result = new ArrayList<>(requestHeaders.size());
    for (String requestHeader : requestHeaders) {
      if (requestHeader.isBlank()) {
        continue;
      }
      String trimmed = requestHeader.trim();
      for (String allowedHeader : ALLOWED_HEADERS) {
        if (trimmed.equalsIgnoreCase(allowedHeader)) {
          result.add(trimmed);
          break;
        }
      }
    }
    return result.isEmpty() ? null : result;
  }

  private static boolean isCrossOrigin(HttpRequest<?> request, String origin) {
    Matcher matcher = ORIGIN_PATTERN.matcher(origin);
    String originScheme = null;
    String originHost = null;
    int originPort = -1;
    if (matcher.matches()) {
      originScheme = matcher.group(1);
      originHost = matcher.group(2);
      String port = matcher.group(3);
      if (port != null && !port.isEmpty()) {
        try {
          originPort = Integer.parseInt(port);
        } catch (NumberFormatException e) {
          // An Origin whose port is not a number cannot be this server: treated as cross-origin.
          return true;
        }
      }
    }
    String scheme = request.isSecure() ? "https" : "http";
    String hostHeader = request.getHeaders().get(HttpHeaders.HOST);
    String host = hostHeader == null ? "" : hostHeader;
    int port = -1;
    int colon = host.lastIndexOf(':');
    if (colon > host.lastIndexOf(']')) {
      try {
        port = Integer.parseInt(host.substring(colon + 1));
      } catch (NumberFormatException e) {
        // A Host port that is not a number is compared as no port, as the original service does;
        // the request itself is still served.
        port = -1;
      }
      host = host.substring(0, colon);
    }
    return !(Objects.equals(scheme, originScheme)
        && Objects.equals(host, originHost)
        && defaultPort(scheme, port) == defaultPort(originScheme, originPort));
  }

  private static int defaultPort(String scheme, int port) {
    if (port == -1) {
      if ("http".equals(scheme) || "ws".equals(scheme)) {
        return 80;
      } else if ("https".equals(scheme) || "wss".equals(scheme)) {
        return 443;
      }
    }
    return port;
  }
}
