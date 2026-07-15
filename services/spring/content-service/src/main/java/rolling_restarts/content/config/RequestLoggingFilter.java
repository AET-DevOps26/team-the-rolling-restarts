package rolling_restarts.content.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Logs "{method} {requestURI}" for every request at INFO. This has no operational purpose by
 * itself (Spring Boot doesn't emit a per-request access log otherwise) — it exists so the OTLP
 * log pipeline carries a line the smoke test (infra/scripts/smoke-test.sh) can cross-check
 * against Loki to confirm a specific request it just made was actually logged and exported.
 *
 * <p>Excludes Prometheus's own scrape requests ({@code /actuator/prometheus}) — those repeat on
 * every scrape interval across every replica, so logging them at INFO would constantly flood the
 * OTLP log pipeline / Loki storage purely as a side effect of being monitored. The smoke test
 * only ever checks for a request path it made itself (e.g. {@code GET /actuator/health}), never
 * the scrape endpoint, so this doesn't affect it.
 */
@Component
public class RequestLoggingFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger("rolling_restarts.content.RequestLog");

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (request instanceof HttpServletRequest httpRequest
                && !"/actuator/prometheus".equals(httpRequest.getRequestURI())) {
            log.info("{} {}", httpRequest.getMethod(), httpRequest.getRequestURI());
        }
        chain.doFilter(request, response);
    }
}
