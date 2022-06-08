package io.spring.application.article;

import io.spring.application.ServiceArgumentValidator;
import io.spring.core.article.Article;
import io.spring.core.article.ArticleRepository;
import io.spring.core.user.User;
import jakarta.inject.Singleton;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

@Singleton
@AllArgsConstructor
public class ArticleCommandService {

  private ArticleRepository articleRepository;
  private ServiceArgumentValidator argumentValidator;

  public Article createArticle(@Valid NewArticleParam newArticleParam, User creator) {
    argumentValidator.validate(this, "createArticle", newArticleParam, creator);
    Article article =
        new Article(
            newArticleParam.getTitle(),
            newArticleParam.getDescription(),
            newArticleParam.getBody(),
            newArticleParam.getTagList(),
            creator.getId());
    articleRepository.save(article);
    return article;
  }

  public Article updateArticle(Article article, @Valid UpdateArticleParam updateArticleParam) {
    argumentValidator.validate(this, "updateArticle", article, updateArticleParam);
    article.update(
        updateArticleParam.getTitle(),
        updateArticleParam.getDescription(),
        updateArticleParam.getBody());
    articleRepository.save(article);
    return article;
  }
}
