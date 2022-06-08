package io.spring.graphql.types;

import graphql.relay.PageInfo;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(builderMethodName = "newBuilder")
public class ArticlesConnection {
  private List<ArticleEdge> edges;
  private PageInfo pageInfo;
}
