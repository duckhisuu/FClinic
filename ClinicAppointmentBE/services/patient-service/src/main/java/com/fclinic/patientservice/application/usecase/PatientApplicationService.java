package com.fclinic.patientservice.application.usecase;

import com.fclinic.patientservice.application.command.RegisterPatientCommand;
import com.fclinic.patientservice.application.port.in.GetPatientUseCase;
import com.fclinic.patientservice.application.port.in.ListPatientsUseCase;
import com.fclinic.patientservice.application.port.in.RegisterPatientUseCase;
import com.fclinic.patientservice.application.port.out.PatientRepositoryPort;
import com.fclinic.patientservice.application.result.PatientView;
import com.fclinic.patientservice.domain.aggregate.Patient;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class PatientApplicationService implements GetPatientUseCase, ListPatientsUseCase, RegisterPatientUseCase {

    private static final Logger log = LoggerFactory.getLogger(PatientApplicationService.class);

    private final PatientRepositoryPort patientRepository;

    public PatientApplicationService(PatientRepositoryPort patientRepository) {
        this.patientRepository = patientRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PatientView> getById(Long id) {
        return patientRepository.findById(id).map(PatientView::from);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PatientView> getByPhoneNumber(String phoneNumber) {
        return patientRepository.findByPhoneNumber(phoneNumber).map(PatientView::from);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PatientView> listAll() {
        return patientRepository.findAll().stream().map(PatientView::from).toList();
    }

    @Override
    @Transactional
    public PatientView registerOrUpdate(RegisterPatientCommand command) {
        if (command.phoneNumber() != null && patientRepository.existsByPhoneNumber(command.phoneNumber())) {
            Patient existing = patientRepository.findByPhoneNumber(command.phoneNumber()).orElseThrow();
            existing.updateInfo(
                    command.fullName(),
                    command.email(),
                    command.dateOfBirth(),
                    command.gender(),
                    command.address()
            );
            Patient updated = patientRepository.save(existing);
            log.info("[PatientApplicationService] Updated existing patient id={} phone={}", updated.getId(), updated.getPhoneNumber());
            return PatientView.from(updated);
        }

        Patient newPatient = Patient.createNew(
                command.fullName(),
                command.phoneNumber(),
                command.email(),
                command.dateOfBirth(),
                command.gender(),
                command.address()
        );
        Patient saved = patientRepository.save(newPatient);
        log.info("[PatientApplicationService] Registered new patient id={} phone={}", saved.getId(), saved.getPhoneNumber());
        return PatientView.from(saved);
    }

    @PostConstruct
    public void initSampleData() {
        if (patientRepository.count() == 0) {
            patientRepository.save(Patient.createNew(
                    "Nguyễn Văn Hùng",
                    "0912345678",
                    "hung.nguyen@example.com",
                    LocalDate.of(1985, 4, 12),
                    "Nam",
                    "123 Nguyễn Huệ, Quận 1, TP. Hồ Chí Minh"
            ));

            patientRepository.save(Patient.createNew(
                    "Lê Thuỳ Linh",
                    "0987654321",
                    "linh.le@example.com",
                    LocalDate.of(1992, 9, 25),
                    "Nữ",
                    "45 Lê Lợi, Phường Bến Nghé, Quận 1, TP. Hồ Chí Minh"
            ));

            patientRepository.save(Patient.createNew(
                    "Trần Quốc Bảo",
                    "0909112233",
                    "bao.tran@example.com",
                    LocalDate.of(1978, 11, 3),
                    "Nam",
                    "88 Hai Bà Trưng, Quận 3, TP. Hồ Chí Minh"
            ));
            log.info("[PatientApplicationService] Initialized sample patient data");
        }
    }
}
