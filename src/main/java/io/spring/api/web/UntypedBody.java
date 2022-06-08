package io.spring.api.web;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.MutableHeaders;
import io.micronaut.http.ByteBodyHttpResponse;
import io.micronaut.http.ByteBodyHttpResponseWrapper;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.ResponseBodyWriter;
import io.micronaut.http.codec.CodecException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** A response body written as is, without a {@code Content-Type} header. */
public final class UntypedBody {
  private final byte[] bytes;

  private UntypedBody(byte[] bytes) {
    this.bytes = bytes;
  }

  public static UntypedBody of(String text) {
    return new UntypedBody(text.getBytes(StandardCharsets.UTF_8));
  }

  /** Sets {@code body} as the response body, written without a content type. */
  public static <T> MutableHttpResponse<UntypedBody> respond(
      MutableHttpResponse<T> response, String body) {
    MutableHttpResponse<UntypedBody> untyped = response.body(of(body));
    untyped.bodyWriter(WRITER);
    return untyped;
  }

  private static final Writer WRITER = new Writer();

  private static final class Writer implements ResponseBodyWriter<UntypedBody> {

    @Override
    public ByteBodyHttpResponse<?> write(
        @NonNull ByteBodyFactory bodyFactory,
        @NonNull HttpRequest<?> request,
        @NonNull MutableHttpResponse<UntypedBody> httpResponse,
        @NonNull Argument<UntypedBody> type,
        @NonNull MediaType mediaType,
        UntypedBody object) {
      return ByteBodyHttpResponseWrapper.wrap(
          httpResponse, writePiece(bodyFactory, request, httpResponse, type, mediaType, object));
    }

    @Override
    public CloseableByteBody writePiece(
        @NonNull ByteBodyFactory bodyFactory,
        @NonNull HttpRequest<?> request,
        @NonNull HttpResponse<?> response,
        @NonNull Argument<UntypedBody> type,
        @NonNull MediaType mediaType,
        UntypedBody object) {
      return bodyFactory.adapt(object.bytes);
    }

    @Override
    public void writeTo(
        @NonNull Argument<UntypedBody> type,
        @NonNull MediaType mediaType,
        UntypedBody object,
        @NonNull MutableHeaders outgoingHeaders,
        @NonNull OutputStream outputStream)
        throws CodecException {
      try {
        outputStream.write(object.bytes);
      } catch (java.io.IOException e) {
        throw new CodecException("Failed to write body", e);
      }
    }
  }
}
