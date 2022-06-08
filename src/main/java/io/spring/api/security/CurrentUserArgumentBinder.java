package io.spring.api.security;

import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.bind.binders.AnnotatedRequestArgumentBinder;
import io.spring.core.user.User;
import jakarta.inject.Singleton;
import java.util.Optional;

@Singleton
public class CurrentUserArgumentBinder
    implements AnnotatedRequestArgumentBinder<CurrentUser, User> {

  @Override
  public Class<CurrentUser> getAnnotationType() {
    return CurrentUser.class;
  }

  @Override
  public BindingResult<User> bind(ArgumentConversionContext<User> context, HttpRequest<?> source) {
    Optional<User> user = Authentication.currentUser(source);
    return () -> user;
  }
}
