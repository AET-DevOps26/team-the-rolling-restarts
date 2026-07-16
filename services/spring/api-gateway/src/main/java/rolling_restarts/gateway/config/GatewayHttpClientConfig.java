package rolling_restarts.gateway.config;

import java.net.http.HttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;

@Configuration
public class GatewayHttpClientConfig {

    // Spring Cloud Gateway Server WebMVC proxies every route through a RestClient backed by the
    // JDK's own HttpClient (no Apache/Jetty/Reactor Netty client on the classpath), which defaults
    // to attempting an HTTP/1.1 -> HTTP/2 cleartext upgrade (Upgrade: h2c) on every request.
    // uvicorn (gen-ai's server) doesn't support h2c and mishandles the chunked body that follows,
    // dropping it entirely — confirmed live via a raw TCP capture between api-gateway and gen-ai,
    // reproducing FastAPI's "Validation failed: body: Field required" on every /api/ai/** call
    // even though the client sent a well-formed body.
    //
    // A plain RestClientCustomizer bean doesn't survive: gateway's own
    // GatewayServerMvcAutoConfiguration#gatewayRestClientCustomizer looks up a
    // ClientHttpRequestFactory bean via ObjectProvider and applies it unconditionally, running
    // after (and overwriting) any customizer that just sets requestFactory directly — confirmed
    // live via a debug probe (the RestClientCustomizer's customize() logged as invoked, but the
    // outgoing request was unchanged). Defining the ClientHttpRequestFactory bean itself is what
    // that lookup actually finds, since Boot's own auto-configured default backs off
    // (@ConditionalOnMissingBean) once the app supplies one.
    @Bean
    ClientHttpRequestFactory http11ClientHttpRequestFactory() {
        HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        return new JdkClientHttpRequestFactory(httpClient);
    }
}
