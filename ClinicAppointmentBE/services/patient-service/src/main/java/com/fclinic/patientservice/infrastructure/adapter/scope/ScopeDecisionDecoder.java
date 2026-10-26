package com.fclinic.patientservice.infrastructure.adapter.scope;

import com.fclinic.patientservice.domain.model.PatientError;
import tools.jackson.core.*;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;
import java.time.*;
import java.util.*;
import static com.fclinic.patientservice.domain.model.PatientError.Code.DEPENDENCY_UNAVAILABLE;

public final class ScopeDecisionDecoder {
    public record Decision(boolean allowed, Set<UUID> patientIds) {
        public Decision { patientIds=Set.copyOf(patientIds); }
    }
    static final JsonMapper JSON=JsonMapper.builder(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(8).maxStringLength(8192).maxNumberLength(20).build()).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).findAndAddModules().build();
    private static final Set<String> ROOT=Set.of("contractVersion","request","evaluatedAt","allowed","patientIds","evidence");
    private static final Set<String> EVIDENCE=Set.of("assignmentId","active","validFrom","validUntil","visitId");
    private final Clock clock;
    public ScopeDecisionDecoder(Clock clock) { this.clock=clock; }

    public Decision decode(byte[] bytes,ScopeQuery query,int maxPatients) {
        try {
            JsonNode root=JSON.readTree(bytes);
            object(root,ROOT);
            check(ScopeQuery.VERSION.equals(text(root.path("contractVersion"),64)));
            // Full structural echo (nonce, exact operation, principal and resource/visit bindings).
            check(root.path("request").equals(JSON.readTree(JSON.writeValueAsBytes(query.wire()))));
            Instant now=clock.instant(), evaluated=instant(root.path("evaluatedAt"));
            check(!evaluated.isBefore(query.requestedAt().minusSeconds(1)) && !evaluated.isAfter(now.plusSeconds(1)));
            check(root.path("allowed").isBoolean());
            if(!root.path("allowed").asBoolean()) {
                check(root.path("evidence").isNull() && root.path("patientIds").isNull());
                return new Decision(false,Set.of());
            }
            JsonNode evidence=root.path("evidence");
            object(evidence,EVIDENCE);
            text(evidence.path("assignmentId"),255);
            check(evidence.path("active").isBoolean());
            Instant from=instant(evidence.path("validFrom")), until=instant(evidence.path("validUntil"));
            check(from.isBefore(until));
            JsonNode visit=evidence.path("visitId");
            if(query.operation()==ScopeQuery.Operation.VALID_VISIT) text(visit,255);
            else check(visit.isNull());
            Set<UUID> patients=new HashSet<>();
            var ids=root.path("patientIds");
            if(query.search()) {
                check(ids.isArray() && ids.size()<=maxPatients);
                for(var id:ids) {
                    String value=text(id,36);
                    check(value.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"));
                    check(patients.add(UUID.fromString(value)));
                }
            } else check(ids.isNull());
            boolean current=evidence.path("active").asBoolean() && !now.isBefore(from) && now.isBefore(until);
            return new Decision(current,current?patients:Set.of());
        } catch(PatientError e) { throw e; }
        catch(RuntimeException e) { throw new PatientError(DEPENDENCY_UNAVAILABLE); }
    }
    private static Instant instant(JsonNode n) {
        String value=text(n,40); check(value.endsWith("Z")); return Instant.parse(value);
    }
    private static String text(JsonNode n,int max) {
        check(n!=null && n.isString()); String v=n.asText();
        check(!v.isBlank() && v.length()<=max && v.chars().noneMatch(Character::isISOControl)); return v;
    }
    private static void object(JsonNode n,Set<String> fields) {
        check(n!=null && n.isObject() && n.propertyNames().equals(fields));
    }
    private static void check(boolean condition) { if(!condition) throw new PatientError(DEPENDENCY_UNAVAILABLE); }
}

