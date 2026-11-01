package com.fclinic.patientservice.domain.model;
import java.util.UUID;

/** Canonical lowercase UUIDv4 business identifier. Never converts legacy numeric IDs. */
public final class BusinessId {
    private BusinessId() { }
    public static UUID parse(Object raw) {
        if (!(raw instanceof String value) || !value.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"))
            throw new IllegalArgumentException("UUIDv4 required");
        return UUID.fromString(value);
    }
    public static boolean valid(UUID id) { return id != null && id.version() == 4 && id.variant() == 2; }
}
