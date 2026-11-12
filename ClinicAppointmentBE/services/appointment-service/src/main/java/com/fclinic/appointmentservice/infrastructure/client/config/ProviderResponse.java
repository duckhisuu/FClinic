package com.fclinic.appointmentservice.infrastructure.client.config;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
public record ProviderResponse<T>(Integer status, String message, T data) {
  public T validated() {
    FeignHeaderGuards.require(Integer.valueOf(200).equals(status) && message != null && data != null);
    return data;
  }
}

