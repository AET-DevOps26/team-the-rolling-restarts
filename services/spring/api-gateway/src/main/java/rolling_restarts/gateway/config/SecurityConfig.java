package rolling_restarts.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
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
						.anyRequest().authenticated())
				.csrf(csrf -> csrf.disable())
				.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}))
				.formLogin(form -> form.disable())
				.httpBasic(basic -> basic.disable());

		return http.build();
	}
}
