package io.spring.api.web;

import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.bind.binders.AnnotatedRequestArgumentBinder;
import io.micronaut.web.router.RouteAttributes;
import io.micronaut.web.router.UriRouteMatch;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Singleton
public class PathSegmentArgumentBinder
    implements AnnotatedRequestArgumentBinder<PathSegment, String> {

  @Override
  public Class<PathSegment> getAnnotationType() {
    return PathSegment.class;
  }

  @Override
  public BindingResult<String> bind(
      ArgumentConversionContext<String> context, HttpRequest<?> source) {
    String name =
        context
            .getAnnotationMetadata()
            .stringValue(PathSegment.class)
            .orElse(context.getArgument().getName());
    Optional<String> value =
        RouteAttributes.getRouteMatch(source)
            .filter(UriRouteMatch.class::isInstance)
            .map(
                match ->
                    ((UriRouteMatch<?, ?>) match).getRouteInfo().getUriMatchTemplate().toString())
            .flatMap(template -> segment(template, RequestPaths.rawPath(source), name));
    return () -> value;
  }

  private static Optional<String> segment(String template, String path, String name) {
    List<String> variables = templateSegments(template);
    String[] segments = path.split("/", -1);
    for (int i = 0; i < variables.size() && i < segments.length; i++) {
      String variable = variables.get(i);
      if (variable.equals("{" + name + "}") || variable.startsWith("{" + name + ":")) {
        return Optional.of(RequestPaths.percentDecode(segments[i]));
      }
    }
    return Optional.empty();
  }

  /** Splits a URI template on the slashes that are not inside a variable's pattern. */
  private static List<String> templateSegments(String template) {
    List<String> segments = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    int depth = 0;
    for (char c : template.toCharArray()) {
      if (c == '/' && depth == 0) {
        segments.add(current.toString());
        current.setLength(0);
        continue;
      }
      if (c == '{') {
        depth++;
      } else if (c == '}' && depth > 0) {
        depth--;
      }
      current.append(c);
    }
    segments.add(current.toString());
    return segments;
  }
}
