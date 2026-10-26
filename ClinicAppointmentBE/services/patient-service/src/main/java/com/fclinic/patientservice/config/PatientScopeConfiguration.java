package com.fclinic.patientservice.config;

import com.fclinic.patientservice.infrastructure.adapter.scope.*;
import com.fclinic.patientservice.infrastructure.adapter.identity.IdentitySettings;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import java.time.Clock;

@Configuration(proxyBeanMethods=false)
@EnableConfigurationProperties(ScopeSettings.class)
public class PatientScopeConfiguration {
    @Bean(destroyMethod="close")
    @ConditionalOnProperty(name="patient.scope.enabled",havingValue="true")
    RemoteWorkScopeAdapter remoteWorkScope(ScopeSettings settings,IdentitySettings identity,Clock clock) {
        settings.validate();
        for(var authority:new ScopeSettings.Authority[]{settings.administrative(),settings.clinical()}) {
            ScopeSettings.require(!authority.clientId().equals(identity.clientId())
                    && !authority.clientSecret().equals(identity.clientSecret()),"scope credentials must differ from introspection");
        }
        return new RemoteWorkScopeAdapter(settings,clock);
    }
}

