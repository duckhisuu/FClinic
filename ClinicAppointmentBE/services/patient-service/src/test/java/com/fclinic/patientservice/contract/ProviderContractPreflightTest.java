package com.fclinic.patientservice.contract;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class ProviderContractPreflightTest {
    @Test void absentOptInEnvironmentFailsBeforeAnyNetworkClientExists() {
        assertThatThrownBy(() -> ProviderContractPlan.load(Map.of())).isInstanceOf(IllegalStateException.class)
                .hasMessage("Real-provider preflight blocked; see runbook. No credential/URL details emitted.").hasNoCause();
    }
    @Test void plainHttpTargetIsRejectedWithoutEchoingSecretOrInternalLocation() {
        var env=new HashMap<String,String>();
        env.put("PATIENT_REAL_PROVIDER_BOUNDARY","B-04");
        env.put("PATIENT_REAL_PROVIDER_COLLECTION_URI","http://private.invalid/api/v1/doctors");
        env.put("PATIENT_REAL_PROVIDER_CLIENT_ID","synthetic-client");
        env.put("PATIENT_REAL_PROVIDER_CLIENT_SECRET","synthetic-sensitive-value");
        assertThatThrownBy(() -> ProviderContractPlan.load(env)).isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("private.invalid").hasMessageNotContaining("synthetic-sensitive-value").hasNoCause();
    }
    @Test void unsupportedBoundaryCannotSelectAnArbitraryRemoteApi() {
        assertThatThrownBy(() -> ProviderContractPlan.load(Map.of("PATIENT_REAL_PROVIDER_BOUNDARY","B-07")))
                .isInstanceOf(IllegalStateException.class).hasNoCause();
    }
}

