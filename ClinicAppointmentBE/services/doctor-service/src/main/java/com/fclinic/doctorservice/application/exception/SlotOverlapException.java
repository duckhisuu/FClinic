package com.fclinic.doctorservice.application.exception;

public class SlotOverlapException extends RuntimeException {
    public SlotOverlapException() {
        super("Khung giờ bị trùng với lịch khám đã có của bác sĩ. Vui lòng chọn giờ khác.");
    }
}
