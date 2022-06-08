package io.spring.graphql.types;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(builderMethodName = "newBuilder")
public class UpdateUserInput {
  private String email;
  private String username;
  private String password;
  private String image;
  private String bio;
}
