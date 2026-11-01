package com.fclinic.patientservice;
import java.util.UUID;
/** Synthetic UUID fixture labels ONLY; never a legacy-data migration mapping. */
public final class UuidFixtures {
    private UuidFixtures() { }
    public static UUID fixture(int label) {
        return UUID.fromString("550e8400-e29b-41d4-a716-" + String.format("%012x", label));
    }
}
