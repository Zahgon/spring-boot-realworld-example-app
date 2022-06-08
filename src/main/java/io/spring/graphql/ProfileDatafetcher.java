package io.spring.graphql;

import graphql.schema.DataFetchingEnvironment;
import graphql.schema.idl.RuntimeWiring;
import io.spring.api.exception.ResourceNotFoundException;
import io.spring.application.ProfileQueryService;
import io.spring.application.data.ArticleData;
import io.spring.application.data.CommentData;
import io.spring.application.data.ProfileData;
import io.spring.core.user.User;
import io.spring.graphql.types.Article;
import io.spring.graphql.types.Comment;
import io.spring.graphql.types.Profile;
import io.spring.graphql.types.ProfilePayload;
import jakarta.inject.Singleton;
import java.util.Map;
import lombok.AllArgsConstructor;

@Singleton
@AllArgsConstructor
public class ProfileDatafetcher implements GraphQLWiring {

  private ProfileQueryService profileQueryService;

  @Override
  public void wire(RuntimeWiring.Builder wiring) {
    wiring.type("User", type -> type.dataFetcher("profile", env -> getUserProfile(env)));
    wiring.type("Article", type -> type.dataFetcher("author", env -> getAuthor(env)));
    wiring.type("Comment", type -> type.dataFetcher("author", env -> getCommentAuthor(env)));
    wiring.type(
        "Query",
        type -> type.dataFetcher("profile", env -> queryProfile(env.getArgument("username"), env)));
  }

  public Profile getUserProfile(DataFetchingEnvironment dataFetchingEnvironment) {
    User user = dataFetchingEnvironment.getLocalContext();
    String username = user.getUsername();
    return queryProfile(
        username, SecurityUtil.getCurrentUser(dataFetchingEnvironment).orElse(null));
  }

  public Profile getAuthor(DataFetchingEnvironment dataFetchingEnvironment) {
    Map<String, ArticleData> map = dataFetchingEnvironment.getLocalContext();
    Article article = dataFetchingEnvironment.getSource();
    return queryProfile(
        map.get(article.getSlug()).getProfileData().getUsername(),
        SecurityUtil.getCurrentUser(dataFetchingEnvironment).orElse(null));
  }

  public Profile getCommentAuthor(DataFetchingEnvironment dataFetchingEnvironment) {
    Comment comment = dataFetchingEnvironment.getSource();
    Map<String, CommentData> map = dataFetchingEnvironment.getLocalContext();
    return queryProfile(
        map.get(comment.getId()).getProfileData().getUsername(),
        SecurityUtil.getCurrentUser(dataFetchingEnvironment).orElse(null));
  }

  public ProfilePayload queryProfile(
      String username, DataFetchingEnvironment dataFetchingEnvironment) {
    Profile profile =
        queryProfile(
            dataFetchingEnvironment.getArgument("username"),
            SecurityUtil.getCurrentUser(dataFetchingEnvironment).orElse(null));
    return ProfilePayload.newBuilder().profile(profile).build();
  }

  private Profile queryProfile(String username, User current) {
    ProfileData profileData =
        profileQueryService
            .findByUsername(username, current)
            .orElseThrow(ResourceNotFoundException::new);
    return Profile.newBuilder()
        .username(profileData.getUsername())
        .bio(profileData.getBio())
        .image(profileData.getImage())
        .following(profileData.isFollowing())
        .build();
  }
}
