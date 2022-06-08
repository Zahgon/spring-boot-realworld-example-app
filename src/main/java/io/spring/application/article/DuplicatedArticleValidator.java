package io.spring.application.article;

import io.spring.application.ArticleQueryService;
import io.spring.core.article.Article;
import jakarta.inject.Singleton;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

@Singleton
class DuplicatedArticleValidator
    implements ConstraintValidator<DuplicatedArticleConstraint, String> {

  private final ArticleQueryService articleQueryService;

  DuplicatedArticleValidator(ArticleQueryService articleQueryService) {
    this.articleQueryService = articleQueryService;
  }

  @Override
  public boolean isValid(String value, ConstraintValidatorContext context) {
    return !articleQueryService.findBySlug(Article.toSlug(value), null).isPresent();
  }
}
