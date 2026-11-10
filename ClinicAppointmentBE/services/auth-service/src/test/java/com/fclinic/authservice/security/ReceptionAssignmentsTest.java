package com.fclinic.authservice.security;

import com.fclinic.authservice.api.controller.ReceptionAssignmentsController;
import com.fclinic.authservice.application.port.out.*;
import com.fclinic.authservice.application.service.ScopeAuthorityService;
import com.fclinic.authservice.domain.aggregate.User;
import com.fclinic.authservice.domain.valueobject.*;
import com.fclinic.authservice.infrastructure.adapter.*;
import com.fclinic.authservice.infrastructure.config.ScopeAuthorityProperties;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReceptionAssignmentsTest {
 static EmbeddedPostgres pg;
 static JdbcTemplate jdbc;
 final UUID subject=UUID.fromString("22222222-2222-4222-8222-222222222202"),userId=UUID.fromString("11111111-1111-4111-8111-111111111102");
 final UUID source=UUID.randomUUID(),oldPatient=UUID.randomUUID();
 final Instant now=Instant.now();final Clock clock=Clock.fixed(now,ZoneOffset.UTC);
 final UserRepositoryPort users=mock(UserRepositoryPort.class);
 ReceptionGrantStore store; ConfiguredScopeAssignments assignments;ScopeAuthorityProperties properties;ReceptionAssignmentsController controller;User user;
 final String secret="test-only-reception-secret";
 String credential(){return "Basic "+Base64.getEncoder().encodeToString(("patient-reception:"+secret).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
 @BeforeAll static void database() throws Exception {
  var dir=Files.createTempDirectory(Path.of("target").toAbsolutePath(),"reception-auth-");
  pg=EmbeddedPostgres.builder().setPort(0).setDataDirectory(dir.resolve("data")).setOverrideWorkingDirectory(dir.resolve("bin").toFile()).setCleanDataDirectory(true).start();
  jdbc=new JdbcTemplate(pg.getPostgresDatabase());
  new ResourceDatabasePopulator(new ClassPathResource("db/migration/V1__create_auth_tables.sql"),new ClassPathResource("db/migration/V2__seed_roles_and_users.sql"),new ClassPathResource("db/migration/V4__reception_patient_assignments.sql")).execute(pg.getPostgresDatabase());
 }
 @AfterAll static void close() throws Exception {if(pg!=null)pg.close();}
 @BeforeEach void setup(){
  jdbc.execute("truncate reception_patient_assignments");store=new ReceptionGrantStore(jdbc);
  user=User.create(userId,subject,"staff_linh","staff@example.test",null,"Synthetic Staff",new Role(UUID.randomUUID(),RoleName.ROLE_STAFF,"",now,now),null,null,null);
  when(users.findByKeycloakId(subject)).thenReturn(Optional.of(user));
  properties=new ScopeAuthorityProperties(Map.of(),Map.of(),Map.of(source,new ScopeAuthorityProperties.Grant(subject.toString(),Set.of("ADMIN_ASSIGNMENT_READ","ADMIN_ASSIGNMENT_UPDATE","ADMIN_ASSIGNMENT_SEARCH","ADMIN_SEARCH","APPOINTMENT_CREATE","APPOINTMENT_SEARCH"),Set.of(oldPatient),false,true,now.minusSeconds(1),now.plusSeconds(600),null,null,null)));
  assignments=new ConfiguredScopeAssignments(properties,store,clock);
  controller=new ReceptionAssignmentsController(users,assignments,new ScopeAuthorityService(users,assignments,clock),store,clock,secret);
 }
 ReceptionAssignmentsController.Request request(Set<UUID> ids){return new ReceptionAssignmentsController.Request("patient-reception/1",UUID.randomUUID(),now,new ScopeAuthorityService.Caller(subject.toString(),"USER",null,null,null),ids,"SEARCH");}
 @Test void durableFiniteGrantsSurviveServiceRecreationAndSearchUnionsAllAssignments(){
  var a=UUID.randomUUID();var b=UUID.randomUUID();
  assertThat(controller.grant(credential(),request(Set.of(a,b))).getStatusCode().value()).isEqualTo(200);
  assertThat(controller.grant(credential(),request(Set.of(a,b))).getStatusCode().value()).isEqualTo(200);
  assertThat(jdbc.queryForObject("select count(*) from reception_patient_assignments",Integer.class)).isEqualTo(2);
  var loaded=new ConfiguredScopeAssignments(properties,new ReceptionGrantStore(jdbc),clock);
  var q=new ScopeAuthorityService.Query("patient-work-scope/2",UUID.randomUUID(),now,"ADMINISTRATIVE","ADMIN_SEARCH",request(Set.of(a)).caller(),null,null,null);
  assertThat(new ScopeAuthorityService(users,loaded,clock).evaluate(q)).containsEntry("allowed",true).containsEntry("patientIds",Set.of(oldPatient,a,b));
  for(var stored:store.load())assertThat(stored.grant().operations()).noneMatch(op->op.startsWith("MEDICAL_")||op.equals("VALID_VISIT"));
 }
 @Test void removingOrExpiringSourceAssignmentRevokesPersistedRights(){
  var id=UUID.randomUUID();controller.grant(credential(),request(Set.of(id)));
  var empty=new ScopeAuthorityProperties(Map.of(),Map.of(),Map.of());
  assertThat(new ConfiguredScopeAssignments(empty,store,clock).grants()).isEmpty();
  assertThat(new ConfiguredScopeAssignments(properties,store,Clock.fixed(now.plusSeconds(601),ZoneOffset.UTC)).grants()).hasSize(1);
 }
 @Test void browserAndOtherMachineCredentialsCannotProvision(){
  var r=request(Set.of(UUID.randomUUID()));
  for(String auth:List.of("Bearer browser-token","Basic "+Base64.getEncoder().encodeToString("patient-service:wrong".getBytes())))
   assertThat(controller.grant(auth,r).getStatusCode().value()).isEqualTo(401);
  assertThat(store.load()).isEmpty();
 }
 @Test void refreshingAResourceDoesNotMoveItsStartTimeAndDatabaseRejectsClinicalGrants(){
  var id=UUID.randomUUID();
  store.grant(userId,subject,id,source,Set.of("ADMIN_PROFILE_READ"),now,now.plusSeconds(300));
  store.grant(userId,subject,id,source,Set.of("ADMIN_PROFILE_READ"),now.plusSeconds(2),now.plusSeconds(300));
  var stored=store.load().getFirst();
  assertThat(stored.grant().validFrom()).isEqualTo(now.truncatedTo(java.time.temporal.ChronoUnit.MICROS));
  assertThat(new ConfiguredScopeAssignments(properties,store,clock).grants()).containsKey(stored.id());
  assertThatThrownBy(()->store.grant(userId,subject,UUID.randomUUID(),source,Set.of("MEDICAL_READ"),now,now.plusSeconds(300)))
    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
 }
 @Test void inactiveAccountForgedBindingAndOverbroadRequestsFailClosed(){
  var r=request(Set.of(UUID.randomUUID()));user.setActiveStatus(false);
  assertThat(controller.grant(credential(),r).getStatusCode().value()).isEqualTo(403);user.setActiveStatus(true);
  var forged=new ReceptionAssignmentsController.Request(r.contractVersion(),r.nonce(),now,new ScopeAuthorityService.Caller(subject.toString(),"USER",UUID.randomUUID(),null,null),r.patientIds(),r.action());
  assertThat(controller.grant(credential(),forged).getStatusCode().value()).isEqualTo(403);
  Set<UUID> many=new HashSet<>();for(int n=0;n<101;n++)many.add(UUID.randomUUID());
  assertThat(controller.grant(credential(),request(many)).getStatusCode().value()).isEqualTo(400);
  assertThat(controller.grant(credential(),request(Set.of())).getStatusCode().value()).isEqualTo(400);
  assertThat(store.load()).isEmpty();
 }
 @Test void permissionWindowBoundaryDoesNotLeavePartiallyGrantedSearchRights(){
  var search=new ScopeAuthorityProperties.Grant(subject.toString(),Set.of("ADMIN_ASSIGNMENT_SEARCH"),Set.of(),false,true,now.minusSeconds(1),now.plusSeconds(600),null,null,null);
  var read=new ScopeAuthorityProperties.Grant(subject.toString(),Set.of("ADMIN_ASSIGNMENT_READ"),Set.of(),false,true,now.plusSeconds(1),now.plusSeconds(600),null,null,null);
  var settings=new ScopeAuthorityProperties(Map.of(),Map.of(),Map.of(UUID.randomUUID(),search,UUID.randomUUID(),read));
  var configured=new ConfiguredScopeAssignments(settings,store,clock);
  var delayedScopeClock=Clock.fixed(now.plusSeconds(2),ZoneOffset.UTC);
  var c=new ReceptionAssignmentsController(users,configured,new ScopeAuthorityService(users,configured,delayedScopeClock),store,clock,secret);
  assertThat(c.grant(credential(),request(Set.of(UUID.randomUUID()))).getStatusCode().value()).isEqualTo(403);
  assertThat(store.load()).isEmpty();
 }
}
