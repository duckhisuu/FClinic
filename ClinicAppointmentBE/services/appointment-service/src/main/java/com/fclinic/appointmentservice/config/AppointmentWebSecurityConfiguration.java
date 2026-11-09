package com.fclinic.appointmentservice.config;

import com.fclinic.appointmentservice.api.AppointmentAuthentication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
@EnableConfigurationProperties(AppointmentSecuritySettings.class)
public class AppointmentWebSecurityConfiguration implements WebMvcConfigurer {
  private final AppointmentAuthentication authentication;
  public AppointmentWebSecurityConfiguration(AppointmentAuthentication authentication) { this.authentication = authentication; }
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(authentication).addPathPatterns("/api/v2/appointments", "/api/v2/appointments/**");
  }
}
