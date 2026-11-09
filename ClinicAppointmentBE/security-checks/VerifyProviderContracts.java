import static org.mockito.Mockito.*;
import com.fclinic.appointmentservice.config.*;
import com.fclinic.appointmentservice.infrastructure.*;
import com.fclinic.appointmentservice.infrastructure.adapter.HttpAppointmentReferenceAdapter;
import com.fclinic.appointmentservice.infrastructure.security.*;
import com.fclinic.authservice.api.controller.AppointmentRecipientController;
import com.fclinic.authservice.application.service.AppointmentRecipientService;
import com.fclinic.authservice.application.port.out.UserRepositoryPort;
import com.fclinic.authservice.domain.aggregate.User;
import com.fclinic.authservice.domain.valueobject.*;
import com.fclinic.doctorservice.api.controller.InternalDoctorController;
import com.fclinic.doctorservice.api.DoctorReferenceFilter;
import com.fclinic.doctorservice.application.usecase.DoctorApplicationService;
import com.fclinic.doctorservice.application.port.out.*;
import com.fclinic.doctorservice.domain.aggregate.Doctor;
import com.fclinic.doctorservice.domain.model.TimeSlot;
import com.sun.net.httpserver.*;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Real producer MVC/filter -> TCP -> Appointment transport/decoder. Storage and IdP are doubles. */
public class VerifyProviderContracts {
  static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);System.out.println("PASS "+label);}
  public static void main(String[] args) throws Exception {
    UUID patient=UUID.randomUUID(); LocalDate date=LocalDate.now().plusDays(2);
    var doctor=Doctor.createNew("Synthetic Doctor","General","General","Degree",1,10.0,"Room",null,null);
    var dr=mock(DoctorRepositoryPort.class);var sr=mock(TimeSlotRepositoryPort.class);
    when(dr.findById(doctor.getId())).thenReturn(Optional.of(doctor));
    var visible=TimeSlot.createNew(doctor.getId(),date,LocalTime.of(9,0),LocalTime.of(10,0));
    var hidden=TimeSlot.createNew(doctor.getId(),date,LocalTime.of(10,0),LocalTime.of(11,0));hidden.setPublished(false);
    when(sr.findByDoctorIdAndSlotDateOrderByStartTimeAsc(doctor.getId(),date)).thenReturn(List.of(visible,hidden));
    when(sr.findByIdAndDoctorId(visible.getId(),doctor.getId())).thenReturn(Optional.of(visible));
    var service=new DoctorApplicationService(dr,sr);
    var doctorEnv=new MockEnvironment().withProperty("doctor.references.appointment-client-id","appointment")
        .withProperty("doctor.references.appointment-client-secret","doctor-secret")
        .withProperty("doctor.references.patient-client-id","patient").withProperty("doctor.references.patient-client-secret","patient-secret");
    var mvc=MockMvcBuilders.standaloneSetup(new InternalDoctorController(service,service,service))
        .setControllerAdvice(new com.fclinic.doctorservice.api.exception.GlobalExceptionHandler())
        .addFilters(new DoctorReferenceFilter(doctorEnv)).build();
    var users=mock(UserRepositoryPort.class);
    var account=User.create(UUID.randomUUID(),UUID.randomUUID(),"doctor","doctor@example.test",null,"Doctor Contact",
        new Role(UUID.randomUUID(),RoleName.ROLE_DOCTOR,"",Instant.now(),Instant.now()),null,doctor.getId(),null);
    when(users.findDoctorAccounts(doctor.getId())).thenReturn(List.of(account)); when(users.findPatientAccounts(patient)).thenReturn(List.of());
    var recipientEnv=new MockEnvironment().withProperty("app.recipients.client-id","recipient").withProperty("app.recipients.client-secret","recipient-secret");
    var recipientMvc=MockMvcBuilders.standaloneSetup(new AppointmentRecipientController(new AppointmentRecipientService(users),recipientEnv)).build();
    var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
    server.createContext("/",ex->{
      try {
        var path=ex.getRequestURI().toString();
        if(path.startsWith("/api/v3/patients/")) {
          if(!"Bearer patient-only".equals(ex.getRequestHeaders().getFirst("Authorization"))){ex.sendResponseHeaders(401,-1);ex.close();return;}
          var profile=new com.fclinic.patientservice.api.dto.PatientResponse(patient,"PAT-"+patient,"Current Patient","+84900000001","patient@example.test",null,
              com.fclinic.patientservice.domain.model.ProfileData.Gender.UNKNOWN,null,com.fclinic.patientservice.domain.model.ProfileData.Status.ACTIVE,0,Instant.now(),Instant.now(),null,null);
          byte[] bytes=AuthHttpTransport.JSON.writeValueAsBytes(com.fclinic.common.web.ApiResponse.success(profile));
          ex.getResponseHeaders().set("Content-Type","application/json");ex.sendResponseHeaders(200,bytes.length);try(var out=ex.getResponseBody()){out.write(bytes);}return;
        }
        boolean recipients=path.equals(AppointmentRecipientController.PATH);
        var request=recipients?post(path).contentType("application/json").content(ex.getRequestBody().readAllBytes()):get(path);
        ex.getRequestHeaders().forEach((key,values)->request.header(key,values.toArray()));
        var response=(recipients?recipientMvc:mvc).perform(request).andReturn().getResponse();
        for(String header:response.getHeaderNames())for(String value:response.getHeaders(header))ex.getResponseHeaders().add(header,value);
        byte[] bytes=response.getContentAsByteArray();ex.sendResponseHeaders(response.getStatus(),bytes.length);try(var out=ex.getResponseBody()){out.write(bytes);}
      } catch(Exception e){throw new java.io.IOException(e);}
    });server.start();
    try(var http=new ProviderHttp();var client=HttpClient.newHttpClient()) {
      String url="http://127.0.0.1:"+server.getAddress().getPort();
      var settings=new AppointmentSettings(new MockEnvironment().withProperty("appointment.doctor-url",url).withProperty("appointment.patient-url",url)
          .withProperty("appointment.recipient-url",url+AppointmentRecipientController.PATH));
      var tokens=mock(PatientServiceTokenProvider.class);when(tokens.token(anyLong())).thenReturn("patient-only");
      var adapter=new HttpAppointmentReferenceAdapter(http,settings,tokens,new ProviderCredentials("appointment","doctor-secret","recipient","recipient-secret"));
      check(adapter.doctor(doctor.getId(),Long.MAX_VALUE)!=null,"Protected Doctor profile -> Appointment decoder over TCP");
      var offers=adapter.offered(doctor.getId(),date,Long.MAX_VALUE);
      check(offers.size()==1&&offers.getFirst().id().equals(visible.getId())&&offers.getFirst().valid(),"Doctor published 60-minute offer -> Appointment over TCP");
      check(adapter.slot(doctor.getId(),visible.getId(),Long.MAX_VALUE).valid(),"Doctor published-slot lookup -> Appointment decoder over TCP");
      var recipients=adapter.recipients(patient,doctor.getId());
      check(recipients.getFirst().userId()==null&&recipients.getFirst().email().equals("patient@example.test")&&recipients.get(1).userId().equals(account.getId()),
          "Auth account provider + Patient contact -> Appointment recipients over TCP");
      var response=client.send(HttpRequest.newBuilder(URI.create(url+"/internal/v1/doctors/"+doctor.getId()))
          .header("Authorization",AuthHttpTransport.basic("patient","patient-secret")).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
      check(response.headers().firstValue("X-FClinic-Reference-Contract").orElse("").equals("patient-doctor-reference/2")
          &&new com.fclinic.patientservice.infrastructure.adapter.reference.DoctorReferenceDecoder().decode(response.statusCode(),response.body(),doctor.getId()).isPresent(),
          "Protected Doctor full projection -> Patient /2 decoder over TCP");
    } finally{server.stop(0);}
  }
}
