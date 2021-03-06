package ru.hh.jclient.common.balancing;

import ru.hh.jclient.common.Response;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static ru.hh.jclient.common.HttpStatuses.CONNECT_TIMEOUT_ERROR;
import static ru.hh.jclient.common.HttpStatuses.SERVICE_UNAVAILABLE;
import static ru.hh.jclient.common.ResponseStatusMessages.CONNECT_ERROR_MESSAGE;


final class RetryPolicy {
  private static final Pattern HTTP_RETRY = Pattern.compile("http_([0-9]{3})");
  private static final Pattern NON_IDEMPOTENT_RETRY = Pattern.compile("non_idempotent_([0-9]{3})");

  private Map<Integer, Boolean> rules = new HashMap<>();

  RetryPolicy() {
    rules.put(CONNECT_TIMEOUT_ERROR, false);
    rules.put(SERVICE_UNAVAILABLE, false);
  }

  void update(String configString) {
    this.rules = Arrays.stream(configString.split(","))
      .map(configElement -> {
        if ("timeout".equals(configElement)) {
          return new CodeIdempotence(CONNECT_TIMEOUT_ERROR, false);
        }

        Matcher httpRetry = HTTP_RETRY.matcher(configElement);
        if (httpRetry.matches()) {
          return new CodeIdempotence(Integer.parseInt(httpRetry.group(1)), false);
        }

        Matcher nonIdempotentRetry = NON_IDEMPOTENT_RETRY.matcher(configElement);
        if (nonIdempotentRetry.matches()) {
          return new CodeIdempotence(Integer.parseInt(nonIdempotentRetry.group(1)), true);
        }

        return null;
      })
      .filter(Objects::nonNull)
      .collect(Collectors.toMap(ci -> ci.code, ci -> ci.idempotent, (i1, i2) -> i1 || i2));
  }

  boolean isRetriable(Response response, boolean idempotent) {
    int statusCode = response.getStatusCode();

    if (statusCode == CONNECT_TIMEOUT_ERROR && CONNECT_ERROR_MESSAGE.equals(response.getStatusText())) {
      return true;
    }

    Boolean retryNonIdempotent = rules.get(statusCode);
    if (retryNonIdempotent == null) {
      return false;
    }

    return retryNonIdempotent || idempotent;
  }

  boolean isServerError(Response response) {
    int statusCode = response.getStatusCode();

    if (statusCode == CONNECT_TIMEOUT_ERROR && CONNECT_ERROR_MESSAGE.equals(response.getStatusText())) {
      return true;
    }

    return rules.containsKey(statusCode);
  }

  Map<Integer, Boolean> getRules() {
    return Map.copyOf(this.rules);
  }

  @Override
  public String toString() {
    return "RetryPolicy {" + rules + '}';
  }

  private static final class CodeIdempotence {
    final int code;
    final boolean idempotent;

    CodeIdempotence(int code, boolean idempotent) {
      this.code = code;
      this.idempotent = idempotent;
    }
  }
}
