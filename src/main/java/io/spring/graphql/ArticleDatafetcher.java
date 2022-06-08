package io.spring.graphql;

import graphql.execution.DataFetcherResult;
import graphql.relay.DefaultConnectionCursor;
import graphql.relay.DefaultPageInfo;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.idl.RuntimeWiring;
import io.spring.api.exception.ResourceNotFoundException;
import io.spring.application.ArticleQueryService;
import io.spring.application.CursorPageParameter;
import io.spring.application.CursorPager;
import io.spring.application.CursorPager.Direction;
import io.spring.application.DateTimeCursor;
import io.spring.application.data.ArticleData;
import io.spring.application.data.CommentData;
import io.spring.core.user.User;
import io.spring.core.user.UserRepository;
import io.spring.graphql.types.Article;
import io.spring.graphql.types.ArticleEdge;
import io.spring.graphql.types.ArticlesConnection;
import io.spring.graphql.types.Profile;
import jakarta.inject.Singleton;
import java.util.HashMap;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import org.joda.time.format.ISODateTimeFormat;

@Singleton
@AllArgsConstructor
public class ArticleDatafetcher implements GraphQLWiring {

  private ArticleQueryService articleQueryService;
  private UserRepository userRepository;

  @Override
  public void wire(RuntimeWiring.Builder wiring) {
    wiring.type(
        "Query",
        type ->
            type.dataFetcher(
                    "feed",
                    env ->
                        getFeed(
                            env.getArgument("first"),
                            env.getArgument("after"),
                            env.getArgument("last"),
                            env.getArgument("before"),
                            env))
                .dataFetcher(
                    "articles",
                    env ->
                        getArticles(
                            env.getArgument("first"),
                            env.getArgument("after"),
                            env.getArgument("last"),
                            env.getArgument("before"),
                            env.getArgument("authoredBy"),
                            env.getArgument("favoritedBy"),
                            env.getArgument("withTag"),
                            env))
                .dataFetcher("article", env -> findArticleBySlug(env.getArgument("slug"), env)));
    wiring.type(
        "Profile",
        type ->
            type.dataFetcher(
                    "feed",
                    env ->
                        userFeed(
                            env.getArgument("first"),
                            env.getArgument("after"),
                            env.getArgument("last"),
                            env.getArgument("before"),
                            env))
                .dataFetcher(
                    "favorites",
                    env ->
                        userFavorites(
                            env.getArgument("first"),
                            env.getArgument("after"),
                            env.getArgument("last"),
                            env.getArgument("before"),
                            env))
                .dataFetcher(
                    "articles",
                    env ->
                        userArticles(
                            env.getArgument("first"),
                            env.getArgument("after"),
                            env.getArgument("last"),
                            env.getArgument("before"),
                            env)));
    wiring.type("ArticlePayload", type -> type.dataFetcher("article", env -> getArticle(env)));
    wiring.type("Comment", type -> type.dataFetcher("article", env -> getCommentArticle(env)));
  }

  public DataFetcherResult<ArticlesConnection> getFeed(
      Integer first, String after, Integer last, String before, DataFetchingEnvironment dfe) {
    if (first == null && last == null) {
      throw new IllegalArgumentException("first 和 last 必须只存在一个");
    }

    User current = SecurityUtil.getCurrentUser(dfe).orElse(null);

    CursorPager<ArticleData> articles;
    if (first != null) {
      articles =
          articleQueryService.findUserFeedWithCursor(
              current,
              new CursorPageParameter<>(DateTimeCursor.parse(after), first, Direction.NEXT));
    } else {
      articles =
          articleQueryService.findUserFeedWithCursor(
              current,
              new CursorPageParameter<>(DateTimeCursor.parse(before), last, Direction.PREV));
    }
    graphql.relay.PageInfo pageInfo = buildArticlePageInfo(articles);
    ArticlesConnection articlesConnection =
        ArticlesConnection.newBuilder()
            .pageInfo(pageInfo)
            .edges(
                articles.getData().stream()
                    .map(
                        a ->
                            ArticleEdge.newBuilder()
                                .cursor(a.getCursor().toString())
                                .node(buildArticleResult(a))
                                .build())
                    .collect(Collectors.toList()))
            .build();
    return DataFetcherResult.<ArticlesConnection>newResult()
        .data(articlesConnection)
        .localContext(
            articles.getData().stream().collect(Collectors.toMap(ArticleData::getSlug, a -> a)))
        .build();
  }

  public DataFetcherResult<ArticlesConnection> userFeed(
      Integer first, String after, Integer last, String before, DataFetchingEnvironment dfe) {
    if (first == null && last == null) {
      throw new IllegalArgumentException("first 和 last 必须只存在一个");
    }

    Profile profile = dfe.getSource();
    User target =
        userRepository
            .findByUsername(profile.getUsername())
            .orElseThrow(ResourceNotFoundException::new);

    CursorPager<ArticleData> articles;
    if (first != null) {
      articles =
          articleQueryService.findUserFeedWithCursor(
              target,
              new CursorPageParameter<>(DateTimeCursor.parse(after), first, Direction.NEXT));
    } else {
      articles =
          articleQueryService.findUserFeedWithCursor(
              target,
              new CursorPageParameter<>(DateTimeCursor.parse(before), last, Direction.PREV));
    }
    graphql.relay.PageInfo pageInfo = buildArticlePageInfo(articles);
    ArticlesConnection articlesConnection =
        ArticlesConnection.newBuilder()
            .pageInfo(pageInfo)
            .edges(
                articles.getData().stream()
                    .map(
                        a ->
                            ArticleEdge.newBuilder()
                                .cursor(a.getCursor().toString())
                                .node(buildArticleResult(a))
                                .build())
                    .collect(Collectors.toList()))
            .build();
    return DataFetcherResult.<ArticlesConnection>newResult()
        .data(articlesConnection)
        .localContext(
            articles.getData().stream().collect(Collectors.toMap(ArticleData::getSlug, a -> a)))
        .build();
  }

  public DataFetcherResult<ArticlesConnection> userFavorites(
      Integer first, String after, Integer last, String before, DataFetchingEnvironment dfe) {
    if (first == null && last == null) {
      throw new IllegalArgumentException("first 和 last 必须只存在一个");
    }

    User current = SecurityUtil.getCurrentUser(dfe).orElse(null);
    Profile profile = dfe.getSource();

    CursorPager<ArticleData> articles;
    if (first != null) {
      articles =
          articleQueryService.findRecentArticlesWithCursor(
              null,
              null,
              profile.getUsername(),
              new CursorPageParameter<>(DateTimeCursor.parse(after), first, Direction.NEXT),
              current);
    } else {
      articles =
          articleQueryService.findRecentArticlesWithCursor(
              null,
              null,
              profile.getUsername(),
              new CursorPageParameter<>(DateTimeCursor.parse(before), last, Direction.PREV),
              current);
    }
    graphql.relay.PageInfo pageInfo = buildArticlePageInfo(articles);

    ArticlesConnection articlesConnection =
        ArticlesConnection.newBuilder()
            .pageInfo(pageInfo)
            .edges(
                articles.getData().stream()
                    .map(
                        a ->
                            ArticleEdge.newBuilder()
                                .cursor(a.getCursor().toString())
                                .node(buildArticleResult(a))
                                .build())
                    .collect(Collectors.toList()))
            .build();
    return DataFetcherResult.<ArticlesConnection>newResult()
        .data(articlesConnection)
        .localContext(
            articles.getData().stream().collect(Collectors.toMap(ArticleData::getSlug, a -> a)))
        .build();
  }

  public DataFetcherResult<ArticlesConnection> userArticles(
      Integer first, String after, Integer last, String before, DataFetchingEnvironment dfe) {
    if (first == null && last == null) {
      throw new IllegalArgumentException("first 和 last 必须只存在一个");
    }

    User current = SecurityUtil.getCurrentUser(dfe).orElse(null);
    Profile profile = dfe.getSource();

    CursorPager<ArticleData> articles;
    if (first != null) {
      articles =
          articleQueryService.findRecentArticlesWithCursor(
              null,
              profile.getUsername(),
              null,
              new CursorPageParameter<>(DateTimeCursor.parse(after), first, Direction.NEXT),
              current);
    } else {
      articles =
          articleQueryService.findRecentArticlesWithCursor(
              null,
              profile.getUsername(),
              null,
              new CursorPageParameter<>(DateTimeCursor.parse(before), last, Direction.PREV),
              current);
    }
    graphql.relay.PageInfo pageInfo = buildArticlePageInfo(articles);
    ArticlesConnection articlesConnection =
        ArticlesConnection.newBuilder()
            .pageInfo(pageInfo)
            .edges(
                articles.getData().stream()
                    .map(
                        a ->
                            ArticleEdge.newBuilder()
                                .cursor(a.getCursor().toString())
                                .node(buildArticleResult(a))
                                .build())
                    .collect(Collectors.toList()))
            .build();
    return DataFetcherResult.<ArticlesConnection>newResult()
        .data(articlesConnection)
        .localContext(
            articles.getData().stream().collect(Collectors.toMap(ArticleData::getSlug, a -> a)))
        .build();
  }

  public DataFetcherResult<ArticlesConnection> getArticles(
      Integer first,
      String after,
      Integer last,
      String before,
      String authoredBy,
      String favoritedBy,
      String withTag,
      DataFetchingEnvironment dfe) {
    if (first == null && last == null) {
      throw new IllegalArgumentException("first 和 last 必须只存在一个");
    }

    User current = SecurityUtil.getCurrentUser(dfe).orElse(null);

    CursorPager<ArticleData> articles;
    if (first != null) {
      articles =
          articleQueryService.findRecentArticlesWithCursor(
              withTag,
              authoredBy,
              favoritedBy,
              new CursorPageParameter<>(DateTimeCursor.parse(after), first, Direction.NEXT),
              current);
    } else {
      articles =
          articleQueryService.findRecentArticlesWithCursor(
              withTag,
              authoredBy,
              favoritedBy,
              new CursorPageParameter<>(DateTimeCursor.parse(before), last, Direction.PREV),
              current);
    }
    graphql.relay.PageInfo pageInfo = buildArticlePageInfo(articles);
    ArticlesConnection articlesConnection =
        ArticlesConnection.newBuilder()
            .pageInfo(pageInfo)
            .edges(
                articles.getData().stream()
                    .map(
                        a ->
                            ArticleEdge.newBuilder()
                                .cursor(a.getCursor().toString())
                                .node(buildArticleResult(a))
                                .build())
                    .collect(Collectors.toList()))
            .build();
    return DataFetcherResult.<ArticlesConnection>newResult()
        .data(articlesConnection)
        .localContext(
            articles.getData().stream().collect(Collectors.toMap(ArticleData::getSlug, a -> a)))
        .build();
  }

  public DataFetcherResult<Article> getArticle(DataFetchingEnvironment dfe) {
    io.spring.core.article.Article article = dfe.getLocalContext();

    User current = SecurityUtil.getCurrentUser(dfe).orElse(null);
    ArticleData articleData =
        articleQueryService
            .findById(article.getId(), current)
            .orElseThrow(ResourceNotFoundException::new);
    Article articleResult = buildArticleResult(articleData);
    return DataFetcherResult.<Article>newResult()
        .localContext(
            new HashMap<String, Object>() {
              {
                put(articleData.getSlug(), articleData);
              }
            })
        .data(articleResult)
        .build();
  }

  public DataFetcherResult<Article> getCommentArticle(
      DataFetchingEnvironment dataFetchingEnvironment) {
    CommentData comment = dataFetchingEnvironment.getLocalContext();
    User current = SecurityUtil.getCurrentUser(dataFetchingEnvironment).orElse(null);
    ArticleData articleData =
        articleQueryService
            .findById(comment.getArticleId(), current)
            .orElseThrow(ResourceNotFoundException::new);
    Article articleResult = buildArticleResult(articleData);
    return DataFetcherResult.<Article>newResult()
        .localContext(
            new HashMap<String, Object>() {
              {
                put(articleData.getSlug(), articleData);
              }
            })
        .data(articleResult)
        .build();
  }

  public DataFetcherResult<Article> findArticleBySlug(String slug, DataFetchingEnvironment dfe) {
    User current = SecurityUtil.getCurrentUser(dfe).orElse(null);
    ArticleData articleData =
        articleQueryService.findBySlug(slug, current).orElseThrow(ResourceNotFoundException::new);
    Article articleResult = buildArticleResult(articleData);
    return DataFetcherResult.<Article>newResult()
        .localContext(
            new HashMap<String, Object>() {
              {
                put(articleData.getSlug(), articleData);
              }
            })
        .data(articleResult)
        .build();
  }

  private DefaultPageInfo buildArticlePageInfo(CursorPager<ArticleData> articles) {
    return new DefaultPageInfo(
        articles.getStartCursor() == null
            ? null
            : new DefaultConnectionCursor(articles.getStartCursor().toString()),
        articles.getEndCursor() == null
            ? null
            : new DefaultConnectionCursor(articles.getEndCursor().toString()),
        articles.hasPrevious(),
        articles.hasNext());
  }

  private Article buildArticleResult(ArticleData articleData) {
    return Article.newBuilder()
        .body(articleData.getBody())
        .createdAt(ISODateTimeFormat.dateTime().withZoneUTC().print(articleData.getCreatedAt()))
        .description(articleData.getDescription())
        .favorited(articleData.isFavorited())
        .favoritesCount(articleData.getFavoritesCount())
        .slug(articleData.getSlug())
        .tagList(articleData.getTagList())
        .title(articleData.getTitle())
        .updatedAt(ISODateTimeFormat.dateTime().withZoneUTC().print(articleData.getUpdatedAt()))
        .build();
  }
}
