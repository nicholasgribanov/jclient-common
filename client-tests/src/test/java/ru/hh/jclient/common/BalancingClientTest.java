package ru.hh.jclient.common;

import joptsimple.internal.Strings;
import org.asynchttpclient.Request;
import org.junit.Test;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import ru.hh.jclient.common.HttpClientImpl.CompletionHandler;
import static ru.hh.jclient.common.TestRequestDebug.Call.FINISHED;
import static ru.hh.jclient.common.TestRequestDebug.Call.REQUEST;
import static ru.hh.jclient.common.TestRequestDebug.Call.RESPONSE;
import ru.hh.jclient.common.balancing.RequestBalancerBuilder;
import ru.hh.jclient.common.balancing.Server;
import ru.hh.jclient.common.balancing.UpstreamConfig;
import ru.hh.jclient.consul.ValueNode;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class BalancingClientTest extends BalancingClientTestBase {
  private static final String PROFILE_DELIMITER = ":";

  @Test
  public void requestWithProfile() throws Exception {
    ValueNode rootNode = new ValueNode();
    ValueNode profileNode = buildProfileNode(rootNode);

    profileNode.computeMapIfAbsent(UpstreamConfig.DEFAULT).putValue("request_timeout_sec", "33");
    profileNode.computeMapIfAbsent("foo").putValue("request_timeout_sec", "22");
    profileNode.computeMapIfAbsent("bar").putValue("request_timeout_sec", "11");

    when(upstreamConfigService.getUpstreamConfig()).thenReturn(rootNode);

    List<String> upstreamList = List.of(TEST_UPSTREAM, profileName(TEST_UPSTREAM, "foo"), profileName(TEST_UPSTREAM, "bar"));
    createHttpClientFactory(upstreamList);
    Request[] request = new Request[1];
    when(httpClient.executeRequest(isA(Request.class), isA(CompletionHandler.class)))
        .then(iom -> {
          request[0] = completeWith(200, iom);
          return null;
        });

    getTestClient().get();
    assertRequestTimeoutEquals(request[0], TimeUnit.SECONDS.toMillis(33));
    getTestClient().withPreconfiguredEngine(RequestBalancerBuilder.class, builder -> builder.withProfile("foo")).get();
    assertRequestTimeoutEquals(request[0], TimeUnit.SECONDS.toMillis(22));
    getTestClient().withPreconfiguredEngine(RequestBalancerBuilder.class, builder -> builder.withProfile("foo")).getWithProfileInsideClient("bar");
    assertRequestTimeoutEquals(request[0], TimeUnit.SECONDS.toMillis(11));
    getTestClient().withPreconfiguredEngine(RequestBalancerBuilder.class, builder -> builder.withProfile("not existing profile")).get();
    assertRequestTimeoutEquals(request[0], TimeUnit.SECONDS.toMillis(33));
  }

  @Test(expected = IllegalStateException.class)
  public void requestWithProfileDefaultMissing() throws Exception {
    List<String> upstreamList = List.of(profileName(TEST_UPSTREAM, "foo"), profileName(TEST_UPSTREAM, "bar"));
    createHttpClientFactory(upstreamList);

    Request[] request = new Request[1];
    when(httpClient.executeRequest(isA(Request.class), isA(CompletionHandler.class)))
            .then(iom -> {
              request[0] = completeWith(200, iom);
              return null;
            });
    getTestClient().get();
  }

  @Test(expected = ClassCastException.class)
  public void preconfiguredWithWrongClass() throws Exception {

    createHttpClientFactory();

    Request[] request = new Request[1];
    when(httpClient.executeRequest(isA(Request.class), isA(CompletionHandler.class)))
            .then(iom -> {
              request[0] = completeWith(200, iom);
              return null;
            });
    getTestClient().withPreconfiguredEngine(NotValidEngineBuilder.class, NotValidEngineBuilder::withSmth).get();
    assertRequestTimeoutEquals(request[0], TimeUnit.SECONDS.toMillis(2));
  }

  @Test(expected = ClassCastException.class)
  public void configuredWithWrongClass() throws Exception {
    createHttpClientFactory();

    Request[] request = new Request[1];
    when(httpClient.executeRequest(isA(Request.class), isA(CompletionHandler.class)))
            .then(iom -> {
              request[0] = completeWith(200, iom);
              return null;
            });
    getTestClient().getWrongEngineBuilderClass();
    assertRequestTimeoutEquals(request[0], TimeUnit.SECONDS.toMillis(2));
  }

  @Test
  public void requestWithUnknownUpstream() throws Exception {

    List<String> upstreamList = List.of(profileName(TEST_UPSTREAM, "foo"), profileName(TEST_UPSTREAM, "bar"));

    createHttpClientFactory(upstreamList);

    Request[] request = new Request[1];
    when(httpClient.executeRequest(isA(Request.class), isA(CompletionHandler.class)))
        .then(iom -> {
          request[0] = completeWith(200, iom);
          return null;
        });
    withContext(Map.of(HttpHeaderNames.X_OUTER_TIMEOUT_MS, List.of(Long.toString(TimeUnit.SECONDS.toMillis(1)))));
    getTestClient().get("http://foo/get");
    assertHostEquals(request[0], "foo");
  }

  @Test
  public void requestAndUpdateServers() throws Exception {
    when(upstreamService.getServers(TEST_UPSTREAM))
            .thenReturn(List.of(new Server("server1", 1, null)));
    createHttpClientFactory();

    Request[] request = new Request[1];
    when(httpClient.executeRequest(isA(Request.class), isA(CompletionHandler.class)))
        .then(iom -> {
          request[0] = completeWith(200, iom);
          return null;
        });

    getTestClient().get();
    assertHostEquals(request[0], "server1");
    when(upstreamService.getServers(TEST_UPSTREAM))
            .thenReturn(List.of(new Server("server2", 1, null)));

    getTestClient().get();
    assertHostEquals(request[0], "server2");

    when(upstreamService.getServers(TEST_UPSTREAM))
            .thenReturn(List.of(new Server("server2", 1, null), new Server("server3", 1, null)));
    getTestClient().get();
    assertHostEquals(request[0], "server2");

    getTestClient().get();
    assertHostEquals(request[0], "server3");
  }

  @Test
  public void shouldNotRetryRequestTimeoutForPost() {
    createHttpClientFactory();

    Request[] request = new Request[1];

    when(httpClient.executeRequest(isA(Request.class), isA(CompletionHandler.class)))
        .then(iom -> {
          request[0] = failWith(new TimeoutException("Request timed out"), iom);
          return null;
        });

    try {
      getTestClient().post();
    } catch (Exception e) {
      assertRequestEquals(request, "server1");
      debug.assertCalled(REQUEST, RESPONSE, FINISHED);
    }
  }

  @Test
  public void disallowCrossDCRequests() throws Exception {
    List<Server> servers = List.of(new Server("server1", 1, "DC1"), new Server("server2", 1, "DC2"));
    when(upstreamService.getServers(TEST_UPSTREAM)).thenReturn(servers);

    createHttpClientFactory(List.of(TEST_UPSTREAM), "DC1", false);

    Request[] request = new Request[1];
    when(httpClient.executeRequest(isA(Request.class), isA(CompletionHandler.class)))
      .then(iom -> {
        request[0] = completeWith(200, iom);
        return null;
      });

    getTestClient().get();
    assertHostEquals(request[0], "server1");

    getTestClient().get();
    assertHostEquals(request[0], "server1");

    getTestClient().get();
    assertHostEquals(request[0], "server1");
  }

  @Test
  public void balancedRequestMonitoring() throws Exception {
    String datacenter = "DC1";
    createHttpClientFactory(List.of(TEST_UPSTREAM), datacenter, false);
    when(upstreamService.getServers(TEST_UPSTREAM))
            .thenReturn(List.of(new Server("server1", 1, datacenter)));

    when(httpClient.executeRequest(isA(Request.class), isA(CompletionHandler.class)))
      .then(iom -> {
        completeWith(200, iom);
        return null;
      });

    http.with(new RequestBuilder("GET").setUrl("http://backend/path?query").build())
      .expectPlainText().result().get();

    Monitoring monitoring = requestingStrategy.getUpstreamManager().getMonitoring().stream().findFirst().get();
    verify(monitoring).countRequest(
      eq("backend"), eq("DC1"), eq("server1"), eq(200), anyLong(), eq(true)
    );
  }

  @Test
  public void unbalancedRequestMonitoring() throws Exception {
    createHttpClientFactory();

    when(httpClient.executeRequest(isA(Request.class), isA(CompletionHandler.class)))
      .then(iom -> {
        completeWith(200, iom);
        return null;
      });

    http.with(new RequestBuilder("GET").setUrl("http://not-balanced-backend/path?query").build())
      .expectPlainText().result().get();

    Monitoring monitoring = requestingStrategy.getUpstreamManager().getMonitoring().stream().findFirst().get();
    verify(monitoring).countRequest(
      eq("http://not-balanced-backend"), eq(null), eq("http://not-balanced-backend"), eq(200), anyLong(), eq(true)
    );
  }

  @Test(expected = ExecutionException.class)
  public void failIfNoBackendAvailableInCurrentDC() throws Exception {
    createHttpClientFactory(List.of(TEST_UPSTREAM), "DC1", false);
    when(upstreamService.getServers(TEST_UPSTREAM))
            .thenReturn(List.of(new Server("server1", 1, "DC2")));

    getTestClient().get();
  }

  @Test
  public void testAllowCrossDCRequests() throws Exception {
    List<Server> servers = List.of(new Server("server1", 1, "DC1"), new Server("server2", 1, "DC2"));
    when(upstreamService.getServers(TEST_UPSTREAM)).thenReturn(servers);

    createHttpClientFactory(List.of(TEST_UPSTREAM), "DC1", true);

    Request[] request = new Request[1];
    when(httpClient.executeRequest(isA(Request.class), isA(CompletionHandler.class)))
      .then(iom -> {
        request[0] = completeWith(200, iom);
        return null;
      });

    getTestClient().get();
    assertHostEquals(request[0], "server1");

    getTestClient().get();
    assertHostEquals(request[0], "server1");

    when(upstreamService.getServers(TEST_UPSTREAM)).thenReturn(List.of(new Server("server2", 1, "DC2")));

    getTestClient().get();
    assertHostEquals(request[0], "server2");
  }

  @Override
  public boolean isAdaptive() {
    return false;
  }


  private String profileName(String serviceName, String profileName) {
    String[] ar = {serviceName, profileName};
    return Strings.join(ar, PROFILE_DELIMITER);
  }

}
