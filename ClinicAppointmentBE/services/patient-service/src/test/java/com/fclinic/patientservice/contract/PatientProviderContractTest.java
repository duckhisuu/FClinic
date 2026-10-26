package com.fclinic.patientservice.contract;

import com.fclinic.patientservice.api.controller.PatientController;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class PatientProviderContractTest {
    @Test void allEightRuntimeMappingsEqualCanonicalPathsAndMethods() throws Exception {
        var json=JsonMapper.builder().build();
        try(var in=getClass().getResourceAsStream("/contracts/patient.openapi.json")) {
            var paths=json.readTree(in).path("paths");
            Set<String> canonical=new TreeSet<>(), actual=new TreeSet<>();
            paths.properties().forEach(path -> path.getValue().properties().forEach(op -> {
                if(Set.of("get","post","put").contains(op.getKey())) canonical.add(op.getKey()+" "+path.getKey());
            }));
            String root=PatientController.class.getAnnotation(RequestMapping.class).value()[0];
            for(var method:PatientController.class.getDeclaredMethods()) {
                if(method.isAnnotationPresent(GetMapping.class)) add(actual,"get",root,method.getAnnotation(GetMapping.class).value());
                if(method.isAnnotationPresent(PostMapping.class)) add(actual,"post",root,method.getAnnotation(PostMapping.class).value());
                if(method.isAnnotationPresent(PutMapping.class)) add(actual,"put",root,method.getAnnotation(PutMapping.class).value());
            }
            assertThat(actual).hasSize(8).isEqualTo(canonical);
        }
    }
    @Test void fullValidatorChecksStructuredErrorsAndPatientFormatsAndBounds() {
        var json=JsonMapper.builder().build();
        var body=json.readTree("""
                {"status":400,"message":"Invalid request","data":null,"code":"VALIDATION_ERROR","fieldErrors":[]}
                """);
        ContractSchemas.patient(body,"Error");
        assertThatThrownBy(() -> ContractSchemas.patient(json.readTree("""
                {"status":400,"message":"Invalid request","data":null,"code":"VALIDATION_ERROR","fieldErrors":"wrong"}
                """),"Error")).isInstanceOf(AssertionError.class);
        var patient=json.readTree("""
                {"id":"11111111-1111-4111-8111-111111111111","patientCode":"PAT-11111111-1111-4111-8111-111111111111",
                "fullName":"Synthetic","phoneNumber":"+84900000001","email":null,"dateOfBirth":null,"gender":"UNKNOWN",
                "address":null,"status":"ACTIVE","version":0,"createdAt":"2026-10-20T03:00:00Z",
                "updatedAt":"2026-10-20T03:00:00Z","createdBy":"synthetic-subject","updatedBy":"synthetic-subject"}
                """);
        ContractSchemas.patient(patient,"Patient");
        var changed=((tools.jackson.databind.node.ObjectNode)patient).deepCopy();
        changed.put("phoneNumber","invalid");
        assertThatThrownBy(() -> ContractSchemas.patient(changed,"Patient")).isInstanceOf(AssertionError.class);
        changed.put("phoneNumber","+84900000001");changed.put("fullName","x".repeat(256));
        assertThatThrownBy(() -> ContractSchemas.patient(changed,"Patient")).isInstanceOf(AssertionError.class);
        changed.put("fullName","Synthetic");changed.put("dateOfBirth","2026-99-99");
        assertThatThrownBy(() -> ContractSchemas.patient(changed,"Patient")).isInstanceOf(AssertionError.class);
    }
    private static void add(Set<String> out,String verb,String root,String[] suffix) {
        out.add(verb+" "+root+(suffix.length==0?"":suffix[0]));
    }
}

