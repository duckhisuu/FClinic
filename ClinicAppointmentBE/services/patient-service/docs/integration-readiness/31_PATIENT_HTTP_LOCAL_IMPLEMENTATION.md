# 31 — HTTP loopback transport

The latest HTTP+UUID task permits HTTP loopback as a supported scheme without the earlier task's profile/allow-local-http opt-in requirement. Reference ports are still disabled by default. Enabling reference profiles requires separate explicit credentials. `application-local.yml` supplies localhost collection URI defaults; environment variables override. HTTP is permitted only for exact `localhost`, `127.0.0.1`, `[::1]`; IPv6 policy accepted, actual IPv6 socket transport not claimed.

ReferenceSettings rejects external/LAN/public/0.0.0.0/other 127 addresses, Docker names, arbitrary hostnames, userInfo/query/fragment, missing/trailing/encoded/dot/double-slash paths and invalid port. HTTPS remains supported with original CA/hostname/TLS1.2/1.3 verification. No trust-all or TLS hostname relaxation. HTTP loopback explicitly selects NO_PROXY so credentials cannot be routed via a configured proxy.

ReferenceHttpClient still uses dedicated encoded Basic, Accept application/json, exact X-FClinic-Reference-Contract, Cache-Control no-store, bounded response/timeouts/concurrency, no redirects/retries/cache, no human token forwarding and no network within a write transaction. Strict JSON/content-type/version/error/UUID/resource validation remains. No HTTPS failure → HTTP fallback. No HTTP change to identity or scope adapters.

Only disposable demo credentials and synthetic data may traverse HTTP. A normal HTTP REST endpoint returning legacy Long IDs or missing the new version marker remains incompatible. HTTP support alone does not prove provider contract integration.
