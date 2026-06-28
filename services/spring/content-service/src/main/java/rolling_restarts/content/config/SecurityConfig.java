package rolling_restarts.content.config;

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
								"/actuator/health",
								"/actuator/health/**",
								"/swagger-ui/**",
								"/v3/api-docs/**",
								"/swagger-ui.html")
						.permitAll()
						.requestMatchers(HttpMethod.GET, "/sources", "/sources/**", "/topics", "/articles", "/articles/**")
						.permitAll()
						// Subscriber-count mutations are service-to-service only: user-service calls them
						// with a client_credentials token scoped to source.write. Ordinary end-user JWTs
						// never carry this scope, so a user cannot inflate/deflate counts or delete a
						// shared source by hitting these endpoints directly through the gateway.
						.requestMatchers(HttpMethod.POST, "/sources/*/subscribe", "/sources/*/unsubscribe")
						.hasAuthority("SCOPE_source.write")
						.anyRequest().authenticated())
				.csrf(csrf -> csrf.disable())
				.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}))
				.formLogin(form -> form.disable())
				.httpBasic(basic -> basic.disable());

		return http.build();
	}
}
