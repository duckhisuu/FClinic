package com.fclinic.patientservice.infrastructure.adapter.reference;

import com.fclinic.patientservice.application.port.out.AppointmentClientPort;
import java.util.Optional;

public final class RemoteAppointmentClientAdapter implements AppointmentClientPort,AutoCloseable {
    private final ReferenceHttpClient client;
    private final AppointmentReferenceDecoder decoder=new AppointmentReferenceDecoder();
    public RemoteAppointmentClientAdapter(ReferenceSettings.Endpoint settings) {
        client=new ReferenceHttpClient(settings,ReferenceHttpClient.Profile.APPOINTMENT);
    }
    public Optional<AppointmentReference> find(long id) {
        var response=client.get(id); return decoder.decode(response.status(),response.body(),id);
    }
    public void close() { client.close(); }
}

