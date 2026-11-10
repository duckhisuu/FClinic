package com.fclinic.authservice.api.controller;

import com.fclinic.authservice.application.port.out.*;
import com.fclinic.authservice.application.service.ScopeAuthorityService;
import com.fclinic.authservice.infrastructure.adapter.ReceptionGrantStore;
import com.fclinic.authservice.infrastructure.config.ServiceClientCredentials;
import com.fclinic.authservice.domain.valueobject.RoleName;
import com.fclinic.common.web.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.*;
import java.util.*;

/** Trusted Patient provider supplies only IDs it has actually found. Browser UUID claims are rejected. */
@RestController
@RequestMapping(ReceptionAssignmentsController.PATH)
public class ReceptionAssignmentsController {
    public static final String PATH="/api/v1/auth/internal/reception-assignments";
    public static final String VERSION="patient-reception/1";
    public record Request(String contractVersion,UUID nonce,Instant requestedAt,ScopeAuthorityService.Caller caller,Set<UUID> patientIds,String action) {}
    private final UserRepositoryPort users; private final ScopeAssignmentPort assignments;
    private final ScopeAuthorityService scopes; private final ReceptionGrantStore store; private final Clock clock; private final String secret;
    public ReceptionAssignmentsController(UserRepositoryPort users,ScopeAssignmentPort assignments,ScopeAuthorityService scopes,ReceptionGrantStore store,Clock clock,
            @Value("${AUTH_RECEPTION_CLIENT_SECRET:}") String secret) {
        this.users=users;this.assignments=assignments;this.scopes=scopes;this.store=store;this.clock=clock;this.secret=secret;
    }
    @PostMapping @Transactional
    public ResponseEntity<?> grant(@RequestHeader(value="Authorization",required=false) String auth,@RequestBody Request request) {
        var out=ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-FClinic-Reception-Contract",VERSION);
        if(!ServiceClientCredentials.matches(auth,"patient-reception",secret)) return ResponseEntity.status(401).build();
        Instant now=clock.instant();
        if(request==null || !VERSION.equals(request.contractVersion()) || request.nonce()==null || request.nonce().version()!=4 || request.nonce().variant()!=2
                || request.requestedAt()==null || request.requestedAt().isBefore(now.minusSeconds(5)) || request.requestedAt().isAfter(now.plusSeconds(1))
                || request.caller()==null || request.patientIds()==null || request.patientIds().isEmpty() || request.patientIds().size()>100
                || request.patientIds().stream().anyMatch(id->id==null||id.version()!=4||id.variant()!=2)
                || request.action()==null || !Set.of("READ","SEARCH").contains(request.action())) return ResponseEntity.badRequest().build();
        UUID subject;
        try { subject=UUID.fromString(request.caller().subject()); } catch(Exception e) { return ResponseEntity.status(403).build(); }
        var user=users.findByKeycloakId(subject).orElse(null);
        if(user==null || !user.isActive() || user.getRoleName()!=RoleName.ROLE_STAFF) return ResponseEntity.status(403).build();
        String macro="SEARCH".equals(request.action())?"ADMIN_ASSIGNMENT_SEARCH":"ADMIN_ASSIGNMENT_READ";
        var decision=scopes.evaluate(new ScopeAuthorityService.Query("patient-work-scope/2",UUID.randomUUID(),now,"ADMINISTRATIVE",macro,request.caller(),null,null,null));
        if(!Boolean.TRUE.equals(decision.get("allowed"))) return ResponseEntity.status(403).build();
        var read=scopes.evaluate(new ScopeAuthorityService.Query("patient-work-scope/2",UUID.randomUUID(),now,"ADMINISTRATIVE","ADMIN_ASSIGNMENT_READ",request.caller(),null,null,null));
        if(!Boolean.TRUE.equals(read.get("allowed")))return ResponseEntity.status(403).build();
        boolean granted=false;
        record Plan(UUID source,Set<String> operations,Instant until) {}
        List<Plan> plans=new ArrayList<>();
        for(var entry:assignments.grants().entrySet()) {
            var g=entry.getValue();
            if(entry.getKey().version()!=4 || entry.getKey().variant()!=2 || !g.active() || g.allPatients() || !subject.toString().equals(g.subject())
                    || g.operations().stream().noneMatch(op->op.startsWith("ADMIN_ASSIGNMENT_"))
                    || g.validFrom()==null || g.validUntil()==null || !g.validFrom().isBefore(g.validUntil())
                    || now.isBefore(g.validFrom()) || !now.isBefore(g.validUntil())) continue;
            var ops=ReceptionGrantStore.administrativeOperations(g);
            if(ops.isEmpty()) continue;
            var until=g.validUntil().isBefore(now.plusSeconds(8*3600))?g.validUntil():now.plusSeconds(8*3600);
            plans.add(new Plan(entry.getKey(),ops,until));
            if(ops.contains("ADMIN_PROFILE_READ")) granted=true;
        }
        if(!granted) return ResponseEntity.status(403).build();
        for(var plan:plans) for(UUID patient:request.patientIds()) store.grant(user.getId(),subject,patient,plan.source(),plan.operations(),now,plan.until());
        return out.body(ApiResponse.success(Map.of("contractVersion",VERSION,"nonce",request.nonce(),"patientIds",request.patientIds())));
    }
}
