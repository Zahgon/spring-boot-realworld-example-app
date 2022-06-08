package io.spring.graphql;

import graphql.schema.DataFetchingEnvironment;
import graphql.schema.idl.RuntimeWiring;
import io.spring.application.TagsQueryService;
import jakarta.inject.Singleton;
import java.util.List;
import lombok.AllArgsConstructor;

@Singleton
@AllArgsConstructor
public class TagDatafetcher implements GraphQLWiring {
  private TagsQueryService tagsQueryService;

  @Override
  public void wire(RuntimeWiring.Builder wiring) {
    wiring.type("Query", type -> type.dataFetcher("tags", env -> getTags(env)));
  }

  public List<String> getTags(DataFetchingEnvironment dfe) {
    return tagsQueryService.allTags();
  }
}
