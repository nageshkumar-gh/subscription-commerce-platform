package com.example.billingservice.exception;
import java.time.Instant;import java.util.*;import org.springframework.http.*;import org.springframework.web.bind.MethodArgumentNotValidException;import org.springframework.web.bind.annotation.*;
@RestControllerAdvice public class ApiExceptionHandler{
 @ExceptionHandler(ResourceNotFoundException.class)ResponseEntity<Map<String,Object>>notFound(ResourceNotFoundException e){return body(HttpStatus.NOT_FOUND,e.getMessage());}
 @ExceptionHandler(ConflictException.class)ResponseEntity<Map<String,Object>>conflict(ConflictException e){return body(HttpStatus.CONFLICT,e.getMessage());}
 @ExceptionHandler(IllegalArgumentException.class)ResponseEntity<Map<String,Object>>badRequest(IllegalArgumentException e){return body(HttpStatus.BAD_REQUEST,e.getMessage());}
 @ExceptionHandler(MethodArgumentNotValidException.class)ResponseEntity<Map<String,Object>>invalid(MethodArgumentNotValidException e){String m=e.getBindingResult().getFieldErrors().stream().findFirst().map(x->x.getField()+": "+x.getDefaultMessage()).orElse("Request is invalid");return body(HttpStatus.BAD_REQUEST,m);}
 private ResponseEntity<Map<String,Object>>body(HttpStatus s,String m){Map<String,Object>b=new LinkedHashMap<>();b.put("timestamp",Instant.now());b.put("status",s.value());b.put("error",s.getReasonPhrase());b.put("message",m);return ResponseEntity.status(s).body(b);}
}
