package com.athletiq.common.security;

import com.athletiq.common.exception.ProblemDetails;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/**
 * Écrit un ProblemDetail dans la réponse depuis les filtres de sécurité, qui s'exécutent avant
 * Spring MVC et ne passent donc pas par le {@code GlobalExceptionHandler}.
 */
@Component
@RequiredArgsConstructor
public class ProblemDetailResponseWriter {

    private final ObjectMapper objectMapper;

    public void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status,
                      String code, String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(),
                ProblemDetails.of(status, code, detail, request.getRequestURI()));
    }
}
