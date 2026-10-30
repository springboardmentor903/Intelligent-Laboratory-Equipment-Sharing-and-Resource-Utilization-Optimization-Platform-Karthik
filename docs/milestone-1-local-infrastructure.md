# Milestone 1 local infrastructure (T6)

`infra/docker-compose.milestone-1.yml` supplies isolated local development containers for the tracker’s required infrastructure categories:

| Tracker component | Local service | Endpoint after startup | Current application use |
|---|---|---|---|
| Redis | Redis 7 | `redis://localhost:6379` | Not wired into the application yet. |
| Elasticsearch | Elasticsearch 8 | `http://localhost:9200` | Not wired into equipment/institution search yet. |
| Kafka or RabbitMQ | RabbitMQ 4 | AMQP `localhost:5672`; management `http://localhost:15672` | Not wired into application events yet. |
| AWS S3 or Cloudinary | MinIO, S3-compatible local storage | API `http://localhost:9000`; console `http://localhost:9001` | Not wired into document upload yet. |

This file is development infrastructure only. It has no database service and does not target `labdb`. It does not add secrets, enable external services in Spring Boot, or make a cloud provider operational.

## Prerequisites

- Docker Engine / Docker Desktop with the Compose plugin.
- At least 2 GB memory available for Elasticsearch.
- Two locally supplied values for MinIO. Do not commit them and do not reuse production credentials.

PowerShell example:

```powershell
$env:MINIO_ROOT_USER = 'local-admin'
$env:MINIO_ROOT_PASSWORD = 'choose-a-long-local-only-password'
docker compose -f infra/docker-compose.milestone-1.yml up -d
```

Check container state with:

```powershell
docker compose -f infra/docker-compose.milestone-1.yml ps
```

Stop containers while retaining their local named volumes:

```powershell
docker compose -f infra/docker-compose.milestone-1.yml down
```

Removing volumes is intentionally not part of this guide because that destroys local infrastructure data.

## Safe verification sequence

1. Start the containers with the command above.
2. Confirm Redis responds with `redis-cli -h localhost ping`.
3. Request `http://localhost:9200` and confirm Elasticsearch returns cluster metadata.
4. Open the RabbitMQ management UI and sign in only with locally configured credentials if the image configuration is changed from its development defaults.
5. Open the MinIO console, create a development-only bucket, and record its endpoint in local configuration only.
6. Before wiring any application client, add a profile-gated configuration, a health/failure test, and an integration test against these disposable services.

## Current verification and limitations

Docker was unavailable during the Milestone 1 verification run. The Compose file is therefore source-reviewed only; no container, endpoint, broker message, cache entry, Elasticsearch index, or object upload has been exercised. T6 remains **PARTIAL**, because the project contains local setup structure but no application integration or live verification.

Do not set `GOOGLE_OAUTH_ENABLED`, `MAIL_ENABLED`, or any production credentials merely to start these containers. Those integrations are separately opt-in and use their own environment configuration.
