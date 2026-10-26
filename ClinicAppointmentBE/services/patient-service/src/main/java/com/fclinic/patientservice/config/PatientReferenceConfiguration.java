package com.fclinic.patientservice.config;

import com.fclinic.patientservice.infrastructure.adapter.reference.*;
import com.fclinic.patientservice.infrastructure.adapter.identity.IdentitySettings;
import com.fclinic.patientservice.infrastructure.adapter.scope.ScopeSettings;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;

@Configuration(proxyBeanMethods=false)
@EnableConfigurationProperties(ReferenceSettings.class)
public class PatientReferenceConfiguration {
    @Bean(destroyMethod="close")
    @ConditionalOnProperty(name="patient.reference.doctor.enabled",havingValue="true")
    RemoteDoctorClientAdapter remoteDoctorClient(ReferenceSettings references,IdentitySettings identity,ScopeSettings scope) {
        isolate(references.doctor(),references,identity,scope);
        return new RemoteDoctorClientAdapter(references.doctor());
    }
    @Bean(destroyMethod="close")
    @ConditionalOnProperty(name="patient.reference.appointment.enabled",havingValue="true")
    RemoteAppointmentClientAdapter remoteAppointmentClient(ReferenceSettings references,IdentitySettings identity,ScopeSettings scope) {
        isolate(references.appointment(),references,identity,scope);
        return new RemoteAppointmentClientAdapter(references.appointment());
    }
    private static void isolate(ReferenceSettings.Endpoint candidate,ReferenceSettings references,IdentitySettings identity,ScopeSettings scope) {
        ReferenceSettings.require(candidate!=null,"enabled endpoint settings required"); candidate.validate();
        distinct(candidate,identity.clientId(),identity.clientSecret());
        if(scope.administrative()!=null) distinct(candidate,scope.administrative().clientId(),scope.administrative().clientSecret());
        if(scope.clinical()!=null) distinct(candidate,scope.clinical().clientId(),scope.clinical().clientSecret());
        for(var other:new ReferenceSettings.Endpoint[]{references.doctor(),references.appointment()})
            if(other!=null && other!=candidate) distinct(candidate,other.clientId(),other.clientSecret());
    }
    private static void distinct(ReferenceSettings.Endpoint candidate,String id,String secret) {
        ReferenceSettings.require(!candidate.clientId().equals(id) && !candidate.clientSecret().equals(secret),
                "reference credentials must be distinct from identity,scope and other references");
    }
}

