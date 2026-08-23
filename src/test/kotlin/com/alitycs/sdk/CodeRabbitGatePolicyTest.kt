package com.alitycs.sdk

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CodeRabbitGatePolicyTest {
    private val repositoryRoot: Path = Path.of("").toAbsolutePath()

    @Test
    fun `gate executes only from the trusted base branch`() {
        val workflow = read(".github/workflows/coderabbit-gate.yml")

        assertTrue(workflow.contains("  pull_request_target:"))
        assertFalse(Regex("(?m)^  pull_request:").containsMatchIn(workflow))
        assertFalse(Regex("(?m)^  pull_request_review:").containsMatchIn(workflow))
        assertFalse(workflow.contains("actions/checkout"))
        assertTrue(workflow.contains("environment: coderabbit-gate"))
        assertTrue(workflow.contains("const gateName = \"Alitycs CodeRabbit Gate\""))
        assertTrue(workflow.contains("head_sha: headSha"))
        assertTrue(workflow.contains("file.filename === gatePath"))
        assertTrue(workflow.contains("permission-checks: write"))
        assertTrue(workflow.contains("permission-contents: read"))
        assertTrue(workflow.contains("permission-pull-requests: read"))
        assertTrue(
            Regex("actions/create-github-app-token@[0-9a-f]{40}").containsMatchIn(workflow),
        )
        assertTrue(Regex("actions/github-script@[0-9a-f]{40}").containsMatchIn(workflow))
    }

    @Test
    fun `CodeRabbit policy is standalone and fail closed`() {
        val policy = read(".coderabbit.yaml")

        assertFalse(Regex("(?m)^inheritance:").containsMatchIn(policy))
        assertFalse(policy.contains("path_filters:"))
        assertTrue(policy.contains("request_changes_workflow: true"))
        assertTrue(policy.contains("fail_commit_status: true"))
        assertTrue(policy.contains("auto_incremental_review: true"))
        assertTrue(policy.contains("- \"dependabot[bot]\""))
        assertTrue(policy.contains("- \"renovate[bot]\""))
        assertTrue(policy.contains("- \"github-actions[bot]\""))
        assertTrue(Files.isRegularFile(repositoryRoot.resolve("docs/coderabbit.md")))
    }

    private fun read(relativePath: String): String =
        Files.readString(repositoryRoot.resolve(relativePath))
}
