package com.fclinic.authservice.application.model;

import com.fclinic.authservice.domain.exception.AuthValidationException;
import java.time.*;
import java.util.*;

/** The public contract accepts demographics, never a client-selected identity or profile UUID. */
public record PatientOnboardingCommand(String fullName, String phoneNumber, String dateOfBirth,
                                       String gender, String address) {
    public static PatientOnboardingCommand parse(Map<String, Object> body, Clock clock) {
        if (body == null || !Set.of("fullName", "phoneNumber", "dateOfBirth", "gender", "address").containsAll(body.keySet()))
            throw new AuthValidationException("Thông tin khởi tạo hồ sơ không hợp lệ");
        String name = text(body, "fullName", 255, true);
        String phone = text(body, "phoneNumber", 40, true);
        if (!phone.matches("[+0-9 ()-]+")) throw new AuthValidationException("Số điện thoại không hợp lệ");
        phone = phone.replaceAll("[ ()-]", "");
        if (phone.matches("0[0-9]{9}")) phone = "+84" + phone.substring(1);
        if (!phone.matches("\\+[1-9][0-9]{7,14}")) throw new AuthValidationException("Số điện thoại không hợp lệ");
        String dob = text(body, "dateOfBirth", 10, true);
        try {
            if (!dob.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")
                    || LocalDate.parse(dob).isAfter(LocalDate.now(clock.withZone(ZoneId.of("Asia/Saigon")))))
                throw new IllegalArgumentException();
        } catch (RuntimeException ex) { throw new AuthValidationException("Ngày sinh không hợp lệ"); }
        String gender = text(body, "gender", 10, true);
        if (!Set.of("MALE", "FEMALE", "OTHER", "UNKNOWN").contains(gender))
            throw new AuthValidationException("Giới tính không hợp lệ");
        return new PatientOnboardingCommand(name, phone, dob, gender, text(body, "address", 2000, false));
    }
    public Map<String,Object> profile(String email) {
        Map<String,Object> result = new TreeMap<>();
        result.put("fullName", fullName); result.put("phoneNumber", phoneNumber);
        result.put("dateOfBirth", dateOfBirth); result.put("gender", gender); result.put("address", address);
        result.put("email", email);
        return result;
    }
    private static String text(Map<String,Object> body, String key, int max, boolean required) {
        Object raw = body.get(key);
        if (raw == null && !required) return null;
        if (!(raw instanceof String value) || value.codePointCount(0, value.length()) > max
                || (required && value.isBlank())) throw new AuthValidationException("Trường không hợp lệ: " + key);
        return value.isBlank() ? null : value.strip();
    }
}
