package io.spring.api;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.Put;
import io.spring.api.exception.NoAuthorizationException;
import io.spring.api.exception.ResourceNotFoundException;
import io.spring.api.security.CurrentUser;
import io.spring.api.web.PathSegment;
import io.spring.api.web.RequestBodyReader;
import io.spring.application.ArticleQueryService;
import io.spring.application.article.ArticleCommandService;
import io.spring.application.article.UpdateArticleParam;
import io.spring.application.data.ArticleData;
import io.spring.core.article.Article;
import io.spring.core.article.ArticleRepository;
import io.spring.core.service.AuthorizationService;
import io.spring.core.user.User;
import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;

@Controller("/articles/{articleSlug:[^/]+}")
@Consumes(MediaType.ALL)
@Produces(MediaType.ALL)
@AllArgsConstructor
public class ArticleApi {
  private ArticleQueryService articleQueryService;
  private ArticleRepository articleRepository;
  private ArticleCommandService articleCommandService;
  private RequestBodyReader requestBodyReader;

  @Get
  public HttpResponse<?> article(
      @PathSegment("articleSlug") String slug, @Nullable @CurrentUser User user) {
    return articleQueryService
        .findBySlug(slug, user)
        .map(articleData -> HttpResponse.ok(articleResponse(articleData)))
        .orElseThrow(ResourceNotFoundException::new);
  }

  @Put
  public HttpResponse<?> updateArticle(
      HttpRequest<?> request,
      @PathSegment("articleSlug") String slug,
      @Nullable @CurrentUser User user,
      @Nullable @Body byte[] body) {
    UpdateArticleParam updateArticleParam =
        requestBodyReader.readValid(request, body, UpdateArticleParam.class);
    return articleRepository
        .findBySlug(slug)
        .map(
            article -> {
              if (!AuthorizationService.canWriteArticle(user, article)) {
                throw new NoAuthorizationException();
              }
              Article updatedArticle =
                  articleCommandService.updateArticle(article, updateArticleParam);
              return HttpResponse.ok(
                  articleResponse(
                      articleQueryService.findBySlug(updatedArticle.getSlug(), user).get()));
            })
        .orElseThrow(ResourceNotFoundException::new);
  }

  @Delete
  public HttpResponse<?> deleteArticle(
      @PathSegment("articleSlug") String slug, @Nullable @CurrentUser User user) {
    return articleRepository
        .findBySlug(slug)
        .map(
            article -> {
              if (!AuthorizationService.canWriteArticle(user, article)) {
                throw new NoAuthorizationException();
              }
              articleRepository.remove(article);
              return HttpResponse.noContent();
            })
        .orElseThrow(ResourceNotFoundException::new);
  }

  private Map<String, Object> articleResponse(ArticleData articleData) {
    return new HashMap<String, Object>() {
      {
        put("article", articleData);
      }
    };
  }
}
