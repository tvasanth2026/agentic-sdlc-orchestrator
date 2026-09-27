package com.vasanth.agenticsdlcorchestrator.validation;

import com.vasanth.agenticsdlcorchestrator.patch.FileOperationProposalAgent;
import com.vasanth.agenticsdlcorchestrator.patch.FileOperationProposalAgent.RepairProposalContext;
import com.vasanth.agenticsdlcorchestrator.patch.PatchApplicationService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.nio.file.Files;
import java.io.IOException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ModelRepairCoordinator implements RepairCoordinator {
    private final FileOperationProposalAgent agent;
    private final PatchApplicationService patches;
    private final JdbcTemplate jdbc;

    public ModelRepairCoordinator(FileOperationProposalAgent agent, PatchApplicationService patches, JdbcTemplate jdbc) {
        this.agent = agent;
        this.patches = patches;
        this.jdbc = jdbc;
    }

    @Override
    public Optional<UUID> diagnoseProposeValidateAndApply(RepairContext context) {
        List<PriorEvidence> evidence = jdbc.query("select p.proposal_json, p.proposal_hash, o.requirement_id, "
                        + "o.acceptance_criterion_ids, o.relative_path from patch_proposals p join proposed_file_operations o "
                        + "on o.proposal_id=p.id where p.revision_id=? order by p.created_at desc, o.operation_index asc",
                (rs, row) -> new PriorEvidence(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5)),
                context.revisionId());
        if (evidence.isEmpty()) return Optional.empty();
        PriorEvidence latest = evidence.getFirst();
        String failure = bounded(context.failure().classification() + "\n" + context.failure().stdout()
                + "\n" + context.failure().stderr(), 24000);
        RepairProposalContext proposalContext = new RepairProposalContext(latest.requirementId(),
                parseCriteria(latest.criteria()), failure,
                relevantSources(context, evidence),
                latest.json(), List.of(latest.hash(), sha256(failure)));
        return agent.proposeRepair(proposalContext).map(proposal -> patches.applyRepairProposal(context.revisionId(), proposal));
    }

    private List<String> parseCriteria(String json) {
        return java.util.regex.Pattern.compile("(?<=\\\")[A-Za-z]+-[0-9]+(?=\\\")").matcher(json).results()
                .map(java.util.regex.MatchResult::group).distinct().toList();
    }

    private String relevantSources(RepairContext context, List<PriorEvidence> evidence) {
        StringBuilder result = new StringBuilder();
        evidence.stream().map(PriorEvidence::relativePath).distinct().limit(20).forEach(relativePath -> {
            var path = context.workspace().resolve(relativePath).normalize();
            if (!path.startsWith(context.workspace().normalize()) || !Files.isRegularFile(path)) return;
            try {
                String content = Files.readString(path);
                result.append("PATH: ").append(relativePath).append("\nSHA256: ").append(sha256(content))
                        .append("\n").append(bounded(content, 8000)).append("\n");
            } catch (IOException ignored) {
                result.append("PATH: ").append(relativePath).append(" [unreadable]\n");
            }
        });
        return bounded(result.toString(), 24000);
    }

    private String bounded(String value, int maximum) { return value.length() <= maximum ? value : value.substring(0, maximum); }
    private String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
    private record PriorEvidence(String json, String hash, String requirementId, String criteria, String relativePath) {}
}
