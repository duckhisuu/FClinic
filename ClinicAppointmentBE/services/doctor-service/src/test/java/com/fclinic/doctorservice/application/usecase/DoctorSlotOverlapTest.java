package com.fclinic.doctorservice.application.usecase;

import com.fclinic.doctorservice.application.command.*;
import com.fclinic.doctorservice.application.exception.SlotOverlapException;
import com.fclinic.doctorservice.application.port.out.TimeSlotRepositoryPort;
import com.fclinic.doctorservice.api.exception.GlobalExceptionHandler;
import com.fclinic.doctorservice.domain.model.TimeSlot;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE, properties={
    "spring.jpa.hibernate.ddl-auto=create-drop", "eureka.client.enabled=false",
    "spring.cloud.discovery.enabled=false"})
class DoctorSlotOverlapTest {
    static final EmbeddedPostgres pg=startDatabase();
    @Autowired DoctorApplicationService service;
    @Autowired TimeSlotRepositoryPort slots;
    UUID doctor;
    final LocalDate date=LocalDate.of(2050,1,15);
    static EmbeddedPostgres startDatabase() {
        try {
            var dir=Files.createTempDirectory(Path.of("target").toAbsolutePath(),"doctor-overlap-");
            return EmbeddedPostgres.builder().setPort(0).setDataDirectory(dir.resolve("data"))
                .setOverrideWorkingDirectory(dir.resolve("bin").toFile()).start();
        } catch(Exception e) {throw new IllegalStateException(e);}
    }
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",()->pg.getJdbcUrl("postgres","postgres"));
        r.add("spring.datasource.username",()->"postgres");
        r.add("spring.datasource.password",()->"");
    }
    @AfterAll static void close() throws Exception {pg.close();}
    UUID doctor() {return service.createDoctor(new CreateDoctorCommand("Synthetic overlap doctor","QA","QA",null,1,1.0,null,null,null)).id();}
    @BeforeEach void setup(){doctor=doctor();}
    CreateTimeSlotCommand command(UUID d,LocalDate day,int hour){return new CreateTimeSlotCommand(d,day,LocalTime.of(hour,0),LocalTime.of(hour+1,0));}
    @Test void duplicateRejectedAndConflictHasStableCode() {
        service.createTimeSlot(command(doctor,date,8));
        assertThatThrownBy(()->service.createTimeSlot(command(doctor,date,8))).isInstanceOf(SlotOverlapException.class);
        assertThat(service.listSlots(doctor,date)).hasSize(1);
        var response=new GlobalExceptionHandler().handleSlotOverlap(new SlotOverlapException());
        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().code()).isEqualTo("DOCTOR_SLOT_OVERLAP");
    }
    @Test void adjacentDatesAndOtherDoctorsDoNotConflict() {
        service.createTimeSlot(command(doctor,date,8));
        service.createTimeSlot(command(doctor,date,9));
        service.createTimeSlot(command(doctor,date.plusDays(1),8));
        service.createTimeSlot(command(doctor(),date,8));
        assertThat(service.listSlots(doctor,date)).hasSize(2);
    }
    @Test void legacyPartialOverlapAndContainmentAreRejected() {
        // Existing older schedules need not have the current one-hour shape.
        slots.save(TimeSlot.createNew(doctor,date,LocalTime.of(8,30),LocalTime.of(10,30)));
        for(int hour:new int[]{8,9,10})
            assertThatThrownBy(()->service.createTimeSlot(command(doctor,date,hour))).isInstanceOf(SlotOverlapException.class);
        assertThat(service.listSlots(doctor,date)).hasSize(1);
    }
    @Test void concurrentFirstSlotHasOneWinnerAcrossTransactions() throws Exception {
        var ready=new CountDownLatch(2);var go=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<String> create=()->{ready.countDown();if(!go.await(10,TimeUnit.SECONDS))throw new IllegalStateException("start timeout");
                try{service.createTimeSlot(command(doctor,date,8));return "created";}
                catch(SlotOverlapException e){return "conflict";}};
            var a=pool.submit(create);var b=pool.submit(create);
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();go.countDown();
            assertThat(List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder("created","conflict");
        } finally {go.countDown();}
        assertThat(service.listSlots(doctor,date)).hasSize(1);
    }
}
