package com.fclinic.patientservice;
import com.fclinic.patientservice.application.port.out.*;
import com.fclinic.patientservice.application.security.*;
import com.fclinic.patientservice.application.usecase.*;
import com.fclinic.patientservice.domain.aggregate.Patient;
import com.fclinic.patientservice.domain.model.*;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PatientOrchestrationTest {
    private final PatientRepositoryPort repo=mock(PatientRepositoryPort.class);
    private final PatientWriter writer=mock(PatientWriter.class);
    private final WorkScopePort scopes=mock(WorkScopePort.class,CALLS_REAL_METHODS);
    private final DoctorClientPort doctors=mock(DoctorClientPort.class);
    private final AppointmentClientPort appointments=mock(AppointmentClientPort.class);
    private final PatientApplicationService service=new PatientApplicationService(repo,writer,new PatientPolicy(scopes),doctors,appointments);
    private final UUID id=UUID.randomUUID();
    private final TrustedCaller doctor=new TrustedCaller("user:demo-doctor",TrustedCaller.PrincipalType.USER,
            Set.of("ROLE_DOCTOR"),null,12L,null,true);
    private final ClinicalData clinical=new ClinicalData(Instant.parse("2026-10-19T03:00:00Z"),"Simulated","Simulated",null,null);
    @BeforeEach void setup() {
        when(scopes.clinical(doctor,id)).thenReturn(true);
        when(scopes.validVisit(eq(doctor),eq(id),any(),eq(clinical.visitDate()))).thenReturn(true);
        var data=new ProfileData("Demo","+84900000001",null,null,ProfileData.Gender.UNKNOWN,null,ProfileData.Status.ACTIVE);
        when(repo.find(id)).thenReturn(Optional.of(Patient.create(data,"user:demo-staff",Instant.EPOCH)));
        when(doctors.find(12)).thenReturn(Optional.of(new DoctorClientPort.DoctorReference(12,true)));
    }
    @Test void tc10_walkInSkipsOnlyAppointmentAndValidatesBeforeWriter() {
        service.createRecord(doctor,id,12,null,clinical);
        verifyNoInteractions(appointments);
        var order=inOrder(scopes,doctors,writer);
        order.verify(scopes).clinical(doctor,id);
        order.verify(scopes).validVisit(doctor,id,null,clinical.visitDate());
        order.verify(doctors).find(12);
        order.verify(writer).createRecord(id,12,null,clinical,doctor.subject());
    }
    @Test void tc12_linkedMismatchIs422AndDoesNotWrite() {
        when(appointments.find(102)).thenReturn(Optional.of(new AppointmentClientPort.AppointmentReference(102,UUID.randomUUID(),12,false)));
        assertError(PatientError.Code.REFERENCE_MISMATCH,() -> service.createRecord(doctor,id,12,102L,clinical));
        verifyNoInteractions(writer);
    }
    @Test void tc19_technicalDependencyFailureIs503AndNoWrite() {
        when(doctors.find(12)).thenThrow(new IllegalStateException("technical failure"));
        assertError(PatientError.Code.DEPENDENCY_UNAVAILABLE,() -> service.createRecord(doctor,id,12,null,clinical));
        verifyNoInteractions(writer,appointments);
    }
    @Test void tc12_missingOrInactiveDoctorIs422() {
        when(doctors.find(12)).thenReturn(Optional.empty());
        assertError(PatientError.Code.REFERENCE_MISMATCH,() -> service.createRecord(doctor,id,12,null,clinical));
        when(doctors.find(12)).thenReturn(Optional.of(new DoctorClientPort.DoctorReference(12,false)));
        assertError(PatientError.Code.REFERENCE_MISMATCH,() -> service.createRecord(doctor,id,12,null,clinical));
        verifyNoInteractions(writer);
    }
    @Test void tc35_missingScopeFailsBeforeProvidersOrWriter() {
        when(scopes.clinical(doctor,id)).thenReturn(false);
        assertError(PatientError.Code.FORBIDDEN,() -> service.createRecord(doctor,id,12,null,clinical));
        verifyNoInteractions(doctors,appointments,writer);
    }
    private void assertError(PatientError.Code code,Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(PatientError.class,e -> assertThat(e.code()).isEqualTo(code));
    }
}
