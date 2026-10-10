package com.fclinic.notificationservice.domain.valueobject;

public record DeliveryResult(boolean success, String providerMessageId, String errorCode, String errorMessage) {

    public static DeliveryResult success(String providerMessageId) {
        return new DeliveryResult(true, providerMessageId, null, null);
    }

    public static DeliveryResult failure(String errorCode, String errorMessage) {
        return new DeliveryResult(false, null, errorCode, errorMessage);
    }
}
