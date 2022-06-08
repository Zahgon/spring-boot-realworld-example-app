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
public class CreateArticleInput {
  private String body;
  private String description;
  private List<String> tagList;
  private String title;
}
