package io.spring.graphql;

import graphql.execution.DataFetcherResult;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.idl.RuntimeWiring;
import io.spring.api.exception.NoAuthorizationException;
import io.spring.api.exception.ResourceNotFoundException;
import io.spring.application.article.ArticleCommandService;
import io.spring.application.article.NewArticleParam;
import io.spring.application.article.UpdateArticleParam;
import io.spring.core.article.Article;
import io.spring.core.article.ArticleRepository;
import io.spring.core.favorite.ArticleFavorite;
import io.spring.core.favorite.ArticleFavoriteRepository;
import io.spring.core.service.AuthorizationService;
import io.spring.core.user.User;
import io.spring.graphql.exception.AuthenticationException;
import io.spring.graphql.types.ArticlePayload;
import io.spring.graphql.types.CreateArticleInput;
import io.spring.graphql.types.DeletionStatus;
import io.spring.graphql.types.UpdateArticleInput;
import jakarta.inject.Singleton;
import java.util.Collections;
import lombok.AllArgsConstructor;

@Singleton
@AllArgsConstructor
public class ArticleMutation implements GraphQLWiring {

  private ArticleCommandService articleCommandService;
  private ArticleFavoriteRepository articleFavoriteRepository;
  private ArticleRepository articleRepository;

  @Override
  public void wire(RuntimeWiring.Builder wiring) {
    wiring.type(
        "Mutation",
        type ->
            type.dataFetcher(
                    "createArticle",
                    env ->
                        createArticle(
                            GraphQLWiring.inputArgument(env, "input", CreateArticleInput.class),
                            env))
                .dataFetcher(
                    "updateArticle",
                    env ->
                        updateArticle(
                            env.getArgument("slug"),
                            GraphQLWiring.inputArgument(env, "changes", UpdateArticleInput.class),
                            env))
                .dataFetcher(
                    "favoriteArticle", env -> favoriteArticle(env.getArgument("slug"), env))
                .dataFetcher(
                    "unfavoriteArticle", env -> unfavoriteArticle(env.getArgument("slug"), env))
                .dataFetcher("deleteArticle", env -> deleteArticle(env.getArgument("slug"), env)));
  }

  public DataFetcherResult<ArticlePayload> createArticle(
      CreateArticleInput input, DataFetchingEnvironment dfe) {
    User user = SecurityUtil.getCurrentUser(dfe).orElseThrow(AuthenticationException::new);
    NewArticleParam newArticleParam =
        NewArticleParam.builder()
            .title(input.getTitle())
            .description(input.getDescription())
            .body(input.getBody())
            .tagList(input.getTagList() == null ? Collections.emptyList() : input.getTagList())
            .build();
    Article article = articleCommandService.createArticle(newArticleParam, user);
    return DataFetcherResult.<ArticlePayload>newResult()
        .data(ArticlePayload.newBuilder().build())
        .localContext(article)
        .build();
  }

  public DataFetcherResult<ArticlePayload> updateArticle(
      String slug, UpdateArticleInput params, DataFetchingEnvironment dfe) {
    Article article =
        articleRepository.findBySlug(slug).orElseThrow(ResourceNotFoundException::new);
    User user = SecurityUtil.getCurrentUser(dfe).orElseThrow(AuthenticationException::new);
    if (!AuthorizationService.canWriteArticle(user, article)) {
      throw new NoAuthorizationException();
    }
    article =
        articleCommandService.updateArticle(
            article,
            new UpdateArticleParam(params.getTitle(), params.getBody(), params.getDescription()));
    return DataFetcherResult.<ArticlePayload>newResult()
        .data(ArticlePayload.newBuilder().build())
        .localContext(article)
        .build();
  }

  public DataFetcherResult<ArticlePayload> favoriteArticle(
      String slug, DataFetchingEnvironment dfe) {
    User user = SecurityUtil.getCurrentUser(dfe).orElseThrow(AuthenticationException::new);
    Article article =
        articleRepository.findBySlug(slug).orElseThrow(ResourceNotFoundException::new);
    ArticleFavorite articleFavorite = new ArticleFavorite(article.getId(), user.getId());
    articleFavoriteRepository.save(articleFavorite);
    return DataFetcherResult.<ArticlePayload>newResult()
        .data(ArticlePayload.newBuilder().build())
        .localContext(article)
        .build();
  }

  public DataFetcherResult<ArticlePayload> unfavoriteArticle(
      String slug, DataFetchingEnvironment dfe) {
    User user = SecurityUtil.getCurrentUser(dfe).orElseThrow(AuthenticationException::new);
    Article article =
        articleRepository.findBySlug(slug).orElseThrow(ResourceNotFoundException::new);
    articleFavoriteRepository
        .find(article.getId(), user.getId())
        .ifPresent(
            favorite -> {
              articleFavoriteRepository.remove(favorite);
            });
    return DataFetcherResult.<ArticlePayload>newResult()
        .data(ArticlePayload.newBuilder().build())
        .localContext(article)
        .build();
  }

  public DeletionStatus deleteArticle(String slug, DataFetchingEnvironment dfe) {
    User user = SecurityUtil.getCurrentUser(dfe).orElseThrow(AuthenticationException::new);
    Article article =
        articleRepository.findBySlug(slug).orElseThrow(ResourceNotFoundException::new);

    if (!AuthorizationService.canWriteArticle(user, article)) {
      throw new NoAuthorizationException();
    }

    articleRepository.remove(article);
    return DeletionStatus.newBuilder().success(true).build();
  }
}
