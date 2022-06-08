package io.spring.api.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.spring.api.exception.FieldErrorResource;
import io.spring.api.exception.RequestBodyValidationException;
import io.spring.api.web.RequestBodyException.Missing;
import io.spring.api.web.RequestBodyException.UnsupportedMediaType;
import io.spring.application.user.RegisterParam;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

@MicronautTest(environments = "test", startApplication = false)
public class RequestBodyReaderTest {
  private static final String USER =
      "{\"user\":{\"email\":\"jos\u00e9@example.com\",\"username\":\"jos\u00e9\","
          + "\"password\":\"secret\"}}";

  @Inject private RequestBodyReader reader;
  @Inject private RequestBodyValidator validator;

  @Test
  public void should_unwrap_root_name_of_json_types_in_declared_charset() {
    RegisterParam param = read(HttpMethod.POST, "application/json", USER, "UTF-8");
    assertEquals("jos\u00e9@example.com", param.getEmail());
    assertEquals("secret", param.getPassword());
    for (String charset : List.of("UTF-8", "UTF-16BE", "ISO-8859-1")) {
      String contentType = "application/vnd.api+json;charset=" + charset;
      assertEquals("jos\u00e9", read(HttpMethod.PUT, contentType, USER, charset).getUsername());
    }
  }

  @Test
  public void should_reject_non_json_content_type_of_body_methods() {
    assertEquals(
        ContentNegotiation.JSON_TYPES,
        failure(UnsupportedMediaType.class, HttpMethod.POST, "text/plain", USER).getSupported());
    failure(UnsupportedMediaType.class, HttpMethod.PATCH, null, USER);
    assertEquals(
        List.of(),
        failure(UnsupportedMediaType.class, HttpMethod.POST, "json", USER).getSupported());
  }

  @Test
  public void should_report_missing_body() {
    failure(Missing.class, HttpMethod.POST, null, "");
    failure(Missing.class, HttpMethod.POST, "application/json", "");
    failure(Missing.class, HttpMethod.POST, "application/json", "null");
    failure(Missing.class, HttpMethod.DELETE, "text/plain", USER);
  }

  @Test
  public void should_wrap_unreadable_json() {
    RequestBodyException e =
        failure(RequestBodyException.Unreadable.class, HttpMethod.POST, "application/json", "{");
    assertTrue(e.getMessage().startsWith("JSON parse error: "), e.getMessage());
  }

  @Test
  public void should_turn_constraint_violations_into_field_errors() {
    String json = "{\"user\":{\"email\":\"nope\",\"username\":\" \",\"password\":\"x\"}}";
    byte[] body = json.getBytes(StandardCharsets.UTF_8);
    RequestBodyValidationException e =
        assertThrows(
            RequestBodyValidationException.class,
            () ->
                reader.readValid(
                    request(HttpMethod.POST, "application/json"), body, RegisterParam.class));

    assertEquals(
        Map.of("email", "Email: should be an email", "username", "NotBlank: can't be empty"),
        describe(e.getFieldErrors()));
    assertTrue(e.getFieldErrors().stream().allMatch(f -> f.getResource().equals("registerParam")));
  }

  @Test
  public void should_name_nested_and_container_element_fields() {
    RequestBodyValidationException e =
        assertThrows(RequestBodyValidationException.class, () -> validator.validate(new Draft()));

    assertEquals(
        Map.of(
            "title", "NotBlank: must not be blank",
            "items[0].name", "NotBlank: must not be blank",
            "tags[1]", "NotBlank: must not be blank"),
        describe(e.getFieldErrors()));
    assertTrue(e.getFieldErrors().stream().allMatch(f -> f.getResource().equals("draft")));
  }

  static class Draft {
    @NotBlank String title = "";
    @Valid List<Item> items = List.of(new Item());
    List<@NotBlank String> tags = List.of("ok", " ");
  }

  static class Item {
    @NotBlank String name;
  }

  private RegisterParam read(HttpMethod method, String contentType, String body, String charset) {
    return reader.read(
        request(method, contentType), body.getBytes(Charset.forName(charset)), RegisterParam.class);
  }

  private <T extends RequestBodyException> T failure(
      Class<T> type, HttpMethod method, String contentType, String body) {
    return assertThrows(type, () -> read(method, contentType, body, "UTF-8"));
  }

  private static HttpRequest<?> request(HttpMethod method, String contentType) {
    MutableHttpRequest<?> request = HttpRequest.create(method, "/users");
    return contentType == null ? request : request.header("Content-Type", contentType);
  }

  private static Map<String, String> describe(List<FieldErrorResource> errors) {
    return errors.stream()
        .collect(
            Collectors.toMap(
                FieldErrorResource::getField, f -> f.getCode() + ": " + f.getMessage()));
  }
}
