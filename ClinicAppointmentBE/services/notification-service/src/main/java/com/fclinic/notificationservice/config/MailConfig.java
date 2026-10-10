package com.fclinic.notificationservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;

import java.time.Clock;

@Configuration
public class MailConfig {

    @Bean
    TemplateEngine notificationHtmlTemplateEngine() {
        return engine(TemplateMode.HTML);
    }

    @Bean
    TemplateEngine notificationTextTemplateEngine() {
        return engine(TemplateMode.TEXT);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    private static TemplateEngine engine(TemplateMode mode) {
        StringTemplateResolver resolver = new StringTemplateResolver();
        resolver.setTemplateMode(mode);
        resolver.setCacheable(false);
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }
}
