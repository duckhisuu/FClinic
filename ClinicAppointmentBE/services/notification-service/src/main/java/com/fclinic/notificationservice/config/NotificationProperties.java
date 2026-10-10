package com.fclinic.notificationservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.notification")
public class NotificationProperties {

    private String timezone = "Asia/Ho_Chi_Minh";
    private Delivery delivery = new Delivery();
    private Mail mail = new Mail();

    @Getter
    @Setter
    public static class Delivery {
        private long schedulerDelayMs = 5000;
        private int batchSize = 100;
        private int maxAttempts = 3;
        private Duration processingTimeout = Duration.ofMinutes(10);
        private List<Duration> retryDelays = List.of(Duration.ofMinutes(1), Duration.ofMinutes(5));
    }

    @Getter
    @Setter
    public static class Mail {
        private String fromAddress = "no-reply@clinic.local";
        private String fromName = "FClinic";
    }
}
