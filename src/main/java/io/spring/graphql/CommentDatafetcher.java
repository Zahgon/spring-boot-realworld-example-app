package io.spring.graphql;

import graphql.execution.DataFetcherResult;
import graphql.relay.DefaultConnectionCursor;
import graphql.relay.DefaultPageInfo;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.idl.RuntimeWiring;
import io.spring.application.CommentQueryService;
import io.spring.application.CursorPageParameter;
import io.spring.application.CursorPager;
import io.spring.application.CursorPager.Direction;
import io.spring.application.DateTimeCursor;
import io.spring.application.data.ArticleData;
import io.spring.application.data.CommentData;
import io.spring.core.user.User;
import io.spring.graphql.types.Article;
import io.spring.graphql.types.Comment;
import io.spring.graphql.types.CommentEdge;
import io.spring.graphql.types.CommentsConnection;
import jakarta.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import org.joda.time.format.ISODateTimeFormat;

@Singleton
@AllArgsConstructor
public class CommentDatafetcher implements GraphQLWiring {
  private CommentQueryService commentQueryService;

  @Override
  public void wire(RuntimeWiring.Builder wiring) {
    wiring.type("CommentPayload", type -> type.dataFetcher("comment", env -> getComment(env)));
    wiring.type(
        "Article",
        type ->
            type.dataFetcher(
                "comments",
                env ->
                    articleComments(
                        env.getArgument("first"),
                        env.getArgument("after"),
                        env.getArgument("last"),
                        env.getArgument("before"),
                        env)));
  }

  public DataFetcherResult<Comment> getComment(DataFetchingEnvironment dfe) {
    CommentData comment = dfe.getLocalContext();
    Comment commentResult = buildCommentResult(comment);
    return DataFetcherResult.<Comment>newResult()
        .data(commentResult)
        .localContext(
            new HashMap<String, Object>() {
              {
                put(comment.getId(), comment);
              }
            })
        .build();
  }

  public DataFetcherResult<CommentsConnection> articleComments(
      Integer first, String after, Integer last, String before, DataFetchingEnvironment dfe) {

    if (first == null && last == null) {
      throw new IllegalArgumentException("first 和 last 必须只存在一个");
    }

    User current = SecurityUtil.getCurrentUser(dfe).orElse(null);
    Article article = dfe.getSource();
    Map<String, ArticleData> map = dfe.getLocalContext();
    ArticleData articleData = map.get(article.getSlug());

    CursorPager<CommentData> comments;
    if (first != null) {
      comments =
          commentQueryService.findByArticleIdWithCursor(
              articleData.getId(),
              current,
              new CursorPageParameter<>(DateTimeCursor.parse(after), first, Direction.NEXT));
    } else {
      comments =
          commentQueryService.findByArticleIdWithCursor(
              articleData.getId(),
              current,
              new CursorPageParameter<>(DateTimeCursor.parse(before), last, Direction.PREV));
    }
    graphql.relay.PageInfo pageInfo = buildCommentPageInfo(comments);
    CommentsConnection result =
        CommentsConnection.newBuilder()
            .pageInfo(pageInfo)
            .edges(
                comments.getData().stream()
                    .map(
                        a ->
                            CommentEdge.newBuilder()
                                .cursor(a.getCursor().toString())
                                .node(buildCommentResult(a))
                                .build())
                    .collect(Collectors.toList()))
            .build();
    return DataFetcherResult.<CommentsConnection>newResult()
        .data(result)
        .localContext(
            comments.getData().stream().collect(Collectors.toMap(CommentData::getId, c -> c)))
        .build();
  }

  private DefaultPageInfo buildCommentPageInfo(CursorPager<CommentData> comments) {
    return new DefaultPageInfo(
        comments.getStartCursor() == null
            ? null
            : new DefaultConnectionCursor(comments.getStartCursor().toString()),
        comments.getEndCursor() == null
            ? null
            : new DefaultConnectionCursor(comments.getEndCursor().toString()),
        comments.hasPrevious(),
        comments.hasNext());
  }

  private Comment buildCommentResult(CommentData comment) {
    return Comment.newBuilder()
        .id(comment.getId())
        .body(comment.getBody())
        .updatedAt(ISODateTimeFormat.dateTime().withZoneUTC().print(comment.getCreatedAt()))
        .createdAt(ISODateTimeFormat.dateTime().withZoneUTC().print(comment.getCreatedAt()))
        .build();
  }
}
