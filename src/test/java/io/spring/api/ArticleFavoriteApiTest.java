package io.spring.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.spring.application.ArticleQueryService;
import io.spring.application.data.ArticleData;
import io.spring.application.data.ProfileData;
import io.spring.core.article.Article;
import io.spring.core.article.ArticleRepository;
import io.spring.core.article.Tag;
import io.spring.core.favorite.ArticleFavorite;
import io.spring.core.favorite.ArticleFavoriteRepository;
import io.spring.core.user.User;
import jakarta.inject.Inject;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@MicronautTest(environments = "test")
public class ArticleFavoriteApiTest extends TestWithCurrentUser {

  @Inject private ArticleFavoriteRepository articleFavoriteRepository;

  @Inject private ArticleRepository articleRepository;

  @Inject private ArticleQueryService articleQueryService;

  private Article article;

  @BeforeEach
  public void setUp() throws Exception {
    super.setUp();
    User anotherUser = new User("other@test.com", "other", "123", "", "");
    article = new Article("title", "desc", "body", Arrays.asList("java"), anotherUser.getId());
    when(articleRepository.findBySlug(eq(article.getSlug()))).thenReturn(Optional.of(article));
    ArticleData articleData =
        new ArticleData(
            article.getId(),
            article.getSlug(),
            article.getTitle(),
            article.getDescription(),
            article.getBody(),
            true,
            1,
            article.getCreatedAt(),
            article.getUpdatedAt(),
            article.getTags().stream().map(Tag::getName).collect(Collectors.toList()),
            new ProfileData(
                anotherUser.getId(),
                anotherUser.getUsername(),
                anotherUser.getBio(),
                anotherUser.getImage(),
                false));
    when(articleQueryService.findBySlug(eq(articleData.getSlug()), eq(user)))
        .thenReturn(Optional.of(articleData));
  }

  @Test
  public void should_favorite_an_article_success() throws Exception {
    given()
        .header("Authorization", "Token " + token)
        .when()
        .post("/articles/{slug}/favorite", article.getSlug())
        .then()
        .statusCode(200)
        .body("article.id", equalTo(article.getId()));

    verify(articleFavoriteRepository).save(any());
  }

  @Test
  public void should_unfavorite_an_article_success() throws Exception {
    when(articleFavoriteRepository.find(eq(article.getId()), eq(user.getId())))
        .thenReturn(Optional.of(new ArticleFavorite(article.getId(), user.getId())));
    given()
        .header("Authorization", "Token " + token)
        .when()
        .delete("/articles/{slug}/favorite", article.getSlug())
        .then()
        .statusCode(200)
        .body("article.id", equalTo(article.getId()));
    verify(articleFavoriteRepository).remove(new ArticleFavorite(article.getId(), user.getId()));
  }

  @MockBean(ArticleFavoriteRepository.class)
  ArticleFavoriteRepository articleFavoriteRepositoryMock() {
    return mock(ArticleFavoriteRepository.class);
  }

  @MockBean(ArticleRepository.class)
  ArticleRepository articleRepositoryMock() {
    return mock(ArticleRepository.class);
  }

  @MockBean(ArticleQueryService.class)
  ArticleQueryService articleQueryServiceMock() {
    return mock(ArticleQueryService.class);
  }
}
