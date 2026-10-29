package com.fclinic.patientservice.identity;

import com.fclinic.patientservice.infrastructure.adapter.identity.*;
import com.fclinic.patientservice.application.security.TrustedCaller;
import com.fclinic.patientservice.domain.model.PatientError;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import tools.jackson.databind.json.JsonMapper;
import java.net.URI;import java.nio.file.*;import java.time.*;import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit external endpoint probe. No server/token generator or authenticity claim is supplied here. */
class RealAuthIdentityContractCheck {
 static final String PREFIX="FCLINIC_REAL_AUTH_PATIENT_";
 final List<Map<String,Object>> cases=new ArrayList<>();
 @Test @Timeout(90) void productionIdentityAdapterAgainstSuppliedProvider()throws Exception{
  Path evidence=Path.of(required("FCLINIC_REAL_AUTH_EVIDENCE_DIR")).toAbsolutePath();
  Path repo=Path.of(System.getProperty("basedir")).toAbsolutePath().getParent().getParent().getParent();
  assertFalse(evidence.startsWith(repo),"External private evidence directory required");assertTrue(Files.isDirectory(evidence));
  var settings=settings(required(PREFIX+"CLIENT_SECRET"));String serviceSubject=required(PREFIX+"SERVICE_SUBJECT");
  String patient=required(PREFIX+"PATIENT_UUID"),doctor=required(PREFIX+"DOCTOR_ID");
  try(var verifier=new IntrospectionIdentityVerifier(settings,Clock.systemUTC())){
   check("USER",()->{
    var caller=verifier.verify(required(PREFIX+"USER_TOKEN"));
    assertTrue(caller.active()&&caller.principalType()==TrustedCaller.PrincipalType.USER,"Expected verified USER");
    assertTrue(required(PREFIX+"USER_SUBJECT").equals(caller.subject()),"Trusted subject mismatch");
    assertTrue(patient.equals(String.valueOf(caller.patientId())),"Trusted patient link mismatch");
    assertTrue(caller.userRole("ROLE_PATIENT"),"Patient role required for this fixture");assertNull(caller.serviceName());
   });
   check("DOCTOR",()->{
    var caller=verifier.verify(required(PREFIX+"DOCTOR_TOKEN"));
    assertTrue(caller.userRole("ROLE_DOCTOR")&&doctor.equals(String.valueOf(caller.doctorId())),"Trusted doctor mapping mismatch");
   });
   check("SERVICE",()->{
    var caller=verifier.verify(required(PREFIX+"SERVICE_TOKEN"));
    assertTrue(caller.principalType()==TrustedCaller.PrincipalType.SERVICE&&serviceSubject.equals(caller.subject()),"Expected registered SERVICE");
    assertTrue(required(PREFIX+"SERVICE_NAME").equals(caller.serviceName()),"Registry identity mismatch");
    assertNull(caller.patientId());assertNull(caller.doctorId());assertFalse(caller.userRole("ROLE_PATIENT"));
   });
   check("UNLINKED_USER",()->{var caller=verifier.verify(required(PREFIX+"UNLINKED_TOKEN"));assertTrue(caller.principalType()==TrustedCaller.PrincipalType.USER);assertNull(caller.patientId());});
   for(String vector:List.of("INVALID","EXPIRED","INACTIVE","REVOKED","WRONG_ISSUER","WRONG_AUDIENCE"))
    check(vector,()->assertSame(PatientError.Code.UNAUTHENTICATED,assertThrows(PatientError.class,()->verifier.verify(required(PREFIX+vector+"_TOKEN"))).code()));
   check("UNREGISTERED_SERVICE",()->assertSame(PatientError.Code.FORBIDDEN,assertThrows(PatientError.class,()->verifier.verify(required(PREFIX+"UNKNOWN_SERVICE_TOKEN"))).code()));
  check("INVALID_BACKEND_CREDENTIAL",()->{try(var denied=new IntrospectionIdentityVerifier(settings(UUID.randomUUID().toString()),Clock.systemUTC())){assertSame(PatientError.Code.DEPENDENCY_UNAVAILABLE,assertThrows(PatientError.class,()->denied.verify(required(PREFIX+"USER_TOKEN"))).code());}});
  }finally{
   // No subject, UUID, token, backend secret, URI or raw provider response is exported.
   Files.writeString(evidence.resolve("patient-auth-adapter-probe.json"),JsonMapper.builder().build().writerWithDefaultPrettyPrinter().writeValueAsString(Map.of("classification","ADAPTER CONTRACT PROBE ONLY; provider provenance and application authorization require separate verification","module","patient-service","timestamp",Instant.now().toString(),"cases",cases)));
  }

 }
 IdentitySettings settings(String secret){
  String trust=System.getenv(PREFIX+"TRUST_STORE"),password=System.getenv(PREFIX+"TRUST_STORE_PASSWORD");
  return new IdentitySettings(true,URI.create(required(PREFIX+"ENDPOINT")),required(PREFIX+"ISSUER"),required(PREFIX+"AUDIENCE"),required(PREFIX+"CLIENT_ID"),secret,Duration.ofSeconds(1),Duration.ofSeconds(3),65536,4,trust==null?null:Path.of(trust),password,
   Map.of(required(PREFIX+"SERVICE_SUBJECT"),new IdentitySettings.ServiceBinding(required(PREFIX+"SERVICE_NAME"),Set.of(required(PREFIX+"SERVICE_AUTHORITIES").split(",")))));
 }
 void check(String name,Checked action)throws Exception{try{action.run();cases.add(Map.of("id",name,"status","ADAPTER PROBE PASS"));}catch(Exception|AssertionError error){cases.add(Map.of("id",name,"status","FAIL","failureType",error.getClass().getSimpleName()));throw error;}}
 @FunctionalInterface interface Checked{void run()throws Exception;}
 static String required(String key){String value=System.getenv(key);if(value==null||value.isBlank())throw new IllegalStateException("External Auth preflight incomplete: "+key);return value;}
}
