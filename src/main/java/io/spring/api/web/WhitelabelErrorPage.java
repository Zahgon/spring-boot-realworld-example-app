package io.spring.api.web;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

/**
 * The HTML rendering of status errors. The error endpoint has an HTML variant producing {@code
 * text/html} and a JSON variant producing anything; the HTML variant is chosen when it matches the
 * {@code Accept} header and ranks before the JSON variant for the client's preferred media types.
 */
public final class WhitelabelErrorPage {
  public static final MediaRange CONTENT_TYPE = MediaRange.parse("text/html;charset=UTF-8");

  private static final MediaRange TEXT_HTML = MediaRange.parse("text/html");

  private WhitelabelErrorPage() {}

  public static boolean isPreferred(HttpRequest<?> request) {
    List<MediaRange> accepted;
    try {
      accepted = ContentNegotiation.acceptedMediaTypes(request);
    } catch (ContentNegotiation.NotAcceptableException e) {
      // A malformed Accept header never selects the HTML page.
      return false;
    }
    if (accepted.stream().noneMatch(TEXT_HTML::overlaps)) {
      return false;
    }
    for (MediaRange mediaType : accepted) {
      int html = mediaType.sameTypeAs(TEXT_HTML) ? 0 : -1;
      int json = mediaType.sameTypeAs(MediaRange.ANY) ? 0 : -1;
      if (html != json) {
        return html > json;
      }
      html = mediaType.covers(TEXT_HTML) ? 0 : -1;
      json = mediaType.covers(MediaRange.ANY) ? 0 : -1;
      if (html != json) {
        return html > json;
      }
    }
    return false;
  }

  public static byte[] render(HttpStatus status) {
    String page =
        "<html><body><h1>Whitelabel Error Page</h1>"
            + "<p>This application has no explicit mapping for /error, so you are seeing this as a"
            + " fallback.</p>"
            + "<div id='created'>"
            + new Date()
            + "</div>"
            + "<div>There was an unexpected error (type="
            + ErrorResponses.reasonPhrase(status)
            + ", status="
            + status.getCode()
            + ").</div></body></html>";
    return page.getBytes(StandardCharsets.UTF_8);
  }
}
