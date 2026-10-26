package com.fclinic.patientservice.infrastructure.adapter.scope;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;

@ConfigurationProperties(value="patient.scope",ignoreUnknownFields=false)
public record ScopeSettings(@DefaultValue("false") boolean enabled, Authority administrative, Authority clinical) {
    public record Authority(URI endpoint, String clientId, String clientSecret,
            @DefaultValue("1s") Duration connectTimeout, @DefaultValue("3s") Duration exchangeTimeout,
            @DefaultValue("262144") int maxResponseBytes, @DefaultValue("32") int maxConcurrentRequests,
            @DefaultValue("1000") int maxSearchPatients, Path trustStore, String trustStorePassword) {
        @Override public String toString() { return "ScopeAuthority[redacted]"; }
        public void validate() {
            require(endpoint!=null && "https".equalsIgnoreCase(endpoint.getScheme()) && endpoint.getHost()!=null
                    && endpoint.getUserInfo()==null && endpoint.getQuery()==null && endpoint.getFragment()==null,"HTTPS endpoint required");
            require(clientId!=null && clientId.matches("[A-Za-z0-9._-]{1,128}"),"client ID required");
            require(clientSecret!=null && !clientSecret.isBlank() && clientSecret.length()<=4096
                    && clientSecret.chars().noneMatch(Character::isISOControl),"client secret required");
            require(duration(connectTimeout) && duration(exchangeTimeout) && connectTimeout.compareTo(exchangeTimeout)<=0,"invalid timeout");
            require(maxResponseBytes>=1024 && maxResponseBytes<=1048576,"invalid response limit");
            require(maxConcurrentRequests>=1 && maxConcurrentRequests<=256,"invalid concurrency");
            require(maxSearchPatients>=1 && maxSearchPatients<=10000,"invalid search limit");
            require((trustStore==null)==(trustStorePassword==null),"trust store/password must be paired");
        }
        private static boolean duration(Duration d) {
            return d!=null && d.compareTo(Duration.ofMillis(100))>=0 && d.compareTo(Duration.ofSeconds(30))<=0;
        }
    }
    @Override public String toString() { return "ScopeSettings[redacted]"; }
    public void validate() {
        require(administrative!=null && clinical!=null,"both authorities required");
        administrative.validate(); clinical.validate();
        require(!administrative.clientId().equals(clinical.clientId())
                && !administrative.clientSecret().equals(clinical.clientSecret()),"separate authority credentials required");
    }
    public static void require(boolean condition,String message) {
        if(!condition) throw new IllegalStateException("Invalid patient scope configuration: "+message);
    }
}

