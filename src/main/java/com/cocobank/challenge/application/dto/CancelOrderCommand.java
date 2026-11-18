package com.cocobank.challenge.application.dto;

public record CancelOrderCommand(int userId, int orderId, String status) {}
