package com.rezhub.reservation.infrastructure;

import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCode;
import akka.http.javadsl.model.StatusCodes;
import akka.javasdk.http.HttpClient;
import akka.javasdk.http.HttpClientProvider;
import akka.javasdk.http.RequestBuilder;
import akka.javasdk.http.StrictResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Unit tests for {@link IdentityClient}'s fail-open contract, mirroring hit-backend's
 * `hit.infrastructure.IdentityClientTest`.
 *
 * Drives IdentityClient directly against a hand-rolled {@link HttpClient} double — no Akka runtime
 * needed, since IdentityClient is a plain class, not an Akka component.
 */
class IdentityClientTest {

    // -------------------------------------------------------------------------
    // Fakes
    // -------------------------------------------------------------------------

    /** Records the last request and returns a preconfigured (or exception-throwing) response. */
    private static class FakeHttpClient implements HttpClient {
        String lastPath;
        Object lastBody;
        StatusCode nextStatus = StatusCodes.OK;
        Object nextResponseBody;
        RuntimeException nextException;

        @Override
        public RequestBuilder<akka.util.ByteString> GET(String uri) { throw new UnsupportedOperationException(); }

        @Override
        public RequestBuilder<akka.util.ByteString> POST(String uri) {
            this.lastPath = uri;
            return new FakeRequestBuilder<>(this);
        }

        @Override
        public RequestBuilder<akka.util.ByteString> PUT(String uri) { throw new UnsupportedOperationException(); }

        @Override
        public RequestBuilder<akka.util.ByteString> PATCH(String uri) { throw new UnsupportedOperationException(); }

        @Override
        public RequestBuilder<akka.util.ByteString> DELETE(String uri) { throw new UnsupportedOperationException(); }
    }

    private static class FakeRequestBuilder<R> implements RequestBuilder<R> {
        private final FakeHttpClient owner;

        FakeRequestBuilder(FakeHttpClient owner) { this.owner = owner; }

        @Override
        public RequestBuilder<R> withRequest(akka.http.javadsl.model.HttpRequest request) { return this; }

        @Override
        public RequestBuilder<R> addHeader(String header, String value) { return this; }

        @Override
        public RequestBuilder<R> addHeader(akka.http.javadsl.model.HttpHeader header) { return this; }

        @Override
        public RequestBuilder<R> withHeaders(Iterable<akka.http.javadsl.model.HttpHeader> headers) { return this; }

        @Override
        public RequestBuilder<R> addCredentials(akka.http.javadsl.model.headers.HttpCredentials credentials) { return this; }

        @Override
        public RequestBuilder<R> withTimeout(java.time.Duration timeout) { return this; }

        @Override
        public RequestBuilder<R> addQueryParameter(String key, String value) { return this; }

        @Override
        public RequestBuilder<R> modifyRequest(Function<akka.http.javadsl.model.HttpRequest, akka.http.javadsl.model.HttpRequest> adapter) { return this; }

        @Override
        public RequestBuilder<R> withRequestBody(Object object) {
            owner.lastBody = object;
            return this;
        }

        @Override
        public RequestBuilder<R> withRequestBody(String text) { owner.lastBody = text; return this; }

        @Override
        public RequestBuilder<R> withRequestBody(byte[] bytes) { owner.lastBody = bytes; return this; }

        @Override
        public RequestBuilder<R> withRequestBody(akka.http.javadsl.model.ContentType type, byte[] bytes) { owner.lastBody = bytes; return this; }

        @Override
        public RequestBuilder<R> withRetry(akka.pattern.RetrySettings retrySettings) { return this; }

        @Override
        public RequestBuilder<R> withRetry(int maxRetries) { return this; }

        @Override
        public CompletionStage<StrictResponse<R>> invokeAsync() { return CompletableFuture.completedFuture(invoke()); }

        @SuppressWarnings("unchecked")
        @Override
        public StrictResponse<R> invoke() {
            if (owner.nextException != null) throw owner.nextException;
            var httpResponse = HttpResponse.create().withStatus(owner.nextStatus);
            return new StrictResponse<>(httpResponse, (R) owner.nextResponseBody);
        }

        @Override
        public <T> RequestBuilder<T> responseBodyAs(Class<T> type) { return (RequestBuilder<T>) this; }

        @Override
        public <T> RequestBuilder<List<T>> responseBodyAsListOf(Class<T> elementType) { throw new UnsupportedOperationException(); }

        @Override
        public <T> RequestBuilder<T> parseResponseBody(Function<byte[], T> parse) { throw new UnsupportedOperationException(); }
    }

    private FakeHttpClient fakeClient(StatusCode status, Object responseBody) {
        var fake = new FakeHttpClient();
        fake.nextStatus = status;
        fake.nextResponseBody = responseBody;
        return fake;
    }

    private IdentityClient clientFor(FakeHttpClient fake) {
        HttpClientProvider provider = serviceName -> {
            assertThat(serviceName).isEqualTo("identity");
            return fake;
        };
        return new IdentityClient(provider);
    }

    // -------------------------------------------------------------------------
    // resolveOrCreate
    // -------------------------------------------------------------------------

    @Test
    void resolveOrCreate_success_returnsUserId() {
        var fake = fakeClient(StatusCodes.OK, new IdentityClient.ResolveResponse("user-123", true));
        var client = clientFor(fake);

        var result = client.resolveOrCreate("TELEGRAM", "12345", Optional.empty());

        assertThat(result).contains("user-123");
        assertThat(fake.lastPath).isEqualTo("/internal/identities/resolve");
        assertThat(fake.lastBody).isInstanceOf(IdentityClient.ResolveRequest.class);
        var sentRequest = (IdentityClient.ResolveRequest) fake.lastBody;
        assertThat(sentRequest.provider()).isEqualTo("TELEGRAM");
        assertThat(sentRequest.externalId()).isEqualTo("12345");
        assertThat(sentRequest.claims()).isPresent();
        assertThat(sentRequest.claims().get().email()).isEmpty();
    }

    @Test
    void resolveOrCreate_failOpen_onErrorStatus() {
        var fake = fakeClient(StatusCodes.INTERNAL_SERVER_ERROR, null);
        var client = clientFor(fake);

        var result = client.resolveOrCreate("TELEGRAM", "12345", Optional.empty());

        assertThat(result).isEmpty();
    }

    @Test
    void resolveOrCreate_failOpen_onException() {
        var fake = new FakeHttpClient();
        fake.nextException = new RuntimeException("connection refused");
        var client = clientFor(fake);

        assertThatCode(() -> {
            var result = client.resolveOrCreate("TELEGRAM", "12345", Optional.empty());
            assertThat(result).isEmpty();
        }).doesNotThrowAnyException();
    }

    // -------------------------------------------------------------------------
    // link
    // -------------------------------------------------------------------------

    @Test
    void link_success_sendsExpectedRequest() {
        var fake = fakeClient(StatusCodes.OK, new IdentityClient.LinkResponse("user-123", true));
        var client = clientFor(fake);

        client.link("user-123", "AUTH", "TELEGRAM", "12345");

        assertThat(fake.lastPath).isEqualTo("/internal/identities/link");
        var sentRequest = (IdentityClient.LinkRequest) fake.lastBody;
        assertThat(sentRequest.userId()).isEqualTo("user-123");
        assertThat(sentRequest.kind()).isEqualTo("AUTH");
        assertThat(sentRequest.provider()).isEqualTo("TELEGRAM");
        assertThat(sentRequest.externalId()).isEqualTo("12345");
    }

    @Test
    void link_failOpen_onErrorStatus_doesNotThrow() {
        var fake = fakeClient(StatusCodes.NOT_FOUND, null);
        var client = clientFor(fake);

        assertThatCode(() -> client.link("user-123", "AUTH", "TELEGRAM", "12345"))
                .doesNotThrowAnyException();
    }

    @Test
    void link_failOpen_onException_doesNotThrow() {
        var fake = new FakeHttpClient();
        fake.nextException = new RuntimeException("timeout");
        var client = clientFor(fake);

        assertThatCode(() -> client.link("user-123", "AUTH", "TELEGRAM", "12345"))
                .doesNotThrowAnyException();
    }
}
