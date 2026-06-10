package com.agent.harness.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 글로벌 CORS 설정.
 * <p>
 * 로컬 개발 환경에서 프론트엔드(정적 HTML)를 Caddy 프록시 없이
 * 직접 열 때 CORS 오류가 발생하는 것을 방지합니다.
 * <p>
 * ⚠️ Caddy/Nginx 프록시가 완전히 안정되면 이 클래스를 제거하고
 * 프록시 레벨에서 CORS를 관리하세요.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(
                        "http://localhost:3000",
                        "http://localhost:5173",
                        "http://localhost:8080",
                        "http://localhost:80"
                )
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
