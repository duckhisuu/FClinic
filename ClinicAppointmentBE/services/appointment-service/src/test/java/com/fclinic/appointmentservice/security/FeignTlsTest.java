package com.fclinic.appointmentservice.security;

import static org.assertj.core.api.Assertions.*;
import com.fclinic.appointmentservice.config.AppointmentSecuritySettings;
import com.fclinic.appointmentservice.domain.AppointmentError;
import com.fclinic.appointmentservice.infrastructure.ProviderTls;
import com.fclinic.appointmentservice.infrastructure.client.config.FeignCalls;
import com.sun.net.httpserver.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.KeyStore;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

class FeignTlsTest {
  @TempDir Path directory;
  @Test void privateTrustStoreKeepsCertificateAndHostnameVerification() throws Exception {
    Path storePath = directory.resolve("provider.p12");
    String keytool = Path.of(System.getProperty("java.home"), "bin",
        System.getProperty("os.name").startsWith("Windows") ? "keytool.exe" : "keytool").toString();
    var process = new ProcessBuilder(keytool, "-genkeypair", "-alias", "provider", "-keyalg", "RSA",
        "-storetype", "PKCS12", "-keystore", storePath.toString(), "-storepass", "synthetic-password",
        "-keypass", "synthetic-password", "-dname", "CN=localhost", "-ext", "SAN=dns:localhost", "-validity", "1", "-noprompt")
        .redirectErrorStream(true).redirectOutput(directory.resolve("keytool.log").toFile()).start();
    assertThat(process.waitFor(20, TimeUnit.SECONDS)).isTrue(); assertThat(process.exitValue()).isZero();
    var store = KeyStore.getInstance("PKCS12");
    try (var input = Files.newInputStream(storePath)) { store.load(input, "synthetic-password".toCharArray()); }
    var keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()); keys.init(store, "synthetic-password".toCharArray());
    var ssl = SSLContext.getInstance("TLS"); ssl.init(keys.getKeyManagers(), null, null);
    var server = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.setHttpsConfigurator(new HttpsConfigurator(ssl));
    server.createContext("/token-check", ex -> {
      byte[] bytes = "{\"active\":false}".getBytes(StandardCharsets.UTF_8);
      ex.getResponseHeaders().set("Content-Type", "application/json");
      ex.sendResponseHeaders(200, bytes.length); try (var out = ex.getResponseBody()) { out.write(bytes); }
    });
    server.start();
    var settings = new AppointmentSecuritySettings(null, null, null, "client", "secret",
        null, null, null, null, null, null, null, null, null, null);
    var env = new MockEnvironment().withProperty("appointment.tls.trust-store", storePath.toString())
        .withProperty("appointment.tls.trust-store-password", "synthetic-password");
    URI endpoint = URI.create("https://localhost:" + server.getAddress().getPort() + "/token-check");
    try (var untrusted = new FeignHarness(); var trusted = new FeignHarness(env)) {
      assertThatThrownBy(() -> FeignCalls.call(() -> untrusted.introspect(settings)
          .introspect(endpoint, "token=synthetic", FeignCalls.options(Long.MAX_VALUE)))).isInstanceOf(AppointmentError.class);
      var client = trusted.introspect(settings);
      assertThat(client.introspect(endpoint, "token=synthetic", FeignCalls.options(Long.MAX_VALUE)).active()).isFalse();
      URI wrongHost = URI.create(endpoint.toString().replace("localhost", "127.0.0.1"));
      assertThatThrownBy(() -> FeignCalls.call(() -> client.introspect(wrongHost, "token=synthetic",
          FeignCalls.options(Long.MAX_VALUE)))).isInstanceOf(AppointmentError.class);
    } finally { server.stop(0); }
  }
  @Test void partialOrInvalidTrustStoreConfigurationFailsClosed() {
    assertThatThrownBy(() -> ProviderTls.socketFactory(new MockEnvironment().withProperty("appointment.tls.trust-store", "missing.p12")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ProviderTls.socketFactory(new MockEnvironment().withProperty("appointment.tls.trust-store", "missing.p12")
        .withProperty("appointment.tls.trust-store-password", "synthetic-password"))).isInstanceOf(IllegalArgumentException.class);
  }
}
