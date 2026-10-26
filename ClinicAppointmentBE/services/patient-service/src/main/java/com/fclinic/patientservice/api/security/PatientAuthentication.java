package com.fclinic.patientservice.api.security;

import com.fclinic.patientservice.application.port.out.IdentityVerifierPort;
import com.fclinic.patientservice.application.security.PatientPolicy;
import com.fclinic.patientservice.application.security.BearerTokens;
import com.fclinic.patientservice.domain.model.PatientError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import java.util.Collections;

@Component
public class PatientAuthentication implements HandlerInterceptor {
    private final IdentityVerifierPort verifier;
    private final PatientPolicy policy;
    public PatientAuthentication(IdentityVerifierPort verifier, PatientPolicy policy) { this.verifier=verifier; this.policy=policy; }
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        var headers = Collections.list(request.getHeaders("Authorization"));
        if (headers.size()!=1)
            throw new PatientError(PatientError.Code.UNAUTHENTICATED);
        String token = BearerTokens.fromAuthorization(headers.getFirst());
        com.fclinic.patientservice.application.security.TrustedCaller caller;
        try { caller = verifier.verify(token); }
        catch (PatientError error) { throw error; }
        catch (RuntimeException error) { throw new PatientError(PatientError.Code.DEPENDENCY_UNAVAILABLE); }
        policy.authenticated(caller);
        request.setAttribute("patientCaller",caller);
        return true;
    }
}
