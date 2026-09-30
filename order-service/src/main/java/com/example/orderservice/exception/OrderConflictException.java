package com.example.orderservice.exception;

public class OrderConflictException extends RuntimeException {
    public OrderConflictException(String message) { super(message); }
}
