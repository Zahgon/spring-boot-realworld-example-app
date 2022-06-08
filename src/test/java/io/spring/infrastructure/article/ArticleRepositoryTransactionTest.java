package io.spring.infrastructure.article;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.spring.core.article.Article;
import io.spring.core.article.ArticleRepository;
import io.spring.core.user.User;
import io.spring.core.user.UserRepository;
import io.spring.infrastructure.mybatis.mapper.ArticleMapper;
import jakarta.inject.Inject;
import java.util.Arrays;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@MicronautTest(environments = "test", startApplication = false)
public class ArticleRepositoryTransactionTest {
  @Inject private ArticleRepository articleRepository;

  @Inject private UserRepository userRepository;

  @Inject private ArticleMapper articleMapper;

  @Test
  public void transactional_test() {
    User user = new User("aisensiy@gmail.com", "aisensiy", "123", "bio", "default");
    userRepository.save(user);
    Article article =
        new Article("test", "desc", "body", Arrays.asList("java", "spring"), user.getId());
    articleRepository.save(article);
    Article anotherArticle =
        new Article("test", "desc", "body", Arrays.asList("java", "spring", "other"), user.getId());
    try {
      articleRepository.save(anotherArticle);
    } catch (Exception e) {
      Assertions.assertNull(articleMapper.findTag("other"));
    }
  }
}
