package io.spring.graphql.types;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(builderMethodName = "newBuilder")
public class Article {
  private Profile author;
  private String body;
  private CommentsConnection comments;
  private String createdAt;
  private String description;
  private boolean favorited;
  private int favoritesCount;
  private String slug;
  private List<String> tagList;
  private String title;
  private String updatedAt;
}
