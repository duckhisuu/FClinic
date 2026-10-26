package com.fclinic.patientservice.infrastructure.adapter.reference;

import com.fclinic.patientservice.domain.model.PatientError;
import tools.jackson.core.*;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;
import java.util.Set;
import static com.fclinic.patientservice.domain.model.PatientError.Code.DEPENDENCY_UNAVAILABLE;

/** Local wire DTO validation only; never expose provider fields or parser exception text. */
final class ReferenceJson {
    static final long MAX_ID=9007199254740991L;
    static final JsonMapper JSON=JsonMapper.builder(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(8).maxStringLength(8192).maxNumberLength(20).build()).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private ReferenceJson() { }
    static JsonNode parse(byte[] bytes) {
        try { return JSON.readTree(bytes); }
        catch(RuntimeException e) { throw unavailable(); }
    }
    static void object(JsonNode n,Set<String> fields) { check(n!=null && n.isObject() && n.propertyNames().equals(fields)); }
    static long id(JsonNode n) {
        check(n.isIntegralNumber() && n.canConvertToLong()); long id=n.longValue(); requireId(id); return id;
    }
    static void requireId(long id) { check(id>0 && id<=MAX_ID); }
    static String string(JsonNode n,boolean nullable) {
        if(nullable && n.isNull()) return null;
        check(n.isString() && n.asText().length()<=8192); return n.asText();
    }
    static JsonNode body(int httpStatus,byte[] bytes,String missingCode) {
        JsonNode root=parse(bytes);
        if(httpStatus==404) {
            object(root,Set.of("status","message","data","code","fieldErrors"));
            status(root,404); check(root.path("data").isNull() && missingCode.equals(string(root.path("code"),false))
                    && root.path("fieldErrors").isArray() && root.path("fieldErrors").isEmpty());
            return null;
        }
        check(httpStatus==200); object(root,Set.of("status","message","data")); status(root,200);
        return root.path("data");
    }
    private static void status(JsonNode root,int expected) {
        check(root.path("status").isIntegralNumber() && root.path("status").canConvertToInt()
                && root.path("status").intValue()==expected);
        string(root.path("message"),false);
    }
    static void check(boolean ok) { if(!ok) throw unavailable(); }
    static PatientError unavailable() { return new PatientError(DEPENDENCY_UNAVAILABLE); }
}

