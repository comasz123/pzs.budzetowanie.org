package pl.ngo.budget.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final int REMEMBER_ME_SECONDS = 60 * 60 * 24 * 30; // 30 dni

    private final UserDetailsService userDetailsService;
    private final boolean requireAuth;
    private final boolean requireHttps;

    public SecurityConfig(UserDetailsService userDetailsService,
                          @Value("${app.security.require-auth:true}") boolean requireAuth,
                          @Value("${app.security.require-https:false}") boolean requireHttps) {
        this.userDetailsService = userDetailsService;
        this.requireAuth = requireAuth;
        this.requireHttps = requireHttps;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        if (!requireAuth) {
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }

        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/logout", "/css/**", "/js/**", "/images/**",
                        "/favicon.ico", "/favicon-16x16.png", "/favicon-32x32.png", "/apple-touch-icon.png").permitAll()
                .requestMatchers("/admin/users", "/admin/users/**").hasRole("ADMIN")
                .requestMatchers("/admin/dziennik", "/admin/dziennik/**").hasRole("ADMIN")
                .requestMatchers("/dashboard/backup/download", "/realizacja/backup/download").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET,
                        "/admin/grants/new", "/admin/grants/*/edit",
                        "/realizacja/grants/new", "/realizacja/grants/*/edit",
                        "/admin/sponsors/new", "/admin/sponsors/*/edit",
                        "/realizacja/sponsors/new", "/realizacja/sponsors/*/edit",
                        "/admin/sponsor-contacts/new", "/admin/sponsor-contacts/*/edit",
                        "/realizacja/sponsor-contacts/new", "/realizacja/sponsor-contacts/*/edit",
                        "/admin/employees/new", "/admin/employees/*/edit",
                        "/realizacja/employees/new", "/realizacja/employees/*/edit",
                        "/admin/projects/new", "/realizacja/projects/new",
                        "/admin/budget/new", "/admin/budget/new/**",
                        "/realizacja/budget/new", "/realizacja/budget/new/**",
                        "/admin/expenditures/import",
                        "/realizacja/wydatki/import").hasAnyRole("ADMIN", "EDIT")
                .requestMatchers(HttpMethod.POST, "/**").hasAnyRole("ADMIN", "EDIT")
                .requestMatchers(HttpMethod.PUT, "/**").hasAnyRole("ADMIN", "EDIT")
                .requestMatchers(HttpMethod.PATCH, "/**").hasAnyRole("ADMIN", "EDIT")
                .requestMatchers(HttpMethod.DELETE, "/**").hasAnyRole("ADMIN", "EDIT")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/", true)
                .failureUrl("/login?error=true")
                .permitAll()
            )
            .rememberMe(remember -> remember
                .key("budget-ngo-remember-me")
                .userDetailsService(userDetailsService)
                .tokenValiditySeconds(REMEMBER_ME_SECONDS)
                .useSecureCookie(requireHttps)
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/login?logout=true")
                .deleteCookies("JSESSIONID", "remember-me")
                .permitAll()
            );

        if (requireHttps) {
            http.headers(headers -> headers.httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .maxAgeInSeconds(31_536_000)));
        }

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
