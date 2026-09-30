package com.example.productservice.exception;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.Map;
import java.time.Instant;
@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ProductNotFoundException.class) ResponseEntity<Map<String,Object>> notFound(ProductNotFoundException e){return error(HttpStatus.NOT_FOUND,e.getMessage());}
    @ExceptionHandler(EsimPlanNotFoundException.class) ResponseEntity<Map<String,Object>> planNotFound(EsimPlanNotFoundException e){return error(HttpStatus.NOT_FOUND,e.getMessage());}
    @ExceptionHandler(IllegalArgumentException.class) ResponseEntity<Map<String,Object>> conflict(IllegalArgumentException e){return error(HttpStatus.CONFLICT,e.getMessage());}
    @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<Map<String,Object>> invalid(MethodArgumentNotValidException e){String message=e.getBindingResult().getFieldErrors().stream().findFirst().map(error->error.getField()+": "+error.getDefaultMessage()).orElse("Product details are invalid");return error(HttpStatus.BAD_REQUEST,message);}
    private ResponseEntity<Map<String,Object>> error(HttpStatus status,String message){return ResponseEntity.status(status).body(Map.of("timestamp",Instant.now().toString(),"status",status.value(),"error",status.getReasonPhrase(),"message",message));}
}
