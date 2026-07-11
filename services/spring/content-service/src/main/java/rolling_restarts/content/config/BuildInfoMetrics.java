package rolling_restarts.content.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Component;

/**
 * Exposes the deployed build's version as a static Prometheus gauge (always 1, version carried
 * as a label), so dashboards can correlate metric/behavior changes with a specific release.
 */
@Component
public class BuildInfoMetrics implements MeterBinder {

    private final BuildProperties buildProperties;

    public BuildInfoMetrics(BuildProperties buildProperties) {
        this.buildProperties = buildProperties;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("app_build_info", () -> 1)
                .tag("service", buildProperties.getName())
                .tag("version", buildProperties.getVersion())
                .description("Static 1; version/service are labels for correlating releases with metric changes")
                .register(registry);
    }
}
