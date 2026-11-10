package com.fclinic.authservice.infrastructure.adapter;

import com.fclinic.authservice.application.port.out.ScopeAssignmentPort.Grant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.*;

@Repository
public class ReceptionGrantStore {
    private final JdbcTemplate jdbc;
    public ReceptionGrantStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public record Stored(UUID id, UUID source, Grant grant) {}
    public List<Stored> load() {
        return jdbc.query("select * from reception_patient_assignments", (r,n) -> new Stored(
            r.getObject("id",UUID.class), r.getObject("source_assignment_id",UUID.class),
            new Grant(r.getObject("subject",UUID.class).toString(), Set.copyOf(Arrays.asList((String[])r.getArray("operations").getArray())),
                Set.of(r.getObject("patient_id",UUID.class)),false,true,r.getTimestamp("valid_from").toInstant(),
                r.getTimestamp("valid_until").toInstant(),null,null,null)));
    }
    public void grant(UUID user, UUID subject, UUID patient, UUID source, Set<String> operations, Instant from, Instant until) {
        jdbc.update("""
            insert into reception_patient_assignments(id,user_id,subject,patient_id,source_assignment_id,operations,valid_from,valid_until)
            values (?,?,?,?,?,string_to_array(?,','),?,?)
            on conflict(user_id,patient_id,source_assignment_id) do update
            set operations=excluded.operations,valid_from=least(reception_patient_assignments.valid_from,excluded.valid_from),valid_until=excluded.valid_until
            """, UUID.randomUUID(),user,subject,patient,source,String.join(",",operations),
            java.sql.Timestamp.from(from.truncatedTo(java.time.temporal.ChronoUnit.MICROS)),
            java.sql.Timestamp.from(until.truncatedTo(java.time.temporal.ChronoUnit.MICROS)));
    }
    /** Permissions derived only from the explicit administrative desk assignment. */
    public static Set<String> administrativeOperations(Grant source) {
        Set<String> result=new HashSet<>();
        if(source.operations().contains("ADMIN_ASSIGNMENT_READ")) result.add("ADMIN_PROFILE_READ");
        if(source.operations().contains("ADMIN_ASSIGNMENT_UPDATE")) result.add("ADMIN_PROFILE_UPDATE");
        if(source.operations().contains("ADMIN_ASSIGNMENT_SEARCH")) result.add("ADMIN_SEARCH");
        for(String op:Set.of("APPOINTMENT_CREATE","APPOINTMENT_READ","APPOINTMENT_SEARCH","APPOINTMENT_CANCEL","APPOINTMENT_RESCHEDULE"))
            if(source.operations().contains(op)) result.add(op);
        return Set.copyOf(result);
    }
}
