package io.spring.infrastructure.mybatis;

import io.micronaut.context.annotation.ConfigurationProperties;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties("mybatis")
public class MyBatisProperties {
  private boolean cacheEnabled = true;
  private Integer defaultStatementTimeout;
  private boolean mapUnderscoreToCamelCase;
  private boolean useGeneratedKeys;
  private String typeHandlersPackage;
  private List<String> mapperLocations = new ArrayList<>();
}
