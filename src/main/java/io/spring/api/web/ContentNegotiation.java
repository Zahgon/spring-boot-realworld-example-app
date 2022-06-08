package io.spring.api.web;

import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Chooses the response media type from the request's {@code Accept} header. */
public final class ContentNegotiation {
  /** Media types the API can render response bodies as. */
  public static final List<MediaRange> JSON_TYPES =
      List.of(MediaRange.APPLICATION_JSON, MediaRange.ANY_JSON_SUFFIX);

  private ContentNegotiation() {}

  /**
   * The accepted media types of the request, most preferred first. A missing or empty {@code
   * Accept} header accepts everything.
   *
   * @throws NotAcceptableException if the header cannot be parsed
   */
  public static List<MediaRange> acceptedMediaTypes(HttpRequest<?> request) {
    List<String> values = request.getHeaders().getAll(HttpHeaders.ACCEPT);
    if (values.isEmpty()) {
      return List.of(MediaRange.ANY);
    }
    try {
      List<MediaRange> mediaTypes = MediaRange.parseList(values);
      MediaRange.sortByPreference(mediaTypes);
      return mediaTypes.isEmpty() ? List.of(MediaRange.ANY) : mediaTypes;
    } catch (IllegalArgumentException e) {
      throw new NotAcceptableException();
    }
  }

  /** Whether any accepted media type is compatible with one of {@code producible}. */
  public static boolean accepts(HttpRequest<?> request, List<MediaRange> producible) {
    List<MediaRange> accepted;
    try {
      accepted = acceptedMediaTypes(request);
    } catch (NotAcceptableException e) {
      // A malformed Accept header accepts nothing.
      return false;
    }
    for (MediaRange produce : producible) {
      for (MediaRange accept : accepted) {
        if (produce.overlaps(accept)) {
          return true;
        }
      }
    }
    return accepted.stream().anyMatch(MediaRange.ANY::sameTypeAs);
  }

  /** The concrete media type to write the response body as, if any is acceptable. */
  public static Optional<MediaRange> select(HttpRequest<?> request, List<MediaRange> producible) {
    List<MediaRange> accepted;
    try {
      accepted = acceptedMediaTypes(request);
    } catch (NotAcceptableException e) {
      // A malformed Accept header selects nothing; callers answer 406.
      return Optional.empty();
    }
    List<MediaRange> candidates = new ArrayList<>();
    for (MediaRange requested : accepted) {
      for (MediaRange produce : producible) {
        if (requested.overlaps(produce)) {
          candidates.add(mostSpecific(requested, produce));
        }
      }
    }
    MediaRange.sortByPreference(candidates);
    for (MediaRange candidate : candidates) {
      if (candidate.isSpecific()) {
        return Optional.of(candidate.withoutQuality());
      } else if (candidate.sameTypeAs(MediaRange.ANY)
          || candidate.sameTypeAs(MediaRange.ANY_APPLICATION)) {
        return Optional.empty();
      }
    }
    return Optional.empty();
  }

  private static MediaRange mostSpecific(MediaRange accept, MediaRange produce) {
    MediaRange produceToUse = produce.withQualityOf(accept);
    return MediaRange.compareSpecificity(accept, produceToUse) <= 0 ? accept : produceToUse;
  }

  /** The request's {@code Accept} header rules out every producible media type. */
  public static class NotAcceptableException extends RuntimeException {
    public NotAcceptableException() {
      super("Could not find acceptable representation");
    }
  }
}
