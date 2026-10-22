package com.athletiq.support;

import com.athletiq.common.config.CorsConfig;
import com.athletiq.common.security.JwtConfig;
import com.athletiq.common.security.ProblemDetailAccessDeniedHandler;
import com.athletiq.common.security.ProblemDetailAuthenticationEntryPoint;
import com.athletiq.common.security.ProblemDetailResponseWriter;
import com.athletiq.common.security.SecurityConfig;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

/**
 * La vraie configuration de sécurité, à importer dans les tests {@code @WebMvcTest} (qui ne chargent
 * pas les {@code @Component} et {@code @Configuration} ordinaires).
 */
@TestConfiguration
@Import({SecurityConfig.class, JwtConfig.class, CorsConfig.class, ProblemDetailResponseWriter.class,
        ProblemDetailAuthenticationEntryPoint.class, ProblemDetailAccessDeniedHandler.class})
public class WebSecurityTestConfig {
}
