package com.fclinic.patientservice.contract;

import com.fclinic.patientservice.infrastructure.adapter.reference.ReferenceSettings;
import tools.jackson.core.*;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.jar.JarFile;

/** Explicit external real-binary provenance + synthetic fixture preflight. No environment values in diagnostics. */
final class ProviderContractPlan {
    static final JsonMapper JSON=JsonMapper.builder(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(12).maxStringLength(8192).build()).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    enum Case {
        ACTIVE, INACTIVE, VALID, CANCELLED, MISSING, PATIENT_MISMATCH, DOCTOR_MISMATCH,
        INVALID_CREDENTIAL, WRONG_VERSION_REQUEST, WRONG_RESOURCE, UNKNOWN_STATUS,
        WRONG_VERSION_RESPONSE, TECHNICAL_5XX, INVALID_PAYLOAD
    }
    record Vector(Case scenario,long resourceId,UUID patientId,Long doctorId) { }
    final boolean doctor;
    final ReferenceSettings.Endpoint endpoint,invalidCredentialEndpoint;
    final List<Vector> vectors;
    final String binaryHash,deploymentHash,vectorHash,endpointHash,sourceCommit;
    private ProviderContractPlan(boolean doctor,ReferenceSettings.Endpoint endpoint,ReferenceSettings.Endpoint invalid,
            List<Vector> vectors,String binaryHash,String deploymentHash,String vectorHash,String endpointHash,String sourceCommit) {
        this.doctor=doctor;this.endpoint=endpoint;this.invalidCredentialEndpoint=invalid;this.vectors=vectors;
        this.binaryHash=binaryHash;this.deploymentHash=deploymentHash;this.vectorHash=vectorHash;
        this.endpointHash=endpointHash;this.sourceCommit=sourceCommit;
    }
    static ProviderContractPlan load(Map<String,String> env) {
        try {
            boolean doctor=switch(required(env,"BOUNDARY")) { case "B-04" -> true; case "B-05" -> false; default -> throw blocked(); };
            URI uri=URI.create(required(env,"COLLECTION_URI"));
            String expectedPath=doctor?"/api/v1/doctors":"/internal/v1/appointments";
            check(uri.getPath().equals(expectedPath));
            Path trust=optional(env,"TRUST_STORE")==null?null:Path.of(optional(env,"TRUST_STORE"));
            var endpoint=new ReferenceSettings.Endpoint(true,uri,required(env,"CLIENT_ID"),required(env,"CLIENT_SECRET"),
                    Duration.ofSeconds(1),Duration.ofSeconds(3),65536,1,trust,optional(env,"TRUST_STORE_PASSWORD"));
            endpoint.validate();
            var invalid=new ReferenceSettings.Endpoint(true,uri,required(env,"INVALID_CLIENT_ID"),required(env,"INVALID_CLIENT_SECRET"),
                    endpoint.connectTimeout(),endpoint.exchangeTimeout(),65536,1,trust,endpoint.trustStorePassword());
            invalid.validate(); check(!invalid.clientId().equals(endpoint.clientId()) && !invalid.clientSecret().equals(endpoint.clientSecret()));
            Path binary=Path.of(required(env,"BINARY_PATH"));
            String binaryHash=hash(Files.readAllBytes(binary));
            // Require an actual provider executable JAR, not a generated test server. Deployment linkage remains operator-attested.
            try(var jar=new JarFile(binary.toFile())) {
                String name=doctor?"doctorservice/api/controller/DoctorController":"appointmentservice/api/controller/AppointmentController";
                check(jar.getEntry("BOOT-INF/classes/com/fclinic/"+name+".class")!=null);
                check(jar.stream().noneMatch(e -> e.getName().contains("TestReferenceServer") || e.getName().contains("TestAuthServer")));
            }
            Path provenance=Path.of(required(env,"DEPLOYMENT_EVIDENCE_PATH"));
            byte[] provenanceBytes=bounded(provenance);
            JsonNode proof=JSON.readTree(provenanceBytes);
            exact(proof,Set.of("providerKind","isolatedSynthetic","binarySha256","endpointSha256","sourceCommit","deploymentId"));
            String endpointHash=hash(uri.toASCIIString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            check("REAL_BINARY".equals(proof.path("providerKind").asText()) && proof.path("isolatedSynthetic").isBoolean()
                    && proof.path("isolatedSynthetic").booleanValue() && binaryHash.equals(proof.path("binarySha256").asText())
                    && endpointHash.equals(proof.path("endpointSha256").asText())
                    && proof.path("sourceCommit").asText().matches("[0-9a-f]{40}")
                    && proof.path("deploymentId").asText().matches("[0-9a-f-]{36}"));
            Path vectorPath=Path.of(required(env,"VECTOR_PATH")); byte[] bytes=bounded(vectorPath);
            JsonNode root=JSON.readTree(bytes); exact(root,Set.of("version","boundary","vectors"));
            check(root.path("version").isIntegralNumber() && root.path("version").intValue()==1
                    && required(env,"BOUNDARY").equals(root.path("boundary").asText()) && root.path("vectors").isArray());
            List<Vector> vectors=new ArrayList<>(); Set<Case> seen=EnumSet.noneOf(Case.class);
            for(var node:root.path("vectors")) {
                exact(node,Set.of("case","resourceId","patientId","doctorId"));
                Case scenario=Case.valueOf(node.path("case").asText()); check(seen.add(scenario));
                long id=safeId(node.path("resourceId")); UUID patient=null; Long doctorId=null;
                if(!doctor && Set.of(Case.VALID,Case.CANCELLED,Case.PATIENT_MISMATCH,Case.DOCTOR_MISMATCH).contains(scenario)) {
                    String value=node.path("patientId").asText();
                    check(value.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"));
                    patient=UUID.fromString(value); doctorId=safeId(node.path("doctorId"));
                } else check(node.path("patientId").isNull() && node.path("doctorId").isNull());
                if(doctor) check(!Set.of(Case.VALID,Case.CANCELLED,Case.PATIENT_MISMATCH,Case.DOCTOR_MISMATCH,Case.UNKNOWN_STATUS).contains(scenario));
                else check(!Set.of(Case.ACTIVE,Case.INACTIVE).contains(scenario));
                vectors.add(new Vector(scenario,id,patient,doctorId));
            }
            Set<Case> mandatory=doctor?EnumSet.of(Case.ACTIVE,Case.INACTIVE,Case.MISSING,Case.INVALID_CREDENTIAL,Case.WRONG_VERSION_REQUEST):
                    EnumSet.of(Case.VALID,Case.CANCELLED,Case.MISSING,Case.PATIENT_MISMATCH,Case.DOCTOR_MISMATCH,Case.INVALID_CREDENTIAL,Case.WRONG_VERSION_REQUEST);
            check(seen.containsAll(mandatory));
            return new ProviderContractPlan(doctor,endpoint,invalid,List.copyOf(vectors),binaryHash,hash(provenanceBytes),hash(bytes),
                    endpointHash,proof.path("sourceCommit").asText());
        } catch(Exception e) { throw blocked(); }
    }
    static String required(Map<String,String> env,String key) {
        String value=optional(env,key); if(value==null || value.isBlank()) throw blocked(); return value;
    }
    static String optional(Map<String,String> env,String key) { return env.get("PATIENT_REAL_PROVIDER_"+key); }
    static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch(Exception e) { throw blocked(); }
    }
    static void exact(JsonNode n,Set<String> fields) { check(n!=null && n.isObject() && n.propertyNames().equals(fields)); }
    static void check(boolean ok) { if(!ok) throw blocked(); }
    static long safeId(JsonNode n) { check(n.isIntegralNumber() && n.canConvertToLong()); long v=n.longValue(); check(v>0 && v<=9007199254740991L); return v; }
    static byte[] bounded(Path path) throws Exception { check(Files.size(path)<=1048576); return Files.readAllBytes(path); }
    static IllegalStateException blocked() { return new IllegalStateException("Real-provider preflight blocked; see runbook. No credential/URL details emitted."); }
}

