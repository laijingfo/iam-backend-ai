package com.lenovo.security.config;

import com.lenovo.security.config.bean.SecurityProperties;
import com.lenovo.security.security.JwtAccessDeniedHandler;
import com.lenovo.security.security.JwtAuthenticationEntryPoint;
import com.lenovo.security.security.TokenFilter;
import com.lenovo.security.security.TokenProvider;
import com.lenovo.security.service.OnlineUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.core.GrantedAuthorityDefaults;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

@Order(1)
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
public class SpringSecurityConfig
{
    private final TokenProvider tokenProvider;
    private final JwtAuthenticationEntryPoint authenticationErrorHandler;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;
    private final SecurityProperties properties;
    private final OnlineUserService onlineUserService;


    @Bean
    GrantedAuthorityDefaults grantedAuthorityDefaults()
    {
        // 去除 ROLE_ 前缀
        return new GrantedAuthorityDefaults("");
    }

    @Bean
    public PasswordEncoder passwordEncoder()
    {
        // 密码加密方式
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity) throws Exception
    {
        httpSecurity
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .addFilterBefore(new TokenFilter(tokenProvider, properties, onlineUserService),
                        UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationErrorHandler)
                        .accessDeniedHandler(jwtAccessDeniedHandler))
                .headers(headers -> headers.frameOptions(frameOptions -> frameOptions.disable()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(
                        HttpMethod.GET,
                        "/*.html",
                        "/**/*.html",
                        "/**/*.css",
                        "/**/*.js",
                        "/webSocket/**"
                ).permitAll()
                .requestMatchers("/ping","/ping2","/ping3","/ping4").permitAll()
                .requestMatchers("/auth/**").permitAll()
                .requestMatchers("/adfs").permitAll()
                .requestMatchers("/applicationAccessLink/openQuery").permitAll()
                .requestMatchers("/api/v1/**").permitAll()
                .requestMatchers("/task/**").permitAll()
                .requestMatchers("/api/tasks/**","/api/parame/**","/vue/user/**").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/webjars/**").permitAll()
                .anyRequest().authenticated());

        return httpSecurity.build();
    }


}
