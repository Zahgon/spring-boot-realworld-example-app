package io.spring.api;

import static io.restassured.RestAssured.given;
import static io.spring.TestHelper.articleDataFixture;
import static java.util.Arrays.asList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.restassured.RestAssured;
import io.spring.application.ArticleQueryService;
import io.spring.application.Page;
import io.spring.application.article.ArticleCommandService;
import io.spring.application.data.ArticleDataList;
import io.spring.core.article.ArticleRepository;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@MicronautTest(environments = "test")
public class ListArticleApiTest extends TestWithCurrentUser {
  @Inject private ArticleRepository articleRepository;

  @Inject private ArticleQueryService articleQueryService;

  @Inject private ArticleCommandService articleCommandService;

  @Override
  @BeforeEach
  public void setUp() throws Exception {
    super.setUp();
  }

  @Test
  public void should_get_default_article_list() throws Exception {
    ArticleDataList articleDataList =
        new ArticleDataList(
            asList(articleDataFixture("1", user), articleDataFixture("2", user)), 2);
    when(articleQueryService.findRecentArticles(
            eq(null), eq(null), eq(null), eq(new Page(0, 20)), eq(null)))
        .thenReturn(articleDataList);
    RestAssured.when().get("/articles").then().statusCode(200);
  }

  @Test
  public void should_get_feeds_401_without_login() throws Exception {
    RestAssured.when().get("/articles/feed").then().statusCode(401);
  }

  @Test
  public void should_get_feeds_success() throws Exception {
    ArticleDataList articleDataList =
        new ArticleDataList(
            asList(articleDataFixture("1", user), articleDataFixture("2", user)), 2);
    when(articleQueryService.findUserFeed(eq(user), eq(new Page(0, 20))))
        .thenReturn(articleDataList);

    given()
        .header("Authorization", "Token " + token)
        .when()
        .get("/articles/feed")
        .then()
        .statusCode(200);
  }

  @MockBean(ArticleRepository.class)
  ArticleRepository articleRepositoryMock() {
    return mock(ArticleRepository.class);
  }

  @MockBean(ArticleQueryService.class)
  ArticleQueryService articleQueryServiceMock() {
    return mock(ArticleQueryService.class);
  }

  @MockBean(ArticleCommandService.class)
  ArticleCommandService articleCommandServiceMock() {
    return mock(ArticleCommandService.class);
  }
}
