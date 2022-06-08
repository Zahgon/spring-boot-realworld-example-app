package io.spring.api.web;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.order.Ordered;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.web.router.RouteAttributes;
import io.micronaut.web.router.Router;
import io.micronaut.web.router.UriRouteMatch;
import io.spring.api.security.CorsProcessor;
import io.spring.api.security.WebSecurityFilter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resolves requests that match no route (404), no route for their method (405, or the allowed
 * methods for {@code OPTIONS}) and renders JSON response bodies in the negotiated media type.
 */
@ServerFilter(ServerFilter.MATCH_ALL_PATTERN)
public class ApiDispatchFilter implements Ordered {
  private static final List<HttpMethod> METHOD_ORDER =
      List.of(
          HttpMethod.GET,
          HttpMethod.HEAD,
          HttpMethod.POST,
          HttpMethod.PUT,
          HttpMethod.PATCH,
          HttpMethod.DELETE,
          HttpMethod.OPTIONS,
          HttpMethod.TRACE);
  private static final Map<String, JsonEncoding> JSON_ENCODINGS =
      Map.of(
          "UTF-8", JsonEncoding.UTF8,
          "US-ASCII", JsonEncoding.UTF8,
          "UTF-16BE", JsonEncoding.UTF16_BE,
          "UTF-16LE", JsonEncoding.UTF16_LE,
          "UTF-32BE", JsonEncoding.UTF32_BE,
          "UTF-32LE", JsonEncoding.UTF32_LE);

  private final Router router;
  private final ObjectMapper objectMapper;

  public ApiDispatchFilter(Router router, ObjectMapper objectMapper) {
    this.router = router;
    this.objectMapper = objectMapper;
  }

  @Override
  public int getOrder() {
    return WebSecurityFilter.ORDER + 100;
  }

  @RequestFilter
  @Nullable
  public HttpResponse<?> dispatch(HttpRequest<?> request) {
    if (CorsProcessor.isPreflight(request)) {
      return null;
    }
    Set<HttpMethod> declared = declaredMethods(request);
    if (declared.isEmpty()) {
      return ErrorResponses.error(request, HttpStatus.NOT_FOUND);
    }
    HttpMethod method = request.getMethod();
    if (declared.contains(method)
        || (method == HttpMethod.HEAD && declared.contains(HttpMethod.GET))) {
      return null;
    }
    if (method == HttpMethod.OPTIONS) {
      List<HttpMethod> allowed = new ArrayList<>();
      for (HttpMethod candidate : declared) {
        allowed.add(candidate);
        if (candidate == HttpMethod.GET) {
          allowed.add(HttpMethod.HEAD);
        }
      }
      allowed.add(HttpMethod.OPTIONS);
      return HttpResponse.ok().header(HttpHeaders.ALLOW, join(allowed)).header("Accept-Patch", "");
    }
    return HttpResponse.status(HttpStatus.METHOD_NOT_ALLOWED)
        .header(HttpHeaders.ALLOW, join(declared));
  }

  @ResponseFilter
  public void render(HttpRequest<?> request, MutableHttpResponse<?> response) {
    boolean head = request.getMethod() == HttpMethod.HEAD;
    Optional<?> body = head ? RouteAttributes.getHeadBody(response) : response.getBody();
    if (body.isEmpty()
        || body.get() instanceof byte[]
        || body.get() instanceof CharSequence
        || body.get() instanceof UntypedBody) {
      return;
    }
    @SuppressWarnings("unchecked")
    MutableHttpResponse<Object> writable = (MutableHttpResponse<Object>) response;
    if (response.getAttribute(ErrorResponses.ERROR_PAGE).isPresent()
        && WhitelabelErrorPage.isPreferred(request)) {
      if (!head) {
        writable.body(WhitelabelErrorPage.render(response.status()));
      }
      writable
          .getHeaders()
          .set(HttpHeaders.CONTENT_TYPE, WhitelabelErrorPage.CONTENT_TYPE.toString());
      return;
    }
    MediaRange contentType;
    if (response.getAttribute(ErrorResponses.PRESET_JSON).isPresent()) {
      contentType = MediaRange.APPLICATION_JSON;
    } else {
      Optional<MediaRange> selected =
          ContentNegotiation.select(request, ContentNegotiation.JSON_TYPES)
              .filter(ApiDispatchFilter::writableAsJson);
      if (selected.isEmpty()) {
        notAcceptable(request, writable, head);
        return;
      }
      contentType = selected.get();
    }
    if (!head) {
      writable.body(write(body.get(), contentType));
    }
    writable.getHeaders().set(HttpHeaders.CONTENT_TYPE, contentType.toString());
  }

  /**
   * No accepted media type can carry the body. A plain response becomes an empty 406. An error
   * response keeps its status with an empty body when the {@code Accept} header does not parse;
   * otherwise it becomes its fallback status error, rendered as the HTML error page when that is
   * preferred and empty when not.
   */
  private static void notAcceptable(
      HttpRequest<?> request, MutableHttpResponse<Object> response, boolean head) {
    Optional<Integer> fallback =
        response.getAttribute(ErrorResponses.NOT_ACCEPTABLE_STATUS, Integer.class);
    response.body(null);
    response.getHeaders().remove(HttpHeaders.CONTENT_TYPE);
    if (fallback.isEmpty()) {
      response.status(HttpStatus.NOT_ACCEPTABLE);
      return;
    }
    if (!acceptParses(request) && response.code() >= 400) {
      return;
    }
    HttpStatus status = HttpStatus.valueOf(fallback.get());
    response.status(status);
    if (WhitelabelErrorPage.isPreferred(request)) {
      if (!head) {
        response.body(WhitelabelErrorPage.render(status));
      }
      response
          .getHeaders()
          .set(HttpHeaders.CONTENT_TYPE, WhitelabelErrorPage.CONTENT_TYPE.toString());
    }
  }

  private static boolean acceptParses(HttpRequest<?> request) {
    try {
      ContentNegotiation.acceptedMediaTypes(request);
      return true;
    } catch (ContentNegotiation.NotAcceptableException e) {
      // The exception is the answer: the Accept header is malformed.
      return false;
    }
  }

  /** Jackson writes JSON only in the Unicode encodings it knows (and US-ASCII as UTF-8). */
  private static boolean writableAsJson(MediaRange mediaType) {
    String charset = mediaType.parameterValue("charset");
    if (charset == null) {
      return true;
    }
    try {
      return JSON_ENCODINGS.containsKey(mediaType.charset().name());
    } catch (IllegalArgumentException e) {
      // A charset name the JVM does not know cannot be written either.
      return false;
    }
  }

  /**
   * Writes {@code value} as JSON in the charset of {@code contentType}, which is plain {@code
   * application/json} or a type that passed {@link #writableAsJson}, so its charset is known.
   */
  private byte[] write(Object value, MediaRange contentType) {
    Charset charset = contentType.charset();
    JsonEncoding encoding =
        charset == null
            ? JsonEncoding.UTF8
            : JSON_ENCODINGS.getOrDefault(charset.name(), JsonEncoding.UTF8);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (JsonGenerator generator = objectMapper.getFactory().createGenerator(out, encoding)) {
      objectMapper.writeValue(generator, value);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return out.toByteArray();
  }

  private Set<HttpMethod> declaredMethods(HttpRequest<?> request) {
    List<UriRouteMatch<Object, Object>> matches =
        router.<Object, Object>findAny(request.getPath(), request).collect(Collectors.toList());
    Set<HttpMethod> methods = EnumSet.noneOf(HttpMethod.class);
    for (UriRouteMatch<Object, Object> match : matches) {
      if (match.getHttpMethod() != HttpMethod.HEAD) {
        methods.add(match.getHttpMethod());
      }
    }
    return methods;
  }

  private static String join(Iterable<HttpMethod> methods) {
    List<HttpMethod> ordered = new ArrayList<>();
    methods.forEach(ordered::add);
    if (!(methods instanceof List)) {
      ordered.sort((a, b) -> Integer.compare(METHOD_ORDER.indexOf(a), METHOD_ORDER.indexOf(b)));
    }
    return ordered.stream().map(HttpMethod::name).collect(Collectors.joining(","));
  }
}
