package com.fclinic.authservice.security;

import com.fclinic.authservice.application.port.out.UserRepositoryPort;
import com.fclinic.authservice.domain.aggregate.User;
import com.fclinic.authservice.domain.valueobject.*;
import com.fclinic.authservice.infrastructure.entity.JpaRoleEntity;
import com.fclinic.authservice.infrastructure.persistence.SpringDataJpaRoleRepository;
import java.net.*;
import java.net.http.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import static org.assertj.core.api.Assertions.*;

/** Real SecurityFilterChain, controller and JPA mapping queries, isolated in-memory storage. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
    "spring.datasource.url=jdbc:h2:mem:recipient-contract;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false",
    "eureka.client.enabled=false", "spring.cloud.discovery.enabled=false", "server.address=127.0.0.1",
    "app.recipients.client-id=recipient", "app.recipients.client-secret=recipient-secret"})
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class AppointmentRecipientTcpTest {
    @LocalServerPort int port;
    @Autowired UserRepositoryPort users;
    @Autowired SpringDataJpaRoleRepository roles;
    static final HttpClient HTTP=HttpClient.newHttpClient();
    @AfterAll static void close(){HTTP.close();}
    Role role(RoleName name) {
        var entity=roles.findByRoleName(name.name()).orElseGet(()->{var r=new JpaRoleEntity();r.setId(UUID.randomUUID());r.setRoleName(name.name());r.setDescription("Synthetic");return roles.save(r);});
        return new Role(entity.getId(),name,"Synthetic",Instant.now(),Instant.now());
    }
    User account(RoleName role,UUID patient,UUID doctor) {
        String unique=UUID.randomUUID().toString();
        return users.save(User.create(UUID.randomUUID(),UUID.randomUUID(),unique,unique+"@example.test",null,"Synthetic",role(role),patient,doctor,null));
    }
    HttpResponse<String> resolve(UUID patient,UUID doctor,String auth) throws Exception {
        var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/auth/internal/appointment-recipient-accounts"))
            .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString("{\"patientId\":\""+patient+"\",\"doctorId\":\""+doctor+"\"}"));
        if(auth!=null)b.header("Authorization",auth);
        return HTTP.send(b.build(),HttpResponse.BodyHandlers.ofString());
    }
    @Test void dedicatedBasicWorksThroughActualSecurityAndJpaRejectsAmbiguousActiveMappings() throws Exception {
        UUID patient=UUID.randomUUID(),doctor=UUID.randomUUID();
        User d=account(RoleName.ROLE_DOCTOR,null,doctor);
        String basic="Basic "+Base64.getEncoder().encodeToString("recipient:recipient-secret".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(resolve(patient,doctor,null).statusCode()).isEqualTo(401);
        assertThat(resolve(patient,doctor,"Bearer invalid").statusCode()).isEqualTo(401);
        var unlinked=resolve(patient,doctor,basic);assertThat(unlinked.statusCode()).isEqualTo(200);
        assertThat(unlinked.body()).contains(d.getId().toString(),"\"patientUserId\":null");
        User p=account(RoleName.ROLE_PATIENT,patient,null);
        assertThat(resolve(patient,doctor,basic).body()).contains(p.getId().toString());
        User duplicate=account(RoleName.ROLE_PATIENT,patient,null);
        assertThat(resolve(patient,doctor,basic).statusCode()).isEqualTo(503);
        duplicate.setActiveStatus(false);users.save(duplicate);
        assertThat(resolve(patient,doctor,basic).statusCode()).isEqualTo(200);
        // A wrong-role account mapped to the same business ID is not a recipient.
        account(RoleName.ROLE_STAFF,patient,doctor);
        assertThat(users.findPatientAccounts(patient)).extracting(User::getId).containsExactly(p.getId());
        assertThat(users.findDoctorAccounts(doctor)).extracting(User::getId).containsExactly(d.getId());
    }
}
