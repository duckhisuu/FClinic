package com.fclinic.patientservice.contract;

import com.fclinic.patientservice.infrastructure.adapter.reference.*;
import com.fclinic.patientservice.domain.model.PatientError;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static com.fclinic.patientservice.contract.ProviderContractPlan.Case;

/** Explicit Maven profile only. No Spring context, datasource, fixture server or provider mutations. */
class RealProviderContractCheck {
    @Test void verifyActualProviderBinary() throws Exception {
        List<Map<String,Object>> results=new ArrayList<>();
        Map<String,Object> report=new LinkedHashMap<>();
        report.put("timestamp",Instant.now().toString());
        report.put("wholePatientTrustChain","NOT EXECUTED");
        report.put("databaseMutation","NONE: GET only; no datasource");
        report.put("cases",results);
        Path output=Path.of("target/real-provider-reports/observations.json");
        try {
            ProviderContractPlan plan=ProviderContractPlan.load(System.getenv());
            report.put("boundary",plan.doctor?"B-04":"B-05");
            report.put("binarySha256",plan.binaryHash);report.put("sourceCommit",plan.sourceCommit);
            report.put("deploymentEvidenceSha256",plan.deploymentHash);report.put("fixtureManifestSha256",plan.vectorHash);
            report.put("endpointSha256",plan.endpointHash);
            report.put("provenance","Operator-attested deployment linked to inspected executable provider JAR; review deployment evidence before signoff");
            Set<Case> executed=EnumSet.noneOf(Case.class);
            for(var vector:plan.vectors) {
                executed.add(vector.scenario());
                Map<String,Object> result=new LinkedHashMap<>();
                result.put("case",vector.scenario().name());result.put("expected",expected(vector.scenario()));
                results.add(result);
                try { execute(plan,vector,result); result.put("classification","REAL PROVIDER PASS"); }
                catch(Exception | AssertionError e) {
                    result.put("classification","REAL PROVIDER FAIL");
                    result.put("actual","Assertion/transport failed; response and exception details suppressed");
                }
            }
            for(Case scenario:Case.values()) if(!executed.contains(scenario)
                    && (plan.doctor?!Set.of(Case.VALID,Case.CANCELLED,Case.PATIENT_MISMATCH,Case.DOCTOR_MISMATCH,Case.UNKNOWN_STATUS).contains(scenario):
                    !Set.of(Case.ACTIVE,Case.INACTIVE).contains(scenario))) {
                results.add(Map.of("case",scenario.name(),"classification","NOT EXECUTED",
                        "expected",expected(scenario),"actual","No controlled real-provider fixture provisioned"));
            }
            long failures=results.stream().filter(r -> "REAL PROVIDER FAIL".equals(r.get("classification"))).count();
            report.put("result",failures==0?"CONFIGURED VECTORS PASS; review NOT EXECUTED cases":"REAL PROVIDER FAIL");
            if(failures!=0) throw new AssertionError("Real-provider contract failures; inspect redacted observations.json");
        } catch(IllegalStateException e) {
            report.put("result","BLOCKED"); report.put("actual","Preflight incomplete/invalid; no provider verification claimed");
            throw new AssertionError("Real-provider preflight blocked; follow 12_REAL_INTEGRATION_RUNBOOK.md");
        } finally {
            Files.createDirectories(output.getParent());
            Files.writeString(output,ProviderContractPlan.JSON.writerWithDefaultPrettyPrinter().writeValueAsString(report));
        }
    }
    private static void execute(ProviderContractPlan p,ProviderContractPlan.Vector v,Map<String,Object> result) throws Exception {
        var profile=p.doctor?ReferenceHttpClient.Profile.DOCTOR:ReferenceHttpClient.Profile.APPOINTMENT;
        boolean denied=v.scenario()==Case.INVALID_CREDENTIAL,wrongRequest=v.scenario()==Case.WRONG_VERSION_REQUEST;
        var settings=denied?p.invalidCredentialEndpoint:p.endpoint;
        ProviderProbe.Response response;
        try(var probe=new ProviderProbe(settings)) { response=probe.get(v.resourceId(),profile,wrongRequest); }
        result.put("httpStatus",response.status());
        // Structural disclosure checks apply to denial/error too. Never write raw bodies, messages, URI or IDs.
        String body=new String(response.bytes(),java.nio.charset.StandardCharsets.UTF_8);
        check(!body.contains(settings.clientSecret()) && !body.contains(p.endpoint.clientSecret())
                && !body.contains(p.endpoint.collectionUri().toASCIIString()) && !body.contains("java.lang.")
                && !body.contains("stackTrace"));
        check(response.headers().allValues("Content-Type").size()==1
                && response.headers().firstValue("Content-Type").orElse("").matches("(?i)application/json(?:\\s*;.*)?"));
        if(denied || wrongRequest) {
            check(denied?(response.status()==401 || response.status()==403):(response.status()==400 || response.status()==406));
            var root=ProviderContractPlan.JSON.readTree(response.bytes());
            ProviderContractPlan.exact(root,Set.of("status","message","data","code","fieldErrors"));
            check(root.path("status").isIntegralNumber() && root.path("status").intValue()==response.status()
                    && root.path("data").isNull() && root.path("message").isString()
                    && root.path("message").asText().length()<=255 && root.path("fieldErrors").isArray() && root.path("fieldErrors").isEmpty());
            check(denied?Set.of("UNAUTHENTICATED","FORBIDDEN").contains(root.path("code").asText()):
                    "UNSUPPORTED_REFERENCE_CONTRACT".equals(root.path("code").asText()));
            if(denied) productionUnavailable(p,v,settings);
            result.put("actual","Upstream denied; no resource data; generic structural envelope");
            return;
        }
        if(v.scenario()==Case.TECHNICAL_5XX) {
            check(response.status()>=500 && response.status()<=599);
            productionUnavailable(p,v,settings);
            result.put("actual","Observed upstream5xx; actual production consumer503");return;
        }
        check(response.status()==(v.scenario()==Case.MISSING?404:200));
        boolean marker=response.headers().allValues(ReferenceHttpClient.VERSION_HEADER).size()==1
                && profile.version.equals(response.headers().firstValue(ReferenceHttpClient.VERSION_HEADER).orElse(null));
        if(v.scenario()==Case.WRONG_VERSION_RESPONSE) {
            check(!marker); productionUnavailable(p,v,settings);
            result.put("actual","Observed wrong/missing marker; production consumer503");return;
        }
        check(marker);
        if(Set.of(Case.UNKNOWN_STATUS,Case.INVALID_PAYLOAD,Case.WRONG_RESOURCE).contains(v.scenario())) {
            if(v.scenario()==Case.UNKNOWN_STATUS) {
                check(!p.doctor);
                var status=ProviderContractPlan.JSON.readTree(response.bytes()).at("/data/status");
                check(status.isString() && !Set.of("CONFIRMED","CANCELLED","COMPLETED","NO_SHOW").contains(status.asText()));
            }
            if(v.scenario()==Case.WRONG_RESOURCE) {
                ContractSchemas.reference(response.bytes(),p.doctor);
                check(ProviderContractPlan.safeId(ProviderContractPlan.JSON.readTree(response.bytes()).at("/data/id"))!=v.resourceId());
            }
            if(v.scenario()==Case.INVALID_PAYLOAD) {
                boolean rejected=false;
                try { ContractSchemas.reference(response.bytes(),p.doctor); }
                catch(RuntimeException | AssertionError e) { rejected=true; }
                check(rejected);
            }
            productionUnavailable(p,v,settings);
            result.put("actual","Observed controlled contract violation; production consumer503");return;
        }
        ContractSchemas.reference(response.bytes(),p.doctor);
        if(p.doctor) {
            new DoctorReferenceDecoder().decode(response.status(),response.bytes(),v.resourceId());
            try(var adapter=new RemoteDoctorClientAdapter(settings)) {
                var facts=adapter.find(v.resourceId());
                if(v.scenario()==Case.MISSING) check(facts.isEmpty());
                else check(facts.isPresent() && facts.get().id()==v.resourceId() && facts.get().active()==(v.scenario()==Case.ACTIVE));
            }
        } else {
            new AppointmentReferenceDecoder().decode(response.status(),response.bytes(),v.resourceId());
            try(var adapter=new RemoteAppointmentClientAdapter(settings)) {
                var facts=adapter.find(v.resourceId());
                if(v.scenario()==Case.MISSING) check(facts.isEmpty());
                else {
                    check(facts.isPresent());var f=facts.orElseThrow();
                    check(f.id()==v.resourceId());
                    check(f.cancelled()==(v.scenario()==Case.CANCELLED));
                    check(v.scenario()==Case.PATIENT_MISMATCH?!f.patientId().equals(v.patientId()):f.patientId().equals(v.patientId()));
                    check(v.scenario()==Case.DOCTOR_MISMATCH?f.doctorId()!=v.doctorId():f.doctorId()==v.doctorId());
                }
            }
        }
        result.put("actual","HTTP/schema/version and production reference adapter facts match vector; Patient mutation not executed");
    }
    private static void productionUnavailable(ProviderContractPlan p,ProviderContractPlan.Vector v,ReferenceSettings.Endpoint settings) {
        try {
            if(p.doctor) {try(var adapter=new RemoteDoctorClientAdapter(settings)) {adapter.find(v.resourceId());}}
            else {try(var adapter=new RemoteAppointmentClientAdapter(settings)) {adapter.find(v.resourceId());}}
        } catch(PatientError e) { check(e.code()==PatientError.Code.DEPENDENCY_UNAVAILABLE); return; }
        throw new AssertionError("Production consumer did not reject provider failure");
    }
    private static void check(boolean condition) { if(!condition) throw new AssertionError("Contract assertion failed; details redacted"); }
    private static String expected(Case c) {
        return switch(c) {
            case ACTIVE -> "HTTP200/active=true/exact full Doctor profile";
            case INACTIVE -> "HTTP200/active=false; business422 at Patient application layer";
            case VALID -> "HTTP200/minimum UUID projection/exact expected patient and doctor; no visit grant inferred";
            case CANCELLED -> "HTTP200/CANCELLED; business422 at Patient application layer";
            case MISSING -> "HTTP404/exact domain error/version; Optional.empty; application422";
            case PATIENT_MISMATCH,DOCTOR_MISMATCH -> "HTTP200/well-formed reference differs expected parent/doctor; application422 (not executed by read-only harness)";
            case INVALID_CREDENTIAL -> "Actual upstream401/403/no resource data; production consumer503";
            case WRONG_VERSION_REQUEST -> "Actual upstream400/406/UNSUPPORTED_REFERENCE_CONTRACT/no resource data";
            case TECHNICAL_5XX -> "Actual upstream5xx and production consumer503";
            default -> "Controlled real-provider invalid wire fact observed, production consumer503";
        };
    }
}

