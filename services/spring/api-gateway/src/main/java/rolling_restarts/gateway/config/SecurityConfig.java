package rolling_restarts.gateway.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

	@Value("${cors.allowed-origins:}")
	private String allowedOrigins;

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				.cors(cors -> cors.configurationSource(corsConfigurationSource()))
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers(
								"/",
								"/test",
								"/dummy",
								"/api/hello",
								"/actuator/health",
								"/actuator/health/**",
								"/favicon.ico",
								"/swagger-ui/**",
								"/v3/api-docs/**",
								"/swagger-ui.html",
								"/api/users/v3/api-docs/**",
								"/api/content/v3/api-docs/**",
								"/api/users/auth/**")
						.permitAll()
						.requestMatchers(HttpMethod.GET,
								"/api/content/sources", "/api/content/sources/**",
								"/api/content/topics",
								"/api/content/articles", "/api/content/articles/**")
						.permitAll()
						.requestMatchers("/api/ai/**")
						.permitAll()
						.requestMatchers(HttpMethod.POST,
								"/api/content/sources/*/subscribe",
								"/api/content/sources/*/unsubscribe")
						.hasAuthority("SCOPE_source.write")
						.anyRequest().authenticated())
				.csrf(csrf -> csrf.disable())
				.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}))
				.formLogin(form -> form.disable())
				.httpBasic(basic -> basic.disable());

		return http.build();
	}

	@Bean
	public CorsConfigurationSource corsConfigurationSource() {
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

		// Fail closed: with no configured origins, register no CORS rule so the browser blocks
		// every cross-origin request instead of the gateway serving a wildcard by default.
		if (allowedOrigins == null || allowedOrigins.isBlank()) {
			return source;
		}

		List<String> origins = Arrays.stream(allowedOrigins.split(","))
				.map(String::trim)
				.filter(o -> !o.isEmpty())
				.toList();

		CorsConfiguration config = new CorsConfiguration();
		if (origins.contains("*")) {
			config.setAllowedOriginPatterns(List.of("*"));
		} else {
			config.setAllowedOrigins(origins);
			config.setAllowCredentials(true);
		}
		config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
		config.setAllowedHeaders(List.of("*"));
		source.registerCorsConfiguration("/**", config);
		return source;
	}
}
