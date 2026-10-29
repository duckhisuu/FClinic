# P-FINAL — acceptance và bàn giao Patient Service

26/10/2026, Asia/Saigon. **IMPLEMENTATION COMPLETE / CONSUMER INTEGRATION READY / REAL AUTH & SCOPE PENDING.** Completion này giới hạn ở8selected Patient operations, persistence/core invariants và4production outbound consumers. Không là full shared-contract adoption, whole-chain integration hoặc release approval.

[Implementation review15](15_P_FINAL_IMPLEMENTATION_REVIEW.md) · [Auth owner handoff16](16_P_FINAL_AUTH_CONSUMER_HANDOFF.md) · [Scope owners handoff17](17_P_FINAL_WORKSCOPE_HANDOFF.md) · [zero-rewrite runbook18](18_P_FINAL_REAL_INTEGRATION_RUNBOOK.md) · [machine evidence](../../evidence/patient-p-final-verification.json).

## Fresh verification — historical counts không được dùng thay lần này

| Run / module | Actual tests | Failures / errors / skipped | Build / finished |
|---|---|---|---|
| Patient-only verify |257 =180Surefire+77Failsafe |0 /0 /0 |SUCCESS,26/10/2026 09:02:07+07:00 |
| Full reactor Patient |257 =180+77 |0 /0 /0 |SUCCESS |
| Full reactor Doctor |59 =26+33 |0 /0 /0 |SUCCESS |
| Full reactor Appointment |138 =69+69 |0 /0 /0 |SUCCESS |
| Full reactor Notification |70 =60+10 |0 /0 /0 |SUCCESS |
| Full backend reactor |9modules,524JUnit cases across4service modules |0 /0 /0 |SUCCESS,26/10/2026 09:07:01+07:00 |

Commands thực chạy từ root:

```powershell
mvn -f ClinicAppointmentBE/pom.xml -pl services/patient-service -am verify -B -ntp
mvn -f ClinicAppointmentBE/pom.xml verify -B -ntp
```

Baseline254Patient cases được bảo toàn, thêm3TCP security regressions, không xóa/disable/skip assertions. Patient TCP now57cases (identity15 +scope19 +reference23), core PG/MockMvc20 =77Failsafe. Các authority trong57TCP cases là test doubles, **không real Auth/scope verification**. Token signature/issuance/live revocation không được kiểm chứng. Không suy coverage% từ số tests.

Default full verify còn khởi động actual Doctor/Appointment JARs trong existing provider ITs và gọi Patient `-P real-provider-contracts` khi provider còn chạy. Mỗi nested run có258JUnit cases (257regression +1aggregate harness), không cộng lặp vào524 hoặc coi vectors là JUnit tests riêng.

## Real reference boundaries rerun trong phiên này

| Boundary | Actual process / artifact | Expected → actual | Classification |
|---|---|---|---|
| B-04 Doctor | PID7220; JAR SHA `07af740ea7f01beb037c5090bca9cdb547c3508f666c0ee39770f834e3b38d9b` | ACTIVE200; INACTIVE200(false); MISSING404; INVALID_CREDENTIAL401; WRONG_VERSION_REQUEST406; controlled persisted invalid fee500. All6vectors PASS, strict adapter/domain error semantics. | REAL DOCTOR PROVIDER VERIFIED / B-04 CONTRACT VERIFIED |
| B-05 Appointment | PID7584; JAR SHA `37efbd03caf9aa08c43b45568d977adfd75be7588483bdad56a06f49dc97c38b` | VALID/CANCELLED/PATIENT_MISMATCH/DOCTOR_MISMATCH200, MISSING404, INVALID_CREDENTIAL401, WRONG_VERSION_REQUEST406, persisted PENDING technical500. All8vectors PASS. | REAL APPOINTMENT PROVIDER VERIFIED / B-05 CONTRACT VERIFIED |

Provider tests dùng fresh native PostgreSQL16.10, HTTPS/CA+hostname checks, dedicated Basic credentials và observed SELECT-only app roles (`doctor_reference_app`, `appointment_reference_app`,10connections mỗi provider). Before/after snapshot hashes identical; no unexpected DB mutation; processes đã stop và owned private TLS stores đã được xóa. Record/source/config/public-cert/observation hashes được archive trong evidence mới. Production code hai provider không bị sửa.

Optional wrong-resource/response-version/invalid-payload faults vẫn NOT EXECUTED; B-05 unknown status wire200 cũng NOT EXECUTED. Persisted PENDING→real500 không bị giả thành unknown-status200. Current reference snapshots không là encounter grants; actual Patient clinical mutation with genuine Auth/scope **NOT EXECUTED**. B-04/B-05 không chứng minh full human/clinical chain.

## Acceptance gates và ownership

| Gate | Verdict | Ownership / exact remaining work |
|---|---|---|
| Patient core implementation | COMPLETE within selected scope | Patient:8APIs/UUID/create-only/full PUT/no-op/stale/ACTIVE/author/nested/revision/concurrency preserved and verified. |
| Auth consumer | READY, COMPONENT/TCP WITH AUTH DOUBLE | Auth owner provides genuine provider, exact profile, issuance/current activation/revocation/trusted linking, HTTPS/dedicated clients; [16](16_P_FINAL_AUTH_CONSUMER_HANDOFF.md). |
| WorkScope consumer | READY, COMPONENT/TCP WITH AUTH+SCOPE DOUBLES | Integration Lead assigns administrative and clinical/encounter authority owners; real current grants/store/provenance and [17](17_P_FINAL_WORKSCOPE_HANDOFF.md) tests pending. |
| Real Auth / real WorkScope | PENDING / NOT VERIFIED | No alternative issuer, Auth module, fake production authority or role-based grant introduced. |
| B-04 / B-05 | REAL VERIFIED again in isolated selected profiles | Deployment credential rotation/load and excluded fault vectors remain operational follow-up. Shared adoption not inferred. |
| Whole Patient / B-02 | INTEGRATION PENDING | Genuine Auth+scope+Appointment human authorization/DoctorSlot operational chain, no-write/side-effect evidence still required. |
| Gateway / Frontend | NOT VERIFIED | Direct-port and Gateway permissions, routing/body/requestId propagation, full PUT/409/UUID flows need owner tests. |
| D-21 / F-05 | OPEN exclusions | Shared OAS503 coverage, permission-summary/safe-integer adoption; cross-service request correlation/telemetry not newly implemented or claimed. |
| M6 / release | NOT EXECUTED / NOT READY | Stable persisted legacy mapping, clone backup/restore, coordinated drain/cutover/rollback/signoff separate work. |

Auth owner may choose any approved technology that meets the FClinic integration profile; no Spring Authorization Server/Keycloak mandate. Recipient authority/notification delivery and event-contract governance are separate work; N1/N2 historical evidence remains unchanged. No booking, default relay, SMTP or delivery activation.

## Hashes, preservation và limitations

- Production+POM manifest: `b8c57ca9460e31c15f71f029583b24554766b0168afad5d99c0b82513c1392c1` — unchanged from P-FINAL baseline.
- Test-source manifest: `e8b3b9ea40f20f510433e82a3ab3ec8eeaea15f352ce456a34af4991e3ed667e`; changed test SHA `57c275a670e385ffffc09fe8d25d928e0283a9cbd54ce5d146b028a63d008df6`.
- Patient regression JAR: `44846763bbb897ca743865926761d6892157334c18a202db2cefa55bf3606c09`; not deployed as part of P-FINAL. Dirty source manifests, not base HEAD alone, describe this artifact.
- Identity/Scope contracts unchanged: `1ed4764c1480cc0e7776f36bdd0d6fbd9e97e6766a64dd827308dd2785b61088` / `e402cf523fe69f2fcf35eacb3517ebebc27d3f44653fdad4ab8ac52e7e919de1`.
- Doctor/Appointment contract pins unchanged: `098d3651fe4bc5daad3ec813ce459acfc624a0888ad209f789aa6a4bcd08c172` / `b2ce460335e273e227b45e982c6cefb2512d2625f6f7ff05f2342d29e34c1e86`.

Backup `D:\FClinic_SafeSync\patient-p-final-20261026-085656-570bd1cc` holds initial source/status manifest and verified Patient byte copies, final source/test/report/provider evidence. Git main/HEAD/index preserved; only Patient test/docs/status handoff changed, no commit/push performed in this task. Historical M5-A…D2/N1/N2/canonical artifacts, other services and pre-existing intentional deletions are per-path checked; exact changed/new paths and preservation comparison in machine evidence.

Default/auth/scope fail-closed behavior retained. No production stub/issuer/signing key, shared/legacy DB access, M6 or unrelated service edit. HTTP loopback servlet tests do not replace deployment TLS proof; remote snapshots/revocation after final check have normal distributed consistency limits. This acceptance hands off Patient implementation; **WHOLE FCLINIC NOT RELEASE READY**.
