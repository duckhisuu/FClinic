package com.fclinic.appointmentservice.infrastructure;

import java.net.http.HttpClient;
import java.nio.file.*;
import java.security.KeyStore;
import java.time.Duration;
import javax.net.ssl.*;
import org.springframework.core.env.Environment;

/** Optional private CA. Default JVM trust and hostname verification remain enabled. */
public final class ProviderTls {
  private ProviderTls() { }
  public static HttpClient client(Environment env) {
    var builder = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).followRedirects(HttpClient.Redirect.NEVER);
    String path = env == null ? "" : env.getProperty("appointment.tls.trust-store", "");
    String password = env == null ? "" : env.getProperty("appointment.tls.trust-store-password", "");
    if (path.isBlank() != password.isBlank()) throw new IllegalArgumentException("Trust store and password must be configured together");
    if (!path.isBlank()) {
      char[] chars = password.toCharArray();
      try (var stream = Files.newInputStream(Path.of(path))) {
        var store = KeyStore.getInstance("PKCS12"); store.load(stream, chars);
        var managers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()); managers.init(store);
        var ssl = SSLContext.getInstance("TLS"); ssl.init(null, managers.getTrustManagers(), null); builder.sslContext(ssl);
      } catch (Exception e) { throw new IllegalArgumentException("Cannot initialize provider trust store"); }
      finally { java.util.Arrays.fill(chars, '\0'); }
    }
    return builder.build();
  }
}
