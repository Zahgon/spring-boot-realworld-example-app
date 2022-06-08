package io.spring.api.web;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@MicronautTest(environments = "test")
public class ApiErrorHandlingTest {
  private static final AtomicInteger SEQUENCE = new AtomicInteger();
  private static final Map<String, Object> BLANK_USER =
      Map.of("user", Map.of("email", "", "username", "", "password", ""));
  private static final Map<String, Object> UNKNOWN_LOGIN =
      Map.of("user", Map.of("email", "nobody@example.com", "password", "secret"));

  @Inject private EmbeddedServer server;

  private String token;

  @BeforeEach
  public void setUp() {
    RestAssured.port = server.getPort();
    token = register();
  }

  @Test
  public void should_return_422_with_field_errors_for_invalid_body() {
    json(given(), BLANK_USER)
        .post("/users")
        .then()
        .statusCode(422)
        .header("Content-Type", "application/json")
        .body("errors.email", contains("can't be empty"))
        .body("errors.username", contains("can't be empty"))
        .body("errors.password", contains("can't be empty"));
    json(as(token), Map.of("article", Map.of("title", "", "description", " ", "body", "body")))
        .post("/articles")
        .then()
        .statusCode(422)
        .body("errors.title", contains("can't be empty"))
        .body("errors.description", contains("can't be empty"))
        .body("errors", not(hasKey("body")));
  }

  @Test
  public void should_return_422_with_message_for_wrong_credentials() {
    json(given(), UNKNOWN_LOGIN)
        .post("/users/login")
        .then()
        .statusCode(422)
        .body(equalTo("{\"message\":\"invalid email or password\"}"));
  }

  @Test
  public void should_return_400_without_body_for_malformed_or_empty_json() {
    for (String body : new String[] {"{\"user\":", "", "null"}) {
      given()
          .contentType("application/json")
          .body(body.getBytes(StandardCharsets.UTF_8))
          .post("/users")
          .then()
          .statusCode(400)
          .body(emptyString());
    }
  }

  @Test
  public void should_return_400_for_unconvertible_query_parameter() {
    given().queryParam("limit", "ten").get("/articles").then().statusCode(400).body(emptyString());
    given().queryParam("limit", "0x0").get("/articles").then().statusCode(200);
  }

  @Test
  public void should_return_415_for_unsupported_content_type() {
    byte[] body = "hello".getBytes(StandardCharsets.UTF_8);
    given()
        .contentType("text/plain")
        .body(body)
        .post("/users")
        .then()
        .statusCode(415)
        .header("Accept", "application/json, application/*+json")
        .body(emptyString());
    given().contentType("bogus").body(body).post("/users").then().header("Accept", nullValue());
  }

  @Test
  public void should_negotiate_json_response_types() {
    given().accept("text/plain").get("/tags").then().statusCode(406).body(emptyString());
    given().accept("text/").get("/tags").then().statusCode(406);
    given()
        .accept("application/hal+json")
        .get("/tags")
        .then()
        .statusCode(200)
        .header("Content-Type", "application/hal+json");
  }

  @Test
  public void should_not_compress_responses() {
    json(
            given().header("Accept-Encoding", "gzip, deflate"),
            Map.of("query", "{ __schema { types { name } } }"))
        .post("/graphql")
        .then()
        .statusCode(200)
        .header("Content-Encoding", nullValue());
  }

  @Test
  public void should_fall_back_to_status_without_body_when_error_is_not_acceptable() {
    given().accept("text/plain").get("/articles/x").then().statusCode(404).body(emptyString());
    json(given().accept("text/plain"), BLANK_USER)
        .post("/users")
        .then()
        .statusCode(400)
        .body(emptyString());
    json(given().accept("text/plain"), UNKNOWN_LOGIN)
        .post("/users/login")
        .then()
        .statusCode(500)
        .body(emptyString());
  }

  @Test
  public void should_return_404_error_body_for_unknown_path_and_missing_resource() {
    as(token)
        .urlEncodingEnabled(false)
        .get("/unknown%20path?x=1")
        .then()
        .statusCode(404)
        .header("Content-Type", "application/json")
        .body("status", equalTo(404))
        .body("error", equalTo("Not Found"))
        .body("path", equalTo("/unknown%20path"))
        .body("timestamp", matchesPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}.+"));
    given()
        .accept("application/hal+json")
        .get("/articles/missing")
        .then()
        .statusCode(404)
        .header("Content-Type", "application/hal+json")
        .body("path", equalTo("/articles/missing"));
  }

  @Test
  public void should_return_405_with_allow_header_for_undeclared_method() {
    as(token).delete("/tags").then().statusCode(405).header("Allow", "GET").body(emptyString());
    as(token).patch("/articles/x").then().statusCode(405).header("Allow", "GET,PUT,DELETE");
    given().options("/articles/x").then().header("Allow", "GET,HEAD,PUT,DELETE,OPTIONS");
  }

  @Test
  public void should_return_401_before_body_reading_or_routing() {
    json(given(), "{").post("/articles").then().statusCode(401).body(emptyString());
    given().delete("/tags").then().statusCode(401);
    given().get("/no/such/route").then().statusCode(401);
  }

  private static RequestSpecification as(String token) {
    return given().header("Authorization", "Token " + token);
  }

  private static RequestSpecification json(RequestSpecification request, Object body) {
    return request.contentType("application/json").body(body);
  }

  private static String register() {
    String name = "errors" + SEQUENCE.incrementAndGet();
    Map<String, Object> user =
        Map.of("email", name + "@example.com", "username", name, "password", "secret");
    return json(given(), Map.of("user", user))
        .post("/users")
        .then()
        .statusCode(201)
        .extract()
        .path("user.token");
  }
}
