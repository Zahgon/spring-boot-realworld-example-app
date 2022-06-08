package io.spring.application.tag;

import io.spring.application.TagsQueryService;
import io.spring.core.article.Article;
import io.spring.core.article.ArticleRepository;
import io.spring.infrastructure.DbTestBase;
import jakarta.inject.Inject;
import java.util.Arrays;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TagsQueryServiceTest extends DbTestBase {
  @Inject private TagsQueryService tagsQueryService;

  @Inject private ArticleRepository articleRepository;

  @Test
  public void should_get_all_tags() {
    articleRepository.save(new Article("test", "test", "test", Arrays.asList("java"), "123"));
    Assertions.assertTrue(tagsQueryService.allTags().contains("java"));
  }
}
