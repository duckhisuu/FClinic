# Conflict resolution

Git conflicts: **0**. main was a descendant of Primary HEAD; fast-forward preserved all published history. No blanket ours/theirs, rebase or squash.

Semantic integration fixes:

- Removed production mock doctors, fake booking, numeric Patient fallback and fake Notification SENT/retry from services/api.js. Compatibility exports delegate to the canonical Appointment HTTP transport; retired mutations reject explicitly.
- Auth API shares the canonical bearer/envelope/refresh client. Public auth failures do not recursively refresh. Refresh is coalesced and bounded; network failures do not replay Patient writes.
- Patient v3 receives the real session. UI action hints are computed on Auth from active exact assignments; Patient independently enforces every request.
- Doctor navigation reaches Patient profiles; Medical Record forms use the authenticated Doctor UUID. No numeric-to-UUID conversion.
- Product Patient module has no Preview switch/query fallback. Fixture scenario controls moved to a test harness; existing assertions preserved.
- One Vite /api proxy targets Gateway. Auth uses HTTPS with a trusted PKCS12 anchor; certificate verification remains enabled.
- Notification UUID/schema3 incompatibility is exposed as an unavailable personal notification flow; no fabricated data or success.
