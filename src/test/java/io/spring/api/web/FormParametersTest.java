package io.spring.api.web;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class FormParametersTest {
  private static final String FORM = "application/x-www-form-urlencoded";

  @Test
  public void should_merge_query_string_and_form_post_body() {
    MutableHttpRequest<?> request = request(HttpMethod.POST, "/graphql?a=1&b=2", FORM);

    Map<String, List<String>> parameters =
        FormParameters.parameters(request, bytes("b=3&c=%E2%82%AC+sign"));

    assertEquals(
        Map.of("a", List.of("1"), "b", List.of("2", "3"), "c", List.of("€ sign")), parameters);
  }

  @Test
  public void should_skip_malformed_and_nameless_pairs() {
    MutableHttpRequest<?> request = request(HttpMethod.POST, "/graphql", FORM + ";charset=UTF-8");

    Map<String, List<String>> parameters =
        FormParameters.parameters(request, bytes("bad=%zz&short=%4&=v&&flag&ok=1"));

    assertEquals(Map.of("flag", List.of(""), "ok", List.of("1")), parameters);
  }

  @Test
  public void should_re_encode_form_post_and_empty_other_form_bodies() {
    MutableHttpRequest<?> post = request(HttpMethod.POST, "/graphql?x=1", FORM);
    byte[] raw = FormParameters.rawBody(post, bytes("query=%7B+tags+%7D&x=2"));
    assertEquals("x=1&x=2&query=%7B+tags+%7D", new String(raw, StandardCharsets.UTF_8));

    for (HttpMethod method : List.of(HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE)) {
      MutableHttpRequest<?> request = request(method, "/graphql", FORM + "; charset=ISO-8859-1");
      assertArrayEquals(new byte[0], FormParameters.rawBody(request, bytes("query=x")));
    }
  }

  @Test
  public void should_ignore_and_pass_through_non_form_post_bodies() {
    byte[] body = bytes("{\"query\":\"{ tags }\"}");

    assertEquals(
        Map.of("q", List.of("1")),
        FormParameters.parameters(request(HttpMethod.PUT, "/graphql?q=1", FORM), body));
    assertEquals(
        Map.of(),
        FormParameters.parameters(request(HttpMethod.POST, "/graphql", "application/json"), body));
    assertArrayEquals(
        body,
        FormParameters.rawBody(request(HttpMethod.POST, "/graphql", "application/json"), body));
    assertArrayEquals(
        body, FormParameters.rawBody(request(HttpMethod.PUT, "/graphql", "not a type"), body));
    assertArrayEquals(body, FormParameters.rawBody(HttpRequest.POST("/graphql", ""), body));
  }

  private static MutableHttpRequest<?> request(HttpMethod method, String uri, String contentType) {
    return HttpRequest.create(method, uri).header("Content-Type", contentType);
  }

  private static byte[] bytes(String value) {
    return value.getBytes(StandardCharsets.UTF_8);
  }
}
