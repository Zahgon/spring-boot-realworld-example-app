package io.spring.api.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpRequest;
import io.spring.api.web.QueryParameters.InvalidParameterException;
import java.util.List;
import org.junit.jupiter.api.Test;

public class QueryParametersTest {

  @Test
  public void should_join_repeated_text_parameters() {
    assertNull(QueryParameters.text(request(), "tag"));
    assertEquals("java", QueryParameters.text(request("java"), "tag"));
    assertEquals("java,,spring", QueryParameters.text(request("java", "", "spring"), "tag"));
  }

  @Test
  public void should_fall_back_to_default_for_missing_or_empty_integer() {
    assertEquals(20, QueryParameters.integer(request(), "tag", 20));
    assertEquals(20, QueryParameters.integer(request(""), "tag", 20));
  }

  @Test
  public void should_convert_integers_like_the_original_api() {
    assertEquals(10, QueryParameters.integer(request(" 1 0 "), "tag", 0));
    assertEquals(-3, QueryParameters.integer(request("-3"), "tag", 0));
    assertEquals(31, QueryParameters.integer(request("0x1F"), "tag", 0));
    assertEquals(16, QueryParameters.integer(request("#10"), "tag", 0));
    assertEquals(-16, QueryParameters.integer(request("-0X10"), "tag", 0));
    assertEquals(5, QueryParameters.integer(request("5", "7"), "tag", 0));
  }

  @Test
  public void should_reject_unconvertible_integers() {
    InvalidParameterException e =
        assertThrows(
            InvalidParameterException.class,
            () -> QueryParameters.integer(request("ten"), "tag", 0));
    assertEquals("Failed to convert value 'ten' of parameter 'tag'", e.getMessage());
    assertInstanceOf(NumberFormatException.class, e.getCause());

    assertThrows(
        InvalidParameterException.class, () -> QueryParameters.integer(request("", "3"), "tag", 0));
    assertThrows(
        InvalidParameterException.class,
        () -> QueryParameters.integer(request("2147483648"), "tag", 0));
  }

  private static MutableHttpRequest<?> request(String... values) {
    MutableHttpRequest<?> request = HttpRequest.GET("/articles");
    if (values.length > 0) {
      request.getParameters().add("tag", List.<CharSequence>of(values));
    }
    return request;
  }
}
