package io.spring.infrastructure.comment;

import io.spring.core.comment.Comment;
import io.spring.core.comment.CommentRepository;
import io.spring.infrastructure.DbTestBase;
import jakarta.inject.Inject;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class MyBatisCommentRepositoryTest extends DbTestBase {
  @Inject private CommentRepository commentRepository;

  @Test
  public void should_create_and_fetch_comment_success() {
    Comment comment = new Comment("content", "123", "456");
    commentRepository.save(comment);

    Optional<Comment> optional = commentRepository.findById("456", comment.getId());
    Assertions.assertTrue(optional.isPresent());
    Assertions.assertEquals(optional.get(), comment);
  }
}
