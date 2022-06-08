package io.spring.api;

import com.fasterxml.jackson.annotation.JsonRootName;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.spring.api.exception.NoAuthorizationException;
import io.spring.api.exception.ResourceNotFoundException;
import io.spring.api.security.CurrentUser;
import io.spring.api.web.PathSegment;
import io.spring.api.web.RequestBodyReader;
import io.spring.application.CommentQueryService;
import io.spring.application.data.CommentData;
import io.spring.core.article.Article;
import io.spring.core.article.ArticleRepository;
import io.spring.core.comment.Comment;
import io.spring.core.comment.CommentRepository;
import io.spring.core.service.AuthorizationService;
import io.spring.core.user.User;
import jakarta.validation.constraints.NotBlank;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Controller("/articles/{articleSlug:[^/]+}/comments")
@Consumes(MediaType.ALL)
@Produces(MediaType.ALL)
@AllArgsConstructor
public class CommentsApi {
  private ArticleRepository articleRepository;
  private CommentRepository commentRepository;
  private CommentQueryService commentQueryService;
  private RequestBodyReader requestBodyReader;

  @Post
  public HttpResponse<?> createComment(
      HttpRequest<?> request,
      @PathSegment("articleSlug") String slug,
      @Nullable @CurrentUser User user,
      @Nullable @Body byte[] body) {
    NewCommentParam newCommentParam =
        requestBodyReader.readValid(request, body, NewCommentParam.class);
    Article article =
        articleRepository.findBySlug(slug).orElseThrow(ResourceNotFoundException::new);
    Comment comment = new Comment(newCommentParam.getBody(), user.getId(), article.getId());
    commentRepository.save(comment);
    return HttpResponse.status(HttpStatus.CREATED)
        .body(commentResponse(commentQueryService.findById(comment.getId(), user).get()));
  }

  @Get
  public HttpResponse<?> getComments(
      @PathSegment("articleSlug") String slug, @Nullable @CurrentUser User user) {
    Article article =
        articleRepository.findBySlug(slug).orElseThrow(ResourceNotFoundException::new);
    List<CommentData> comments = commentQueryService.findByArticleId(article.getId(), user);
    return HttpResponse.ok(
        new HashMap<String, Object>() {
          {
            put("comments", comments);
          }
        });
  }

  @Delete("/{id:[^/]+}")
  public HttpResponse<?> deleteComment(
      @PathSegment("articleSlug") String slug,
      @PathSegment("id") String commentId,
      @Nullable @CurrentUser User user) {
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
              return HttpResponse.noContent();
            })
        .orElseThrow(ResourceNotFoundException::new);
  }

  private Map<String, Object> commentResponse(CommentData commentData) {
    return new HashMap<String, Object>() {
      {
        put("comment", commentData);
      }
    };
  }
}

@Getter
@NoArgsConstructor
@JsonRootName("comment")
class NewCommentParam {
  @NotBlank(message = "can't be empty")
  private String body;
}
