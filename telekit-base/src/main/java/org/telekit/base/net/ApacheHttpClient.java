package org.telekit.base.net;

import org.apache.hc.client5.http.auth.AuthCache;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.CredentialsStore;
import org.apache.hc.client5.http.classic.methods.*;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.cookie.StandardCookieSpec;
import org.apache.hc.client5.http.impl.DefaultSchemePortResolver;
import org.apache.hc.client5.http.impl.auth.BasicAuthCache;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.auth.BasicScheme;
import org.apache.hc.client5.http.impl.auth.DigestScheme;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.BasicHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.routing.DefaultRoutePlanner;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.socket.ConnectionSocketFactory;
import org.apache.hc.client5.http.socket.PlainConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.http.*;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.util.Timeout;
import org.telekit.base.domain.Proxy;
import org.telekit.base.domain.security.UsernamePasswordCredentials;
import org.telekit.base.net.HttpConstants.AuthScheme;
import org.telekit.base.net.connection.ConnectionParams;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCause;
import static org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace;
import static org.telekit.base.util.NumberUtils.ensureRange;

public class ApacheHttpClient implements HttpClient {

    public static final AuthScope AUTH_SCOPE_ANY = new AuthScope(null, null, -1, null, null);
    public static final Set<String> SUPPORTED_SSL_PROTOCOLS = HttpConstants.SSL_PROTOCOLS;

    private final HttpClientResponseHandler<Response> handler = new SpecificResponseHandler();
    private final CloseableHttpClient client;
    private final HttpClientContext localContext;

    private ApacheHttpClient(CloseableHttpClient client, HttpClientContext localContext) {
        this.client = client;
        this.localContext = localContext;
    }

    @Override
    public Response execute(Request request) {
        try {
            HttpUriRequestBase httpRequest = createHttpRequest(request);
            Response response = client.execute(httpRequest, localContext, handler);

            // extract actual request headers from context
            request.headers().putAll(
                    headersToMap(localContext.getRequest().getHeaders())
            );
            return response;
        } catch (IOException e) {
            return new Response(-1, e.getMessage(), new HashMap<>(), getStackTrace(getRootCause(e)));
        }
    }

    private static Header[] mapToHeaders(Map<String, String> headers) {
        return headers.entrySet().stream()
                .map(e -> new BasicHeader(e.getKey(), e.getValue()))
                .toArray(Header[]::new);
    }

    private static SortedMap<String, String> headersToMap(Header[] headers) {
        return Arrays.stream(headers)
                .collect(Collectors.toMap(Header::getName, Header::getValue, (v1, v2) -> v2, TreeMap::new));
    }

    private static String consumeBodyQuietly(HttpEntity entity) {
        try {
            return entity != null ? EntityUtils.toString(entity) : null;
        } catch (IOException | ParseException e) {
            e.printStackTrace();
            return "<unable to obtain response body>";
        }
    }

    public HttpUriRequestBase createHttpRequest(Request request) {
        HttpUriRequestBase requestBase = switch (request.method()) {
            case DELETE -> new HttpDelete(request.uri());
            case GET -> new HttpGet(request.uri());
            case HEAD -> new HttpHead(request.uri());
            case PATCH -> new HttpPatch(request.uri());
            case POST -> new HttpPost(request.uri());
            case PUT -> new HttpPut(request.uri());
        };

        requestBase.setHeaders(mapToHeaders(request.headers()));
        requestBase.setEntity(new ByteArrayEntity(request.body().getBytes(), ContentType.TEXT_PLAIN));

        return requestBase;
    }

    public static Builder builder() {
        return new Builder();
    }

    ///////////////////////////////////////////////////////////////////////////

    public static class Builder {

        private final HttpClientBuilder httpBuilder = HttpClients.custom();
        private final RequestConfig.Builder requestConfigBuilder = RequestConfig.custom();
        private final CredentialsStore credentialsProvider = new BasicCredentialsProvider();
        private final HttpClientContext localContext = HttpClientContext.create();

        public Builder() {
            requestConfigBuilder
                    .setConnectTimeout(Timeout.of(CONNECT_TIMEOUT, TimeUnit.MILLISECONDS))
                    .setResponseTimeout(Timeout.of(RESPONSE_TIMEOUT, TimeUnit.MILLISECONDS));
            httpBuilder.setUserAgent(USER_AGENT);
            httpBuilder.setDefaultCredentialsProvider(credentialsProvider);
        }

        public Builder timeouts(int timeout) {
            return timeouts(timeout, timeout);
        }

        public Builder timeouts(int connectTimeout, int responseTimeout) {
            requestConfigBuilder.setConnectTimeout(Timeout.of(
                    ensureRange(connectTimeout, 100, 10_000), TimeUnit.MILLISECONDS)
            );
            requestConfigBuilder.setResponseTimeout(Timeout.of(
                    ensureRange(responseTimeout, 100, 60_000), TimeUnit.MILLISECONDS)
            );
            return this;
        }

        public Builder userAgent(String userAgent) {
            httpBuilder.setUserAgent(userAgent);
            return this;
        }

        public Builder trustAllCertificates() {
            try {
                SSLContext sslContext = SSLContexts.custom()
                        .loadTrustMaterial((chain, authType) -> true)
                        .build();

                SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(
                        sslContext,
                        SUPPORTED_SSL_PROTOCOLS.toArray(new String[0]),
                        null,
                        NoopHostnameVerifier.INSTANCE
                );

                BasicHttpClientConnectionManager connectionManager = new BasicHttpClientConnectionManager(
                        RegistryBuilder.<ConnectionSocketFactory>create()
                                .register(URIScheme.HTTP.id, PlainConnectionSocketFactory.getSocketFactory())
                                .register(URIScheme.HTTPS.id, sslSocketFactory)
                                .build()
                );

                httpBuilder.setConnectionManager(connectionManager);
            } catch (Exception ignored) {}

            return this;
        }

        public Builder ignoreCookies() {
            requestConfigBuilder.setCookieSpec(StandardCookieSpec.IGNORE);
            return this;
        }

        public Builder proxy(Proxy proxy) {
            httpBuilder.setRoutePlanner(new CachingProxyRoutePlanner(proxy, credentialsProvider));
            return this;
        }

        public Builder basicAuth(PasswordAuthentication auth, URI uri, boolean preemptive) {
            return passwordBasedAuth(AuthScheme.BASIC, auth, uri, preemptive);
        }

        public Builder digestAuth(PasswordAuthentication auth, URI uri, boolean preemptive) {
            return passwordBasedAuth(AuthScheme.DIGEST, auth, uri, preemptive);
        }

        private Builder passwordBasedAuth(AuthScheme authScheme,
                                          PasswordAuthentication auth,
                                          URI uri,
                                          boolean preemptive) {
            credentialsProvider.setCredentials(
                    AUTH_SCOPE_ANY,
                    new org.apache.hc.client5.http.auth.UsernamePasswordCredentials(auth.getUserName(), auth.getPassword().clone())
            );

            // preemptive auth puts auth headers into each request to omit challenge (401) stage
            if (!preemptive) { return this; }

            // BasicAuthCache doesn't mean "for basic auth", it's just a misleading name choice
            AuthCache authCache = new BasicAuthCache();
            HttpHost targetHost = new HttpHost(uri.getHost());

            if (authScheme == AuthScheme.BASIC) { authCache.put(targetHost, new BasicScheme()); }
            if (authScheme == AuthScheme.DIGEST) { authCache.put(targetHost, new DigestScheme()); }

            localContext.setAuthCache(authCache);

            return this;
        }

        public ApacheHttpClient build() {
            httpBuilder.setDefaultRequestConfig(requestConfigBuilder.build());
            CloseableHttpClient httpClient = httpBuilder.build();
            return new ApacheHttpClient(httpClient, localContext);
        }
    }

    ///////////////////////////////////////////////////////////////////////////

    static class SpecificResponseHandler implements HttpClientResponseHandler<Response> {

        @Override
        public Response handleResponse(ClassicHttpResponse response) {
            return new Response(
                    response.getCode(),
                    response.getReasonPhrase(),
                    headersToMap(response.getHeaders()),
                    consumeBodyQuietly(response.getEntity())
            );
        }
    }

    static class CachingProxyRoutePlanner extends DefaultRoutePlanner {

        private final Proxy proxy;
        private final CredentialsStore credentialsProvider;

        private final Map<String, HttpHost> routeCache = new HashMap<>();
        private final Map<String, HttpHost> proxyHostCache = new HashMap<>();

        public CachingProxyRoutePlanner(Proxy proxy, CredentialsStore credentialsProvider) {
            super(new DefaultSchemePortResolver());

            this.proxy = proxy;
            this.credentialsProvider = credentialsProvider;
        }

        @Override
        protected HttpHost determineProxy(HttpHost target, HttpContext context) {
            String targetHostname = target.getHostName();
            ConnectionParams params = proxy.getConnectionParams(targetHostname);

            // proxying not required, use direct route
            if (params == null) { return null; }

            // use route from cache, even if null
            HttpHost cachedRoute = routeCache.get(targetHostname);
            if (cachedRoute != null) { return cachedRoute; }

            String proxyKey = params.scheme().toString() + "://" + params.host() + ":" + params.port();
            HttpHost proxyHost = proxyHostCache.get(proxyKey);
            if (proxyHost == null) {
                proxyHost = new HttpHost(params.scheme().toString(), params.host(), params.port());

                // Do not instantiate auth scope from proxyHost, because the latter also
                // includes scheme, There will be no match if proxy uses HTTP and target uses HTTPS.
                AuthScope proxyScope = new AuthScope(params.host(), params.port());

                // credentials is optional for proxy
                UsernamePasswordCredentials cred = null;
                if (params.credential() instanceof UsernamePasswordCredentials userPassword) {
                    cred = userPassword;
                }

                if (cred != null && credentialsProvider.getCredentials(proxyScope, context) != null) {
                    credentialsProvider.setCredentials(proxyScope,
                            new org.apache.hc.client5.http.auth.UsernamePasswordCredentials(cred.getUsername(), cred.getPassword())
                    );
                }

                proxyHostCache.put(proxyKey, proxyHost);
            }

            routeCache.put(targetHostname, proxyHost);

            return proxyHost;
        }
    }
}
