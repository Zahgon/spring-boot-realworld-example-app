package io.spring.graphql;

import graphql.schema.DataFetchingEnvironment;
import graphql.schema.idl.RuntimeWiring;
import io.spring.api.exception.ResourceNotFoundException;
import io.spring.application.ProfileQueryService;
import io.spring.application.data.ProfileData;
import io.spring.core.user.FollowRelation;
import io.spring.core.user.User;
import io.spring.core.user.UserRepository;
import io.spring.graphql.exception.AuthenticationException;
import io.spring.graphql.types.Profile;
import io.spring.graphql.types.ProfilePayload;
import jakarta.inject.Singleton;
import lombok.AllArgsConstructor;

@Singleton
@AllArgsConstructor
public class RelationMutation implements GraphQLWiring {

  private UserRepository userRepository;
  private ProfileQueryService profileQueryService;

  @Override
  public void wire(RuntimeWiring.Builder wiring) {
    wiring.type(
        "Mutation",
        type ->
            type.dataFetcher("followUser", env -> follow(env.getArgument("username"), env))
                .dataFetcher("unfollowUser", env -> unfollow(env.getArgument("username"), env)));
  }

  public ProfilePayload follow(String username, DataFetchingEnvironment dfe) {
    User user = SecurityUtil.getCurrentUser(dfe).orElseThrow(AuthenticationException::new);
    return userRepository
        .findByUsername(username)
        .map(
            target -> {
              FollowRelation followRelation = new FollowRelation(user.getId(), target.getId());
              userRepository.saveRelation(followRelation);
              Profile profile = buildProfile(username, user);
              return ProfilePayload.newBuilder().profile(profile).build();
            })
        .orElseThrow(ResourceNotFoundException::new);
  }

  public ProfilePayload unfollow(String username, DataFetchingEnvironment dfe) {
    User user = SecurityUtil.getCurrentUser(dfe).orElseThrow(AuthenticationException::new);
    User target =
        userRepository.findByUsername(username).orElseThrow(ResourceNotFoundException::new);
    return userRepository
        .findRelation(user.getId(), target.getId())
        .map(
            relation -> {
              userRepository.removeRelation(relation);
              Profile profile = buildProfile(username, user);
              return ProfilePayload.newBuilder().profile(profile).build();
            })
        .orElseThrow(ResourceNotFoundException::new);
  }

  private Profile buildProfile(String username, User current) {
    ProfileData profileData = profileQueryService.findByUsername(username, current).get();
    return Profile.newBuilder()
        .username(profileData.getUsername())
        .bio(profileData.getBio())
        .image(profileData.getImage())
        .following(profileData.isFollowing())
        .build();
  }
}
