package io.spring.api.security;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.order.Ordered;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpHeaders;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.spring.api.web.RequestPaths;
import io.spring.api.web.UntypedBody;
import io.spring.core.service.JwtService;
import io.spring.core.user.UserRepository;
import java.util.List;
import java.util.Optional;

/**
 * Front door of every request: applies the CORS policy, authenticates the {@code Authorization}
 * token, enforces {@link WebSecurityConfig} and adds the security response headers.
 */
@ServerFilter(ServerFilter.MATCH_ALL_PATTERN)
public class WebSecurityFilter implements Ordered {
  public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 100;

  private static final String CORS_ALLOW_ORIGIN_ATTRIBUTE =
      WebSecurityFilter.class.getName() + ".ALLOW_ORIGIN";
  private static final String REJECTED_ATTRIBUTE = WebSecurityFilter.class.getName() + ".REJECTED";
  private static final List<String> VARY_HEADERS =
      List.of(
          HttpHeaders.ORIGIN,
          HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
          HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS);
  private static final String AUTHORIZATION_HEADER = "Authorization";

  private final CorsProcessor corsProcessor;
  private final JwtService jwtService;
  private final UserRepository userRepository;

  public WebSecurityFilter(
      CorsProcessor corsProcessor, JwtService jwtService, UserRepository userRepository) {
    this.corsProcessor = corsProcessor;
    this.jwtService = jwtService;
    this.userRepository = userRepository;
  }

  @Override
  public int getOrder() {
    return ORDER;
  }

  @RequestFilter
  @ExecuteOn(TaskExecutors.BLOCKING)
  @Nullable
  public HttpResponse<?> filterRequest(HttpRequest<?> request) {
    // A path that is not normalised (repeated slashes) is rejected before CORS, authentication
    // and the security headers apply: the answer is a bare 401.
    if (RequestPaths.requestLinePath(request).contains("//")) {
      request.setAttribute(REJECTED_ATTRIBUTE, true);
      return HttpResponse.status(HttpStatus.UNAUTHORIZED);
    }
    CorsProcessor.Decision cors = corsProcessor.process(request);
    if (cors.isRejected()) {
      return writeHeaders(
          UntypedBody.respond(HttpResponse.status(HttpStatus.FORBIDDEN), "Invalid CORS request"),
          false);
    }
    if (cors.isCorsRequest()) {
      request.setAttribute(CORS_ALLOW_ORIGIN_ATTRIBUTE, CorsProcessor.ALL);
    }
    if (cors.isPreflight()) {
      MutableHttpResponse<?> preflight = HttpResponse.ok();
      preflight.header(
          HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, String.join(",", cors.getAllowMethods()));
      if (!cors.getAllowHeaders().isEmpty()) {
        preflight.header(
            HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, String.join(", ", cors.getAllowHeaders()));
      }
      return writeHeaders(preflight, true);
    }

    tokenFrom(request.getHeaders().get(AUTHORIZATION_HEADER))
        .flatMap(jwtService::getSubFromToken)
        .flatMap(userRepository::findById)
        .ifPresent(user -> Authentication.authenticate(request, user));

    String path = RequestPaths.decodedPath(request);
    if (WebSecurityConfig.requiresAuthentication(request, path)
        && Authentication.currentUser(request).isEmpty()) {
      return writeHeaders(HttpResponse.status(HttpStatus.UNAUTHORIZED), cors.isCorsRequest());
    }
    return null;
  }

  @ResponseFilter
  public void filterResponse(HttpRequest<?> request, MutableHttpResponse<?> response) {
    if (request.getAttribute(REJECTED_ATTRIBUTE).isPresent()) {
      return;
    }
    writeHeaders(response, request.getAttribute(CORS_ALLOW_ORIGIN_ATTRIBUTE).isPresent());
  }

  private static MutableHttpResponse<?> writeHeaders(
      MutableHttpResponse<?> response, boolean allowOrigin) {
    MutableHttpHeaders headers = response.getHeaders();
    List<String> vary = headers.getAll(HttpHeaders.VARY);
    for (String header : VARY_HEADERS) {
      if (!vary.contains(header)) {
        headers.add(HttpHeaders.VARY, header);
      }
    }
    if (allowOrigin && !headers.contains(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)) {
      headers.add(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, CorsProcessor.ALL);
    }
    setIfAbsent(headers, "X-Content-Type-Options", "nosniff");
    setIfAbsent(headers, "X-XSS-Protection", "1; mode=block");
    if (!headers.contains(HttpHeaders.CACHE_CONTROL)
        && !headers.contains(HttpHeaders.EXPIRES)
        && !headers.contains(HttpHeaders.PRAGMA)) {
      headers.add(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, max-age=0, must-revalidate");
      headers.add(HttpHeaders.PRAGMA, "no-cache");
      headers.add(HttpHeaders.EXPIRES, "0");
    }
    setIfAbsent(headers, "X-Frame-Options", "DENY");
    return response;
  }

  private static void setIfAbsent(MutableHttpHeaders headers, String name, String value) {
    if (!headers.contains(name)) {
      headers.add(name, value);
    }
  }

  private static Optional<String> tokenFrom(String header) {
    if (header == null) {
      return Optional.empty();
    }
    String[] split = header.split(" ");
    if (split.length < 2) {
      return Optional.empty();
    }
    return Optional.ofNullable(split[1]);
  }
}
