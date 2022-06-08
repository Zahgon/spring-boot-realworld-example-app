package io.spring;

import io.micronaut.runtime.Micronaut;

public class RealWorldApplication {

  public static void main(String[] args) {
    Micronaut.build(args).mainClass(RealWorldApplication.class).banner(false).start();
  }
}
