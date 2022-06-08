package io.spring.graphql.types;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(builderMethodName = "newBuilder")
public class Comment {
  private String id;
  private Profile author;
  private Article article;
  private String body;
  private String createdAt;
  private String updatedAt;
}
