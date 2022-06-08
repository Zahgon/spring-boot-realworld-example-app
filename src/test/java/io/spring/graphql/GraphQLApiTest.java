package io.spring.graphql;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.restassured.RestAssured;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@MicronautTest(environments = "test")
public class GraphQLApiTest {
  private static final AtomicInteger SEQUENCE = new AtomicInteger();
  private static final String CREATE_USER =
      "mutation($input: CreateUserInput) { createUser(input: $input) { __typename"
          + " ... on UserPayload { user { email username token profile { bio image following } } }"
          + " ... on Error { message errors { key value } } } }";
  private static final String CREATE_ARTICLE =
      "mutation($input: CreateArticleInput!) { createArticle(input: $input) { article {"
          + " slug tagList author { username } } } }";
  private static final String ARTICLE = "query($slug: String!) { article(slug: $slug) { slug } }";

  @Inject private EmbeddedServer server;

  @BeforeEach
  public void setUp() {
    RestAssured.port = server.getPort();
  }

  @Test
  public void should_create_user_with_create_user_mutation() {
    String name = uniqueName();
    graphql(null, CREATE_USER, Map.of("input", userInput(name + "@example.com", name)))
        .body("errors", nullValue())
        .body("data.createUser.__typename", equalTo("UserPayload"))
        .body("data.createUser.user.email", equalTo(name + "@example.com"))
        .body("data.createUser.user.username", equalTo(name))
        .body("data.createUser.user.token", not(empty()))
        .body("data.createUser.user.profile.following", equalTo(false));
  }

  @Test
  public void should_return_error_payload_for_invalid_user_input() {
    graphql(null, CREATE_USER, Map.of("input", userInput("not-an-email", "")))
        .body("errors", nullValue())
        .body("data.createUser.__typename", equalTo("Error"))
        .body("data.createUser.message", equalTo("BAD_REQUEST"))
        .body("data.createUser.errors", hasSize(2))
        .body(
            "data.createUser.errors.find { it.key == 'email' }.value",
            contains("should be an email"))
        .body(
            "data.createUser.errors.find { it.key == 'username' }.value",
            contains("can't be empty"));
  }

  @Test
  public void should_login_and_query_me_with_token() {
    String name = uniqueName();
    register(name);
    String login =
        "mutation($email: String!, $password: String!) {"
            + " login(email: $email, password: $password) { user { email token } } }";
    String token =
        graphql(null, login, Map.of("email", name + "@example.com", "password", "secret"))
            .body("data.login.user.email", equalTo(name + "@example.com"))
            .extract()
            .path("data.login.user.token");

    graphql(token, "{ me { email username token profile { username } } }")
        .body("errors", nullValue())
        .body("data.me.email", equalTo(name + "@example.com"))
        .body("data.me.username", equalTo(name))
        .body("data.me.token", equalTo(token))
        .body("data.me.profile.username", equalTo(name));

    graphql(null, login, Map.of("email", name + "@example.com", "password", "wrong"))
        .body("data.login", nullValue())
        .body("errors", hasSize(1))
        .body("errors[0].message", equalTo("invalid email or password"))
        .body("errors[0].path", contains("login"))
        .body("errors[0].extensions.errorType", equalTo("UNAUTHENTICATED"));
  }

  @Test
  public void should_report_missing_authorization_header_for_me() {
    graphql(null, "{ me { email } }")
        .body("data.me", nullValue())
        .body("errors[0].path", contains("me"))
        .body("errors[0].extensions.errorType", equalTo("INTERNAL"))
        .body(
            "errors[0].message",
            equalTo(
                "io.spring.graphql.exception.MissingHeaderException:"
                    + " Required header 'Authorization' was not provided"));
  }

  @Test
  public void should_create_update_and_delete_article() {
    String name = uniqueName();
    String token = register(name);
    Map<String, Object> input = articleInput("Article of " + name, List.of("graphql", "micronaut"));
    graphql(token, CREATE_ARTICLE, Map.of("input", input))
        .body("errors", nullValue())
        .body("data.createArticle.article.slug", equalTo("article-of-" + name))
        .body("data.createArticle.article.tagList", containsInAnyOrder("graphql", "micronaut"))
        .body("data.createArticle.article.author.username", equalTo(name));
    Map<String, Object> slug = Map.of("slug", "article-of-" + name);

    graphql(
            token,
            "mutation($slug: String!) { updateArticle(slug: $slug, changes: {body: \"updated\"})"
                + " { article { title body } } }",
            slug)
        .body("errors", nullValue())
        .body("data.updateArticle.article.title", equalTo("Article of " + name))
        .body("data.updateArticle.article.body", equalTo("updated"));

    String reader = register(uniqueName());
    String fields = "(slug: $slug) { article { favorited favoritesCount } } }";
    graphql(reader, "mutation($slug: String!) { favoriteArticle" + fields, slug)
        .body("data.favoriteArticle.article.favorited", equalTo(true))
        .body("data.favoriteArticle.article.favoritesCount", equalTo(1));
    graphql(reader, "mutation($slug: String!) { unfavoriteArticle" + fields, slug)
        .body("data.unfavoriteArticle.article.favorited", equalTo(false))
        .body("data.unfavoriteArticle.article.favoritesCount", equalTo(0));

    graphql(token, "mutation($slug: String!) { deleteArticle(slug: $slug) { success } }", slug)
        .body("data.deleteArticle.success", equalTo(true));
    graphql(null, ARTICLE, slug).body("data.article", nullValue());
  }

  @Test
  public void should_report_bean_validation_errors_as_bad_request() {
    String token = register(uniqueName());
    graphql(token, CREATE_ARTICLE, Map.of("input", articleInput("", List.of())))
        .body("data.createArticle", nullValue())
        .body("errors[0].message", equalTo("createArticle.newArticleParam.title: can't be empty"))
        .body("errors[0].path", contains("createArticle"))
        .body("errors[0].extensions.errorType", equalTo("BAD_REQUEST"))
        .body("errors[0].extensions.title", contains("can't be empty"));
  }

  @Test
  public void should_reject_article_mutations_of_anonymous_and_other_users() {
    graphql(null, CREATE_ARTICLE, Map.of("input", articleInput("Anonymous", List.of())))
        .body("data.createArticle", nullValue())
        .body("errors[0].path", contains("createArticle"))
        .body("errors[0].extensions.errorType", equalTo("INTERNAL"))
        .body(
            "errors[0].message",
            equalTo("io.spring.graphql.exception.AuthenticationException: null"));

    String slug = createArticle(register(uniqueName()), "Owned " + uniqueName(), List.of());
    String other = register(uniqueName());
    for (String mutation :
        List.of(
            "updateArticle(slug: $slug, changes: {title: \"stolen\"}) { article { slug } }",
            "deleteArticle(slug: $slug) { success }")) {
      graphql(other, "mutation($slug: String!) { " + mutation + " }", Map.of("slug", slug))
          .body("errors[0].extensions.errorType", equalTo("INTERNAL"))
          .body(
              "errors[0].message",
              equalTo("io.spring.api.exception.NoAuthorizationException: null"));
    }
    graphql(null, ARTICLE, Map.of("slug", slug)).body("data.article.slug", equalTo(slug));
  }

  @Test
  public void should_report_missing_article_as_error_entry() {
    graphql(null, ARTICLE, Map.of("slug", "does-not-exist"))
        .body("data.article", nullValue())
        .body("errors", hasSize(1))
        .body("errors[0].path", contains("article"))
        .body("errors[0].locations", empty())
        .body("errors[0].extensions.errorType", equalTo("INTERNAL"))
        .body(
            "errors[0].message",
            equalTo("io.spring.api.exception.ResourceNotFoundException: null"));
  }

  @Test
  public void should_add_list_and_delete_comments() {
    String slug = createArticle(register(uniqueName()), "Commented " + uniqueName(), List.of());
    String commenterName = uniqueName();
    String commenter = register(commenterName);

    String commentId =
        graphql(
                commenter,
                "mutation($slug: String!) { addComment(slug: $slug, body: \"first!\") {"
                    + " comment { id body author { username } } } }",
                Map.of("slug", slug))
            .body("errors", nullValue())
            .body("data.addComment.comment.body", equalTo("first!"))
            .body("data.addComment.comment.author.username", equalTo(commenterName))
            .extract()
            .path("data.addComment.comment.id");

    graphql(
            null,
            "query($slug: String!) { article(slug: $slug) {"
                + " comments(first: 10) { edges { node { id } } } } }",
            Map.of("slug", slug))
        .body("data.article.comments.edges.node.id", contains(commentId));

    graphql(
            commenter,
            "mutation($slug: String!, $id: ID!) {"
                + " deleteComment(slug: $slug, id: $id) { success } }",
            Map.of("slug", slug, "id", commentId))
        .body("data.deleteComment.success", equalTo(true));
  }

  private static String uniqueName() {
    return "gql" + SEQUENCE.incrementAndGet();
  }

  private static Map<String, Object> userInput(String email, String username) {
    return Map.of("email", email, "username", username, "password", "secret");
  }

  private static Map<String, Object> articleInput(String title, List<String> tags) {
    return Map.of("title", title, "description", "description", "body", "body", "tagList", tags);
  }

  private String register(String name) {
    return graphql(null, CREATE_USER, Map.of("input", userInput(name + "@example.com", name)))
        .extract()
        .path("data.createUser.user.token");
  }

  private String createArticle(String token, String title, List<String> tags) {
    return graphql(token, CREATE_ARTICLE, Map.of("input", articleInput(title, tags)))
        .body("errors", nullValue())
        .extract()
        .path("data.createArticle.article.slug");
  }

  private ValidatableResponse graphql(String token, String query) {
    return graphql(token, query, Map.of());
  }

  private ValidatableResponse graphql(String token, String query, Map<String, Object> variables) {
    RequestSpecification request =
        given()
            .contentType("application/json")
            .body(Map.of("query", query, "variables", variables));
    if (token != null) {
      request.header("Authorization", "Token " + token);
    }
    return request.post("/graphql").then().statusCode(200).contentType("application/json");
  }
}
