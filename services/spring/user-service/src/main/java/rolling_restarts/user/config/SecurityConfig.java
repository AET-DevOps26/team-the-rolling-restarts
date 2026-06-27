package rolling_restarts.user.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
public class SecurityConfig {

	/**
	 * Authorization Server protocol endpoints (/oauth2/jwks, /.well-known/openid-configuration, …).
	 * Must run first so resource servers (api-gateway, content-service, and this service itself)
	 * can fetch the public keys to validate JWT signatures.
	 */
	@Bean
	@Order(1)
	public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
		OAuth2AuthorizationServerConfigurer authorizationServer = new OAuth2AuthorizationServerConfigurer();
		RequestMatcher endpointsMatcher = authorizationServer.getEndpointsMatcher();

		http
				.securityMatcher(endpointsMatcher)
				.authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
				.csrf(csrf -> csrf.ignoringRequestMatchers(endpointsMatcher))
				.with(authorizationServer, server -> server.oidc(Customizer.withDefaults()))
				.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));

		return http.build();
	}

	@Bean
	@Order(2)
	public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
		http
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers(
								"/auth/**",
								"/actuator/health",
								"/actuator/health/**",
								"/swagger-ui/**",
								"/v3/api-docs/**",
								"/swagger-ui.html")
						.permitAll()
						.anyRequest().authenticated())
				// Token-based API: never create or rely on an HTTP session, so any replica
				// can serve any request without sticky sessions or a shared JSESSIONID store.
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.csrf(csrf -> csrf.disable())
				.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}));

		return http.build();
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}
