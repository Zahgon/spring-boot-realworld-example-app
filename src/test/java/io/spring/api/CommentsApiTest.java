package io.spring.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.restassured.RestAssured;
import io.spring.application.CommentQueryService;
import io.spring.application.data.CommentData;
import io.spring.application.data.ProfileData;
import io.spring.core.article.Article;
import io.spring.core.article.ArticleRepository;
import io.spring.core.comment.Comment;
import io.spring.core.comment.CommentRepository;
import io.spring.core.user.User;
import jakarta.inject.Inject;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@MicronautTest(environments = "test")
public class CommentsApiTest extends TestWithCurrentUser {

  @Inject private ArticleRepository articleRepository;

  @Inject private CommentRepository commentRepository;
  @Inject private CommentQueryService commentQueryService;

  private Article article;
  private CommentData commentData;
  private Comment comment;

  @BeforeEach
  public void setUp() throws Exception {
    super.setUp();
    article = new Article("title", "desc", "body", Arrays.asList("test", "java"), user.getId());
    when(articleRepository.findBySlug(eq(article.getSlug()))).thenReturn(Optional.of(article));
    comment = new Comment("comment", user.getId(), article.getId());
    commentData =
        new CommentData(
            comment.getId(),
            comment.getBody(),
            comment.getArticleId(),
            comment.getCreatedAt(),
            comment.getCreatedAt(),
            new ProfileData(
                user.getId(), user.getUsername(), user.getBio(), user.getImage(), false));
  }

  @Test
  public void should_create_comment_success() throws Exception {
    Map<String, Object> param =
        new HashMap<String, Object>() {
          {
            put(
                "comment",
                new HashMap<String, Object>() {
                  {
                    put("body", "comment content");
                  }
                });
          }
        };

    when(commentQueryService.findById(anyString(), eq(user))).thenReturn(Optional.of(commentData));

    given()
        .contentType("application/json")
        .header("Authorization", "Token " + token)
        .body(param)
        .when()
        .post("/articles/{slug}/comments", article.getSlug())
        .then()
        .statusCode(201)
        .body("comment.body", equalTo(commentData.getBody()));
  }

  @Test
  public void should_get_422_with_empty_body() throws Exception {
    Map<String, Object> param =
        new HashMap<String, Object>() {
          {
            put(
                "comment",
                new HashMap<String, Object>() {
                  {
                    put("body", "");
                  }
                });
          }
        };

    given()
        .contentType("application/json")
        .header("Authorization", "Token " + token)
        .body(param)
        .when()
        .post("/articles/{slug}/comments", article.getSlug())
        .then()
        .statusCode(422)
        .body("errors.body[0]", equalTo("can't be empty"));
  }

  @Test
  public void should_get_comments_of_article_success() throws Exception {
    when(commentQueryService.findByArticleId(anyString(), eq(null)))
        .thenReturn(Arrays.asList(commentData));
    RestAssured.when()
        .get("/articles/{slug}/comments", article.getSlug())
        .then()
        .statusCode(200)
        .body("comments[0].id", equalTo(commentData.getId()));
  }

  @Test
  public void should_delete_comment_success() throws Exception {
    when(commentRepository.findById(eq(article.getId()), eq(comment.getId())))
        .thenReturn(Optional.of(comment));

    given()
        .header("Authorization", "Token " + token)
        .when()
        .delete("/articles/{slug}/comments/{id}", article.getSlug(), comment.getId())
        .then()
        .statusCode(204);
  }

  @Test
  public void should_get_403_if_not_author_of_article_or_author_of_comment_when_delete_comment()
      throws Exception {
    User anotherUser = new User("other@example.com", "other", "123", "", "");
    when(userRepository.findByUsername(eq(anotherUser.getUsername())))
        .thenReturn(Optional.of(anotherUser));
    when(jwtService.getSubFromToken(any())).thenReturn(Optional.of(anotherUser.getId()));
    when(userRepository.findById(eq(anotherUser.getId())))
        .thenReturn(Optional.ofNullable(anotherUser));

    when(commentRepository.findById(eq(article.getId()), eq(comment.getId())))
        .thenReturn(Optional.of(comment));
    String token = jwtService.toToken(anotherUser);
    when(userRepository.findById(eq(anotherUser.getId()))).thenReturn(Optional.of(anotherUser));
    given()
        .header("Authorization", "Token " + token)
        .when()
        .delete("/articles/{slug}/comments/{id}", article.getSlug(), comment.getId())
        .then()
        .statusCode(403);
  }

  @MockBean(ArticleRepository.class)
  ArticleRepository articleRepositoryMock() {
    return mock(ArticleRepository.class);
  }

  @MockBean(CommentRepository.class)
  CommentRepository commentRepositoryMock() {
    return mock(CommentRepository.class);
  }

  @MockBean(CommentQueryService.class)
  CommentQueryService commentQueryServiceMock() {
    return mock(CommentQueryService.class);
  }
}
