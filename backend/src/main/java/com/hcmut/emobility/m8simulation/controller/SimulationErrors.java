package com.hcmut.emobility.m8simulation.controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import java.util.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/** Uu tien truoc GlobalExceptionHandler, neu khong loi 400 cua M8 co the thanh 500 tuy thu tu quet bean. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes=SimulationController.class)
public class SimulationErrors {
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String,String>> invalid(IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("code","INVALID_SCENARIO","message",e.getMessage())); }
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String,String>> missing(NoSuchElementException e) { return ResponseEntity.status(404).body(Map.of("code","NOT_FOUND","message","Run or recommendation not found")); }
}
