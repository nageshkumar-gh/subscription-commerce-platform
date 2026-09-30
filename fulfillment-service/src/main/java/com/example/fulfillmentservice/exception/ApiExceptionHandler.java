package com.example.fulfillmentservice.exception;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ResourceNotFoundException.class) ResponseEntity<Map<String,Object>> notFound(ResourceNotFoundException e){return body(HttpStatus.NOT_FOUND,e.getMessage());}
    @ExceptionHandler(ConflictException.class) ResponseEntity<Map<String,Object>> conflict(ConflictException e){return body(HttpStatus.CONFLICT,e.getMessage());}
    @ExceptionHandler(IllegalArgumentException.class) ResponseEntity<Map<String,Object>> badRequest(IllegalArgumentException e){return body(HttpStatus.BAD_REQUEST,e.getMessage());}
    @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<Map<String,Object>> invalid(MethodArgumentNotValidException e){String message=e.getBindingResult().getFieldErrors().stream().findFirst().map(x->x.getField()+": "+x.getDefaultMessage()).orElse("Request is invalid");return body(HttpStatus.BAD_REQUEST,message);}
    private ResponseEntity<Map<String,Object>> body(HttpStatus status,String message){Map<String,Object> result=new LinkedHashMap<>();result.put("timestamp",Instant.now());result.put("status",status.value());result.put("error",status.getReasonPhrase());result.put("message",message);return ResponseEntity.status(status).body(result);}
}
