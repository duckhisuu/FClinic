package com.fclinic.patientservice.config;
import com.fclinic.patientservice.api.security.PatientAuthentication;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class PatientWebConfiguration implements WebMvcConfigurer {
    private final PatientAuthentication authentication;
    public PatientWebConfiguration(PatientAuthentication authentication) { this.authentication=authentication; }
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authentication).addPathPatterns("/api/v2/**");
    }
}
