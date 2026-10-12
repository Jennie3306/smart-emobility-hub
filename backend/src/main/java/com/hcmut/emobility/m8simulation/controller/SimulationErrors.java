package com.hcmut.emobility.m8simulation.controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import java.util.*;
@RestControllerAdvice(assignableTypes=SimulationController.class)
public class SimulationErrors {
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String,String>> invalid(IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("code","INVALID_SCENARIO","message",e.getMessage())); }
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String,String>> missing(NoSuchElementException e) { return ResponseEntity.status(404).body(Map.of("code","NOT_FOUND","message","Run or recommendation not found")); }
}
