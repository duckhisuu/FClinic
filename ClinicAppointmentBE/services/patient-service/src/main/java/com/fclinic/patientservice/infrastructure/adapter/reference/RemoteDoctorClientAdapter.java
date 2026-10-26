package com.fclinic.patientservice.infrastructure.adapter.reference;

import com.fclinic.patientservice.application.port.out.DoctorClientPort;
import java.util.Optional;

public final class RemoteDoctorClientAdapter implements DoctorClientPort,AutoCloseable {
    private final ReferenceHttpClient client;
    private final DoctorReferenceDecoder decoder=new DoctorReferenceDecoder();
    public RemoteDoctorClientAdapter(ReferenceSettings.Endpoint settings) {
        client=new ReferenceHttpClient(settings,ReferenceHttpClient.Profile.DOCTOR);
    }
    public Optional<DoctorReference> find(long id) {
        var response=client.get(id); return decoder.decode(response.status(),response.body(),id);
    }
    public void close() { client.close(); }
}

