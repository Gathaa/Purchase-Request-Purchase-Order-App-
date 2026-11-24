package Ninova;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import jakarta.servlet.ServletException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    public static class ResourceNotFoundException extends RuntimeException {   // -> 404
        public ResourceNotFoundException(String message) { super(message); }
    }

    public static class BusinessRuleException extends RuntimeException {       // -> 400
        public BusinessRuleException(String message) { super(message); }
    }

    public static class ConflictException extends RuntimeException {           // -> 409
        public ConflictException(String message) { super(message); }
    }

    // The JSON shape of every error
    public record ErrorResponse(int status, String error, String message, LocalDateTime timestamp) {}

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message) {
        ErrorResponse body = new ErrorResponse(status.value(), status.getReasonPhrase(), message,
                LocalDateTime.now(ZoneId.of("Asia/Qatar")));
        return ResponseEntity.status(status).body(body);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> notFound(ResourceNotFoundException e) {
        return build(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ErrorResponse> badRequest(BusinessRuleException e) {
        return build(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> conflict(ConflictException e) {
        return build(HttpStatus.CONFLICT, e.getMessage());
    }

    // Two tabs edited the same request: the @Version check failed
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> lockConflict(ObjectOptimisticLockingFailureException e) {
        return build(HttpStatus.CONFLICT, "The request was changed by someone else. Reload it and try again.");
    }

    // @Valid failed on a DTO
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .sorted()
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, msg);
    }

    // Bad JSON, wrong date format, unknown enum value, text where a number is expected
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> unreadable(HttpMessageNotReadableException e) {
        return build(HttpStatus.BAD_REQUEST,
                "Invalid or malformed request body (check JSON, dates, numbers and enum values)");
    }
    // /api/purchase-requests/abc or ?status=Foo
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> typeMismatch(MethodArgumentTypeMismatchException e) {
        return build(HttpStatus.BAD_REQUEST, "Invalid value for parameter '" + e.getName() + "'");
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> integrity(DataIntegrityViolationException e) {
        return build(HttpStatus.CONFLICT, "The operation conflicts with existing data");
    }

    // Wrong URL (404), wrong HTTP method (405), wrong content type (415)
    @ExceptionHandler(ServletException.class)
    public ResponseEntity<ErrorResponse> servlet(ServletException e) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        if (e instanceof org.springframework.web.ErrorResponse er) {
            status = HttpStatus.valueOf(er.getStatusCode().value());
        }
        String msg = switch (status) {
            case NOT_FOUND -> "Endpoint not found";
            case METHOD_NOT_ALLOWED -> "HTTP method not allowed for this endpoint";
            case UNSUPPORTED_MEDIA_TYPE -> "Unsupported content type, use application/json";
            default -> "Bad request";
        };
        return build(status, msg);
    }

    // Anything unexpected: logged on the server, generic message to the client
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> other(Exception e) {
        e.printStackTrace();
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error");
    }
}