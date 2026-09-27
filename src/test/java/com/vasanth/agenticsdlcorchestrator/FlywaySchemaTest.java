package com.vasanth.agenticsdlcorchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class FlywaySchemaTest {
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void createsControlAndExecutionPlaneTables() {
        List<String> tables = jdbcTemplate.queryForList("""
                select table_name from information_schema.tables
                where table_schema = 'public'
                """, String.class);

        assertThat(tables).contains(
                "workflows", "workflow_revisions", "agent_tasks", "task_dependencies",
                "execution_attempts", "engineering_artifacts", "validation_results",
                "approvals", "policy_decisions", "audit_events", "requirement_analyses",
                "requirement_items", "clarification_questions", "clarifications",
                "revision_outputs", "repository_analyses", "engineering_plans",
                "agent_invocations",
                "patch_proposals", "proposed_file_operations", "applied_file_operations",
                "flyway_schema_history");
        Integer successful = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where version = '1' and success = true",
                Integer.class);
        assertThat(successful).isOne();
        Integer v2 = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where version = '2' and success = true",
                Integer.class);
        assertThat(v2).isOne();
        Integer v3 = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where version = '3' and success = true",
                Integer.class);
        assertThat(v3).isOne();
        Integer v4 = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where version = '4' and success = true",
                Integer.class);
        assertThat(v4).isOne();
        Integer v5 = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where version = '5' and success = true",
                Integer.class);
        assertThat(v5).isOne();
    }
}
