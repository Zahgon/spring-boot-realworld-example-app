package io.spring.application;

import io.spring.infrastructure.mybatis.readservice.TagReadService;
import jakarta.inject.Singleton;
import java.util.List;
import lombok.AllArgsConstructor;

@Singleton
@AllArgsConstructor
public class TagsQueryService {
  private TagReadService tagReadService;

  public List<String> allTags() {
    return tagReadService.all();
  }
}
