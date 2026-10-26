# M6 cutover prerequisites — D-15, preparation only

Ngày 24/10/2026. **Không phải migration approval, không chạy migration/backup/restore/start legacy hoặc ghi database trong audit.** Current M5 real integration BLOCKED; M6 execution cũng BLOCKED. Căn cứ C-MIG:9–22, C-D:23, C-ADR Q-04, C-IT IT-12:188–201; source/evidence aliases ở [01](01_M5_READINESS_AUDIT.md), owner request H-06 ở [03](03_OWNER_HANDOFF_REQUESTS.md).

## Phân biệt điều đã có và chưa có

- Đã có: approved Patient empty-schema SQL copy hash MATCH; fresh PG16.10 schema validate/core tests pass, explicit v2 datasource/validate/no auto-init, target binary không còn Patient v1 routes/seed. Nguồn SQL/S-YML/T-IT; regression24/10 ở01.
- Chưa có: actual v1 DDL/data/timezone/writer inventory, stable persisted upgrade map, tool/window/owner adoption, protected clone restore/rehearsal/rerun/rollback, compatible consumer migration và live legacy retirement.
- Source chỉ cho thấy consumer blockers: X-APT Long/client snapshots, X-GW v1 routes, X-FE fallback1/by-phone. Không lấy “target source bỏ v1” làm bằng chứng mọi deployment/writer đã bị dừng.
- Canonical registry legacy mapping là synthetic fixture. Không áp UUID demo/legacy id1 lên data thật; không hash/cast/substring UUID→Long hoặc merge matching phone/email.
- M5 signoff (sau này) chỉ cho exact integration environment/build/profile; không tự cho quyền M6 execution hay production reopen.

## Accountable owner và approval record

Integration Lead cần gán **cá nhân + backup** cho Patient DB, Auth DB/mapping, Appointment DB, Doctor khi có affected refs, Gateway, Frontend, QA/Security và Operations. C-OWN hiện chỉ role, chưa có người được xác nhận. Mỗi owner ký affected build/schema/hash/window/checklist evidence; ngày/approval link không được điền thay.

Tool migration/version/checksum/baseline procedure vẫn chờ D-15. Flyway là proposed, không được coi installed/adopted chỉ vì có V001 resource. Mọi script/tool upgrade phải được review riêng và chạy trên clone do owner kiểm soát, không dùng ddl-auto:update.

## Prerequisites checklist

Tất cả rows hiện **OPEN / NOT EXECUTED**, trừ các source/fresh-schema facts đã mô tả ở trên. Evidence cần là controlled reference đã redact, không dump/PHI/credential trong thư mục audit.

| ID | Điều kiện bắt buộc | Accountable / phối hợp | Required evidence và acceptance | Stop condition |
|---|---|---|---|---|
| M6-01 | Inventory schema/data v1 và mọi writers | Từng DB owner + Ops, Lead tổng hợp | Actual DDL/constraint/index/sequence/version/timezone, row counts/null/invalid reports; inventory applications/pods/processes/jobs/seeds/direct DB/API writers/relays và consumers theo build | Không biết actual schema, original timezone, source of truth hoặc còn writer chưa rõ |
| M6-02 | Backup và restore rehearsal trên clone | Ops + từng DB owner + QA | Versioned backup/restore references, consistency scope across DB, count/hash/readback evidence, protected access; owner chốt recovery objectives/window và rehearsal thành công | Chưa restore thành công hoặc backups các DB không nhất quán, thiếu access authority |
| M6-03 | Stable persisted Long↔UUID mapping | Patient owner cung cấp map; Auth/Appointment own application | Một UUIDv4 duy nhất mỗi legacy Long, persist unique legacy_patient_id↔UUID; bijection/count/no null/no duplicates, controlled mapping hash/export, rerun/crash không sinh map mới | Unmapped/duplicate/re-generated UUID hoặc dùng fixture/static id làm map |
| M6-04 | Shared phone preservation và cleansing | Patient owner + business/data owner review | Reviewed normalization/gender/null/overlength/timezone decisions; distinct people retained; remove unique-phone đúng thứ tự trước colliding normalization; count/identity checks, không silent truncation | Có merge/delete để ép phone unique, đoán gender/timezone hoặc bỏ record invalid không phê duyệt |
| M6-05 | Consumer UUID readiness | Appointment/Auth/Gateway/FE owners + Patient | All affected API/domain/persistence/client/event consumers compatible; adopted Patient hash/full DTO; caller self/scope trusted; no Long cast/id1/by-phone identity; M5 real CT/TCP evidence | X-APT/FE/GW legacy behavior còn hoạt động, unknown consumers hoặc auth mapping chưa trusted |
| M6-06 | Maintenance/drain v1 writers/jobs/seed/direct access | Ops + Gateway + mỗi service owner | Approved window + traffic controls Gateway **và direct**, drain requests/queues theo impact, stop all v1 binaries/jobs/PostConstruct seeds/affected relays; process/build inventory and zero active legacy writers assertion | Chỉ đóng Gateway, direct write/DB writer/seed còn sống; không kiểm process/build |
| M6-07 | Per-service-owned migration | Patient/Auth/Appointment DB owners, Lead điều phối | Patient tự PK/FK/revisions/code/map; Appointment tự patient refs; Auth tự trusted links; approved version/checksum and ordered application per DB; no cross-DB repository/DDL | Patient muốn tự backfill DB khác, failed map, tool chưa adopt hoặc chưa có owner go/no-go |
| M6-08 | Validate constraints/count/version/revisions | Từng DB owner + QA/Security | No unmapped refs, counts preserved, UUID/code equality, legacy map uniqueness, local FK/nullable unique linked appointment, versions/revision before-after consistency. Migration CREATE baseline actor migration/version0 theo C-MIG, không bịa lịch sử; bigint sequence >import max, Patient UUID không sequence | Data loss/null refs/duplicate code/orphan audit/invalid sequence; hash script khác reviewed artefact |
| M6-09 | Target-only restart và smoke | Ops + Patient/Auth/consumers/Gateway/FE + QA | Compatible v2 binaries, explicit v2 DB, ddl-auto:validate/no auto-init/seed; real security/self/admin/clinical + chosen UUID readback; restart giữ data/version/revisions; probe all legacy Gateway/direct/by-phone paths retired | V1 binary/Long consumer được khởi động lại, schema update tự chạy, fake token/mock success hoặc no authority |
| M6-10 | Coordinated rollback/restore hoặc approved forward fix | Ops + Lead + DB owners/business authority | Tested restore consistency, stop-writes procedure, restore/readback checks; decision authority cho forward fix; mọi affected consumer/config/profile included, secret refs restored hợp lệ | Rollback chỉ Patient DB hoặc deploy v1 lên UUID/shared-phone dataset; delete/merge để quay unique-phone |
| M6-11 | Signoff trước reopen | Lead chịu trách nhiệm, từng owner + QA/Security/Ops ký | Named signoffs/time/build/source/schema/map/contract hashes, approved exclusions, critical tests pass + no old writer; explicit reopen authority và monitoring/recovery escalation owner | Thiếu chữ ký/evidence của bất kỳ affected owner, critical security/schema failure, unresolved blockers |

## Coordinated sequence đề xuất, chưa được cho phép thực thi

1. Hoàn tất M6-01/02, owner review actual inventory và phục hồi clone.
2. Review tool/versioned upgrade + stable mapping và cleansing decisions M6-03/04; rehearse rerun/crash/restore trên clones.
3. Chuẩn bị compatible builds/auth/scopes/routes/consumers/seeds target, M6-05; chốt window và stop criteria.
4. Có go/no-go owner rồi maintenance/drain M6-06, backfill cuối + apply owned migrations M6-07.
5. Validate M6-08, target-only startup/restart/smoke M6-09, không mở lại v1 dù có lỗi.
6. Nếu fail: giữ stop-writes, coordinated restore hoặc approved forward fix M6-10.
7. Chỉ M6-11 signed mới reopen. Bất kỳ critical failure phải giữ gate đóng, không “demo success” bằng fallback.

Không có command hoặc upgrade script execution trong audit này. Thứ tự là preparation từ C-MIG, không phê duyệt thời gian vận hành/sửa data ngoài owner authority.

## Acceptance evidence bundle khi được thực thi sau này

| Nhóm | Nội dung phải có | Không đưa vào shared audit |
|---|---|---|
| Identity/compatibility | Owner+backup, boundary adoption/version/hash, compatible builds, environment/profile labels | Raw token/key/password |
| Data/mapping | Schema version/checksum, anonymized counts/invariants, mapping export hash/location/access owner, timezone cleansing approvals | Raw patient rows, phone/email, clinical snapshots, map dump chứa PII |
| Restore/recovery | Controlled backup/restore references, rehearsed status, consistency/readback, allowed window/go-no-go/forward-fix authority | Backup content/secret path credentials |
| Runtime/test | IT-12 case/build/hash/requestId expected-vs-actual, v1 probes, process/writer inventory assertion, restart data/revision results | Unredacted requests/auth headers/log clinical body |
| Signoff | Named timestamped approvals/exclusions/reopen decision, monitoring and rollback escalation | Invented acceptance hoặc “58 tests ⇒release ready” |

M6 case T-MIG-01 và T-RST-01/T-LOAD-01 ở [05](05_M5_TEST_MATRIX.md) vẫn NOT EXECUTED/BLOCKED. File này chỉ đáp ứng preparation deliverable; không đổi D-15, M5 hoặc M6 thành DONE.

