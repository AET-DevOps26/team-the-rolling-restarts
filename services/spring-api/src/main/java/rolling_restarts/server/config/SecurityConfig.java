package rolling_restarts.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(authorize -> authorize
                // allow anonymous access to the root and the hello API used for smoke testing
                .requestMatchers("/", "/api/hello", "/actuator/**", "/favicon.ico", "/css/**", "/js/**").permitAll()
                .anyRequest().authenticated()
            )
            .csrf(csrf -> csrf.disable())
            // disable interactive form login so the framework won't redirect to a login page
            .formLogin(form -> form.disable())
            // disable http basic as well for now
            .httpBasic(basic -> basic.disable());

        return http.build();
    }
}
