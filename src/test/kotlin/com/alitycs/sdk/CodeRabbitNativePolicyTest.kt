package com.alitycs.sdk

import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CodeRabbitNativePolicyTest {
    private val repositoryRoot: Path =
        Path.of(requireNotNull(System.getProperty("alitycs.repositoryRoot")))

    @Test
    fun `native CodeRabbit review is enabled without custom gate machinery`() {
        val policy = read(".coderabbit.yaml")
        val docs = read("docs/coderabbit.md")

        listOf(
            "request_changes_workflow: true",
            "review_progress: true",
            "fail_commit_status: true",
            "enabled: true",
            "auto_incremental_review: true",
            "github-checks:",
        ).forEach { requiredSetting ->
            assertTrue(policy.contains(requiredSetting), requiredSetting)
        }

        assertFalse(policy.contains("ignore_usernames:"))
        assertTrue(policy.contains("latest pull-request head coverage"))
        assertTrue(policy.contains("CODEOWNER approval is the"))
        assertTrue(policy.contains("trust boundary for changes"))
        assertFalse(Files.exists(repositoryRoot.resolve(".github/workflows/coderabbit-gate.yml")))
        assertFalse(
            Files.exists(repositoryRoot.resolve(".github/workflows/coderabbit-review-event.yml")),
        )
        assertFalse(Files.exists(repositoryRoot.resolve("scripts/audit-coderabbit-github.sh")))

        assertTrue(docs.contains("completion signal, not an approval proxy"))
        assertTrue(docs.contains("No shared policy repository or custom GitHub App is required"))
        assertFalse(docs.contains("Alitycs CodeRabbit Gate"))
        assertFalse(docs.contains("/coderabbit-gate"))
    }

    @Test
    fun `governance files have an active code owner`() {
        val codeowners = read(".github/CODEOWNERS")

        listOf(
            "/.coderabbit.yaml @bulanovdm",
            "/.github/ @bulanovdm",
            "/docs/coderabbit.md @bulanovdm",
            "/scripts/coderabbit-schema.v2.json @bulanovdm",
            "/scripts/coderabbit-validator-requirements.txt @bulanovdm",
            "/scripts/validate-coderabbit.sh @bulanovdm",
            "/scripts/verify-workflow-pins.rb @bulanovdm",
            "/CONTRIBUTING.md @bulanovdm",
        ).forEach { ownership -> assertTrue(codeowners.contains(ownership), ownership) }
    }

    @Test
    fun `CodeRabbit policy validation is pinned and credential free`() {
        val workflow = read(".github/workflows/ci.yml")
        val validator = read("scripts/validate-coderabbit.sh")

        assertTrue(Files.isRegularFile(repositoryRoot.resolve("scripts/coderabbit-schema.v2.json")))
        assertTrue(
            Files.isRegularFile(
                repositoryRoot.resolve("scripts/coderabbit-validator-requirements.txt"),
            ),
        )
        assertTrue(workflow.contains("Detect CodeRabbit validation input changes"))
        assertTrue(workflow.contains("python-version: \"3.14.7\""))
        assertTrue(workflow.contains("run: ./scripts/validate-coderabbit.sh"))
        assertTrue(validator.contains("coderabbit-schema.v2.json"))
        assertTrue(validator.contains("coderabbit-validator-requirements.txt"))
        assertTrue(validator.contains("--require-hashes"))
        assertFalse(validator.contains("https://coderabbit.ai"))
    }

    @Test
    fun `live schema drift remains a non-gating maintenance check`() {
        val workflow = read(".github/workflows/coderabbit-schema-drift.yml")
        val docs = read("docs/coderabbit.md")

        assertTrue(workflow.contains("  schedule:"))
        assertTrue(workflow.contains("  workflow_dispatch:"))
        assertFalse(Regex("(?m)^  pull_request:").containsMatchIn(workflow))
        assertFalse(Regex("(?m)^  push:").containsMatchIn(workflow))
        assertTrue(workflow.contains("https://coderabbit.ai/integrations/schema.v2.json"))
        assertTrue(docs.contains("maintenance alert, not a required merge check"))
    }

    @Test
    fun `workflow and release hardening remain enabled`() {
        val ci = read(".github/workflows/ci.yml")
        val release = read(".github/workflows/release.yml")

        assertTrue(Files.isRegularFile(repositoryRoot.resolve("scripts/verify-workflow-pins.rb")))
        assertTrue(ci.contains("run: ./scripts/verify-workflow-pins.rb"))
        assertTrue(ci.contains("runs-on: ubuntu-24.04"))
        assertTrue(release.contains("permissions: {}"))
        assertTrue(release.contains("Verify tag targets reviewed main history"))
        assertTrue(release.contains("Recheck immutable release tag"))
        assertTrue(release.contains("git merge-base --is-ancestor"))
        assertFalse(release.contains("concurrency:"))
        assertFalse(readAllWorkflows().contains("runs-on: ubuntu-latest"))
    }

    private fun readAllWorkflows(): String =
        Files.walk(repositoryRoot.resolve(".github/workflows")).use { paths ->
            paths
                .filter(Files::isRegularFile)
                .sorted()
                .map { Files.readString(it) }
                .collect(Collectors.joining("\n"))
        }

    private fun read(relativePath: String): String =
        Files.readString(repositoryRoot.resolve(relativePath))
}
