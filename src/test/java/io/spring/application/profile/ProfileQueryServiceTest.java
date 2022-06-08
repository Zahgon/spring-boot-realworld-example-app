package io.spring.application.profile;

import io.spring.application.ProfileQueryService;
import io.spring.application.data.ProfileData;
import io.spring.core.user.User;
import io.spring.core.user.UserRepository;
import io.spring.infrastructure.DbTestBase;
import jakarta.inject.Inject;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ProfileQueryServiceTest extends DbTestBase {
  @Inject private ProfileQueryService profileQueryService;
  @Inject private UserRepository userRepository;

  @Test
  public void should_fetch_profile_success() {
    User currentUser = new User("a@test.com", "a", "123", "", "");
    User profileUser = new User("p@test.com", "p", "123", "", "");
    userRepository.save(profileUser);

    Optional<ProfileData> optional =
        profileQueryService.findByUsername(profileUser.getUsername(), currentUser);
    Assertions.assertTrue(optional.isPresent());
  }
}
