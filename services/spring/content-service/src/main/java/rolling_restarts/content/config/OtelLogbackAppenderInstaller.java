package rolling_restarts.content.config;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/**
 * The OTEL appender declared in logback-spring.xml needs an explicit install() call to bind
 * it to the actual OpenTelemetry SDK instance Spring Boot configures — declaring it in the
 * XML alone leaves it a no-op.
 */
@Component
public class OtelLogbackAppenderInstaller {

    @PostConstruct
    void install() {
        OpenTelemetryAppender.install(GlobalOpenTelemetry.get());
    }
}
