package com.fclinic.patientservice;

import com.fclinic.patientservice.application.port.out.*;
import com.fclinic.patientservice.application.security.TrustedCaller;
import com.fclinic.patientservice.application.usecase.*;
import com.fclinic.patientservice.domain.aggregate.*;
import com.fclinic.patientservice.domain.model.*;
import com.fclinic.patientservice.infrastructure.adapter.JpaRevisionAdapter;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import jakarta.persistence.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.boot.test.context.*;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.*;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"eureka.client.enabled=false","spring.cloud.discovery.enabled=false"})
@AutoConfigureMockMvc
@Import(PatientServiceIT.TestClock.class)
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class PatientServiceIT {
    static final ObjectMapper JSON=JsonMapper.builder().findAndAddModules().build();
    static final JsonNode REGISTRY=read("fixtures/registry.json"), OAS=read("contracts/patient.openapi.json");
    static final Clock CLOCK=Clock.fixed(Instant.parse(REGISTRY.path("clock").asText()),ZoneOffset.UTC);
    static final EmbeddedPostgres PG=startPostgres();
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",() -> PG.getJdbcUrl("postgres","postgres"));
        r.add("spring.datasource.username",() -> "postgres"); r.add("spring.datasource.password",() -> "postgres");
    }
    @TestConfiguration static class TestClock { @Bean @Primary Clock testClock() { return CLOCK; } }
    @Autowired MockMvc mvc;
    @Autowired PatientApplicationService service;
    @Autowired PatientWriter writer;
    @Autowired PatientRepositoryPort repository;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @MockitoBean IdentityVerifierPort identities;
    @MockitoBean(answers=org.mockito.Answers.CALLS_REAL_METHODS) WorkScopePort scopes;
    @MockitoBean DoctorClientPort doctors;
    @MockitoBean AppointmentClientPort appointments;
    @MockitoSpyBean JpaRevisionAdapter revisions;
    JpaRevisionAdapter revisionSpy;
    final Set<UUID> allowed=ConcurrentHashMap.newKeySet();
    final TrustedCaller staff=caller("user:demo-staff","ROLE_STAFF",null,null);
    final TrustedCaller doctor=caller("user:demo-doctor","ROLE_DOCTOR",null,12L);
    final TrustedCaller otherDoctor=caller("user:other-doctor","ROLE_DOCTOR",null,13L);
    final AtomicReference<TrustedCaller> patientCaller=new AtomicReference<>();

    @BeforeEach void setup() {
        revisionSpy=AopTestUtils.getUltimateTargetObject(revisions);
        reset(identities,scopes,doctors,appointments,revisionSpy);
        jdbc.execute("TRUNCATE medical_record_revisions,patient_revisions,medical_records,patients RESTART IDENTITY CASCADE");
        allowed.clear();
        when(identities.verify(anyString())).thenAnswer(inv -> switch((String)inv.getArgument(0)) {
            case "component-staff" -> staff; case "component-doctor" -> doctor;
            case "component-other-doctor" -> otherDoctor; case "component-patient" -> patientCaller.get();
            default -> throw new PatientError(PatientError.Code.UNAUTHENTICATED);
        });
        when(scopes.receptionist(any())).thenAnswer(inv -> staff.equals(inv.getArgument(0)));
        when(scopes.profile(any(),any())).thenReturn(true);
        when(scopes.clinical(any(),any())).thenReturn(true);
        when(scopes.validVisit(any(),any(),any(),any())).thenReturn(true);
        when(scopes.search(any())).thenAnswer(inv -> new WorkScopePort.SearchScope(Set.copyOf(allowed)));
        when(doctors.find(anyLong())).thenAnswer(inv -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return Optional.of(new DoctorClientPort.DoctorReference(inv.getArgument(0),true));
        });
    }
    @AfterAll static void stop() throws Exception { PG.close(); }
    @Test void tc01_tc02_sharedPhoneCreateAndCanonicalDetail() throws Exception {
        UUID a=createHttp("Demo A"), b=createHttp("Demo B");
        assertThat(a).isNotEqualTo(b); assertThat(a.version()).isEqualTo(4);
        assertThat(count("patients")).isEqualTo(2); assertThat(count("patient_revisions")).isEqualTo(2);
        var r=mvc.perform(get("/api/v2/patients/"+a).header("Authorization","Bearer component-staff"))
                .andExpect(status().isOk()).andReturn();
        schema(r,"PatientResponse");
        assertThat(json(r).at("/data/patientCode").asText()).isEqualTo("PAT-"+a);
        assertThat(json(r).at("/data/phoneNumber").asText()).isEqualTo("+84900000001");
        assertThat(jdbc.queryForObject("select after_data->>'id' from patient_revisions where patient_id=?",String.class,a)).isEqualTo(a.toString());
    }
    @Test void tc05_searchCombinesFiltersAndRestrictsCountAndStablePage() throws Exception {
        for(int i=0;i<12;i++) create("Same Name");
        UUID excluded=create("Same Name"); allowed.remove(excluded);
        var request=get("/api/v2/patients").param("keyword","same").param("phoneNumber","0900000001")
                .param("size","5").param("page","1").header("Authorization","Bearer component-staff");
        var a=mvc.perform(request).andExpect(status().isOk()).andReturn(); schema(a,"PatientPageResponse");
        assertThat(json(a).at("/data/totalElements").asInt()).isEqualTo(12);
        assertThat(json(a).at("/data/content").size()).isEqualTo(5);
        var b=mvc.perform(request).andReturn();
        assertThat(json(a).at("/data/content")).isEqualTo(json(b).at("/data/content"));
        for(var n:json(a).at("/data/content")) assertThat(n.path("id").asText()).isNotEqualTo(excluded.toString());
        mvc.perform(get("/api/v2/patients").param("keyword","absent").param("phoneNumber","0900000001")
                .header("Authorization","Bearer component-staff")).andExpect(jsonPath("$.data.totalElements").value(0));
        mvc.perform(get("/api/v2/patients").param("size","101").header("Authorization","Bearer component-staff")).andExpect(status().isBadRequest());
    }
    @Test void tc06_tc31_profileFullPutNoOpStaleAndRevisionVersion() throws Exception {
        UUID id=createHttp("Demo");
        var body=replaceBody("Demo",0);
        var noop=putProfile(id,body,200);
        assertThat(json(noop).at("/data/version").asLong()).isZero(); assertThat(count("patient_revisions")).isEqualTo(1);
        body.put("fullName","Changed");
        var changed=putProfile(id,body,200); schema(changed,"PatientResponse");
        assertThat(json(changed).at("/data/version").asLong()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select (after_data->>'version')::bigint from patient_revisions where entity_version=1",Long.class)).isEqualTo(1);
        assertThat(json(putProfile(id,body,409)).path("code").asText()).isEqualTo("VERSION_CONFLICT");
        assertThat(count("patient_revisions")).isEqualTo(2);
    }
    @Test void tc06_optionalFieldsAreErasedByReplacement() {
        var p=service.create(staff,new ProfileData("Demo","+84900000001","demo@example.test",LocalDate.of(1995,5,15),
                ProfileData.Gender.UNKNOWN,"Simulated address",ProfileData.Status.ACTIVE));
        var changed=service.replace(staff,p.id(),0,profile("Demo"));
        assertThat(changed.email()).isNull(); assertThat(changed.dateOfBirth()).isNull(); assertThat(changed.address()).isNull();
        assertThat(changed.version()).isEqualTo(1);
    }
    @Test void unicodePatientNamePersistsAndReadsBackWithoutLoss() throws Exception {
        UUID id=createHttp("Bệnh nhân mô phỏng 😀");
        assertThat(repository.find(id).orElseThrow().fullName()).isEqualTo("Bệnh nhân mô phỏng 😀");
        assertThat(jdbc.queryForObject("show server_encoding",String.class)).isEqualTo("UTF8");
    }
    @Test void tc09_tc10_tc14_linkedAndWalkInCanonicalMedicalReads() throws Exception {
        UUID id=create("Demo");
        when(appointments.find(102)).thenAnswer(inv -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return Optional.of(new AppointmentClientPort.AppointmentReference(102,id,12,false));
        });
        long record=createMedicalHttp(id,102L); createMedicalHttp(id,null); createMedicalHttp(id,null);
        var list=mvc.perform(get("/api/v2/patients/"+id+"/medical-records").param("appointmentId","102")
                .header("Authorization","Bearer component-doctor")).andExpect(status().isOk()).andReturn();
        schema(list,"MedicalRecordPageResponse"); assertThat(json(list).at("/data/totalElements").asLong()).isEqualTo(1);
        var detail=mvc.perform(get("/api/v2/patients/"+id+"/medical-records/"+record)
                .header("Authorization","Bearer component-doctor")).andExpect(status().isOk()).andReturn();
        schema(detail,"MedicalRecordResponse"); verify(appointments,times(1)).find(102);
        assertThat(count("medical_record_revisions")).isEqualTo(3);
    }
    @Test void tc15_tc16_tc36_parentAuthorImmutableRefsAndMedicalPut() throws Exception {
        UUID a=create("Demo A"),b=create("Demo B"); long record=createMedicalHttp(a,null);
        String path="/api/v2/patients/"+a+"/medical-records/"+record;
        mvc.perform(get("/api/v2/patients/"+b+"/medical-records/"+record).header("Authorization","Bearer component-doctor"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("MEDICAL_RECORD_NOT_FOUND"));
        var body=medicalBody(null); body.remove("doctorId"); body.remove("appointmentId");
        body.put("version",0); body.put("changeReason","Simulated correction");
        putMedical(path,body,"component-other-doctor",403);
        body.put("doctorId",12); putMedical(path,body,"component-doctor",400); body.remove("doctorId");
        assertThat(json(putMedical(path,body,"component-doctor",200)).at("/data/version").asLong()).isZero();
        body.put("diagnosis","Updated simulated diagnosis");
        assertThat(json(putMedical(path,body,"component-doctor",200)).at("/data/version").asLong()).isEqualTo(1);
        putMedical(path,body,"component-doctor",409);
        assertThat(jdbc.queryForObject("select change_reason from medical_record_revisions where entity_version=1",String.class)).isEqualTo("Simulated correction");
        verifyNoInteractions(appointments);
    }
    @Test void tc08_inactiveForbidsNewMedicalButAllowsExistingReadAndEdit() {
        UUID id=create("Demo"); var old=service.createRecord(doctor,id,12,null,clinical());
        service.replace(staff,id,0,inactive());
        assertCode(PatientError.Code.PATIENT_INACTIVE,() -> service.createRecord(doctor,id,12,null,clinical()));
        assertThat(service.record(doctor,id,old.id()).id()).isEqualTo(old.id());
        assertThat(service.replaceRecord(doctor,id,old.id(),0,clinical(),"Simulated review").version()).isZero();
    }
    @Test void tc17_tc18_tc35_authScopeAndForgedHeadersDoNotLeak() throws Exception {
        UUID id=create("Demo");
        mvc.perform(get("/api/v2/patients/"+id).header("X-Role","ROLE_STAFF").header("X-User-Id","401"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(get("/api/v2/patients/"+id).header("Authorization","Bearer invalid")).andExpect(status().isUnauthorized());
        patientCaller.set(caller("user:demo-patient","ROLE_PATIENT",id,null));
        mvc.perform(get("/api/v2/patients/"+id).header("Authorization","Bearer component-patient")).andExpect(status().isOk());
        mvc.perform(get("/api/v2/patients").header("Authorization","Bearer component-patient")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v2/patients/"+UUID.randomUUID()).header("Authorization","Bearer component-patient")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v2/patients/"+id+"/medical-records").header("Authorization","Bearer component-staff")).andExpect(status().isForbidden());
        when(scopes.clinical(doctor,id)).thenReturn(false);
        mvc.perform(post("/api/v2/patients/"+id+"/medical-records").header("Authorization","Bearer component-doctor")
                .contentType("application/json").content(JSON.writeValueAsString(medicalBody(null)))).andExpect(status().isForbidden());
        assertThat(count("medical_records")).isZero();
    }
    @Test void tc19_reference422VsTechnical503AndNoWrite() throws Exception {
        UUID id=create("Demo"); when(doctors.find(12)).thenReturn(Optional.empty());
        var r=postMedical(id,null,422); schema(r,"Error");
        assertThat(json(r).path("code").asText()).isEqualTo("REFERENCE_MISMATCH");
        when(doctors.find(12)).thenThrow(new IllegalStateException("private details"));
        assertThat(json(postMedical(id,null,503)).path("code").asText()).isEqualTo("DEPENDENCY_UNAVAILABLE");
        assertThat(count("medical_records")).isZero();
    }
    @Test void tc23_revisionFailureRollsBackBothAggregatesAndVersions() {
        doThrow(new IllegalStateException("fault")).when(revisionSpy).patient(isNull(),any());
        assertThatThrownBy(() -> service.create(staff,profile("Rollback"))).isInstanceOf(org.springframework.dao.DataAccessException.class).hasRootCauseMessage("fault");
        assertThat(count("patients")).isZero(); reset(revisionSpy);
        UUID id=create("Demo");
        doThrow(new IllegalStateException("fault")).when(revisionSpy).patient(notNull(),any());
        assertThatThrownBy(() -> service.replace(staff,id,0,profile("Rollback"))).isInstanceOf(org.springframework.dao.DataAccessException.class).hasRootCauseMessage("fault");
        assertThat(repository.find(id).orElseThrow().version()).isZero();
        assertThat(repository.find(id).orElseThrow().fullName()).isEqualTo("Demo");
        doThrow(new IllegalStateException("fault")).when(revisionSpy).medical(isNull(),any(),isNull());
        assertThatThrownBy(() -> service.createRecord(doctor,id,12,null,clinical())).isInstanceOf(org.springframework.dao.DataAccessException.class).hasRootCauseMessage("fault");
        assertThat(count("medical_records")).isZero(); reset(revisionSpy);
        var old=service.createRecord(doctor,id,12,null,clinical());
        doThrow(new IllegalStateException("fault")).when(revisionSpy).medical(notNull(),any(),anyString());
        assertThatThrownBy(() -> service.replaceRecord(doctor,id,old.id(),0,changedClinical(),"Simulated correction")).isInstanceOf(org.springframework.dao.DataAccessException.class).hasRootCauseMessage("fault");
        assertThat(repository.findRecord(id,old.id()).orElseThrow()).isEqualTo(old);
        assertThat(count("medical_record_revisions")).isEqualTo(1);
    }
    @Test void tc07_competingProfileUpdatesHaveOneWinner() throws Exception {
        UUID id=create("Demo");
        var result=race(() -> service.replace(staff,id,0,profile("First")),() -> service.replace(staff,id,0,profile("Second")));
        assertThat(result.stream().filter(Patient.class::isInstance).count()).isEqualTo(1);
        assertThat(result.stream().filter(e -> e instanceof PatientError p && p.code()==PatientError.Code.VERSION_CONFLICT).count()).isEqualTo(1);
        assertThat(count("patient_revisions")).isEqualTo(2);
    }
    @Test void tc11_linkedCreateRaceReturns201And409WithOneRevision() throws Exception {
        UUID id=create("Demo");
        when(appointments.find(102)).thenReturn(Optional.of(new AppointmentClientPort.AppointmentReference(102,id,12,false)));
        Callable<Integer> call=() -> postMedical(id,102L,-1).getResponse().getStatus();
        assertThat(race(call,call)).containsExactlyInAnyOrder(201,409);
        assertThat(count("medical_records")).isEqualTo(1); assertThat(count("medical_record_revisions")).isEqualTo(1);
    }
    @Test void tc29_deactivateCommitBeforeCreateRejectsMedical() throws Exception {
        UUID id=create("Demo"); CountDownLatch locked=new CountDownLatch(1),release=new CountDownLatch(1),attempting=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var first=pool.submit(() -> transaction(() -> {
                repository.lock(id).orElseThrow(); writer.replace(id,0,inactive(),staff.subject()); locked.countDown(); await(release);
            }));
            await(locked);
            var second=pool.submit(() -> { attempting.countDown(); assertCode(PatientError.Code.PATIENT_INACTIVE,() -> service.createRecord(doctor,id,12,null,clinical())); });
            await(attempting); release.countDown(); first.get(15,TimeUnit.SECONDS); second.get(15,TimeUnit.SECONDS);
        } finally { release.countDown(); }
        assertThat(count("medical_records")).isZero();
    }
    @Test void tc29_recordCommittedBeforeDeactivationRemains() {
        UUID id=create("Demo"); var record=service.createRecord(doctor,id,12,null,clinical());
        service.replace(staff,id,0,inactive());
        assertThat(repository.findRecord(id,record.id())).isPresent(); assertThat(count("medical_record_revisions")).isEqualTo(1);
    }
    @Test void tc31_noOpMedicalDetectsConcurrentVersionChangeAtCommit() throws Exception {
        UUID id=create("Demo"); var record=service.createRecord(doctor,id,12,null,clinical());
        CountDownLatch checked=new CountDownLatch(1),committed=new CountDownLatch(1);
        try(var pool=Executors.newSingleThreadExecutor()) {
            var noOp=pool.submit(() -> assertThatThrownBy(() -> transaction(() -> {
                repository.checkAtCommit(repository.findRecord(id,record.id()).orElseThrow()); checked.countDown(); await(committed);
            })).isInstanceOfAny(org.springframework.orm.ObjectOptimisticLockingFailureException.class,
                    org.springframework.transaction.TransactionSystemException.class,OptimisticLockException.class));
            await(checked);
            try { service.replaceRecord(doctor,id,record.id(),0,changedClinical(),"Simulated correction"); }
            finally { committed.countDown(); }
            noOp.get(15,TimeUnit.SECONDS);
        } finally { committed.countDown(); }
        assertThat(count("medical_record_revisions")).isEqualTo(2);
    }
    @Test void tc33_constraintsAndRetiredV1() throws Exception {
        UUID id=create("Demo");
        assertThatThrownBy(() -> jdbc.update("update patients set patient_code='PAT-wrong' where id=?",id)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update patients set version=-1 where id=?",id)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        mvc.perform(get("/api/v1/patients")).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/patients").contentType("application/json").content("{}")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v2/patients/1").header("Authorization","Bearer component-staff")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v2/patients/"+UUID.randomUUID()).header("Authorization","Bearer component-staff"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PATIENT_NOT_FOUND"));
    }
    @Test void tc03_tc32_apiBindingRejectsUnknownNullAndCoercedValuesWithoutWriting() throws Exception {
        var cases=List.of(
                "{\"fullName\":\"Demo\",\"phoneNumber\":\"0900000001\",\"gender\":null}",
                "{\"fullName\":12,\"phoneNumber\":\"0900000001\"}",
                "{\"fullName\":\"Demo\",\"phoneNumber\":\"0900000001\",\"createdBy\":\"spoof\"}",
                "{\"fullName\":\"Demo\",\"phoneNumber\":\"0900000001\",\"dateOfBirth\":\" \"}");
        for(String body:cases) {
            var result=mvc.perform(post("/api/v2/patients").header("Authorization","Bearer component-staff")
                    .contentType("application/json").content(body)).andExpect(status().isBadRequest()).andReturn();
            schema(result,"Error"); assertThat(json(result).path("code").asText()).isEqualTo("VALIDATION_ERROR");
        }
        assertThat(count("patients")).isZero(); assertThat(count("patient_revisions")).isZero();
    }
    @Test void tc14_parentExistsWithoutRecordsReturnsEmptyPageAndUnknownParent404() throws Exception {
        UUID id=create("Demo");
        mvc.perform(get("/api/v2/patients/"+id+"/medical-records").header("Authorization","Bearer component-doctor"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content").isEmpty()).andExpect(jsonPath("$.data.totalPages").value(0));
        mvc.perform(get("/api/v2/patients/"+UUID.randomUUID()+"/medical-records").header("Authorization","Bearer component-doctor"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PATIENT_NOT_FOUND"));
    }
    @Test void tc17_verifierUnavailableReturns503AndInactiveIdentityReturns401() throws Exception {
        doThrow(new IllegalStateException("technical")).when(identities).verify("offline");
        mvc.perform(get("/api/v2/patients").header("Authorization","Bearer offline"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("DEPENDENCY_UNAVAILABLE"));
        doReturn(new TrustedCaller("user:demo",TrustedCaller.PrincipalType.USER,
                Set.of("ROLE_STAFF"),null,null,null,false)).when(identities).verify("inactive");
        mvc.perform(get("/api/v2/patients").header("Authorization","Bearer inactive")).andExpect(status().isUnauthorized());
    }
    private UUID createHttp(String name) throws Exception {
        var r=mvc.perform(post("/api/v2/patients").header("Authorization","Bearer component-staff")
                .contentType("application/json").content(JSON.writeValueAsString(Map.of("fullName",name,"phoneNumber","0900000001"))))
                .andExpect(status().isCreated()).andReturn();
        schema(r,"PatientCreated"); UUID id=UUID.fromString(json(r).at("/data/id").asText()); allowed.add(id); return id;
    }
    private UUID create(String name) { var p=service.create(staff,profile(name)); allowed.add(p.id()); return p.id(); }
    private long createMedicalHttp(UUID id,Long appointment) throws Exception {
        var r=postMedical(id,appointment,201); schema(r,"MedicalRecordCreated"); return json(r).at("/data/id").asLong();
    }
    private MvcResult postMedical(UUID id,Long appointment,int expected) throws Exception {
        var r=mvc.perform(post("/api/v2/patients/"+id+"/medical-records").header("Authorization","Bearer component-doctor")
                .contentType("application/json").content(JSON.writeValueAsString(medicalBody(appointment)))).andReturn();
        if(expected!=-1) assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(expected);
        return r;
    }
    private MvcResult putProfile(UUID id,Map<String,Object> b,int expected) throws Exception {
        return mvc.perform(put("/api/v2/patients/"+id).header("Authorization","Bearer component-staff")
                .contentType("application/json").content(JSON.writeValueAsString(b))).andExpect(status().is(expected)).andReturn();
    }
    private MvcResult putMedical(String path,Map<String,Object> b,String token,int expected) throws Exception {
        var result=mvc.perform(put(path).header("Authorization","Bearer "+token).contentType("application/json")
                .content(JSON.writeValueAsString(b))).andExpect(status().is(expected)).andReturn();
        schema(result,expected==200?"MedicalRecordResponse":"Error");
        return result;
    }
    private Map<String,Object> replaceBody(String name,long version) {
        return new HashMap<>(Map.of("fullName",name,"phoneNumber","0900000001","gender","UNKNOWN","status","ACTIVE","version",version));
    }
    private Map<String,Object> medicalBody(Long appointment) {
        var b=new HashMap<String,Object>(Map.of("doctorId",12,"visitDate",REGISTRY.at("/clinicalScene/visitDate").asText(),"symptoms","Simulated symptoms","diagnosis","Simulated diagnosis"));
        b.put("appointmentId",appointment); return b;
    }
    private ProfileData profile(String name) { return new ProfileData(name,REGISTRY.at("/patient/phoneNumber").asText(),null,null,ProfileData.Gender.UNKNOWN,null,ProfileData.Status.ACTIVE); }
    private ProfileData inactive() { return new ProfileData("Demo","+84900000001",null,null,ProfileData.Gender.UNKNOWN,null,ProfileData.Status.INACTIVE); }
    private ClinicalData clinical() { return new ClinicalData(Instant.parse(REGISTRY.at("/clinicalScene/visitDate").asText()),"Simulated symptoms","Simulated diagnosis",null,null); }
    private ClinicalData changedClinical() { return new ClinicalData(clinical().visitDate(),"Updated simulated","Simulated diagnosis",null,null); }
    private int count(String table) { return jdbc.queryForObject("select count(*) from "+table,Integer.class); }
    private static TrustedCaller caller(String subject,String role,UUID id,Long doctorId) { return new TrustedCaller(subject,TrustedCaller.PrincipalType.USER,Set.of(role),id,doctorId,null,true); }
    private JsonNode json(MvcResult r) { return JSON.readTree(r.getResponse().getContentAsByteArray()); }
    private void schema(MvcResult r,String name) { com.fclinic.patientservice.contract.ContractSchemas.patient(json(r),name); }
    private static JsonNode read(String path) { try(var in=new ClassPathResource(path).getInputStream()) { return JSON.readTree(in); } catch(Exception e) { throw new ExceptionInInitializerError(e); } }
    private static EmbeddedPostgres startPostgres() {
        try {
            Path root=Files.createTempDirectory(Path.of("target").toAbsolutePath(),"patient-v2-it-");
            var pg=EmbeddedPostgres.builder().setPort(0).setDataDirectory(root.resolve("data"))
                    .setOverrideWorkingDirectory(root.resolve("binaries").toFile()).setCleanDataDirectory(true)
                    .setServerConfig("listen_addresses","127.0.0.1")
                    .setServerConfig("timezone","UTC")
                    .setServerConfig("log_min_error_statement","panic").start();
            try { new ResourceDatabasePopulator(new ClassPathResource("db/schema/V001__patient_v2_empty.sql")).execute(pg.getPostgresDatabase()); return pg; }
            catch(Throwable e) { pg.close(); throw e; }
        } catch(Exception e) { throw new ExceptionInInitializerError(e); }
    }
    private void transaction(Runnable call) { new TransactionTemplate(manager).executeWithoutResult(s -> call.run()); }
    private static void await(CountDownLatch latch) {
        try { if(!latch.await(15,TimeUnit.SECONDS)) throw new AssertionError("Test barrier timeout"); }
        catch(InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
    }
    private static <T> List<Object> race(Callable<T> a,Callable<T> b) throws Exception {
        CyclicBarrier barrier=new CyclicBarrier(2);
        try(var pool=Executors.newFixedThreadPool(2)) {
            List<Future<Object>> futures=new ArrayList<>();
            for(var action:List.of(a,b)) futures.add(pool.submit(() -> { barrier.await(15,TimeUnit.SECONDS); try { return action.call(); } catch(Exception e) { return e; } }));
            return List.of(futures.get(0).get(20,TimeUnit.SECONDS),futures.get(1).get(20,TimeUnit.SECONDS));
        }
    }
    private static void assertCode(PatientError.Code code,Runnable call) { assertThatThrownBy(call::run).isInstanceOfSatisfying(PatientError.class,e -> assertThat(e.code()).isEqualTo(code)); }
}

