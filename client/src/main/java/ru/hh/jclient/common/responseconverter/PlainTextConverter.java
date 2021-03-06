package ru.hh.jclient.common.responseconverter;

import static java.util.Objects.requireNonNull;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import ru.hh.jclient.common.Response;
import ru.hh.jclient.common.ResultWithResponse;
import ru.hh.jclient.common.util.MoreFunctionalInterfaces.FailableFunction;
import com.google.common.collect.ImmutableSet;
import com.google.common.net.MediaType;

public class PlainTextConverter extends SingleTypeConverter<String> {

  /**
   * Default is UTF-8.
   */
  public static final Charset DEFAULT = StandardCharsets.UTF_8;

  private Charset charset;

  public PlainTextConverter(Charset charset) {
    this.charset = requireNonNull(charset, "charset must not be null");
  }

  public PlainTextConverter() {
    this(DEFAULT);
  }

  @Override
  public FailableFunction<Response, ResultWithResponse<String>, Exception> singleTypeConverterFunction() {
    return r -> new ResultWithResponse<>(r.getResponseBody(charset), r);
  }

  @Override
  protected Collection<MediaType> getMediaTypes() {
    return ImmutableSet.of(MediaType.PLAIN_TEXT_UTF_8.withoutParameters().withCharset(charset));
  }
}
