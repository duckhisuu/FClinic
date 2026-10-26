package com.fclinic.patientservice.infrastructure.adapter.reference;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;

@ConfigurationProperties(value="patient.reference",ignoreUnknownFields=false)
public record ReferenceSettings(Endpoint doctor, Endpoint appointment) {
    public record Endpoint(@DefaultValue("false") boolean enabled, URI collectionUri, String clientId, String clientSecret,
            @DefaultValue("1s") Duration connectTimeout, @DefaultValue("3s") Duration exchangeTimeout,
            @DefaultValue("65536") int maxResponseBytes, @DefaultValue("16") int maxConcurrentRequests,
            Path trustStore, String trustStorePassword) {
        @Override public String toString() { return "ReferenceEndpoint[redacted]"; }
        public void validate() {
            require(collectionUri!=null && "https".equalsIgnoreCase(collectionUri.getScheme()) && collectionUri.getHost()!=null
                    && collectionUri.getUserInfo()==null && collectionUri.getQuery()==null && collectionUri.getFragment()==null
                    && collectionUri.getRawPath()!=null && !collectionUri.getRawPath().isEmpty()
                    && !collectionUri.getRawPath().endsWith("/") && collectionUri.normalize().equals(collectionUri),"HTTPS collection URI required");
            require(clientId!=null && clientId.matches("[A-Za-z0-9._-]{1,128}"),"client ID required");
            require(clientSecret!=null && !clientSecret.isBlank() && clientSecret.length()<=4096
                    && clientSecret.chars().noneMatch(Character::isISOControl),"client secret required");
            require(duration(connectTimeout) && duration(exchangeTimeout) && connectTimeout.compareTo(exchangeTimeout)<=0,"invalid timeout");
            require(maxResponseBytes>=1024 && maxResponseBytes<=262144,"invalid response limit");
            require(maxConcurrentRequests>=1 && maxConcurrentRequests<=256,"invalid concurrency");
            require((trustStore==null)==(trustStorePassword==null),"trust store/password must be paired");
        }
        private static boolean duration(Duration d) {
            return d!=null && d.compareTo(Duration.ofMillis(100))>=0 && d.compareTo(Duration.ofSeconds(30))<=0;
        }
    }
    @Override public String toString() { return "ReferenceSettings[redacted]"; }
    public static void require(boolean ok,String reason) {
        if(!ok) throw new IllegalStateException("Invalid patient reference configuration: "+reason);
    }
}

