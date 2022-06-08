package io.spring.api.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpRequest;
import io.spring.api.security.CorsProcessor.Decision;
import java.util.List;
import org.junit.jupiter.api.Test;

public class CorsProcessorTest {
  private static final List<String> METHODS =
      List.of("HEAD", "GET", "POST", "PUT", "DELETE", "PATCH");

  private final CorsProcessor processor = new CorsProcessor();

  @Test
  public void should_ignore_requests_without_origin_or_from_same_origin() {
    assertFalse(processor.process(request(HttpMethod.GET, null)).isCorsRequest());
    assertFalse(
        processor.process(request(HttpMethod.GET, "http://localhost:8080")).isCorsRequest());
    MutableHttpRequest<?> defaultPort =
        HttpRequest.GET("/tags")
            .header("Host", "example.com")
            .header("Origin", "http://example.com:80");
    assertFalse(processor.process(defaultPort).isCorsRequest());
  }

  @Test
  public void should_accept_simple_requests_from_other_scheme_host_or_port() {
    for (String origin :
        List.of("https://localhost:8080", "http://localhost:8081", "http://example.com:8080")) {
      Decision decision = processor.process(request(HttpMethod.GET, origin));
      assertTrue(decision.isCorsRequest(), origin);
      assertFalse(decision.isRejected(), origin);
    }

    Decision decision =
        processor.process(
            request(HttpMethod.POST, "http://example.com").header("Authorization", "Token x"));
    assertFalse(decision.isPreflight());
    assertFalse(decision.isRejected());
    assertEquals(METHODS, decision.getAllowMethods());
    assertEquals(1, decision.getAllowHeaders().size());
    assertTrue(decision.getAllowHeaders().get(0).equalsIgnoreCase("Authorization"));
  }

  @Test
  public void should_reject_blank_origin_and_disallowed_actual_methods() {
    assertRejected(request(HttpMethod.GET, ""), false);
    assertRejected(request(HttpMethod.OPTIONS, "http://example.com"), false);
    assertRejected(request(HttpMethod.TRACE, "http://example.com"), false);
  }

  @Test
  public void should_accept_preflight_with_allowed_method_and_headers() {
    Decision decision =
        processor.process(
            preflight("PATCH").header("Access-Control-Request-Headers", "content-type, X-Other"));

    assertTrue(decision.isPreflight());
    assertFalse(decision.isRejected());
    assertEquals(METHODS, decision.getAllowMethods());
    assertEquals(List.of("content-type"), decision.getAllowHeaders());
    assertEquals(List.of(), processor.process(preflight("GET")).getAllowHeaders());
    assertTrue(CorsProcessor.isPreflight(preflight("GET")));
    assertFalse(CorsProcessor.isPreflight(request(HttpMethod.OPTIONS, "http://example.com")));
  }

  @Test
  public void should_reject_preflight_with_disallowed_method_or_headers() {
    assertRejected(preflight("OPTIONS"), true);
    assertRejected(preflight("get"), true);
    assertRejected(preflight("CONNECT"), true);
    assertRejected(preflight("GET").header("Access-Control-Request-Headers", "X-Custom"), true);
  }

  private void assertRejected(HttpRequest<?> request, boolean preflight) {
    Decision decision = processor.process(request);
    assertTrue(decision.isCorsRequest());
    assertTrue(decision.isRejected());
    assertEquals(preflight, decision.isPreflight());
    assertEquals(List.of(), decision.getAllowMethods());
  }

  private static MutableHttpRequest<?> preflight(String method) {
    return request(HttpMethod.OPTIONS, "http://example.com")
        .header("Access-Control-Request-Method", method);
  }

  private static MutableHttpRequest<?> request(HttpMethod method, String origin) {
    MutableHttpRequest<?> request =
        HttpRequest.create(method, "/articles").header("Host", "localhost:8080");
    return origin == null ? request : request.header("Origin", origin);
  }
}
