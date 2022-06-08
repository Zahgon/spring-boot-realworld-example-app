package io.spring.api;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.spring.application.TagsQueryService;
import java.util.HashMap;
import lombok.AllArgsConstructor;

@Controller("/tags")
@Consumes(MediaType.ALL)
@Produces(MediaType.ALL)
@AllArgsConstructor
public class TagsApi {
  private TagsQueryService tagsQueryService;

  @Get
  public HttpResponse<?> getTags() {
    return HttpResponse.ok(
        new HashMap<String, Object>() {
          {
            put("tags", tagsQueryService.allTags());
          }
        });
  }
}
