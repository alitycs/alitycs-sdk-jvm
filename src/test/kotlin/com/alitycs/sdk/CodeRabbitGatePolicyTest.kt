package com.alitycs.sdk

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CodeRabbitGatePolicyTest {
    private val repositoryRoot: Path =
        Path.of(requireNotNull(System.getProperty("alitycs.repositoryRoot")))

    @Test
    fun `gate executes only from the trusted base branch`() {
        val workflow = read(".github/workflows/coderabbit-gate.yml")
        val reviewSignal = read(".github/workflows/coderabbit-review-event.yml")

        assertTrue(workflow.contains("  push:\n    branches: [main]"))
        assertTrue(workflow.contains("  pull_request_target:\n    branches: [main]"))
        assertTrue(
            workflow.contains(
                "types: [opened, edited, reopened, synchronize, ready_for_review]",
            ),
        )
        assertTrue(workflow.contains("  workflow_run:"))
        assertTrue(Regex("(?m)^permissions: \\{\\}$").containsMatchIn(workflow))
        assertFalse(Regex("(?m)^  pull_request:").containsMatchIn(workflow))
        assertFalse(Regex("(?m)^  pull_request_review:").containsMatchIn(workflow))
        assertFalse(workflow.contains("actions/checkout"))
        assertFalse(workflow.contains("concurrency:"))
        assertTrue(workflow.contains("name: Validate reconciliation trigger"))
        assertTrue(workflow.contains("actions: read"))
        assertTrue(workflow.contains("pull-requests: read"))
        assertTrue(workflow.contains("environment: coderabbit-gate"))
        assertTrue(workflow.contains("const gateName = \"Alitycs CodeRabbit Gate\""))
        assertTrue(workflow.contains("head_sha: headSha"))
        assertTrue(workflow.contains("alitycs-coderabbit-gate/v9:"))
        assertTrue(workflow.contains("review.commit_id === headSha"))
        assertTrue(workflow.contains("context.eventName === \"push\""))
        assertTrue(workflow.contains("github.rest.pulls.get"))
        assertTrue(workflow.contains("run.workflow_id !== canonical.data.id"))
        assertTrue(workflow.contains("canonical.data.name !== \"CodeRabbit review event\""))
        assertTrue(workflow.contains("runPath !== reviewSignalPath"))
        assertTrue(workflow.contains("pullRequest.merge_commit_sha === runHeadSha"))
        assertTrue(workflow.contains("new Set(pullNumbers).size !== 1"))
        assertTrue(workflow.contains("const workflowsPath = \".github/workflows\""))
        assertTrue(workflow.contains("const protectedObjects = ["))
        assertTrue(workflow.contains("{ path: workflowsPath, mode: \"040000\", type: \"tree\" }"))
        assertTrue(workflow.contains("github.rest.git.getCommit"))
        assertTrue(workflow.contains("github.rest.git.getRef"))
        assertTrue(workflow.contains("github.rest.git.getTree"))
        assertTrue(workflow.contains("response.data.truncated"))
        assertTrue(workflow.contains("entry.type === expectedType && entry.mode === expectedMode"))
        assertTrue(workflow.contains("github.rest.checks.create"))
        assertTrue(workflow.contains("github.rest.checks.listForRef"))
        assertTrue(workflow.contains("filter: \"all\""))
        assertTrue(workflow.contains("app_id: gateAppId"))
        assertTrue(workflow.contains("Superseded gate check"))
        assertTrue(workflow.contains("current.data.external_id === externalId"))
        assertTrue(workflow.contains("currentPullRequest.head.sha !== headSha"))
        assertTrue(workflow.contains("currentMainSha !== baseSha"))
        assertTrue(workflow.contains("finalMainSha !== baseSha"))
        assertEquals(3, Regex("await github.rest.git.getRef").findAll(workflow).count())
        assertFalse(workflow.contains("currentPullRequest.base.sha !== baseSha"))
        assertTrue(workflow.contains("github.rest.pulls.list"))
        assertTrue(workflow.contains("sameHeadPullRequests.length !== 1"))
        assertFalse(workflow.contains("github.rest.pulls.listFiles"))
        assertTrue(workflow.contains("permission-actions: read"))
        assertTrue(workflow.contains("permission-checks: write"))
        assertTrue(workflow.contains("permission-contents: read"))
        assertTrue(workflow.contains("permission-pull-requests: read"))
        val inspectionTokenStep =
            workflow
                .substringAfter("      - name: Mint the selected-repository inspection token")
                .substringBefore("      - name: Mint the dedicated gate token")
        assertTrue(inspectionTokenStep.contains("id: installation-token"))
        assertTrue(inspectionTokenStep.contains("owner: \${{ github.repository_owner }}"))
        assertTrue(inspectionTokenStep.contains("permission-contents: read"))
        assertFalse(inspectionTokenStep.contains("permission-checks:"))
        assertFalse(inspectionTokenStep.contains("permission-pull-requests:"))
        assertTrue(workflow.contains("const installationGithub = getOctokit(installationToken);"))
        assertTrue(workflow.contains("await installationGithub.paginate("))
        assertFalse(workflow.contains("headers: { authorization:"))
        assertTrue(workflow.contains("\"GET /installation/repositories\""))
        assertTrue(
            workflow.contains(
                "const sdkRepositoryPattern = /^alitycs-sdk-[a-z0-9]+(?:-[a-z0-9]+)*$/;",
            ),
        )
        assertEquals(3, Regex("await inspectInstallation\\(\\)").findAll(workflow).count())
        assertTrue(workflow.contains("!initialInstallation.includesCurrent"))
        assertTrue(workflow.contains("!currentInstallation.includesCurrent"))
        assertTrue(workflow.contains("!finalInstallation.includesCurrent"))
        assertTrue(
            workflow.contains(
                "currentInstallation.fingerprint !== initialInstallation.fingerprint",
            ),
        )
        assertTrue(
            workflow.contains(
                "finalInstallation.fingerprint !== initialInstallation.fingerprint",
            ),
        )
        val supersedePrevious =
            workflow.indexOf("await Promise.all(previousChecks.map(supersede));")
        val createCanonical =
            workflow.indexOf("const created = await github.rest.checks.create({")
        assertTrue(supersedePrevious >= 0)
        assertTrue(createCanonical > supersedePrevious)
        assertTrue(workflow.contains("stableChecks.slice(1).map(supersede)"))
        assertFalse(workflow.contains("previousChecks.map((checkRun)"))
        assertTrue(workflow.contains("deploymentPolicy?.custom_branch_policies === true"))
        assertTrue(workflow.contains("branchPolicies[0].name === \"main\""))
        assertTrue(workflow.contains("reviewActor !== \"coderabbitai[bot]\""))
        assertTrue(workflow.contains("!context.payload.changes?.base"))
        assertTrue(workflow.contains("error.status === 403"))
        assertTrue(
            workflow.contains(
                "The router token cannot read collaborator permission; treating the actor as untrusted.",
            ),
        )
        assertTrue(
            Regex("actions/create-github-app-token@[0-9a-f]{40}").containsMatchIn(workflow),
        )
        assertTrue(Regex("actions/github-script@[0-9a-f]{40}").containsMatchIn(workflow))

        assertTrue(reviewSignal.contains("  pull_request_review:\n    types:"))
        assertTrue(reviewSignal.contains("types: [submitted, dismissed]"))
        assertTrue(reviewSignal.contains("permissions: {}"))
        assertFalse(reviewSignal.contains("actions/checkout"))
        assertFalse(reviewSignal.contains("secrets."))
        assertFalse(reviewSignal.contains("environment:"))
        assertFalse(reviewSignal.contains("concurrency:"))
    }

    @Test
    fun `release builds are unprivileged and tags must target main history`() {
        val workflow = read(".github/workflows/release.yml")
        val buildJob = workflow.substringAfter("  build:\n").substringBefore("\n  release:\n")
        val releaseJob = workflow.substringAfter("\n  release:\n")

        assertTrue(Regex("(?m)^permissions: \\{\\}$").containsMatchIn(workflow))
        assertTrue(buildJob.contains("permissions:\n      contents: read"))
        assertFalse(buildJob.contains("contents: write"))
        assertFalse(buildJob.contains("id-token: write"))
        assertFalse(buildJob.contains("attestations: write"))
        assertTrue(buildJob.contains("persist-credentials: false"))
        assertTrue(
            buildJob.contains(
                "git fetch --no-tags --force origin \"+refs/heads/main:refs/remotes/origin/main\"",
            ),
        )
        assertTrue(buildJob.contains("git cat-file -t \"${'$'}GITHUB_REF\""))
        assertTrue(
            buildJob.contains(
                "git merge-base --is-ancestor \"${'$'}tag_commit\" \"${'$'}main_commit\"",
            ),
        )
        assertTrue(Regex("actions/upload-artifact@[0-9a-f]{40}").containsMatchIn(buildJob))
        assertTrue(releaseJob.contains("needs: build"))
        assertTrue(releaseJob.contains("attestations: write"))
        assertTrue(releaseJob.contains("contents: write"))
        assertTrue(releaseJob.contains("id-token: write"))
        assertTrue(
            Regex("actions/download-artifact@[0-9a-f]{40}").containsMatchIn(releaseJob),
        )
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

        val policyBots =
            Regex("\"([^\"]+\\[bot])\"")
                .findAll(
                    Regex(
                        "(?ms)    ignore_usernames:\\n((?:      - \"[^\"]+\"\\n)+)",
                    ).find(policy)?.groupValues?.get(1)
                        ?: error("CodeRabbit ignored-bot policy is missing"),
                ).map { it.groupValues[1] }.toSet()
        val gateBots =
            Regex("\"([^\"]+\\[bot])\"")
                .findAll(
                    Regex(
                        "(?ms)const ignoredBots = new Set\\(\\[\\n(.*?)\\n            \\]\\);",
                    ).find(read(".github/workflows/coderabbit-gate.yml"))
                        ?.groupValues
                        ?.get(1)
                        ?: error("Gate ignored-bot policy is missing"),
                ).map { it.groupValues[1] }.toSet()
        assertEquals(policyBots, gateBots)
    }

    @Test
    fun `pinned validation only gates relevant pull request inputs`() {
        val workflow = read(".github/workflows/ci.yml")
        val docs = read("docs/coderabbit.md")
        val validator = read("scripts/validate-coderabbit.sh")
        val requirements = read("scripts/coderabbit-validator-requirements.txt")

        assertTrue(workflow.contains("Detect CodeRabbit validation input changes"))
        assertTrue(
            workflow.contains("if: steps.coderabbit-config.outputs.changed == 'true'"),
        )
        listOf(
            ".coderabbit.yaml",
            "scripts/coderabbit-schema.v2.json",
            "scripts/coderabbit-validator-requirements.txt",
            "scripts/validate-coderabbit.sh",
        ).forEach { path -> assertTrue(workflow.contains("\"$path\"")) }
        assertTrue(
            workflow.contains(
                "git diff --quiet \"\$BASE_SHA\" \"\$HEAD_SHA\" -- \"\${validation_paths[@]}\"",
            ),
        )
        assertTrue(workflow.contains("./scripts/verify-workflow-pins.rb"))
        assertTrue(Regex("ruby/setup-ruby@[0-9a-f]{40}").containsMatchIn(workflow))
        assertTrue(workflow.contains("ruby-version: \"3.3.12\""))
        assertTrue(validator.contains("--require-hashes"))
        assertTrue(validator.contains("coderabbit-schema.v2.json"))
        assertFalse(validator.contains("command -v check-jsonschema"))
        assertFalse(validator.contains("https://coderabbit.ai"))
        assertTrue(requirements.contains("check-jsonschema==0.37.4"))
        assertTrue(requirements.contains("--hash=sha256:"))
        assertTrue(
            Regex(
                "From a clean checkout of the merged `main`, rerun `\\./scripts/verify-workflow-pins\\.rb`,\\s+`\\./scripts/validate-coderabbit\\.sh`, and the repository policy tests, then open",
                RegexOption.DOT_MATCHES_ALL,
            ).containsMatchIn(docs),
        )
        assertTrue(
            Regex(
                "From a clean checkout of the new `main`, rerun the workflow-pin\\s+verifier, pinned-schema validator, and policy tests before opening a canary",
                RegexOption.DOT_MATCHES_ALL,
            ).containsMatchIn(docs),
        )
        assertTrue(
            requirements.contains("# printf 'check-jsonschema==0.37.4\\n' | uv pip compile"),
        )
        assertFalse(
            requirements.contains("# printf 'check-jsonschema==0.37.4\\\\n' | uv pip compile"),
        )
        assertTrue(
            Regex("\\./scripts/validate-coderabbit\\.sh").findAll(docs).count() >= 3,
        )
    }

    @Test
    fun `workflow action verifier structurally rejects mutable references`() {
        val current = runPinVerifier()
        assertEquals(0, current.exitCode, current.stderr)
        assertTrue(current.stdout.contains("immutable third-party"))

        val invalid =
            runPinVerifier(
                """
                anchors:
                  action: &mutable-action actions/checkout@v4
                  key: &uses-key uses
                jobs:
                  reusable:
                    uses: alitycs/reusable/.github/workflows/ci.yml@main
                  invalid-local-workflow:
                    uses: $/.github/actions/not-a-workflow
                  invalid:
                    steps:
                      - "uses" : actions/checkout@v4
                      - { uses: actions/setup-java@v4 }
                      - uses: docker://alpine:latest
                      - uses: $/.github/workflows/not-an-action.yml
                      - uses: $/.github/actions/local-action@main
                      - *uses-key: *mutable-action
                      - uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1
                        uses: actions/cache@v4
                """.trimIndent(),
            )
        assertEquals(1, invalid.exitCode)
        assertTrue(
            invalid.stderr.contains("\"alitycs/reusable/.github/workflows/ci.yml@main\""),
        )
        assertTrue(invalid.stderr.contains("\"actions/checkout@v4\""))
        assertTrue(invalid.stderr.contains("\"actions/setup-java@v4\""))
        assertTrue(invalid.stderr.contains("\"docker://alpine:latest\""))
        assertTrue(invalid.stderr.contains("\"actions/cache@v4\""))
        assertTrue(invalid.stderr.contains("\"$/.github/actions/not-a-workflow\""))
        assertTrue(invalid.stderr.contains("\"$/.github/workflows/not-an-action.yml\""))
        assertTrue(invalid.stderr.contains("\"$/.github/actions/local-action@main\""))

        val flowRedefinition =
            runPinVerifier(
                """
                { jobs: { invalid: { steps: [{ uses: &pin actions/checkout@v4 }, { uses: *pin }] } }, later: &pin actions/checkout@${"e".repeat(40)} }
                """.trimIndent(),
            )
        assertEquals(1, flowRedefinition.exitCode)
        assertEquals(
            2,
            Regex("\"actions/checkout@v4\"").findAll(flowRedefinition.stderr).count(),
        )

        val laterRedefinition =
            runPinVerifier(
                """
                defaults: &pin actions/checkout@v4
                jobs:
                  invalid:
                    steps:
                      - uses: *pin
                later: &pin actions/checkout@${"f".repeat(40)}
                """.trimIndent(),
            )
        assertEquals(1, laterRedefinition.exitCode)
        assertTrue(laterRedefinition.stderr.contains("\"actions/checkout@v4\""))

        val validRedefinition =
            runPinVerifier(
                """
                earlier: &pin actions/checkout@v4
                current: &pin actions/checkout@${"1".repeat(40)}
                jobs:
                  valid:
                    steps:
                      - uses: *pin
                """.trimIndent(),
            )
        assertEquals(0, validRedefinition.exitCode, validRedefinition.stderr)

        val crossDocumentAlias =
            runPinVerifier(
                """
                defaults: &pin actions/checkout@${"2".repeat(40)}
                ---
                jobs:
                  invalid:
                    steps:
                      - uses: *pin
                """.trimIndent(),
            )
        assertEquals(1, crossDocumentAlias.exitCode)
        assertTrue(crossDocumentAlias.stderr.contains("uses must be a scalar string"))

        val valid =
            runPinVerifier(
                """
                env:
                  uses: actions/root-environment@v4
                jobs:
                  valid:
                    uses: alitycs/reusable/.github/workflows/ci.yml@${"a".repeat(40)}
                    with:
                      uses: actions/reusable-input@v4
                  same-commit-workflow:
                    uses: $/.github/workflows/ci.yml
                  actions:
                    env:
                      uses: actions/job-environment@v4
                    steps:
                      - "uses": actions/checkout@${"b".repeat(40)}
                        with:
                          uses: actions/action-input@v4
                        env:
                          uses: actions/step-environment@v4
                      - { uses: "docker://ghcr.io/alitycs/build@sha256:${"c".repeat(64)}" }
                      - uses: ./local-action
                      - uses: $/.github/actions/local-action
                """.trimIndent(),
            )
        assertEquals(0, valid.exitCode, valid.stderr)

        val validComposite =
            runPinVerifier(
                """
                name: Fixture composite action
                description: Exercises non-action uses keys
                inputs:
                  uses:
                    description: A harmless input named uses
                    required: false
                runs:
                  using: composite
                  steps:
                    - shell: bash
                      run: echo ok
                      env:
                        uses: actions/composite-environment@v4
                    - uses: actions/checkout@${"d".repeat(40)}
                      with:
                        uses: actions/composite-input@v4
                    - uses: $/.github/actions/composite-local
                """.trimIndent(),
                "action.yml",
            )
        assertEquals(0, validComposite.exitCode, validComposite.stderr)

        val invalidComposite =
            runPinVerifier(
                """
                name: Fixture composite action
                description: Contains a real mutable action reference
                inputs:
                  uses:
                    description: A harmless input named uses
                runs:
                  using: composite
                  steps:
                    - uses: actions/checkout@v4
                      with:
                        uses: actions/harmless-input@v4
                    - uses: $/.github/workflows/not-an-action.yml
                """.trimIndent(),
                "action.yaml",
            )
        assertEquals(1, invalidComposite.exitCode)
        assertTrue(invalidComposite.stderr.contains("\"actions/checkout@v4\""))
        assertTrue(
            invalidComposite.stderr.contains("\"$/.github/workflows/not-an-action.yml\""),
        )
        assertFalse(invalidComposite.stderr.contains("\"actions/harmless-input@v4\""))
    }

    @Test
    fun `repository audit reads the synchronized commit and exact app allowlists`() {
        val audit = read("scripts/audit-coderabbit-github.sh")
        val docs = read("docs/coderabbit.md")

        assertTrue(
            audit.contains("git show \"\${local_head}:scripts/verify-workflow-pins.rb\""),
        )
        assertTrue(audit.contains("ruby - --git-ref \"\$local_head\""))
        assertTrue(audit.contains("readonly protected_workflow_tree=\".github/workflows\""))
        assertTrue(audit.contains(".repository_selection == \"selected\""))
        assertTrue(audit.contains(".required_status_checks.checks | length == 3"))
        assertTrue(audit.contains(".permissions == {"))
        assertFalse(audit.contains(".permissions.checks =="))
        assertTrue(audit.contains("(.events // []) == []"))
        assertTrue(audit.contains("(.events | sort) == (["))
        assertTrue(audit.contains("first(.[] | .installations[] | select("))
        assertFalse(audit.contains("head -n 1"))
        assertTrue(
            audit.contains(
                "\"user/installations/\$installation_id/repositories?per_page=100\"",
            ),
        )
        assertTrue(audit.contains("must select every active public SDK"))
        val sdkRepositoryPattern = Regex("^alitycs-sdk-[a-z0-9]+(?:-[a-z0-9]+)*$")
        listOf("alitycs-sdk-js", "alitycs-sdk-jvm", "alitycs-sdk-react-native").forEach {
            name ->
            assertTrue(sdkRepositoryPattern.matches(name))
        }
        listOf(
            "alitycs-sdk-cpp.v2",
            "alitycs-sdk-cpp_v2",
            "alitycs-sdk--go",
            "alitycs-sdk-go-",
            "Alitycs-sdk-go",
        ).forEach { name ->
            assertFalse(sdkRepositoryPattern.matches(name))
        }
        assertTrue(
            audit.contains(
                "readonly sdk_repository_pattern='^alitycs-sdk-[a-z0-9]+(-[a-z0-9]+)*$'",
            ),
        )
        assertEquals(2, Regex("test\\(\\${'$'}sdk_pattern\\)").findAll(audit).count())
        assertTrue(docs.contains("lowercase alphanumeric name segments"))
        assertTrue(audit.contains("--argjson require_gate \"\$require_gate\""))
        assertTrue(audit.contains("if [[ \"\${1:-}\" == \"--pre-restore\" ]]"))
        assertTrue(docs.contains("./scripts/audit-coderabbit-github.sh --pre-restore"))
        assertTrue(docs.contains("run the same audit again without"))
        assertTrue(audit.contains("fail \"could not read the gate App ID\""))
        listOf(
            "\$gate_client_id_variable",
            "\$gate_app_id_variable",
            "\$gate_canary_sha_variable",
        ).forEach { variable ->
            assertTrue(audit.contains("fail \"$variable is missing from the repository\""))
        }
        assertTrue(
            audit.contains(
                "readonly gate_canary_sha_variable=\"ALITYCS_CODERABBIT_GATE_CANARY_SHA\"",
            ),
        )
        assertTrue(
            audit.contains(
                "repos/\$repository/actions/variables/\$gate_canary_sha_variable",
            ),
        )
        assertTrue(audit.contains("repos/\$repository/commits/\$canary_sha/check-runs"))
        assertTrue(audit.contains("--arg external_id_prefix \"alitycs-coderabbit-gate/v9:\""))
        assertTrue(audit.contains("--arg app_updated_at \"\$gate_app_updated_at\""))
        assertTrue(audit.contains("-H \"Time-Zone: UTC\""))
        assertTrue(
            audit.contains(
                "--arg installation_updated_at \"\$installation_updated_at\"",
            ),
        )
        assertTrue(
            audit.contains(
                "(.completed_at | epoch) > (\$installation_updated_at | epoch)",
            ),
        )
        assertTrue(audit.contains("(.completed_at | epoch) > (\$app_updated_at | epoch)"))
        assertTrue(audit.contains("--arg secret_updated_at \"\$gate_secret_updated_at\""))
        assertTrue(
            audit.contains(
                "(.completed_at | epoch) > (\$secret_updated_at | epoch)",
            ),
        )
        assertTrue(audit.contains("the recorded Gate App canary is missing, stale"))
    }

    private fun runPinVerifier(
        input: String? = null,
        label: String = "fixture.yml",
    ): VerifierResult {
        val command = mutableListOf("ruby", "scripts/verify-workflow-pins.rb")
        if (input != null) command.addAll(listOf("--stdin", label))
        val process = ProcessBuilder(command).directory(repositoryRoot.toFile()).start()
        process.outputStream.bufferedWriter().use { writer ->
            if (input != null) writer.write(input)
        }
        val stdout = process.inputStream.bufferedReader().readText()
        val stderr = process.errorStream.bufferedReader().readText()
        return VerifierResult(process.waitFor(), stdout, stderr)
    }

    private fun read(relativePath: String): String =
        Files.readString(repositoryRoot.resolve(relativePath))

    private data class VerifierResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
    )
}
