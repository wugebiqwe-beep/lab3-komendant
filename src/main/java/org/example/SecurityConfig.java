
package org.example;

import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http) throws Exception {

        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login").permitAll()

                        .requestMatchers(
                                "/buildings/add",
                                "/buildings/edit",
                                "/buildings/update",
                                "/buildings/delete",
                                "/rooms/add",
                                "/rooms/edit",
                                "/rooms/update",
                                "/rooms/delete",
                                "/furniture/add",
                                "/furniture/edit",
                                "/furniture/update",
                                "/furniture/delete"
                        ).hasRole("ADMIN")

                        .requestMatchers(
                                "/buildings",
                                "/rooms",
                                "/furniture"
                        ).hasAnyRole("ADMIN", "USER")

                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .defaultSuccessUrl("/buildings", true)
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutSuccessUrl("/login?logout")
                )

                .csrf(csrf -> {});

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Завантаження користувачів із PostgreSQL
    @Bean
    public UserDetailsService userDetailsService() {

        return username -> {

            String sql = """
                    SELECT username, password, role
                    FROM users
                    WHERE username = ?
                    """;

            try (Connection con = DatabaseConnection.getConnection();
                 PreparedStatement ps = con.prepareStatement(sql)) {

                ps.setString(1, username);

                try (ResultSet rs = ps.executeQuery()) {

                    if (!rs.next()) {
                        throw new UsernameNotFoundException(
                                "Користувача не знайдено: " + username
                        );
                    }

                    String dbUsername = rs.getString("username");
                    String dbPassword = rs.getString("password");
                    String dbRole = rs.getString("role");

                    return User.withUsername(dbUsername)
                            .password(dbPassword)
                            .roles(dbRole)
                            .build();
                }

            } catch (UsernameNotFoundException e) {
                throw e;
            } catch (Exception e) {
                throw new AuthenticationServiceException(
                        "Помилка підключення до бази даних", e
                );
            }
        };
    }
}
