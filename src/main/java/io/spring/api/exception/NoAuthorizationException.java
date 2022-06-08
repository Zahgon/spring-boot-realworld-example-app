package io.spring.api.exception;

/** The current user may not modify the requested resource; answered with 403. */
public class NoAuthorizationException extends RuntimeException {}
