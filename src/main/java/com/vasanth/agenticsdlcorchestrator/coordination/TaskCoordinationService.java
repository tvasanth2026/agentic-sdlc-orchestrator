package com.vasanth.agenticsdlcorchestrator.coordination;

import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

@Service
public class TaskCoordinationService {
    private final JdbcTemplate jdbc;
    private final CoordinationProperties properties;
    private final MeterRegistry meters;

    public TaskCoordinationService(JdbcTemplate jdbc, CoordinationProperties properties, MeterRegistry meters) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.meters = meters;
    }

    @Transactional
    public Optional<Claim> claim(UUID taskId) {
        Instant now = Instant.now();
        List<Claim> current = jdbc.query("select worker_id, fencing_token, lease_expires_at from task_claims where task_id=? for update",
                (rs, row) -> new Claim(taskId, rs.getString(1), rs.getLong(2), rs.getTimestamp(3).toInstant()), taskId);
        if (!current.isEmpty() && current.getFirst().leaseExpiresAt().isAfter(now)) return Optional.empty();
        long token = current.isEmpty() ? 1 : current.getFirst().fencingToken() + 1;
        Instant expiry = now.plus(properties.leaseDuration());
        if (current.isEmpty()) {
            jdbc.update("insert into task_claims(task_id, worker_id, fencing_token, lease_expires_at, heartbeat_at, claimed_at) values (?, ?, ?, ?, ?, ?)",
                    taskId, properties.workerId(), token, Timestamp.from(expiry), Timestamp.from(now), Timestamp.from(now));
        } else {
            jdbc.update("update task_claims set worker_id=?, fencing_token=?, lease_expires_at=?, heartbeat_at=?, claimed_at=? where task_id=?",
                    properties.workerId(), token, Timestamp.from(expiry), Timestamp.from(now), Timestamp.from(now), taskId);
            meters.counter("agentic.lease.takeovers").increment();
        }
        jdbc.update("update agent_tasks set state='RUNNING', updated_at=? where id=? and state in ('READY','RUNNING','RETRY_SCHEDULED')",
                Timestamp.from(now), taskId);
        return Optional.of(new Claim(taskId, properties.workerId(), token, expiry));
    }

    @Transactional
    public Claim heartbeat(UUID taskId, long fencingToken) {
        Instant now = Instant.now();
        Instant expiry = now.plus(properties.leaseDuration());
        int updated = jdbc.update("update task_claims set lease_expires_at=?, heartbeat_at=? where task_id=? and worker_id=? and fencing_token=? and lease_expires_at>?",
                Timestamp.from(expiry), Timestamp.from(now), taskId, properties.workerId(), fencingToken, Timestamp.from(now));
        if (updated != 1) throw new StaleWorkerException("claim is stale or belongs to another worker");
        return new Claim(taskId, properties.workerId(), fencingToken, expiry);
    }

    @Transactional
    public boolean complete(UUID taskId, long fencingToken, String effectKey, UUID revisionId, String evidenceHash) {
        Integer owned = jdbc.queryForObject("select count(*) from task_claims where task_id=? and worker_id=? and fencing_token=? and lease_expires_at>?",
                Integer.class, taskId, properties.workerId(), fencingToken, Timestamp.from(Instant.now()));
        if (owned == null || owned != 1) throw new StaleWorkerException("stale worker completion rejected");
        int inserted = jdbc.update("insert into idempotent_effects(effect_key, revision_id, effect_type, evidence_hash, created_at) "
                        + "select ?, ?, 'TASK_COMPLETION', ?, ? where not exists (select 1 from idempotent_effects where effect_key=?)",
                effectKey, revisionId, evidenceHash, Timestamp.from(Instant.now()), effectKey);
        if (inserted == 0) return false;
        jdbc.update("update agent_tasks set state='COMPLETED', updated_at=? where id=?", Timestamp.from(Instant.now()), taskId);
        jdbc.update("delete from task_claims where task_id=? and worker_id=? and fencing_token=?",
                taskId, properties.workerId(), fencingToken);
        return true;
    }

    @Scheduled(fixedDelayString = "${agentic.coordination.recovery-interval:PT30S}")
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void recoverExpiredClaims() {
        int recovered = jdbc.update("update agent_tasks set state='READY', updated_at=? where id in "
                        + "(select task_id from task_claims where lease_expires_at<=?) and state='RUNNING'",
                Timestamp.from(Instant.now()), Timestamp.from(Instant.now()));
        jdbc.update("delete from task_claims where lease_expires_at<=?", Timestamp.from(Instant.now()));
        if (recovered > 0) meters.counter("agentic.recovery.tasks", "reason", "expired_lease").increment(recovered);
    }

    public record Claim(UUID taskId, String workerId, long fencingToken, Instant leaseExpiresAt) {}
    public static final class StaleWorkerException extends RuntimeException {
        public StaleWorkerException(String message) { super(message); }
    }
}
