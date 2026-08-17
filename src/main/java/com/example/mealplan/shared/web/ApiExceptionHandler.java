package com.example.mealplan.shared.web;

import com.example.mealplan.shared.domain.DomainException;
import com.example.mealplan.shared.domain.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Turns every failure that reaches the dispatcher into the one error shape this API promises.
 *
 * <p>It extends {@link ResponseEntityExceptionHandler} so that the exceptions Spring MVC raises
 * before a controller is ever entered come through here too.
 *
 * <p>There is deliberately no handler for {@code AccessDeniedException}: in this application it can
 * only originate inside the security filter chain, which resolves it with its own handler and never
 * reaches a controller advice. If method level authorisation were ever introduced, it would need a
 * handler here, and without one it would fall through to the generic case and answer 500.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ProblemDetail> handleDomain(DomainException ex, HttpServletRequest request) {
        return respond(ProblemDetailFactory.of(ex.code(), ex.getMessage(), ex.details(), request.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                            HttpServletRequest request) {
        return respond(ProblemDetailFactory.of(ErrorCode.MALFORMED_REQUEST, request.getRequestURI()));
    }

    /**
     * Both optimistic locking races of this application land here: cooking the same plan entry
     * twice, and two different entries competing for the same pantry row. Nothing is retried, on
     * purpose: a silent retry would return a success computed over numbers the client never saw.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleOptimisticLocking(OptimisticLockingFailureException ex,
                                                                 HttpServletRequest request) {
        return respond(ProblemDetailFactory.of(ErrorCode.CONCURRENT_MODIFICATION, request.getRequestURI()));
    }

    /** The only case that is logged, and the only one whose body says nothing about the cause. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception while serving {}", request.getRequestURI(), ex);
        return respond(ProblemDetailFactory.of(ErrorCode.INTERNAL_ERROR, request.getRequestURI()));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        List<Map<String, Object>> errors = new ArrayList<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.add(Map.of("field", error.getField(), "message", String.valueOf(error.getDefaultMessage())));
        }
        for (ObjectError error : ex.getBindingResult().getGlobalErrors()) {
            errors.add(Map.of("field", error.getObjectName(), "message", String.valueOf(error.getDefaultMessage())));
        }
        return asObject(ProblemDetailFactory.of(ErrorCode.VALIDATION_FAILED,
                "The request body is not valid.", Map.of("errors", errors), uriOf(request)));
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
                                                                            HttpHeaders headers,
                                                                            HttpStatusCode status,
                                                                            WebRequest request) {
        List<Map<String, Object>> errors = new ArrayList<>();
        for (ParameterValidationResult result : ex.getParameterValidationResults()) {
            String field = result.getMethodParameter().getParameterName();
            for (MessageSourceResolvable error : result.getResolvableErrors()) {
                errors.add(Map.of(
                        "field", field == null ? "request" : field,
                        "message", String.valueOf(error.getDefaultMessage())));
            }
        }
        for (MessageSourceResolvable error : ex.getCrossParameterValidationResults()) {
            errors.add(Map.of("field", "request", "message", String.valueOf(error.getDefaultMessage())));
        }
        return asObject(ProblemDetailFactory.of(ErrorCode.VALIDATION_FAILED,
                "The request body is not valid.", Map.of("errors", errors), uriOf(request)));
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        return asObject(ProblemDetailFactory.of(ErrorCode.MALFORMED_REQUEST, uriOf(request)));
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(MissingServletRequestParameterException ex,
                                                                          HttpHeaders headers,
                                                                          HttpStatusCode status,
                                                                          WebRequest request) {
        return asObject(ProblemDetailFactory.of(ErrorCode.VALIDATION_FAILED,
                "The request body is not valid.",
                Map.of("errors", List.of(Map.of("field", ex.getParameterName(), "message", "must be present"))),
                uriOf(request)));
    }

    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(NoResourceFoundException ex,
                                                                    HttpHeaders headers,
                                                                    HttpStatusCode status,
                                                                    WebRequest request) {
        return asObject(ProblemDetailFactory.of(ErrorCode.ROUTE_NOT_FOUND, uriOf(request)));
    }

    @Override
    protected ResponseEntity<Object> handleNoHandlerFoundException(NoHandlerFoundException ex,
                                                                   HttpHeaders headers,
                                                                   HttpStatusCode status,
                                                                   WebRequest request) {
        return asObject(ProblemDetailFactory.of(ErrorCode.ROUTE_NOT_FOUND, uriOf(request)));
    }

    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                                          HttpHeaders headers,
                                                                          HttpStatusCode status,
                                                                          WebRequest request) {
        return asObject(ProblemDetailFactory.of(ErrorCode.METHOD_NOT_ALLOWED, uriOf(request)));
    }

    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex,
                                                                     HttpHeaders headers,
                                                                     HttpStatusCode status,
                                                                     WebRequest request) {
        return asObject(ProblemDetailFactory.of(ErrorCode.UNSUPPORTED_MEDIA_TYPE, uriOf(request)));
    }

    private static String uriOf(WebRequest request) {
        return request instanceof ServletWebRequest servletRequest
                ? servletRequest.getRequest().getRequestURI()
                : null;
    }

    private static ResponseEntity<ProblemDetail> respond(ProblemDetail problem) {
        return ResponseEntity.status(problem.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    private static ResponseEntity<Object> asObject(ProblemDetail problem) {
        return ResponseEntity.status(problem.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
