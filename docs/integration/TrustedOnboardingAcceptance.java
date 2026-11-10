import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.postgresql.ds.PGSimpleDataSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** Real packaged Auth/Patient/Doctor/Appointment + real configured Keycloak; all business DBs are temporary. */
class TrustedOnboardingAcceptance {
    static final JsonMapper JSON=JsonMapper.builder().build();
    static final HttpClient HTTP=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    static final List<Process> APPS=new ArrayList<>();
    static final List<String> CHECKS=new ArrayList<>();
    static final List<String> CREATED_IDENTITIES=new ArrayList<>();
    static final String NONCE=UUID.randomUUID().toString().substring(0,8);
    static Path repo, logs;
    static String kc, realm, issuer;
    static int authPort, patientPort, doctorPort, appointmentPort;
    static String env(String key,String fallback) {String v=System.getenv(key);return v==null || v.isBlank()?fallback:v;}
    static void check(boolean ok,String name) {if(!ok)throw new AssertionError(name);CHECKS.add(name);System.out.println("PASS "+name);}
    static int port() throws Exception {try(var socket=new ServerSocket(0,0,InetAddress.getLoopbackAddress())){return socket.getLocalPort();}}
    static String url(int port,String path){return "http://localhost:"+port+path;}
    static HttpResponse<String> request(String method,String url,Object body,String bearer,String key) throws Exception {
        var b=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).header("Content-Type","application/json");
        if(bearer!=null)b.header("Authorization","Bearer "+bearer);if(key!=null)b.header("Idempotency-Key",key);
        return HTTP.send(b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
    }
    static JsonNode data(HttpResponse<String> response,int expected) {
        if(response.statusCode()!=expected)throw new AssertionError("Expected HTTP "+expected+", received "+response.statusCode()+" code="+JSON.readTree(response.body()).path("code").asText());
        return JSON.readTree(response.body()).path("data");
    }
    static String form(Map<String,String> fields) {return fields.entrySet().stream().map(e->URLEncoder.encode(e.getKey(),StandardCharsets.UTF_8)+"="+URLEncoder.encode(e.getValue(),StandardCharsets.UTF_8)).collect(java.util.stream.Collectors.joining("&"));}
    static JsonNode token(Map<String,String> fields,String targetRealm) throws Exception {
        var r=HTTP.send(HttpRequest.newBuilder(URI.create(kc+"/realms/"+targetRealm+"/protocol/openid-connect/token"))
            .header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form(fields))).build(),HttpResponse.BodyHandlers.ofString());
        if(r.statusCode()!=200)throw new AssertionError("Keycloak token request failed: "+r.statusCode());return JSON.readTree(r.body());
    }
    static PGSimpleDataSource database(EmbeddedPostgres pg,String database) {
        var ds=new PGSimpleDataSource();ds.setURL(pg.getJdbcUrl("postgres",database));ds.setUser("postgres");ds.setPassword("postgres");return ds;
    }
    static void sql(PGSimpleDataSource ds,String value) throws Exception {try(var c=ds.getConnection();var s=c.createStatement()){s.execute(value);}}
    static int count(PGSimpleDataSource ds,String table) throws Exception {try(var c=ds.getConnection();var s=c.createStatement();var rs=s.executeQuery("SELECT count(*) FROM "+table)){rs.next();return rs.getInt(1);}}
    static Map<String,String> settings() {return new HashMap<>();}
    static void launch(String module,int port,PGSimpleDataSource db,Map<String,String> environment,String...extra) throws Exception {
        String java=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString();
        var args=new ArrayList<>(List.of(java,"-Xmx256m","-jar",repo.resolve("ClinicAppointmentBE/services/"+module+"/target/"+module+"-1.0.0-SNAPSHOT.jar").toString(),
            "--server.port="+port,"--server.address=127.0.0.1","--spring.config.import=","--eureka.client.enabled=false","--spring.cloud.discovery.enabled=false",
            "--spring.datasource.url="+db.getURL(),"--spring.datasource.username=postgres","--spring.datasource.password=postgres",
            "--management.health.mail.enabled=false","--management.health.rabbit.enabled=false","--management.health.redis.enabled=false"));
        args.addAll(List.of(extra));var builder=new ProcessBuilder(args).directory(repo.toFile()).redirectErrorStream(true).redirectOutput(logs.resolve(module+".log").toFile());
        builder.environment().putAll(environment);Process process=builder.start();APPS.add(process);
        long deadline=System.nanoTime()+Duration.ofSeconds(60).toNanos();
        while(process.isAlive() && System.nanoTime()<deadline){
            try {if(request("GET",url(port,"/actuator/health"),null,null,null).statusCode()==200)return;}catch(Exception ignored){}
            Thread.sleep(200);
        }
        throw new AssertionError(module+" failed readiness; inspect test-owned log "+logs.resolve(module+".log"));
    }
    static JsonNode register(String suffix) throws Exception {
        String name="codex_flow_"+NONCE+"_"+suffix;
        var registered=data(request("POST",url(authPort,"/api/v1/auth/register"),Map.of("username",name,"password","SyntheticFlow@123!","fullName","Synthetic Flow "+suffix,"email",name+"@example.test"),null,null),201);
        CREATED_IDENTITIES.add(registered.path("user").path("keycloakId").asText());
        var logged=data(request("POST",url(authPort,"/api/v1/auth/login"),Map.of("username",name,"password","SyntheticFlow@123!"),null,null),200);
        check(registered.path("user").path("id").asText().equals(logged.path("user").path("id").asText()),"real registration and login preserve account "+suffix);
        return logged;
    }
    static void cleanupIdentities() throws Exception {
        if(CREATED_IDENTITIES.isEmpty())return;
        String admin=token(Map.of("grant_type","password","client_id","admin-cli","username",env("KEYCLOAK_ADMIN_USER","admin"),
            "password",env("KEYCLOAK_ADMIN_PASSWORD","admin")),env("KEYCLOAK_ADMIN_REALM","master")).path("access_token").asText();
        for(String id:CREATED_IDENTITIES){
            // Delete only accounts returned by this run, after verifying the test-specific username prefix.
            var found=request("GET",kc+"/admin/realms/"+realm+"/users/"+id,null,admin,null);
            if(found.statusCode()==404)continue;
            if(found.statusCode()!=200 || !JSON.readTree(found.body()).path("username").asText().startsWith("codex_flow_"+NONCE+"_"))
                throw new AssertionError("Refusing to clean an identity not owned by this acceptance run");
            check(request("DELETE",kc+"/admin/realms/"+realm+"/users/"+id,null,admin,null).statusCode()==204,"cleaned test-owned Keycloak identity");
        }
    }
    public static void main(String[] args) throws Exception {
        repo=Path.of(args[0]).toAbsolutePath();logs=Files.createTempDirectory("fclinic-onboarding-acceptance-");
        kc=env("KEYCLOAK_BASE_URL","http://localhost:8180").replaceAll("/$","");realm=env("KEYCLOAK_REALM","fclinic");issuer=kc+"/realms/"+realm;
        authPort=port();patientPort=port();doctorPort=port();appointmentPort=port();
        var shared=settings();
        shared.put("DB_USERNAME","postgres");shared.put("DB_PASSWORD","postgres");shared.put("EUREKA_ENABLED","false");
        shared.put("KEYCLOAK_ISSUER_URI",issuer);shared.put("AUTH_INTROSPECTION_ISSUER",issuer);
        shared.put("AUTH_PATIENT_ONBOARDING_URI",url(patientPort,"/internal/v1/patient-onboarding"));
        shared.put("AUTH_PATIENT_ONBOARDING_CLIENT_SECRET",UUID.randomUUID().toString());
        shared.put("PATIENT_AUTH_INTROSPECTION_URI",url(authPort,"/api/v1/auth/introspect"));shared.put("PATIENT_AUTH_ISSUER",issuer);
        String patientSecret=UUID.randomUUID().toString();shared.put("PATIENT_AUTH_CLIENT_SECRET",patientSecret);shared.put("AUTH_INTROSPECTION_CLIENT_SECRET",patientSecret);
        String appointmentSecret=UUID.randomUUID().toString();shared.put("APPOINTMENT_AUTH_CLIENT_SECRET",appointmentSecret);
        shared.put("APPOINTMENT_AUTH_INTROSPECTION_URI",url(authPort,"/api/v1/auth/introspect"));shared.put("APPOINTMENT_SCOPE_URI",url(authPort,"/api/v1/auth/scopes"));
        shared.put("APPOINTMENT_SCOPE_CLIENT_SECRET",UUID.randomUUID().toString());
        shared.put("APPOINTMENT_DOCTOR_CLIENT_SECRET",UUID.randomUUID().toString());
        shared.put("APPOINTMENT_PATIENT_URL",url(patientPort,""));shared.put("APPOINTMENT_DOCTOR_URL",url(doctorPort,""));
        shared.put("APPOINTMENT_PATIENT_TOKEN_URI",issuer+"/protocol/openid-connect/token");
        String serviceToken=token(Map.of("grant_type","client_credentials","client_id",env("APPOINTMENT_PATIENT_TOKEN_CLIENT_ID","appointment-service"),
            "client_secret",env("APPOINTMENT_PATIENT_TOKEN_CLIENT_SECRET","appointment-service-secret-dev")),realm).path("access_token").asText();
        String subject=JSON.readTree(Base64.getUrlDecoder().decode(serviceToken.split("\\.")[1])).path("sub").asText();
        shared.put("APPOINTMENT_SERVICE_SUBJECT",subject);
        try(var pg=EmbeddedPostgres.builder().setPort(0).setDataDirectory(logs.resolve("data"))
            .setOverrideWorkingDirectory(logs.resolve("bin").toFile()).setCleanDataDirectory(true).start()) {
            try {
                for(String name:List.of("auth_flow","patient_flow","doctor_flow","appointment_flow"))sql(database(pg,"postgres"),"CREATE DATABASE "+name);
                var authDb=database(pg,"auth_flow");var patientDb=database(pg,"patient_flow");var doctorDb=database(pg,"doctor_flow");var appointmentDb=database(pg,"appointment_flow");
                sql(patientDb,Files.readString(repo.resolve("ClinicAppointmentBE/services/patient-service/src/main/resources/db/schema/uuid/V001__patient_uuid_empty.sql")));
                sql(patientDb,Files.readString(repo.resolve("ClinicAppointmentBE/services/patient-service/src/main/resources/db/schema/uuid/V002__patient_onboarding.sql")));
                sql(doctorDb,Files.readString(repo.resolve("ClinicAppointmentBE/services/doctor-service/src/main/resources/db/schema/V001__doctor_uuid_empty.sql")));
                sql(doctorDb,Files.readString(repo.resolve("ClinicAppointmentBE/services/doctor-service/src/main/resources/db/schema/V002__doctor_booking_constraints.sql")));
                launch("auth-service",authPort,authDb,shared);
                launch("patient-service",patientPort,patientDb,shared,"--patient.scope.enabled=false","--patient.reference.doctor.enabled=false","--patient.reference.appointment.enabled=false");
                launch("doctor-service",doctorPort,doctorDb,shared,"--spring.jpa.hibernate.ddl-auto=validate");
                launch("appointment-service",appointmentPort,appointmentDb,shared,"--appointment.outbox-enabled=false","--appointment.reminder-lead=");
                var first=register("a");String bearer=first.path("accessToken").asText();String account=first.path("user").path("id").asText();
                var profile=Map.of("fullName","Synthetic Patient A","phoneNumber","+84900000001","dateOfBirth","1995-01-01","gender","OTHER","address","Synthetic acceptance");
                JsonNode owner;
                try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
                    Callable<JsonNode> submit=()->data(request("POST",url(authPort,"/api/v1/auth/me/patient-onboarding"),profile,bearer,null),200);
                    var a=executor.submit(submit);var b=executor.submit(submit);owner=a.get(30,TimeUnit.SECONDS);var other=b.get(30,TimeUnit.SECONDS);
                    check(owner.path("patientId").asText().equals(other.path("patientId").asText()),"two concurrent onboarding requests return one Patient UUID");
                }
                String patient=owner.path("patientId").asText();check(owner.path("id").asText().equals(account),"onboarding binds the authenticated account");
                check(count(patientDb,"patients")==1 && count(patientDb,"patient_revisions")==1 && count(authDb,"patient_onboardings")==1,"one durable profile, revision and Auth reservation");
                var replay=data(request("POST",url(authPort,"/api/v1/auth/me/patient-onboarding"),profile,bearer,null),200);
                check(patient.equals(replay.path("patientId").asText()),"retry after a discarded response reuses the same profile");
                check(data(request("GET",url(authPort,"/api/v1/auth/me/patient-onboarding"),null,bearer,null),200).path("user").path("patientId").asText().equals(patient),"onboarding state recovers without browser cache");
                var changed=new HashMap<>(profile);changed.put("fullName","Changed request");
                check(request("POST",url(authPort,"/api/v1/auth/me/patient-onboarding"),changed,bearer,null).statusCode()==409,"conflicting replay cannot create a second profile");
                data(request("GET",url(patientPort,"/api/v3/patients/"+patient),null,bearer,null),200);
                check(true,"original pre-link bearer reads its newly linked profile through live introspection");
                var second=register("b");String bearerB=second.path("accessToken").asText();
                check(request("PUT",url(authPort,"/api/v1/auth/me/patient-profile"),Map.of("patientId",patient),bearerB,null).statusCode()==403,"another account cannot claim the existing Patient UUID");
                var injected=new HashMap<String,Object>(profile);injected.put("patientId",patient);
                check(request("POST",url(authPort,"/api/v1/auth/me/patient-onboarding"),injected,bearerB,null).statusCode()==400,"public orchestration rejects a client-selected Patient UUID");
                var ownerB=data(request("POST",url(authPort,"/api/v1/auth/me/patient-onboarding"),profile,bearerB,null),200);
                check(!patient.equals(ownerB.path("patientId").asText()) && count(patientDb,"patients")==2,"separate accounts receive separate owned profiles");
                check(request("GET",url(patientPort,"/api/v3/patients/"+patient),null,bearerB,null).statusCode()==403,"cross-patient read is denied after onboarding");
                var doctor=data(request("POST",url(doctorPort,"/api/v1/doctors"),Map.of("name","Synthetic Doctor","specialty","General","department","General","consultationFee",100),null,null),201);
                String doctorId=doctor.path("id").asText(), day=LocalDate.now(ZoneId.of("Asia/Saigon")).plusDays(2).toString();
                var slot=data(request("POST",url(doctorPort,"/api/v1/doctors/"+doctorId+"/slots"),Map.of("slotDate",day,"startTime","09:00:00","endTime","10:00:00"),null,null),201);
                var next=data(request("POST",url(doctorPort,"/api/v1/doctors/"+doctorId+"/slots"),Map.of("slotDate",day,"startTime","10:00:00","endTime","11:00:00"),null,null),201);
                var booking=data(request("POST",url(appointmentPort,"/api/v2/appointments"),Map.of("patientId",patient,"doctorId",doctorId,"slotId",slot.path("id").asText()),bearer,UUID.randomUUID().toString()),201);
                check(booking.path("patientId").asText().equals(patient) && "CONFIRMED".equals(booking.path("status").asText()),"new account books through real Auth, Patient and Doctor references");
                String appointment=booking.path("id").asText();
                var moved=data(request("PUT",url(appointmentPort,"/api/v2/appointments/"+appointment+"/reschedule"),Map.of("version",booking.path("version").asLong(),"slotId",next.path("id").asText()),bearer,UUID.randomUUID().toString()),200);
                check(moved.path("slotId").asText().equals(next.path("id").asText()),"reschedule transfers occupancy to the destination offer");
                var availability=data(request("GET",url(appointmentPort,"/api/v2/appointments/available-slots?doctorId="+doctorId+"&date="+day),null,bearer,null),200);
                check(availability.path("slots").get(0).path("available").asBoolean() && !availability.path("slots").get(1).path("available").asBoolean(),"old hour is free and new hour is occupied");
                data(request("PUT",url(appointmentPort,"/api/v2/appointments/"+appointment+"/cancel"),Map.of("version",moved.path("version").asLong()),bearer,UUID.randomUUID().toString()),200);
                availability=data(request("GET",url(appointmentPort,"/api/v2/appointments/available-slots?doctorId="+doctorId+"&date="+day),null,bearer,null),200);
                check(availability.path("slots").get(0).path("available").asBoolean() && availability.path("slots").get(1).path("available").asBoolean(),"cancellation frees occupancy in the owning Appointment service");
                check(count(appointmentDb,"appointment_history")==3,"create, reschedule and cancel retain their audit history");
            } finally {
                for(Process process:APPS) {process.destroy();if(!process.waitFor(10,TimeUnit.SECONDS)){process.destroyForcibly();process.waitFor(5,TimeUnit.SECONDS);}}
                cleanupIdentities();
            }
        } finally {HTTP.close();}
        var evidence=Map.of("result","PASS","checks",CHECKS,"business_databases","temporary native PostgreSQL only","keycloak","real configured realm; test-owned users removed","logs",logs.toString());
        Files.writeString(repo.resolve("docs/integration/trusted-onboarding-acceptance.json"),JSON.writerWithDefaultPrettyPrinter().writeValueAsString(evidence));
    }
}
