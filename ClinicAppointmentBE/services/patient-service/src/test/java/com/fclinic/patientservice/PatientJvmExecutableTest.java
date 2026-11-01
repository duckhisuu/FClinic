package com.fclinic.patientservice;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.*;

class PatientJvmExecutableTest {
    @ParameterizedTest
    @CsvSource({"Windows 11,java.exe", "WINDOWS Server 2022,java.exe", "Linux,java", "Mac OS X,java", "Darwin,java"})
    void platformNameSelectsJvmBinaryWithoutShellQuoting(String os,String executable) {
        Path home=Path.of("synthetic JVM with spaces");
        assertThat(PatientUuidJarIT.javaExecutable(os,home)).isEqualTo(home.resolve("bin").resolve(executable));
    }
    @Test void currentJvmExecutableActuallyExists() {
        assertThat(PatientUuidJarIT.javaExecutable(System.getProperty("os.name"),Path.of(System.getProperty("java.home"))))
                .isRegularFile().isExecutable();
    }
}
