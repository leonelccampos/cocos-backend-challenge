package com.cocobank.challenge.application.dto;

public record IdempotentSubmission(
    String fingerprint, int responseStatus, OrderSubmissionResult result) {}
