# 08. CODING STANDARDS & ENGINEERING RULES

## 1. Core Architectural Rules
* **SOLID Principles:** Enforce Single Responsibility across Java classes and Python modules.
* **Layered Architecture:** `Controller` $\rightarrow$ `Service` (Interface + Impl) $\rightarrow$ `Repository` $\rightarrow$ `Entity` / `DTO`.
* **No Business Logic in Controllers:** Controllers must only handle HTTP mapping, request validation, and invoking service interfaces.
* **No Exposed Entities:** Database JPA Entities must never be returned directly in REST API responses. Use Data Transfer Objects (DTOs).

## 2. Global Exception Handling (Spring Boot)
All Spring Boot microservices must implement a `@RestControllerAdvice` class:

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorDetails> handleNotFound(ResourceNotFoundException ex, WebRequest req) {
        ErrorDetails error = new ErrorDetails(LocalDateTime.now(), HttpStatus.NOT_FOUND.value(), ex.getMessage(), req.getDescription(false));
        return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> 
            errors.put(error.getField(), error.getDefaultMessage()));
        return new ResponseEntity<>(errors, HttpStatus.BAD_REQUEST);
    }
}
```

## 3. Python FastAPI Standards
* Use **Pydantic** models for strict request/response schema validation.
* Type hints are mandatory on all functions (`def parse_text(input_str: str) -> ExpenseDTO:`).

## 4. Logging & Validation
* Use `SLF4J` / `logback` in Java (`@Slf4j`). `System.out.println` is strictly prohibited.
* Use `jakarta.validation` annotations (`@NotNull`, `@Positive`, `@NotBlank`) on DTO fields.