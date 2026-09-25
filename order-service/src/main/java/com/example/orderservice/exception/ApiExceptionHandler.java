package com.example.orderservice.exception;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.Map;
@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(OrderNotFoundException.class) ResponseEntity<Map<String,String>> notFound(OrderNotFoundException e){return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message",e.getMessage()));}
    @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<Map<String,String>> invalid(MethodArgumentNotValidException e){String message=e.getBindingResult().getFieldErrors().stream().findFirst().map(error->error.getDefaultMessage()).orElse("Order details are invalid");return ResponseEntity.badRequest().body(Map.of("message",message));}
}
