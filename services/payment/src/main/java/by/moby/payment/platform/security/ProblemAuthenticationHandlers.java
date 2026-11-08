package by.moby.payment.platform.security;

import by.moby.common.error.ErrorCode;
import by.moby.common.error.ProblemFields;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;

/** 401/403 от фильтров безопасности — тоже в формате ProblemDetail. */
@Component
public class ProblemAuthenticationHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final JsonMapper json;

    public ProblemAuthenticationHandlers(JsonMapper json) {
        this.json = json;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException e) throws IOException {
        write(response, HttpStatus.UNAUTHORIZED, "Нужна авторизация", "UNAUTHORIZED");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException e) throws IOException {
        write(response, HttpStatus.FORBIDDEN, "Нет доступа", ErrorCode.FORBIDDEN.name());
    }

    private void write(HttpServletResponse response, HttpStatus status, String detail, String code) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("https://moby.by/problems/" + code.toLowerCase()));
        problem.setProperty(ProblemFields.CODE, code);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        json.writeValue(response.getOutputStream(), problem);
    }
}
