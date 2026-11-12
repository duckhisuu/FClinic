package com.fclinic.appointmentservice.infrastructure;

import java.nio.file.*;
import java.security.KeyStore;
import javax.net.ssl.*;
import org.springframework.core.env.Environment;

/** Optional private CA. Default JVM trust and hostname verification remain enabled. */
public final class ProviderTls {
  private ProviderTls() { }
  public static SSLSocketFactory socketFactory(Environment env) {
    String path = env == null ? "" : env.getProperty("appointment.tls.trust-store", "");
    String password = env == null ? "" : env.getProperty("appointment.tls.trust-store-password", "");
    if (path.isBlank() != password.isBlank()) throw new IllegalArgumentException("Trust store and password must be configured together");
    if (!path.isBlank()) {
      char[] chars = password.toCharArray();
      try (var stream = Files.newInputStream(Path.of(path))) {
        var store = KeyStore.getInstance("PKCS12"); store.load(stream, chars);
        var managers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()); managers.init(store);
        var ssl = SSLContext.getInstance("TLS"); ssl.init(null, managers.getTrustManagers(), null); return ssl.getSocketFactory();
      } catch (Exception e) { throw new IllegalArgumentException("Cannot initialize provider trust store"); }
      finally { java.util.Arrays.fill(chars, '\0'); }
    }
    return (SSLSocketFactory) SSLSocketFactory.getDefault();
  }
}
