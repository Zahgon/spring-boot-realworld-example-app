package io.spring.graphql.types;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(builderMethodName = "newBuilder")
public class Profile {
  private String username;
  private String bio;
  private boolean following;
  private String image;
  private ArticlesConnection articles;
  private ArticlesConnection favorites;
  private ArticlesConnection feed;
}
