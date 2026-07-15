package rolling_restarts.user.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/**
 * The OTEL appender declared in logback-spring.xml needs an explicit install() call to bind
 * it to the actual OpenTelemetry SDK instance Spring Boot configures — declaring it in the
 * XML alone leaves it a no-op.
 *
 * <p>Must use the {@link OpenTelemetry} bean Spring Boot's OpenTelemetrySdkAutoConfiguration
 * wires up (injected here), NOT {@code GlobalOpenTelemetry.get()}: Spring Boot builds its own
 * OpenTelemetrySdk bean for internal use but never registers it as the process-wide
 * GlobalOpenTelemetry singleton, so installing against the global accessor silently binds the
 * appender to a no-op LoggerProvider and every log record is dropped instead of exported.
 */
@Component
public class OtelLogbackAppenderInstaller {

    private final OpenTelemetry openTelemetry;

    public OtelLogbackAppenderInstaller(OpenTelemetry openTelemetry) {
        this.openTelemetry = openTelemetry;
    }

    @PostConstruct
    void install() {
        OpenTelemetryAppender.install(openTelemetry);
    }
}
