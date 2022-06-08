package io.spring.api;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.spring.api.exception.ResourceNotFoundException;
import io.spring.api.security.CurrentUser;
import io.spring.api.web.PathSegment;
import io.spring.application.ArticleQueryService;
import io.spring.application.data.ArticleData;
import io.spring.core.article.Article;
import io.spring.core.article.ArticleRepository;
import io.spring.core.favorite.ArticleFavorite;
import io.spring.core.favorite.ArticleFavoriteRepository;
import io.spring.core.user.User;
import java.util.HashMap;
import lombok.AllArgsConstructor;

@Controller("/articles/{articleSlug:[^/]+}/favorite")
@Consumes(MediaType.ALL)
@Produces(MediaType.ALL)
@AllArgsConstructor
public class ArticleFavoriteApi {
  private ArticleFavoriteRepository articleFavoriteRepository;
  private ArticleRepository articleRepository;
  private ArticleQueryService articleQueryService;

  @Post
  public HttpResponse<?> favoriteArticle(
      @PathSegment("articleSlug") String slug, @Nullable @CurrentUser User user) {
    Article article =
        articleRepository.findBySlug(slug).orElseThrow(ResourceNotFoundException::new);
    ArticleFavorite articleFavorite = new ArticleFavorite(article.getId(), user.getId());
    articleFavoriteRepository.save(articleFavorite);
    return responseArticleData(articleQueryService.findBySlug(slug, user).get());
  }

  @Delete
  public HttpResponse<?> unfavoriteArticle(
      @PathSegment("articleSlug") String slug, @Nullable @CurrentUser User user) {
    Article article =
        articleRepository.findBySlug(slug).orElseThrow(ResourceNotFoundException::new);
    articleFavoriteRepository
        .find(article.getId(), user.getId())
        .ifPresent(
            favorite -> {
              articleFavoriteRepository.remove(favorite);
            });
    return responseArticleData(articleQueryService.findBySlug(slug, user).get());
  }

  private HttpResponse<HashMap<String, Object>> responseArticleData(final ArticleData articleData) {
    return HttpResponse.ok(
        new HashMap<String, Object>() {
          {
            put("article", articleData);
          }
        });
  }
}
