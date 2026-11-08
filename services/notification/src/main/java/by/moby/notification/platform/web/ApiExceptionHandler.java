package by.moby.notification.platform.web;

import by.moby.common.error.BusinessException;
import by.moby.common.error.ErrorCode;
import by.moby.common.error.ProblemFields;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;

/** Все ошибки API — RFC 9457 ProblemDetail с машиночитаемым {@code code}. */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    public static ProblemDetail problem(ErrorCode code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(code.httpStatus()), detail);
        problem.setType(URI.create(code.typeUri()));
        problem.setTitle(code.title());
        problem.setProperty(ProblemFields.CODE, code.name());
        return problem;
    }

    @ExceptionHandler(BusinessException.class)
    ProblemDetail business(BusinessException e) {
        ProblemDetail problem = problem(e.code(), e.reason());
        e.details().forEach(problem::setProperty);
        return problem;
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail forbidden(AccessDeniedException e) {
        return problem(ErrorCode.FORBIDDEN, "Нет доступа");
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    ProblemDetail missingHeader(MissingRequestHeaderException e) {
        if (IdempotencyKey.HEADER.equalsIgnoreCase(e.getHeaderName())) {
            return problem(ErrorCode.IDEMPOTENCY_KEY, "Нужен заголовок " + IdempotencyKey.HEADER);
        }
        return problem(ErrorCode.VALIDATION, "Нужен заголовок " + e.getHeaderName());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException e, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .findFirst()
                .orElse("Некорректный запрос");
        return ResponseEntity.badRequest().body(problem(ErrorCode.VALIDATION, detail));
    }
}
