package com.example.orderservice.model;
/**
 * Order lifecycle, in the order the workflow drives it: paid, phone dispatched and delivered, eSIM activated, then
 * COMPLETED once monthly billing is active. CANCELLED and FAILED end an order early.
 */
public enum OrderStatus { PENDING_PAYMENT, PAID, DISPATCHED, DELIVERED, ACTIVATED, COMPLETED, CANCELLED, FAILED }
