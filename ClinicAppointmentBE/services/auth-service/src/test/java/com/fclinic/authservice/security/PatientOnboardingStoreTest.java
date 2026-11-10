package com.fclinic.authservice.security;

import com.fclinic.authservice.infrastructure.adapter.PatientOnboardingStore;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

class PatientOnboardingStoreTest {
    static EmbeddedPostgres pg;
    JdbcTemplate jdbc; TransactionTemplate tx; PatientOnboardingStore store;
    UUID user=UUID.randomUUID(), subject=UUID.randomUUID();
    @BeforeAll static void start() throws Exception {
        Path root=Files.createTempDirectory("auth-onboarding-test-");
        pg=EmbeddedPostgres.builder().setPort(0).setDataDirectory(root.resolve("data"))
            .setOverrideWorkingDirectory(root.resolve("bin").toFile()).setCleanDataDirectory(true).start();
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V1__create_auth_tables.sql"),
            new ClassPathResource("db/migration/V3__patient_onboarding.sql")).execute(pg.getPostgresDatabase());
    }
    @AfterAll static void close() throws Exception { if(pg!=null)pg.close(); }
    @BeforeEach void setup() {
        var ds=pg.getPostgresDatabase(); jdbc=new JdbcTemplate(ds); tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
        store=new PatientOnboardingStore(jdbc);
        jdbc.update("DELETE FROM patient_onboardings"); jdbc.update("DELETE FROM users"); jdbc.update("DELETE FROM roles");
        UUID role=UUID.randomUUID();
        jdbc.update("INSERT INTO roles(id,role_name,description) VALUES(?,'ROLE_PATIENT','test')",role);
        jdbc.update("INSERT INTO users(id,keycloak_id,role_id,username,email,full_name) VALUES(?,?,?,'synthetic','synthetic@example.test','Synthetic')",user,subject,role);
    }
    @Test void concurrentFirstRequestsReserveOneProfileAndReplayAfterRestart() throws Exception {
        var gate=new CountDownLatch(1);
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            Callable<UUID> work=()-> { gate.await(); return tx.execute(s->store.reserve(user,subject,"command","body","a".repeat(64))).patientId(); };
            var first=executor.submit(work); var second=executor.submit(work); gate.countDown();
            assertThat(first.get(5,TimeUnit.SECONDS)).isEqualTo(second.get(5,TimeUnit.SECONDS));
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM patient_onboardings",Integer.class)).isEqualTo(1);
        var replay=new PatientOnboardingStore(jdbc).find(user).orElseThrow();
        tx.executeWithoutResult(s->store.bind(replay)); tx.executeWithoutResult(s->store.bind(replay));
        assertThat(jdbc.queryForObject("SELECT patient_id FROM users WHERE id=?",UUID.class,user)).isEqualTo(replay.patientId());
    }
    @Test void changingSubjectPayloadOrAdministrativeBindingCannotHijackReservation() {
        var r=tx.execute(s->store.reserve(user,subject,"command","body","a".repeat(64)));
        assertThatThrownBy(()->tx.execute(s->store.reserve(user,subject,"other","body","b".repeat(64)))).hasMessageContaining("khởi tạo");
        assertThatThrownBy(()->tx.execute(s->store.reserve(user,UUID.randomUUID(),"command","body","a".repeat(64))))
            .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        jdbc.update("UPDATE users SET patient_id=? WHERE id=?",UUID.randomUUID(),user);
        assertThatThrownBy(()->tx.executeWithoutResult(s->store.bind(r))).hasMessageContaining("khởi tạo");
    }
    @Test void uniqueBusinessMappingAlsoProtectsAdministrativeWrites() {
        UUID patient=UUID.randomUUID(); jdbc.update("UPDATE users SET patient_id=? WHERE id=?",patient,user);
        UUID role=jdbc.queryForObject("SELECT role_id FROM users WHERE id=?",UUID.class,user);
        assertThatThrownBy(()->jdbc.update("INSERT INTO users(id,keycloak_id,role_id,username,email,full_name,patient_id) VALUES(?,?,?,'other','other@example.test','Other',?)",
            UUID.randomUUID(),UUID.randomUUID(),role,patient)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
