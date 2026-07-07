package rolling_restarts.user.config;

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
 */
@Component
public class RequestLoggingFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger("rolling_restarts.user.RequestLog");

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (request instanceof HttpServletRequest httpRequest) {
            log.info("{} {}", httpRequest.getMethod(), httpRequest.getRequestURI());
        }
        chain.doFilter(request, response);
    }
}
