package com.example.orderservice.exception;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(OrderNotFoundException.class) ResponseEntity<ApiError> notFound(OrderNotFoundException e,HttpServletRequest r){return response(HttpStatus.NOT_FOUND,"ORDER_NOT_FOUND",e.getMessage(),r,Map.of());}
    @ExceptionHandler(OrderConflictException.class) ResponseEntity<ApiError> conflict(OrderConflictException e,HttpServletRequest r){return response(HttpStatus.CONFLICT,"ORDER_STATE_CONFLICT",e.getMessage(),r,Map.of());}
    @ExceptionHandler({IllegalArgumentException.class,MethodArgumentTypeMismatchException.class}) ResponseEntity<ApiError> badRequest(Exception e,HttpServletRequest r){return response(HttpStatus.BAD_REQUEST,"INVALID_REQUEST",e.getMessage(),r,Map.of());}
    @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<ApiError> invalid(MethodArgumentNotValidException e,HttpServletRequest r){Map<String,String> fields=new LinkedHashMap<>();e.getBindingResult().getFieldErrors().forEach(error->fields.putIfAbsent(error.getField(),error.getDefaultMessage()));return response(HttpStatus.BAD_REQUEST,"VALIDATION_FAILED","Order details are invalid",r,fields);}
    private ResponseEntity<ApiError> response(HttpStatus status,String code,String message,HttpServletRequest request,Map<String,String> fields){return ResponseEntity.status(status).body(new ApiError(Instant.now(),status.value(),status.getReasonPhrase(),code,message,request.getRequestURI(),fields));}
}
