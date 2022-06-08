package io.spring.api.web;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.server.netty.NettyHttpRequest;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

public final class RequestPaths {
  private RequestPaths() {}

  /** The request path exactly as sent by the client, without the query string. */
  public static String rawPath(HttpRequest<?> request) {
    return pathOf(request.getUri().toString());
  }

  /**
   * The path of the request line before any normalisation; {@link HttpRequest#getUri()} already
   * reads a leading {@code //} as an authority.
   */
  public static String requestLinePath(HttpRequest<?> request) {
    if (request instanceof NettyHttpRequest<?> netty) {
      return pathOf(netty.getNativeRequest().uri());
    }
    return rawPath(request);
  }

  private static String pathOf(String uri) {
    int end = uri.length();
    int query = uri.indexOf('?');
    if (query >= 0) {
      end = query;
    }
    int fragment = uri.indexOf('#');
    if (fragment >= 0 && fragment < end) {
      end = fragment;
    }
    String path = uri.substring(0, end);
    int scheme = path.indexOf("://");
    if (scheme >= 0) {
      int slash = path.indexOf('/', scheme + 3);
      path = slash >= 0 ? path.substring(slash) : "/";
    }
    return path.isEmpty() ? "/" : path;
  }

  /** The percent-decoded request path; {@code +} is kept as is. */
  public static String decodedPath(HttpRequest<?> request) {
    return percentDecode(rawPath(request));
  }

  static String percentDecode(String value) {
    if (value.indexOf('%') < 0) {
      return value;
    }
    ByteArrayOutputStream bytes = new ByteArrayOutputStream(value.length());
    StringBuilder result = new StringBuilder(value.length());
    int i = 0;
    while (i < value.length()) {
      char c = value.charAt(i);
      if (isEscape(value, i)) {
        bytes.reset();
        while (isEscape(value, i)) {
          bytes.write(Integer.parseInt(value.substring(i + 1, i + 3), 16));
          i += 3;
        }
        result.append(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
      } else {
        result.append(c);
        i++;
      }
    }
    return result.toString();
  }

  private static boolean isEscape(String value, int index) {
    return index + 2 < value.length()
        && value.charAt(index) == '%'
        && isHex(value, index + 1)
        && isHex(value, index + 2);
  }

  private static boolean isHex(String value, int index) {
    return index < value.length() && Character.digit(value.charAt(index), 16) >= 0;
  }
}
