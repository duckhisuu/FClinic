package com.fclinic.authservice.application.port.out;

import com.fclinic.authservice.application.port.out.PatientOnboardingPort.Reservation;

public interface PatientProvisioningPort {
    /** Returns only after validating an ACTIVE profile proof matching every reservation identity. */
    void ensureProfile(Reservation reservation);
}
