package io.spring.application;

import io.spring.application.data.UserData;
import io.spring.infrastructure.mybatis.readservice.UserReadService;
import jakarta.inject.Singleton;
import java.util.Optional;
import lombok.AllArgsConstructor;

@Singleton
@AllArgsConstructor
public class UserQueryService {
  private UserReadService userReadService;

  public Optional<UserData> findById(String id) {
    return Optional.ofNullable(userReadService.findById(id));
  }
}
