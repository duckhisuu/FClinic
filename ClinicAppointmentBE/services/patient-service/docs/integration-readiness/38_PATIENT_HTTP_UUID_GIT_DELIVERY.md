# 38 — Safe Git delivery

Original main `a6bf54e0edc6fa513e07b7e91a5ddaeefd1a58c9`, index SHA256 `73c602f75f6ab8b81d0779964dd6f2ac8c3f680d9c9a72cfe8e6d3386ee9d184`, tracked/untracked files and intentional absences are preserved. The existing HTTP/UUID candidate87-path SHA manifest was verified and backed up outside Git. The separate Appointment clone retains15 unresolved paths and its original HEAD/MERGE_HEAD/index hashes. No original checkout/reset/clean/stash/commit/index update.

Remote before: main `3f833711e282e06b71cc88f7030ce762d78ec201`, bao `a097c67dbbbf399ec02eeb5b26b801946b7a6f41`. PR#3 is CLOSED/MERGED and is not reused. Main's PR#3 commit has one parent (squash-shaped ancestry), and main/bao had IDENTICAL trees, so bao was not an ancestor of main despite equivalent source. The new isolated branch starts from main and prepares an explicit no-commit merge of bao. Final reviewed commit preserves main as first parent and old bao as second parent, enabling a non-force fast-forward push to bao without unrelated source delta. No force update/history rewrite.

All staged paths are exact Patient allowlist entries, including ignored tests via per-path force-add. No shared/other-service source, generated binary/DB/private logs/env/credentials. All287 previous tests retained; Windows293PASS; Linux classification in report36/machine evidence. Before push fetch/recheck both refs, verify ancestor relationships and exact diff, then commit/push only on isolated branch. The full reactor Notification failure blocks merging main, but does not prevent publishing this explicitly Draft Patient proposal.

Publication status before initial commit: PENDING. The finalized implementation SHA, push result, new Draft PR URL, CI snapshot and follow-up evidence are recorded after publication in `patient-http-uuid-finalization.json` and an append-only delivery section below. New PR title must be `feat(patient): HTTP local demo and full UUID migration`, head bao/base main, DRAFT. No Ready/merge operation authorized by this task.

## Published delivery evidence

Implementation commit `38989b9c458448591ed783c2f12db3a44e40ed4e`, tree `1d61368e9a653fe49a39ff92fef44f696e59e373`, both parents `3f833711e282e06b71cc88f7030ce762d78ec201` and `a097c67dbbbf399ec02eeb5b26b801946b7a6f41`. Exact committed tree Patient verify293PASS, zero failures/errors/skipped; Windows/Linux same293 testcase identities. Fast-forward push to bao completed; main unchanged.

NEW [Draft PR#4](https://github.com/duckhisuu/FClinic/pull/4), exact title `feat(patient): HTTP local demo and full UUID migration`, bao → main. GitHub verified93Patient-only paths, no deleted files. PR#3 is historical and was not reused or modified.

Initial source-commit GitHub workflow41330087384 completed FAILURE: backend124918146656 failed in Notification AppointmentNotificationEvent.java:19, missing AppointmentSnapshotDto (bounded log confirms the same local error); frontend124918146420 SUCCESS. This is not a Patient test failure. Follow-up delivery metadata commit changes only reports38/39 and this evidence JSON; runtime/source/fixtures remain exactly the tested implementation. New head CI reruns are visible on PR; no check bypass/Ready/merge action.

The implementation SHA is embedded above; the self-referential evidence commit SHA is obtained from current bao/PR head and external final remote verification. Full final blob hashes live in the external publication manifest; no binaries/private logs/DB/cache/JDK archive are committed.
