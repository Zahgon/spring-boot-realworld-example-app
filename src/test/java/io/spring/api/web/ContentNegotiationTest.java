package io.spring.api.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpRequest;
import io.spring.api.web.ContentNegotiation.NotAcceptableException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

public class ContentNegotiationTest {
  private static final List<MediaRange> JSON = ContentNegotiation.JSON_TYPES;

  @Test
  public void should_accept_everything_without_accept_header() {
    assertEquals(
        List.of(MediaRange.ANY), ContentNegotiation.acceptedMediaTypes(HttpRequest.GET("/")));
    assertEquals(List.of(MediaRange.ANY), ContentNegotiation.acceptedMediaTypes(accept(",")));
    assertEquals(Optional.of(MediaRange.APPLICATION_JSON), select(HttpRequest.GET("/")));
  }

  @Test
  public void should_order_accepted_types_by_specificity_and_quality() {
    MutableHttpRequest<?> request = accept("*/*;q=0.1, text/plain;q=0.5");
    request.header("Accept", "application/json");

    assertEquals(
        List.of("application/json", "text/plain;q=0.5", "*/*;q=0.1"),
        ContentNegotiation.acceptedMediaTypes(request).stream()
            .map(MediaRange::toString)
            .collect(Collectors.toList()));
  }

  @Test
  public void should_select_most_specific_json_type() {
    assertEquals("application/json", select(accept("application/*")).get().toString());
    assertEquals("application/json", select(accept("application/json;q=0.4")).get().toString());
    assertEquals("application/hal+json", select(accept("application/hal+json")).get().toString());
    assertEquals(
        "application/json;charset=UTF-16BE",
        select(accept("text/html, application/json;charset=UTF-16BE")).get().toString());
  }

  @Test
  public void should_select_nothing_for_incompatible_or_invalid_accept() {
    assertEquals(Optional.empty(), select(accept("text/plain")));
    assertEquals(Optional.empty(), select(accept("application/xml, text/*")));
    assertEquals(Optional.empty(), select(accept("not-a-media-type")));
  }

  @Test
  public void should_report_whether_any_json_type_is_accepted() {
    assertTrue(ContentNegotiation.accepts(accept("text/plain, */*;q=0.1"), JSON));
    assertTrue(ContentNegotiation.accepts(accept("application/problem+json"), JSON));
    assertFalse(ContentNegotiation.accepts(accept("text/plain"), JSON));
    assertFalse(ContentNegotiation.accepts(accept("text/"), JSON));
  }

  @Test
  public void should_throw_not_acceptable_for_unparseable_accept() {
    NotAcceptableException e =
        assertThrows(
            NotAcceptableException.class,
            () -> ContentNegotiation.acceptedMediaTypes(accept("application/json;q=x")));
    assertEquals("Could not find acceptable representation", e.getMessage());
  }

  private static MutableHttpRequest<?> accept(String value) {
    return HttpRequest.GET("/").header("Accept", value);
  }

  private static Optional<MediaRange> select(HttpRequest<?> request) {
    return ContentNegotiation.select(request, JSON);
  }
}
