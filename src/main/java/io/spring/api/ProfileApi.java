package io.spring.api;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.spring.api.exception.ResourceNotFoundException;
import io.spring.api.security.CurrentUser;
import io.spring.api.web.PathSegment;
import io.spring.application.ProfileQueryService;
import io.spring.application.data.ProfileData;
import io.spring.core.user.FollowRelation;
import io.spring.core.user.User;
import io.spring.core.user.UserRepository;
import java.util.HashMap;
import java.util.Optional;
import lombok.AllArgsConstructor;

@Controller("/profiles/{profileUsername:[^/]+}")
@Consumes(MediaType.ALL)
@Produces(MediaType.ALL)
@AllArgsConstructor
public class ProfileApi {
  private ProfileQueryService profileQueryService;
  private UserRepository userRepository;

  @Get
  public HttpResponse<?> getProfile(
      @PathSegment("profileUsername") String username, @Nullable @CurrentUser User user) {
    return profileQueryService
        .findByUsername(username, user)
        .map(this::profileResponse)
        .orElseThrow(ResourceNotFoundException::new);
  }

  @Post("/follow")
  public HttpResponse<?> follow(
      @PathSegment("profileUsername") String username, @Nullable @CurrentUser User user) {
    return userRepository
        .findByUsername(username)
        .map(
            target -> {
              FollowRelation followRelation = new FollowRelation(user.getId(), target.getId());
              userRepository.saveRelation(followRelation);
              return profileResponse(profileQueryService.findByUsername(username, user).get());
            })
        .orElseThrow(ResourceNotFoundException::new);
  }

  @Delete("/follow")
  public HttpResponse<?> unfollow(
      @PathSegment("profileUsername") String username, @Nullable @CurrentUser User user) {
    Optional<User> userOptional = userRepository.findByUsername(username);
    if (userOptional.isPresent()) {
      User target = userOptional.get();
      return userRepository
          .findRelation(user.getId(), target.getId())
          .map(
              relation -> {
                userRepository.removeRelation(relation);
                return profileResponse(profileQueryService.findByUsername(username, user).get());
              })
          .orElseThrow(ResourceNotFoundException::new);
    } else {
      throw new ResourceNotFoundException();
    }
  }

  private HttpResponse<?> profileResponse(ProfileData profile) {
    return HttpResponse.ok(
        new HashMap<String, Object>() {
          {
            put("profile", profile);
          }
        });
  }
}
