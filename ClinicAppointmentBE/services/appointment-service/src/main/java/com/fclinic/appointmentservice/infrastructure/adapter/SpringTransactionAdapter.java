package com.fclinic.appointmentservice.infrastructure.adapter;

import com.fclinic.appointmentservice.application.exception.SlotAlreadyBookedException;
import com.fclinic.appointmentservice.application.port.out.UnitOfWorkPort;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class SpringTransactionAdapter implements UnitOfWorkPort {
  private final TransactionTemplate tx;

  public SpringTransactionAdapter(PlatformTransactionManager manager) {
    tx = new TransactionTemplate(manager);
    tx.setTimeout(5);
  }

  public <T> T execute(Supplier<T> work) {
    try {
      return tx.execute(status -> work.get());
    } catch (RuntimeException failure) {
      for (Throwable e = failure; e != null; e = e.getCause())
        if (e.getMessage() != null && e.getMessage().contains("uq_confirmed_doctor_hour"))
          throw new SlotAlreadyBookedException();
      throw failure;
    }
  }
}
