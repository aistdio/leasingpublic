package ru.bell.autoleasing.exceptions;

import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import ru.bell.autoleasing.agent.api.AgentErrorResponse;
import ru.bell.autoleasing.agent.core.AgentUnavailableException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class ControllerAdvice {
    @ExceptionHandler(Exception.class)
    public ResponseEntity<DefaultErrorResponse> handleGlobalException(Exception e) {
        log.error("Было получено необработанное исключение: {}", e.getMessage(), e);
        DefaultErrorResponse errorResponse = new DefaultErrorResponse("Произошла ошибка на стороне сервера.");
        return ResponseEntity.internalServerError().body(errorResponse);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<DefaultErrorResponse> handleOptimisticLockingFailure(ObjectOptimisticLockingFailureException e) {
        log.error("Optimistic Locking Failure: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new DefaultErrorResponse("Поступило слишком много запросов, попробуйте позже."));
    }

    @ExceptionHandler(JpaSystemException.class)
    public ResponseEntity<DefaultErrorResponse> handleJpaSystemException(JpaSystemException e) {
        log.error("JPA_ERROR: {}", e.getMessage(), e);
        return ResponseEntity.internalServerError().body(new DefaultErrorResponse("Произошла внутренняя ошибка при работе с базой данных."));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<DefaultErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        String message = "Произошла ошибка целостности данных.";
        if (e.getCause() instanceof org.hibernate.exception.ConstraintViolationException) {
            message = extractConstraintViolationMessage((ConstraintViolationException) e.getCause());
        }
        log.error("{} {}", message, e.getMessage(), e);
        return ResponseEntity.badRequest().body(new DefaultErrorResponse(message));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<DefaultErrorResponse> handleDataAccess(DataAccessException e) {
        log.error("DATA_ACCESS_ERROR: {}", e.getMessage());
        return ResponseEntity.badRequest().body(new DefaultErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<DefaultErrorResponse> handleBadCredentials(BadCredentialsException e) {
        log.warn("Были введены неверные данные при аутентификации: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new DefaultErrorResponse("Были введены неверные данные."));
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<DefaultErrorResponse> handleEntityNotFound(EntityNotFoundException e) {
        log.warn("Сущность не была найдена: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new DefaultErrorResponse("Сущность не была найдена: " + e.getMessage()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<DefaultErrorResponse> handleNoResourceFound(NoResourceFoundException e) {
        log.warn("Ресурс не был найден: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new DefaultErrorResponse("Ресурс не был найден: " + e.getResourcePath()));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<DefaultErrorResponse> handleNotFound(NotFoundException e) {
        log.warn(e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new DefaultErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<DefaultErrorResponse> handleHttpRequestMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("Метод {} не поддерживается: {}", e.getMethod(), e.getMessage());
        return ResponseEntity.badRequest().body(new DefaultErrorResponse("Метод " + e.getMethod() + " не поддерживается."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleMethodValidation(MethodArgumentNotValidException e) {
        Map<String, Object> response = new HashMap<>();
        List<String> errors = e.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> String.format("%s: %s", fieldError.getField(), fieldError.getDefaultMessage()))
                .toList();
        response.put("errors", errors);
        log.warn("Аргумент метода не прошел валидацию [{}]: {}", response, e.getMessage());
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<DefaultErrorResponse> handleCustomValidation(ValidationException e) {
        log.warn(e.getMessage());
        return ResponseEntity.badRequest().body(new DefaultErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<DefaultErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new DefaultErrorResponse("Неверное значение параметра " + e.getName() + ": " + e.getValue()));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<DefaultErrorResponse> handleMissingParameter(MissingServletRequestParameterException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new DefaultErrorResponse("Не указан обязательный параметр " + e.getParameterName()));
    }

    @ExceptionHandler(AgentUnavailableException.class)
    public ResponseEntity<AgentErrorResponse> handleAgentUnavailable(AgentUnavailableException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new AgentErrorResponse(e.getMessage(), e.getTraceId().toString()));
    }

    @ExceptionHandler(DataCreationException.class)
    public ResponseEntity<DefaultErrorResponse> handleDataCreation(DataCreationException e) {
        log.warn(e.getMessage());
        return ResponseEntity.badRequest().body(new DefaultErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(DataUpdateException.class)
    public ResponseEntity<DefaultErrorResponse> handleDataUpdate(DataUpdateException e) {
        log.warn(e.getMessage());
        return ResponseEntity.badRequest().body(new DefaultErrorResponse(e.getMessage()));
    }

    private String extractConstraintViolationMessage(org.hibernate.exception.ConstraintViolationException e) {
        return e.getConstraintName() != null ?
                "Нарушено ограничение: " + e.getConstraintName() :
                "Нарушено ограничение целостности данных";
    }
}
