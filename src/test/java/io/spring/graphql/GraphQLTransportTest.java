package io.spring.graphql;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.restassured.RestAssured;
import io.restassured.response.ValidatableResponse;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@MicronautTest(environments = "test")
public class GraphQLTransportTest {
  private static final String NO_BODY = "Invalid GraphQL request - no request body was provided";

  @Inject private EmbeddedServer server;

  @BeforeEach
  public void setUp() {
    RestAssured.port = server.getPort();
  }

  @Test
  public void should_execute_json_body_for_post_and_put() {
    for (String method : new String[] {"POST", "PUT"}) {
      given()
          .contentType("application/json")
          .body(Map.of("query", "{ __typename }"))
          .request(method, "/graphql")
          .then()
          .statusCode(200)
          .header("Content-Type", "application/json")
          .body("data.__typename", equalTo("Query"))
          .body("$", not(hasKey("errors")));
    }
  }

  @Test
  public void should_read_bare_query_for_application_graphql() {
    given()
        .contentType("application/graphql")
        .body("{ __typename }".getBytes(StandardCharsets.UTF_8))
        .post("/graphql")
        .then()
        .statusCode(200)
        .body("data.__typename", equalTo("Query"));
  }

  @Test
  public void should_return_400_for_get_without_body() {
    given()
        .queryParam("query", "{ __typename }")
        .get("/graphql")
        .then()
        .statusCode(400)
        .header("Content-Type", "application/json")
        .body(equalTo(NO_BODY));
  }

  @Test
  public void should_return_406_when_json_is_not_acceptable() {
    given()
        .contentType("application/json")
        .accept("text/plain")
        .body(Map.of("query", "{ __typename }"))
        .post("/graphql")
        .then()
        .statusCode(406)
        .header("Content-Type", nullValue())
        .body(emptyString());
  }

  @Test
  public void should_write_response_in_requested_charset() {
    byte[] body =
        given()
            .contentType("application/json")
            .accept("application/json;charset=UTF-16BE")
            .body(Map.of("query", "{ __typename }"))
            .post("/graphql")
            .then()
            .statusCode(200)
            .header("Content-Type", "application/json;charset=UTF-16BE")
            .extract()
            .asByteArray();
    assertArrayEquals(
        "{\"data\":{\"__typename\":\"Query\"}}".getBytes(StandardCharsets.UTF_16BE), body);
  }

  @Test
  public void should_handle_form_urlencoded_bodies_like_servlet_parameters() {
    given()
        .contentType("application/x-www-form-urlencoded")
        .formParam("query", "{ __typename }")
        .post("/graphql")
        .then()
        .statusCode(400)
        .body(startsWith("Invalid query - Unrecognized token 'query'"))
        .body(
            containsString("[Source: (String)\"query=%7B+__typename+%7D\"; line: 1, column: 1]."));
    given()
        .contentType("application/x-www-form-urlencoded")
        .formParam("query", "{ __typename }")
        .put("/graphql")
        .then()
        .statusCode(400)
        .body(equalTo(NO_BODY));
  }

  @Test
  public void should_return_400_for_malformed_json_or_operation_name() {
    postJson("{\"query\": ")
        .statusCode(400)
        .body(startsWith("Invalid query - Unexpected end-of-input within/between Object entries"));
    postJson("[1]").statusCode(400).body(equalTo("Invalid query - No content to map to input."));
    postJson("{\"query\": \"{ __typename }\", \"operationName\": 1}")
        .statusCode(400)
        .body(equalTo("Invalid GraphQL request - operationName must be a String"));
  }

  @Test
  public void should_select_operation_by_name_and_apply_variables() {
    postJson(
            "{\"query\": \"query A { tags } query B($t: String) { articles(first: 1, withTag: $t)"
                + " { edges { cursor } } }\", \"operationName\": \"B\","
                + " \"variables\": {\"t\": \"none\"}}")
        .statusCode(200)
        .body("data.articles.edges", empty())
        .body("data", not(hasKey("tags")));
    postJson("{\"query\": \"query A { tags } query B { tags }\"}")
        .statusCode(200)
        .body("data", nullValue())
        .body(
            "errors[0].message",
            equalTo("Must provide operation name if query contains multiple operations."));
  }

  @Test
  public void should_report_empty_query_and_syntax_errors_with_200() {
    postJson("{\"query\": \"\"}")
        .statusCode(200)
        .body("$", not(hasKey("data")))
        .body("errors[0].message", equalTo("The query is null or empty."))
        .body("errors[0].extensions.errorType", equalTo("BAD_REQUEST"));
    postJson("{\"query\": \"{ article(slug: \"}")
        .statusCode(200)
        .body("$", not(hasKey("data")))
        .body(
            "errors[0].message",
            equalTo("Invalid Syntax : offending token '<EOF>' at line 1 column 17"))
        .body("errors[0].locations[0].line", equalTo(1))
        .body("errors[0].locations[0].column", equalTo(17))
        .body("errors[0].extensions.classification", equalTo("InvalidSyntax"));
  }

  private ValidatableResponse postJson(String json) {
    return given()
        .contentType("application/json")
        .body(json.getBytes(StandardCharsets.UTF_8))
        .post("/graphql")
        .then();
  }
}
