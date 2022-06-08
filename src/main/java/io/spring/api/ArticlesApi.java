package io.spring.api;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.spring.api.security.CurrentUser;
import io.spring.api.web.QueryParameters;
import io.spring.api.web.RequestBodyReader;
import io.spring.application.ArticleQueryService;
import io.spring.application.Page;
import io.spring.application.article.ArticleCommandService;
import io.spring.application.article.NewArticleParam;
import io.spring.core.article.Article;
import io.spring.core.user.User;
import java.util.HashMap;
import lombok.AllArgsConstructor;

@Controller("/articles")
@Consumes(MediaType.ALL)
@Produces(MediaType.ALL)
@AllArgsConstructor
public class ArticlesApi {
  private ArticleCommandService articleCommandService;
  private ArticleQueryService articleQueryService;
  private RequestBodyReader requestBodyReader;

  @Post
  public HttpResponse<?> createArticle(
      HttpRequest<?> request, @Nullable @Body byte[] body, @Nullable @CurrentUser User user) {
    NewArticleParam newArticleParam =
        requestBodyReader.readValid(request, body, NewArticleParam.class);
    Article article = articleCommandService.createArticle(newArticleParam, user);
    return HttpResponse.ok(
        new HashMap<String, Object>() {
          {
            put("article", articleQueryService.findById(article.getId(), user).get());
          }
        });
  }

  @Get("/feed")
  public HttpResponse<?> getFeed(HttpRequest<?> request, @Nullable @CurrentUser User user) {
    int offset = QueryParameters.integer(request, "offset", 0);
    int limit = QueryParameters.integer(request, "limit", 20);
    return HttpResponse.ok(articleQueryService.findUserFeed(user, new Page(offset, limit)));
  }

  @Get
  public HttpResponse<?> getArticles(HttpRequest<?> request, @Nullable @CurrentUser User user) {
    int offset = QueryParameters.integer(request, "offset", 0);
    int limit = QueryParameters.integer(request, "limit", 20);
    String tag = QueryParameters.text(request, "tag");
    String favoritedBy = QueryParameters.text(request, "favorited");
    String author = QueryParameters.text(request, "author");
    return HttpResponse.ok(
        articleQueryService.findRecentArticles(
            tag, author, favoritedBy, new Page(offset, limit), user));
  }
}
