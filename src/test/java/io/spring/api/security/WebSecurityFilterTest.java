package io.spring.api.security;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@MicronautTest(environments = "test")
public class WebSecurityFilterTest {
  private static final String ORIGIN = "http://frontend.example.com";

  @Inject private EmbeddedServer server;

  @BeforeEach
  public void setUp() {
    RestAssured.port = server.getPort();
  }

  @Test
  public void should_allow_any_origin_for_cross_origin_requests_including_401() {
    Response response = given().header("Origin", ORIGIN).get("/tags");

    response.then().statusCode(200).header("Access-Control-Allow-Origin", "*");
    assertEquals(
        List.of("Origin", "Access-Control-Request-Method", "Access-Control-Request-Headers"),
        response.headers().getValues("Vary"));
    given()
        .header("Origin", ORIGIN)
        .get("/user")
        .then()
        .statusCode(401)
        .header("Access-Control-Allow-Origin", "*")
        .body(emptyString());
  }

  @Test
  public void should_add_only_security_headers_for_same_origin_request() {
    given()
        .header("Origin", "http://localhost:" + server.getPort())
        .get("/tags")
        .then()
        .statusCode(200)
        .header("Access-Control-Allow-Origin", nullValue())
        .header("X-Content-Type-Options", "nosniff")
        .header("X-XSS-Protection", "1; mode=block")
        .header("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate")
        .header("Pragma", "no-cache")
        .header("Expires", "0")
        .header("X-Frame-Options", "DENY");
  }

  @Test
  public void should_answer_preflight_before_routing_and_authentication() {
    preflight("DELETE")
        .header("Access-Control-Request-Headers", "Authorization, Content-Type")
        .options("/articles/some-slug/comments/1")
        .then()
        .statusCode(200)
        .header("Access-Control-Allow-Origin", "*")
        .header("Access-Control-Allow-Methods", "HEAD,GET,POST,PUT,DELETE,PATCH")
        .header("Access-Control-Allow-Headers", "Authorization, Content-Type")
        .header("Allow", nullValue())
        .body(emptyString());
    preflight("GET")
        .options("/no/such/route")
        .then()
        .statusCode(200)
        .header("Access-Control-Allow-Headers", nullValue());
  }

  @Test
  public void should_reject_invalid_cors_requests_with_403() {
    List<RequestSpecification> invalid =
        List.of(
            preflight("TRACE"),
            preflight("GET").header("Access-Control-Request-Headers", "X-Requested-With"),
            given().header("Origin", ORIGIN));
    for (RequestSpecification request : invalid) {
      request
          .options("/articles")
          .then()
          .statusCode(403)
          .header("Content-Type", nullValue())
          .header("Access-Control-Allow-Origin", nullValue())
          .header("X-Frame-Options", "DENY")
          .body(equalTo("Invalid CORS request"));
    }
  }

  @Test
  public void should_reject_paths_with_repeated_slashes_with_bare_401() throws Exception {
    HttpClient client = HttpClient.newHttpClient();
    for (String path : List.of("//tags", "/articles//some-slug", "/tags//")) {
      HttpResponse<String> response =
          client.send(
              HttpRequest.newBuilder(URI.create("http://localhost:" + server.getPort() + path))
                  .header("Origin", ORIGIN)
                  .build(),
              HttpResponse.BodyHandlers.ofString());

      assertEquals(401, response.statusCode(), path);
      assertEquals("", response.body(), path);
      for (String header :
          List.of("Vary", "Access-Control-Allow-Origin", "X-Frame-Options", "Cache-Control")) {
        assertTrue(response.headers().firstValue(header).isEmpty(), path + " " + header);
      }
    }
  }

  private static RequestSpecification preflight(String method) {
    return given().header("Origin", ORIGIN).header("Access-Control-Request-Method", method);
  }
}
