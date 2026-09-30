package com.example.paymentservice.exception;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(PaymentNotFoundException.class) ResponseEntity<ApiError> notFound(PaymentNotFoundException e,HttpServletRequest r){return response(HttpStatus.NOT_FOUND,"PAYMENT_NOT_FOUND",e.getMessage(),r,Map.of());}
    @ExceptionHandler(PaymentConflictException.class) ResponseEntity<ApiError> conflict(PaymentConflictException e,HttpServletRequest r){return response(HttpStatus.CONFLICT,"PAYMENT_CONFLICT",e.getMessage(),r,Map.of());}
    @ExceptionHandler(IllegalArgumentException.class) ResponseEntity<ApiError> badRequest(IllegalArgumentException e,HttpServletRequest r){return response(HttpStatus.BAD_REQUEST,"INVALID_REQUEST",e.getMessage(),r,Map.of());}
    @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<ApiError> invalid(MethodArgumentNotValidException e,HttpServletRequest r){Map<String,String> fields=new LinkedHashMap<>();e.getBindingResult().getFieldErrors().forEach(error->fields.putIfAbsent(error.getField(),error.getDefaultMessage()));return response(HttpStatus.BAD_REQUEST,"VALIDATION_FAILED","Payment details are invalid",r,fields);}
    private ResponseEntity<ApiError> response(HttpStatus status,String code,String message,HttpServletRequest request,Map<String,String> fields){return ResponseEntity.status(status).body(new ApiError(Instant.now(),status.value(),status.getReasonPhrase(),code,message,request.getRequestURI(),fields));}
}
