INSERT INTO roles (id, role_name, description, created_at, updated_at)
VALUES
    ('a1000001-0000-4000-8000-000000000001', 'ROLE_ADMIN', 'Quản trị hệ thống - toàn quyền quản lý người dùng và phân quyền', NOW(), NOW()),
    ('a1000001-0000-4000-8000-000000000002', 'ROLE_DOCTOR', 'Bác sĩ - quản lý lịch khám và hồ sơ khám bệnh', NOW(), NOW()),
    ('a1000001-0000-4000-8000-000000000003', 'ROLE_PATIENT', 'Bệnh nhân - đặt lịch khám và quản lý hồ sơ cá nhân', NOW(), NOW()),
    ('a1000001-0000-4000-8000-000000000004', 'ROLE_STAFF', 'Nhân viên tiếp tân - tra cứu và hỗ trợ bệnh nhân tại quầy', NOW(), NOW());

INSERT INTO users (
    id, role_id, keycloak_id, username, email, phone_number, full_name,
    patient_id, doctor_id, google_id, active, created_at, updated_at
)
VALUES
    (
        '11111111-1111-4111-8111-111111111101',
        'a1000001-0000-4000-8000-000000000001',
        '22222222-2222-4222-8222-222222222201',
        'admin',
        'admin@fclinic.vn',
        '0900000000',
        'System Administrator',
        NULL,
        NULL,
        NULL,
        TRUE,
        NOW(),
        NOW()
    ),
    (
        '11111111-1111-4111-8111-111111111102',
        'a1000001-0000-4000-8000-000000000004',
        '22222222-2222-4222-8222-222222222202',
        'staff_linh',
        'staff@fclinic.vn',
        '0900000002',
        'Lê Thị Linh',
        NULL,
        NULL,
        NULL,
        TRUE,
        NOW(),
        NOW()
    ),
    (
        '11111111-1111-4111-8111-111111111103',
        'a1000001-0000-4000-8000-000000000002',
        '22222222-2222-4222-8222-222222222203',
        'bs_an',
        'doctor.an@fclinic.vn',
        '0900000003',
        'BS. CKII Nguyễn Văn An',
        NULL,
        '33333333-3333-4333-8333-333333333301',
        NULL,
        TRUE,
        NOW(),
        NOW()
    ),
    (
        '11111111-1111-4111-8111-111111111104',
        'a1000001-0000-4000-8000-000000000003',
        '22222222-2222-4222-8222-222222222204',
        'patient_hung',
        'patient@fclinic.vn',
        '+84900000001',
        'Nguyễn Văn Hùng',
        '74fba630-5953-44b8-86d6-bc236ac1213b',
        NULL,
        NULL,
        TRUE,
        NOW(),
        NOW()
    );

