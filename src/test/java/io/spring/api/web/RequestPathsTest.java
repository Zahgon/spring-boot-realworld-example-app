package io.spring.api.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.micronaut.http.HttpRequest;
import org.junit.jupiter.api.Test;

public class RequestPathsTest {

  @Test
  public void should_return_raw_path_without_query_or_fragment() {
    assertEquals("/articles/a%20b", RequestPaths.rawPath(HttpRequest.GET("/articles/a%20b?x=1")));
    assertEquals("/articles", RequestPaths.rawPath(HttpRequest.GET("/articles#top?x")));
    assertEquals("/tags", RequestPaths.rawPath(HttpRequest.GET("http://localhost:8080/tags?a")));
    assertEquals("/", RequestPaths.rawPath(HttpRequest.GET("http://localhost:8080")));
    assertEquals("/", RequestPaths.rawPath(HttpRequest.GET("?x=1")));
  }

  @Test
  public void should_percent_decode_path_as_utf8_keeping_plus() {
    assertEquals(
        "/profiles/jos\u00e9 a+b",
        RequestPaths.decodedPath(HttpRequest.GET("/profiles/jos%C3%A9%20a+b?q=%20")));
  }

  @Test
  public void should_leave_invalid_or_truncated_escapes_untouched() {
    assertEquals("/a%zz", RequestPaths.percentDecode("/a%zz"));
    assertEquals("/a%4", RequestPaths.percentDecode("/a%4"));
    assertEquals("/aA", RequestPaths.percentDecode("/a%41"));
    assertEquals("/aAB", RequestPaths.percentDecode("/a%41B"));
    assertEquals("\u20ac/%", RequestPaths.percentDecode("%E2%82%AC/%"));
  }
}
