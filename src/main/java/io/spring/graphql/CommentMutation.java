package io.spring.graphql;

import graphql.execution.DataFetcherResult;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.idl.RuntimeWiring;
import io.spring.api.exception.NoAuthorizationException;
import io.spring.api.exception.ResourceNotFoundException;
import io.spring.application.CommentQueryService;
import io.spring.application.data.CommentData;
import io.spring.core.article.Article;
import io.spring.core.article.ArticleRepository;
import io.spring.core.comment.Comment;
import io.spring.core.comment.CommentRepository;
import io.spring.core.service.AuthorizationService;
import io.spring.core.user.User;
import io.spring.graphql.exception.AuthenticationException;
import io.spring.graphql.types.CommentPayload;
import io.spring.graphql.types.DeletionStatus;
import jakarta.inject.Singleton;
import lombok.AllArgsConstructor;

@Singleton
@AllArgsConstructor
public class CommentMutation implements GraphQLWiring {

  private ArticleRepository articleRepository;
  private CommentRepository commentRepository;
  private CommentQueryService commentQueryService;

  @Override
  public void wire(RuntimeWiring.Builder wiring) {
    wiring.type(
        "Mutation",
        type ->
            type.dataFetcher(
                    "addComment",
                    env -> createComment(env.getArgument("slug"), env.getArgument("body"), env))
                .dataFetcher(
                    "deleteComment",
                    env -> removeComment(env.getArgument("slug"), env.getArgument("id"), env)));
  }

  public DataFetcherResult<CommentPayload> createComment(
      String slug, String body, DataFetchingEnvironment dfe) {
    User user = SecurityUtil.getCurrentUser(dfe).orElseThrow(AuthenticationException::new);
    Article article =
        articleRepository.findBySlug(slug).orElseThrow(ResourceNotFoundException::new);
    Comment comment = new Comment(body, user.getId(), article.getId());
    commentRepository.save(comment);
    CommentData commentData =
        commentQueryService
            .findById(comment.getId(), user)
            .orElseThrow(ResourceNotFoundException::new);
    return DataFetcherResult.<CommentPayload>newResult()
        .localContext(commentData)
        .data(CommentPayload.newBuilder().build())
        .build();
  }

  public DeletionStatus removeComment(String slug, String commentId, DataFetchingEnvironment dfe) {
    User user = SecurityUtil.getCurrentUser(dfe).orElseThrow(AuthenticationException::new);

    Article article =
        articleRepository.findBySlug(slug).orElseThrow(ResourceNotFoundException::new);
    return commentRepository
        .findById(article.getId(), commentId)
        .map(
            comment -> {
              if (!AuthorizationService.canWriteComment(user, article, comment)) {
                throw new NoAuthorizationException();
              }
              commentRepository.remove(comment);
              return DeletionStatus.newBuilder().success(true).build();
            })
        .orElseThrow(ResourceNotFoundException::new);
  }
}
