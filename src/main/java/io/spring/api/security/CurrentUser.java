package io.spring.api.security;

import io.micronaut.core.bind.annotation.Bindable;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Binds the authenticated {@link io.spring.core.user.User} of the current request, or {@code null}
 * for anonymous requests.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER})
@Bindable
public @interface CurrentUser {}
