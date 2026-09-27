package com.vasanth.agenticsdlcorchestrator.config;

import java.util.Collection;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfiguration {
    @Bean
    @Profile("!prod & !test")
    SecurityFilterChain localSecurity(HttpSecurity http) throws Exception {
        return common(http).httpBasic(Customizer.withDefaults()).build();
    }

    @Bean
    @Profile("!prod & !test")
    UserDetailsService localUsers(org.springframework.core.env.Environment environment) {
        String user = environment.getRequiredProperty("agentic.security.local.username");
        String password = environment.getRequiredProperty("agentic.security.local.password");
        return new InMemoryUserDetailsManager(User.withUsername(user).password("{noop}" + password)
                .roles("OPERATOR", "CHANGE_APPROVER", "RELEASE_APPROVER").build());
    }

    @Bean
    @Profile("prod")
    SecurityFilterChain productionSecurity(HttpSecurity http) throws Exception {
        return common(http).oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtRoles()))).build();
    }

    @Bean
    @Profile("test")
    SecurityFilterChain testSecurity(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable()).authorizeHttpRequests(auth -> auth.anyRequest().permitAll()).build();
    }

    private HttpSecurity common(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable()).authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health/**").permitAll()
                .requestMatchers("/actuator/prometheus").hasRole("OPERATOR")
                .requestMatchers("/api/v1/workflows/*/approvals/change").hasRole("CHANGE_APPROVER")
                .requestMatchers("/api/v1/workflows/*/approvals/release").hasRole("RELEASE_APPROVER")
                .anyRequest().hasRole("OPERATOR"));
    }

    private Converter<Jwt, ? extends AbstractAuthenticationToken> jwtRoles() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<String> roles = jwt.getClaimAsStringList("roles");
            if (roles == null) roles = List.of();
            return roles.stream().map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role)).toList();
        });
        return converter;
    }
}
