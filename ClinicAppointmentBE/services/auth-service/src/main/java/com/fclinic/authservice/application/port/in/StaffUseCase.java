package com.fclinic.authservice.application.port.in;

import com.fclinic.authservice.application.command.StaffCreatePatientCommand;
import com.fclinic.authservice.domain.aggregate.User;

public interface StaffUseCase {

    User lookupPatient(String query);

    User createPatientOnSite(StaffCreatePatientCommand command);
}

