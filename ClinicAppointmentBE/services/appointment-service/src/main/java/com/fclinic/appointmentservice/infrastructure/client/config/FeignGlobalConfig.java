package com.fclinic.appointmentservice.infrastructure.client.config;

import com.fclinic.appointmentservice.infrastructure.ProviderTls;
import feign.*;
import feign.codec.*;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/** Inherited only by each Feign child context; deliberately not component scanned. */
public class FeignGlobalConfig {
  protected String contract() { return null; }
  protected boolean retryGet() { return false; }
  @Bean(destroyMethod = "close") public GuardedFeignClient guardedClient(Environment env) {
    return new GuardedFeignClient(new Client.Default(ProviderTls.socketFactory(env), null), contract(), retryGet());
  }
  @Bean @org.springframework.context.annotation.Primary
  public Client client(GuardedFeignClient guarded,
      org.springframework.beans.factory.ObjectProvider<org.springframework.cloud.client.loadbalancer.LoadBalancerClient> loadBalancer,
      org.springframework.beans.factory.ObjectProvider<org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory> factory) {
    // Resolve the instance first; the delegate validates its final HTTPS/loopback URI before sending credentials.
    var balancer = loadBalancer.getIfAvailable();
    return balancer == null ? guarded : new org.springframework.cloud.openfeign.loadbalancer.FeignBlockingLoadBalancerClient(
        guarded, balancer, factory.getObject(), java.util.List.of());
  }
  @Bean public Decoder decoder() { return new FeignJacksonSupport(); }
  @Bean public Encoder encoder() { return FeignJacksonSupport.encoder(); }
  @Bean public Request.Options options() { return new Request.Options(1000, 2000, false); }
  @Bean public Retryer retryer() { return retryGet() ? new BudgetRetryer() : Retryer.NEVER_RETRY; }
  @Bean public Logger.Level loggerLevel() { return Logger.Level.NONE; }
}

