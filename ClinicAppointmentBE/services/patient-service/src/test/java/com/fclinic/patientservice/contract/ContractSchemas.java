package com.fclinic.patientservice.contract;

import com.networknt.schema.*;
import com.networknt.schema.dialect.Dialects;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Test-only Draft2020-12 assertions; never include instance values in failure reports. */
public final class ContractSchemas {
    private static final JsonMapper JSON=JsonMapper.builder().build();
    private static final SchemaRegistry REGISTRY=SchemaRegistry.withDialect(Dialects.getDraft202012());
    private static final Map<String,Schema> SCHEMAS=new ConcurrentHashMap<>();
    private ContractSchemas() { }
    public static void patient(JsonNode body,String name) {
        var schema=SCHEMAS.computeIfAbsent(name,key -> {
            var document=JSON.readTree(resource("patient.openapi.json"));
            var root=JSON.createObjectNode();
            root.set("$defs",document.at("/components/schemas"));
            root.put("$ref","#/$defs/"+key);
            return REGISTRY.getSchema(root.toString().replace("#/components/schemas/","#/$defs/"),InputFormat.JSON);
        });
        validate(schema,body);
    }
    public static void reference(byte[] body,boolean doctor) {
        String file=doctor?"patient-doctor-reference-v1.schema.json":"patient-appointment-medical-reference-v1.schema.json";
        var schema=SCHEMAS.computeIfAbsent(file,key -> REGISTRY.getSchema(resource(key),InputFormat.JSON));
        validate(schema,JSON.readTree(body));
    }
    private static void validate(Schema schema,JsonNode body) {
        var errors=schema.validate(body.toString(),InputFormat.JSON,context ->
                context.executionConfig(config -> config.formatAssertionsEnabled(true)));
        if(!errors.isEmpty()) throw new AssertionError("Contract schema violation; count="+errors.size());
    }
    private static String resource(String name) {
        try(var in=ContractSchemas.class.getResourceAsStream("/contracts/"+name)) {
            if(in==null) throw new IllegalStateException();
            return new String(in.readAllBytes(),StandardCharsets.UTF_8);
        } catch(Exception e) { throw new IllegalStateException("Contract resource unavailable"); }
    }
}

