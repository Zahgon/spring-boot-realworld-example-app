package io.spring;

import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@MicronautTest(environments = "test")
public class RealworldApplicationTests {
  @Inject private EmbeddedServer server;

  @Test
  public void contextLoads() {
    Assertions.assertTrue(server.isRunning());
  }
}
