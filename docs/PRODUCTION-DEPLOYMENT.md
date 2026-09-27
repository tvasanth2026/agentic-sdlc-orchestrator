# Production Deployment

Build with `./mvnw clean verify`, then `docker compose build`. Containers run as UID 10001 and two orchestrators share PostgreSQL and a workspace volume.

Use the `prod` profile with `OIDC_ISSUER_URI`. JWT `roles` values map to `OPERATOR`, `CHANGE_APPROVER`, and `RELEASE_APPROVER`. Configure TLS with `SERVER_SSL_CERTIFICATE` and `SERVER_SSL_PRIVATE_KEY`, or terminate TLS at a trusted proxy while preserving forwarded headers. Local Basic authentication is excluded from `prod`.

Supply database, model, approval, and authentication credentials through environment injection or `CONFIG_TREE`; never bake them into an image. Replace all local defaults. Restrict Prometheus and database network access, use managed PostgreSQL backups, use per-instance worker IDs, and place the workspace on storage whose locking and recovery properties have been qualified for the deployment platform.

Lease ownership is transactional and uses monotonically increasing fencing tokens. Expired running tasks return to `READY`; `AWAITING_CLARIFICATION` and approval states are not recovered automatically. Stale completion is rejected and effect keys make completion idempotent.
