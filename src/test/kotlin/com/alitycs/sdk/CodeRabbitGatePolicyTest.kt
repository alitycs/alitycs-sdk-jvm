package com.alitycs.sdk

import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import java.util.concurrent.TimeUnit
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
        assertTrue(workflow.contains("if (unique.length > 256)"))
        assertTrue(
            workflow.contains("Refusing to create more than 256 reconciliation jobs."),
        )
        assertTrue(
            workflow.contains("if (!/^[0-9a-f]{40}${'$'}/.test(runHeadSha))"),
        )
        assertTrue(
            workflow.contains("The canonical review signal has no valid head SHA."),
        )
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
        val readme = read("README.md")
        val releaseGuide = read("docs/RELEASING.md")
        val policy = read(".coderabbit.yaml")
        val buildJob = workflow.substringAfter("  build:\n").substringBefore("\n  release:\n")
        val releaseJob = workflow.substringAfter("\n  release:\n")
        val releasePolicy =
            policy.substringAfter("    - path: \".github/workflows/release.yml\"")
                .substringBefore("\n    - path: \".github/workflows/**\"")

        assertTrue(Regex("(?m)^permissions: \\{\\}$").containsMatchIn(workflow))
        assertFalse(Regex("(?m)^concurrency:").containsMatchIn(workflow))
        assertTrue(releasePolicy.contains("Do not add an Actions concurrency key"))
        assertTrue(releasePolicy.contains("Concurrency groups are repository-global"))
        assertTrue(releaseGuide.contains("intentionally has no Actions concurrency group"))
        assertTrue(releasePolicy.contains("immutable tag and version publication identities"))
        assertTrue(
            Regex("immutable tag and version\\s+publication identities")
                .containsMatchIn(releaseGuide),
        )
        assertTrue(releaseGuide.contains("without that repository-global lock"))
        assertTrue(buildJob.contains("permissions:\n      contents: read"))
        assertFalse(buildJob.contains("contents: write"))
        assertFalse(buildJob.contains("id-token: write"))
        assertFalse(buildJob.contains("attestations: write"))
        assertTrue(buildJob.contains("persist-credentials: false"))
        assertTrue(buildJob.contains("tag_commit: \${{ steps.verify_tag.outputs.tag_commit }}"))
        assertTrue(buildJob.contains("tag_object: \${{ steps.verify_tag.outputs.tag_object }}"))
        assertTrue(
            buildJob.contains(
                "git fetch --no-tags --force origin \"+refs/heads/main:refs/remotes/origin/main\"",
            ),
        )
        assertTrue(buildJob.contains("git cat-file -t \"${'$'}GITHUB_REF\""))
        assertTrue(buildJob.contains("if [[ \"${'$'}tag_commit\" != \"${'$'}GITHUB_SHA\" ]]"))
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
        assertTrue(releaseJob.contains("Recheck immutable release tag"))
        assertTrue(
            releaseJob.contains(
                "\"+refs/tags/\${GITHUB_REF_NAME}:refs/tags/\${GITHUB_REF_NAME}\"",
            ),
        )
        assertTrue(
            releaseJob.contains(
                "[[ \"${'$'}current_tag_object\" != \"${'$'}EXPECTED_TAG_OBJECT\" ]]",
            ),
        )
        assertTrue(
            releaseJob.contains(
                "[[ \"${'$'}current_tag_commit\" != \"${'$'}EXPECTED_TAG_COMMIT\" ]]",
            ),
        )
        assertTrue(
            Regex("actions/download-artifact@[0-9a-f]{40}").containsMatchIn(releaseJob),
        )
        listOf(readme, releaseGuide).forEach { documentation ->
            assertTrue(documentation.contains("vMAJOR.MINOR.PATCH-PRERELEASE"))
            assertTrue(documentation.contains("v1.1.0-rc.1"))
        }
    }

    @Test
    fun `CodeRabbit policy is standalone and fail closed`() {
        val policy = read(".coderabbit.yaml")

        assertFalse(Regex("(?m)^inheritance:").containsMatchIn(policy))
        assertFalse(policy.contains("path_filters:"))
        assertTrue(policy.contains("request_changes_workflow: true"))
        assertTrue(policy.contains("fail_commit_status: true"))
        assertTrue(policy.contains("auto_incremental_review: true"))
        assertTrue(
            Regex("Initial SDK\\s+bootstrap follows the documented seed procedure").containsMatchIn(
                policy,
            ),
        )
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
        val contributing = read("CONTRIBUTING.md")
        val build = read("build.gradle.kts")

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
        assertTrue(Regex("actions/setup-python@[0-9a-f]{40}").containsMatchIn(workflow))
        assertTrue(workflow.contains("python-version: \"3.14.7\""))
        assertTrue(Regex("ruby/setup-ruby@[0-9a-f]{40}").containsMatchIn(workflow))
        assertTrue(workflow.contains("ruby-version: \"3.3.12\""))
        assertTrue(validator.contains("--require-hashes"))
        assertTrue(validator.contains("coderabbit-schema.v2.json"))
        assertFalse(validator.contains("command -v check-jsonschema"))
        assertFalse(validator.contains("https://coderabbit.ai"))
        assertTrue(validator.contains("readonly python_bin=\"\${PYTHON_BIN:-python3}\""))
        assertTrue(
            validator.contains("not (3, 11) <= sys.version_info[:2] <= (3, 14)"),
        )
        assertTrue(validator.contains("requires CPython 3.11 through 3.14"))
        assertTrue(contributing.contains("CPython 3.11 through 3.14"))
        assertFalse(contributing.contains("Python 3.11 or newer"))
        assertTrue(contributing.contains("./scripts/verify-workflow-pins.rb"))
        assertTrue(build.contains("systemProperty(\"alitycs.repositoryRoot\", \".\")"))
        assertTrue(build.contains("withPathSensitivity(PathSensitivity.RELATIVE)"))
        assertTrue(build.contains("include(\"**/action.yml\", \"**/action.yaml\", \"**/Dockerfile\")"))
        assertFalse(build.contains("layout.projectDirectory.asFile.absolutePath"))
        assertFalse(
            Regex("readonly (?:script_dir|repository_root)=\"\\${'$'}\\(").containsMatchIn(
                validator,
            ),
        )
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
    fun `live schema drift monitor is not a merge gate`() {
        val workflow = read(".github/workflows/coderabbit-schema-drift.yml")
        val docs = read("docs/coderabbit.md")
        val policy = read(".coderabbit.yaml")

        assertTrue(Regex("(?m)^  schedule:${'$'}").containsMatchIn(workflow))
        assertTrue(Regex("(?m)^  workflow_dispatch:${'$'}").containsMatchIn(workflow))
        assertFalse(Regex("(?m)^  pull_request(?:_target)?:${'$'}").containsMatchIn(workflow))
        assertFalse(Regex("(?m)^  push:${'$'}").containsMatchIn(workflow))
        assertTrue(workflow.contains("permissions:\n  contents: read"))
        assertTrue(Regex("actions/checkout@[0-9a-f]{40}").containsMatchIn(workflow))
        assertTrue(workflow.contains("persist-credentials: false"))
        assertTrue(workflow.contains("https://coderabbit.ai/integrations/schema.v2.json"))
        assertTrue(workflow.contains("--proto-redir '=https'"))
        assertTrue(workflow.contains("--retry 3"))
        assertTrue(workflow.contains("--retry-connrefused"))
        assertTrue(workflow.contains("--max-time 60"))
        assertTrue(workflow.contains("cmp --silent \"${'$'}pinned_schema\" \"${'$'}live_schema\""))
        assertFalse(Regex("readonly [a-z_]+=\"\\${'$'}\\(").containsMatchIn(workflow))
        assertTrue(docs.contains("deliberately not a required merge check"))
        assertTrue(policy.contains("keep the scheduled live-schema drift check non-gating"))
    }

    @Test
    fun `every GitHub-hosted job uses an explicit runner image`() {
        val workflowDirectory = repositoryRoot.resolve(".github/workflows")
        val workflowCorpus =
            Files.list(workflowDirectory).use { paths ->
                paths.iterator().asSequence()
                    .filter { it.fileName.toString().matches(Regex(".*\\.ya?ml")) }
                    .joinToString("\n") { Files.readString(it) }
            }
        val docs = read("docs/coderabbit.md")
        val policy = read(".coderabbit.yaml")
        val runnerDeclarations =
            Regex("(?m)^\\s*runs-on\\s*:.*${'$'}").findAll(workflowCorpus).map { it.value.trim() }.toList()

        assertFalse(workflowCorpus.contains("ubuntu-latest"))
        assertTrue(runnerDeclarations.isNotEmpty())
        runnerDeclarations.forEach { declaration ->
            assertEquals("runs-on: ubuntu-24.04", declaration)
        }
        assertTrue(docs.contains("do not use a moving `*-latest` label"))
        assertTrue(policy.contains("runner labels pinned to explicit OS versions"))
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

        val validDockerAction =
            runPinVerifier(
                """
                name: Fixture Docker action
                description: Exercises immutable registry images
                inputs:
                  image:
                    description: A harmless input named image
                runs:
                  using: docker
                  image: docker://ghcr.io/alitycs/build@sha256:${"e".repeat(64)}
                """.trimIndent(),
                "action.yml",
            )
        assertEquals(0, validDockerAction.exitCode, validDockerAction.stderr)

        val validLocalDockerAction =
            runPinVerifier(
                """
                name: Fixture local Docker action
                description: Exercises same-commit Dockerfiles
                runs:
                  using: docker
                  image: ./containers/Dockerfile
                """.trimIndent(),
                "action.yaml",
            )
        assertEquals(0, validLocalDockerAction.exitCode, validLocalDockerAction.stderr)

        val invalidDockerAction =
            runPinVerifier(
                """
                name: Fixture mutable Docker action
                description: Contains a mutable registry image
                runs:
                  using: docker
                  image: docker://alpine:latest
                """.trimIndent(),
                "action.yml",
            )
        assertEquals(1, invalidDockerAction.exitCode)
        assertTrue(
            invalidDockerAction.stderr.contains(
                "unpinned Docker image \"docker://alpine:latest\"",
            ),
        )

        val invalidLocalDockerAction =
            runPinVerifier(
                """
                name: Fixture invalid local Docker action
                description: Escapes the action directory
                runs:
                  using: docker
                  image: ../Dockerfile
                """.trimIndent(),
                "action.yml",
            )
        assertEquals(1, invalidLocalDockerAction.exitCode)
        assertTrue(
            invalidLocalDockerAction.stderr.contains(
                "invalid Docker action image \"../Dockerfile\"",
            ),
        )
    }

    @Test
    fun `workflow pin verifier rejects YAML merge keys everywhere`() {
        val immutableAction = "actions/checkout@${"a".repeat(40)}"
        val fixtures =
            listOf(
                """
                defaults: &defaults { uses: $immutableAction }
                jobs:
                  inherited-job:
                    <<: *defaults
                """.trimIndent(),
                """
                defaults: &defaults { uses: $immutableAction }
                jobs:
                  inherited-step:
                    steps: [{ <<: *defaults, name: inherited }]
                """.trimIndent(),
                """
                merge-key: &merge-key "<<"
                defaults: &defaults { uses: $immutableAction }
                jobs:
                  aliased-key:
                    *merge-key: *defaults
                """.trimIndent(),
                """
                defaults: &defaults { value: harmless }
                metadata:
                  nested: { <<: *defaults }
                jobs:
                  valid:
                    steps:
                      - uses: $immutableAction
                """.trimIndent(),
            )

        fixtures.forEach { fixture ->
            val result = runPinVerifier(fixture)
            assertEquals(1, result.exitCode)
            assertTrue(
                result.stderr.contains("YAML merge keys (<<) are not supported"),
                result.stderr,
            )
        }

        val duplicateMergeKeys =
            runPinVerifier(
                """
                defaults: &defaults { uses: $immutableAction }
                jobs:
                  duplicate:
                    <<: *defaults
                    <<: *defaults
                """.trimIndent(),
            )
        assertEquals(1, duplicateMergeKeys.exitCode)
        assertEquals(
            2,
            Regex("YAML merge keys \\(<<\\) are not supported")
                .findAll(duplicateMergeKeys.stderr).count(),
            duplicateMergeKeys.stderr,
        )

        val actionMetadata =
            runPinVerifier(
                """
                defaults: &defaults { description: inherited }
                inputs:
                  settings: { <<: *defaults }
                runs:
                  using: composite
                  steps:
                    - uses: $immutableAction
                """.trimIndent(),
                "action.yml",
            )
        assertEquals(1, actionMetadata.exitCode)
        assertTrue(actionMetadata.stderr.contains("YAML merge keys (<<) are not supported"))

        val harmlessValue =
            runPinVerifier(
                """
                marker: "<<"
                jobs:
                  valid:
                    steps:
                      - uses: $immutableAction
                """.trimIndent(),
            )
        assertEquals(0, harmlessValue.exitCode, harmlessValue.stderr)

        val docs = read("docs/coderabbit.md")
        val policy = read(".coderabbit.yaml")
        assertTrue(policy.contains("Reject YAML merge keys (`<<`) anywhere"))
        assertTrue(docs.contains("YAML merge keys (`<<`) are rejected anywhere"))
    }

    @Test
    fun `workflow container and service images require literal immutable digests`() {
        val validContainerImage = "ghcr.io/alitycs/build@sha256:${"a".repeat(64)}"
        val validServiceImage = "postgres:16@sha256:${"b".repeat(64)}"
        val validPortRegistryImage =
            "registry.example.com:5000/alitycs/cache@sha256:${"c".repeat(64)}"

        val valid =
            runPinVerifier(
                """
                images:
                  container: &container-image $validContainerImage
                  service: &service-image $validServiceImage
                container-definition: &container-definition
                  image: *container-image
                  options: --cpus 1
                service-definition: &service-definition { image: *service-image, ports: [5432] }
                jobs:
                  scalar-container:
                    container: *container-image
                    services:
                      database: *service-definition
                  mapping-container:
                    container: *container-definition
                    services: { cache: { image: $validPortRegistryImage } }
                """.trimIndent(),
            )
        assertEquals(0, valid.exitCode, valid.stderr)

        val invalidLiterals =
            runPinVerifier(
                """
                images:
                  mutable: &mutable-image alpine:latest
                  uppercase: &uppercase-image ghcr.io/alitycs/build@sha256:${"A".repeat(64)}
                jobs:
                  invalid-container:
                    container: *mutable-image
                    services:
                      uppercase: { image: *uppercase-image }
                      uppercase-registry:
                        image: Ghcr.io/alitycs/build@sha256:${"a".repeat(64)}
                      expression: { image: "${'$'}{{ matrix.image }}" }
                      action-syntax:
                        image: docker://alpine@sha256:${"d".repeat(64)}
                """.trimIndent(),
            )
        assertEquals(1, invalidLiterals.exitCode)
        assertTrue(invalidLiterals.stderr.contains("got \"alpine:latest\""))
        assertTrue(invalidLiterals.stderr.contains("got \"ghcr.io/alitycs/build@sha256:"))
        assertTrue(invalidLiterals.stderr.contains("got \"Ghcr.io/alitycs/build@sha256:"))
        assertTrue(invalidLiterals.stderr.contains("got \"${'$'}{{ matrix.image }}\""))
        assertTrue(invalidLiterals.stderr.contains("got \"docker://alpine@sha256:"))

        val duplicateKeys =
            runPinVerifier(
                """
                images:
                  valid: &valid-image ghcr.io/alitycs/build@sha256:${"e".repeat(64)}
                  mutable: &mutable-image alpine:latest
                jobs:
                  duplicate-container:
                    container: *valid-image
                    container: { image: *mutable-image }
                  duplicate-container-image:
                    container: { image: *valid-image, image: *mutable-image }
                  duplicate-services:
                    services: { valid: { image: *valid-image } }
                    services: { invalid: { image: *mutable-image } }
                  duplicate-service-image:
                    services: { database: { image: *valid-image, image: *mutable-image } }
                """.trimIndent(),
            )
        assertEquals(1, duplicateKeys.exitCode)
        assertEquals(
            4,
            Regex("got \\\"alpine:latest\\\"").findAll(duplicateKeys.stderr).count(),
            duplicateKeys.stderr,
        )

        val nonScalar =
            runPinVerifier(
                """
                image-list: &image-list [ghcr.io/alitycs/build@sha256:${"f".repeat(64)}]
                jobs:
                  invalid-container-declaration:
                    container: [*image-list]
                  invalid-container-image:
                    container: { image: *image-list }
                  missing-container-image:
                    container: { options: --cpus 1 }
                  invalid-services-declaration:
                    services: [database]
                  invalid-service-declaration:
                    services: { database: *image-list }
                  invalid-service-image:
                    services: { database: { image: *image-list } }
                  missing-service-image:
                    services: { database: { ports: [5432] } }
                """.trimIndent(),
            )
        assertEquals(1, nonScalar.exitCode)
        assertEquals(
            3,
            Regex("workflow container image must be a scalar literal")
                .findAll(nonScalar.stderr).count(),
            nonScalar.stderr,
        )
        assertEquals(
            4,
            Regex("workflow service image must be a scalar literal")
                .findAll(nonScalar.stderr).count(),
            nonScalar.stderr,
        )

        val readme = read("README.md")
        val policy = read(".coderabbit.yaml")
        val docs = read("docs/coderabbit.md")
        assertTrue(readme.contains("CPython 3.11 through 3.14"))
        assertTrue(
            policy.contains(
                "workflow job container and service images must use literal lowercase registry",
            ),
        )
        assertTrue(docs.contains("scalar and mapping container forms"))
        assertTrue(docs.contains("64 lowercase hexadecimal digits"))
    }

    @Test
    fun `local Dockerfiles must be regular tracked files`() {
        val repository = Files.createTempDirectory("alitycs-workflow-pin-fixture-")
        val actionDirectory = repository.resolve("fixture")
        val actionPath = actionDirectory.resolve("action.yml")
        val dockerfilePath = actionDirectory.resolve("Dockerfile")
        val targetPath = actionDirectory.resolve("real.Dockerfile")
        val missingDirectory = actionDirectory.resolve("missing")

        fun writeAction(image: String) {
            Files.writeString(
                actionPath,
                """
                name: Local Docker fixture
                description: Verifies tracked file modes
                runs:
                  using: docker
                  image: $image
                """.trimIndent() + "\n",
            )
        }

        try {
            Files.createDirectories(actionDirectory)
            runGit(repository, "init", "--quiet")
            runGit(repository, "config", "user.name", "Alitycs CI")
            runGit(repository, "config", "user.email", "ci@alitycs.com")
            runGit(repository, "config", "commit.gpgSign", "false")
            runGit(repository, "config", "core.hooksPath", ".git/no-hooks")

            writeAction("Dockerfile")
            Files.writeString(dockerfilePath, "FROM scratch\n")
            runGit(repository, "add", ".")
            runGit(
                repository,
                "commit",
                "--no-gpg-sign",
                "--quiet",
                "-m",
                "Add regular Dockerfile",
            )
            val regularHead = runGit(repository, "rev-parse", "HEAD")

            val regularWorktree = runPinVerifier(workingDirectory = repository)
            assertEquals(0, regularWorktree.exitCode, regularWorktree.stderr)
            assertTrue(regularWorktree.stdout.contains("1 local"))
            val regularCommit =
                runPinVerifier(workingDirectory = repository, gitRef = regularHead)
            assertEquals(0, regularCommit.exitCode, regularCommit.stderr)

            Files.delete(dockerfilePath)
            Files.writeString(targetPath, "FROM scratch\n")
            Files.createSymbolicLink(dockerfilePath, Path.of("real.Dockerfile"))
            val replacedByWorktreeSymlink = runPinVerifier(workingDirectory = repository)
            assertEquals(1, replacedByWorktreeSymlink.exitCode)
            assertTrue(
                replacedByWorktreeSymlink.stderr.contains(
                    "does not resolve to a regular tracked Dockerfile",
                ),
            )
            val unchangedCommit =
                runPinVerifier(workingDirectory = repository, gitRef = regularHead)
            assertEquals(0, unchangedCommit.exitCode, unchangedCommit.stderr)

            Files.delete(dockerfilePath)
            Files.delete(targetPath)
            Files.writeString(dockerfilePath, "FROM scratch\n")
            writeAction("missing/Dockerfile")
            Files.createDirectory(missingDirectory)
            Files.writeString(missingDirectory.resolve("Dockerfile"), "FROM scratch\n")
            runGit(repository, "add", "fixture/action.yml")
            runGit(
                repository,
                "commit",
                "--no-gpg-sign",
                "--quiet",
                "-m",
                "Reference untracked Dockerfile",
            )
            val missingHead = runGit(repository, "rev-parse", "HEAD")

            listOf(
                runPinVerifier(workingDirectory = repository),
                runPinVerifier(workingDirectory = repository, gitRef = missingHead),
            ).forEach { result ->
                assertEquals(1, result.exitCode)
                assertTrue(
                    result.stderr.contains("does not resolve to a regular tracked Dockerfile"),
                )
            }

            deleteRecursively(missingDirectory)
            writeAction("Dockerfile")
            Files.delete(dockerfilePath)
            Files.writeString(targetPath, "FROM scratch\n")
            Files.createSymbolicLink(dockerfilePath, Path.of("real.Dockerfile"))
            runGit(repository, "add", "--all")
            runGit(
                repository,
                "commit",
                "--no-gpg-sign",
                "--quiet",
                "-m",
                "Add Dockerfile symlink",
            )
            val symlinkHead = runGit(repository, "rev-parse", "HEAD")

            listOf(
                runPinVerifier(workingDirectory = repository),
                runPinVerifier(workingDirectory = repository, gitRef = symlinkHead),
            ).forEach { result ->
                assertEquals(1, result.exitCode)
                assertTrue(
                    result.stderr.contains("does not resolve to a regular tracked Dockerfile"),
                )
            }
        } finally {
            deleteRecursively(repository)
        }
    }

    @Test
    fun `repository audit reads the synchronized commit and exact app allowlists`() {
        val audit = read("scripts/audit-coderabbit-github.sh")
        val docs = read("docs/coderabbit.md")
        val futureSdkSeedFiles =
            docs.substringAfter("2. Add a complete, standalone")
                .substringBefore("\n3. Create the `coderabbit-gate` environment")

        assertTrue(
            audit.contains("git show \"\${local_head}:scripts/verify-workflow-pins.rb\""),
        )
        assertTrue(futureSdkSeedFiles.contains("`scripts/audit-coderabbit-github.sh`"))
        assertTrue(audit.contains("ruby - --git-ref \"\$local_head\""))
        assertTrue(audit.contains("readonly protected_workflow_tree=\".github/workflows\""))
        assertTrue(
            audit.contains(
                "readonly release_tag_ruleset_name=\"Immutable release tags\"",
            ),
        )
        assertTrue(
            audit.contains(
                "\"repos/\$repository/rulesets?includes_parents=false&targets=tag&per_page=100\"",
            ),
        )
        assertTrue(audit.contains(".conditions.ref_name.include == [\"refs/tags/v*\"]"))
        assertTrue(audit.contains("(.bypass_actors // []) == []"))
        assertTrue(audit.contains(".current_user_can_bypass == \"never\""))
        assertTrue(audit.contains("([.rules[].type] | sort)"))
        assertFalse(audit.contains("update_allows_fetch_and_merge"))
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
        assertTrue(audit.contains("gh api \"repos/\$repository_name\""))
        assertTrue(audit.contains("def active_public_sdk:"))
        assertTrue(audit.contains("((.archived // false) == false)"))
        assertTrue(audit.contains("((.disabled // false) == false)"))
        assertFalse(audit.contains("(.default_branch // \"main\")"))
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

    @Test
    fun `repository audit validates live shaped immutable tag rulesets`() {
        val canonicalList =
            """
            [[{"id":42,"name":"Immutable release tags"}]]
            """.trimIndent()
        val canonicalDetail =
            """
            {
              "id": 42,
              "name": "Immutable release tags",
              "target": "tag",
              "source_type": "Repository",
              "source": "alitycs/alitycs-sdk-jvm",
              "enforcement": "active",
              "conditions": {
                "ref_name": {"exclude": [], "include": ["refs/tags/v*"]}
              },
              "rules": [{"type": "update"}, {"type": "deletion"}],
              "bypass_actors": [],
              "current_user_can_bypass": "never"
            }
            """.trimIndent()

        val valid = runRulesetAuditFixture(canonicalList, canonicalDetail)
        assertEquals(1, valid.exitCode)
        assertTrue(valid.stderr.contains("alitycs-coderabbit-gate is not installed for alitycs"))
        assertFalse(valid.stderr.contains("Immutable release tags must actively prevent"))

        val invalidFixtures =
            listOf(
                "configured bypass actor" to
                    canonicalDetail.replace(
                        "\"bypass_actors\": []",
                        "\"bypass_actors\": [{\"actor_id\": 1}]",
                    ),
                "effective current-user bypass" to
                    canonicalDetail.replace(
                        "\"current_user_can_bypass\": \"never\"",
                        "\"current_user_can_bypass\": \"always\"",
                    ),
                "non-active enforcement" to
                    canonicalDetail.replace(
                        "\"enforcement\": \"active\"",
                        "\"enforcement\": \"evaluate\"",
                    ),
                "wrong include" to
                    canonicalDetail.replace("refs/tags/v*", "refs/tags/release-*"),
                "non-empty exclude" to
                    canonicalDetail.replace(
                        "\"exclude\": []",
                        "\"exclude\": [\"refs/tags/v0.*\"]",
                    ),
                "missing update rule" to
                    canonicalDetail.replace(
                        "[{\"type\": \"update\"}, {\"type\": \"deletion\"}]",
                        "[{\"type\": \"deletion\"}]",
                    ),
                "extra creation rule" to
                    canonicalDetail.replace(
                        "[{\"type\": \"update\"}, {\"type\": \"deletion\"}]",
                        "[{\"type\": \"update\"}, {\"type\": \"deletion\"}, " +
                            "{\"type\": \"creation\"}]",
                    ),
            )
        invalidFixtures.forEach { (label, detail) ->
            val result = runRulesetAuditFixture(canonicalList, detail)
            assertEquals(1, result.exitCode, label)
            assertTrue(
                result.stderr.contains(
                    "Immutable release tags must actively prevent v* tag updates and deletion " +
                        "without bypasses",
                ),
                label,
            )
        }

        val duplicateAcrossPages =
            """
            [
              [{"id":42,"name":"Immutable release tags"}],
              [{"id":43,"name":"Immutable release tags"}]
            ]
            """.trimIndent()
        val duplicate = runRulesetAuditFixture(duplicateAcrossPages, canonicalDetail)
        assertEquals(1, duplicate.exitCode)
        assertTrue(
            duplicate.stderr.contains(
                "must have exactly one Immutable release tags repository ruleset",
            ),
        )
    }

    private fun runRulesetAuditFixture(rulesetList: String, rulesetDetail: String): VerifierResult {
        val fixtureDirectory = Files.createTempDirectory("alitycs-ruleset-audit-")
        val stdoutFile = Files.createTempFile("alitycs-ruleset-audit-", ".stdout")
        val stderrFile = Files.createTempFile("alitycs-ruleset-audit-", ".stderr")
        var process: Process? = null
        try {
            Files.writeString(fixtureDirectory.resolve("rulesets.json"), rulesetList)
            Files.writeString(fixtureDirectory.resolve("ruleset.json"), rulesetDetail)
            val ghStub = fixtureDirectory.resolve("gh")
            Files.writeString(
                ghStub,
                """
                #!/usr/bin/env bash
                set -euo pipefail

                request=""
                for argument in "${'$'}@"; do
                  request="${'$'}argument"
                done
                case "${'$'}request" in
                  "repos/alitycs/alitycs-sdk-jvm")
                    printf '%s\n' '{"private":false,"default_branch":"main"}'
                    ;;
                  "repos/alitycs/alitycs-sdk-jvm/rulesets?includes_parents=false&targets=tag&per_page=100")
                    cat "${'$'}AUDIT_FIXTURE_DIRECTORY/rulesets.json"
                    ;;
                  "repos/alitycs/alitycs-sdk-jvm/rulesets/42")
                    cat "${'$'}AUDIT_FIXTURE_DIRECTORY/ruleset.json"
                    ;;
                  "orgs/alitycs/installations?per_page=100")
                    printf '%s\n' '[{"installations":[]}]'
                    ;;
                  *)
                    printf 'unexpected gh request: %s\n' "${'$'}request" >&2
                    exit 97
                    ;;
                esac
                """.trimIndent() + "\n",
            )
            check(ghStub.toFile().setExecutable(true, true)) { "could not make gh stub executable" }

            val builder =
                ProcessBuilder(
                    "bash",
                    repositoryRoot
                        .resolve("scripts/audit-coderabbit-github.sh")
                        .toAbsolutePath()
                        .toString(),
                    "alitycs/alitycs-sdk-jvm",
                )
                    .directory(repositoryRoot.toFile())
                    .redirectOutput(stdoutFile.toFile())
                    .redirectError(stderrFile.toFile())
            builder.environment()["AUDIT_FIXTURE_DIRECTORY"] = fixtureDirectory.toString()
            builder.environment()["PATH"] =
                "${fixtureDirectory}:${requireNotNull(System.getenv("PATH"))}"
            val startedProcess = builder.start()
            process = startedProcess
            val exitCode = waitFor(startedProcess, "CodeRabbit repository audit fixture")
            return VerifierResult(
                exitCode,
                Files.readString(stdoutFile),
                Files.readString(stderrFile),
            )
        } finally {
            process?.takeIf { it.isAlive }?.destroyForcibly()
            Files.deleteIfExists(stdoutFile)
            Files.deleteIfExists(stderrFile)
            deleteRecursively(fixtureDirectory)
        }
    }

    private fun runPinVerifier(
        input: String? = null,
        label: String = "fixture.yml",
        workingDirectory: Path = repositoryRoot,
        gitRef: String? = null,
    ): VerifierResult {
        val command =
            mutableListOf(
                "ruby",
                repositoryRoot.resolve("scripts/verify-workflow-pins.rb").toAbsolutePath().toString(),
            )
        if (input != null) command.addAll(listOf("--stdin", label))
        else if (gitRef != null) command.addAll(listOf("--git-ref", gitRef))
        val stdoutFile = Files.createTempFile("alitycs-pin-verifier-", ".stdout")
        val stderrFile = Files.createTempFile("alitycs-pin-verifier-", ".stderr")
        var process: Process? = null
        try {
            val startedProcess =
                ProcessBuilder(command)
                    .directory(workingDirectory.toFile())
                    .redirectOutput(stdoutFile.toFile())
                    .redirectError(stderrFile.toFile())
                    .start()
            process = startedProcess
            startedProcess.outputStream.bufferedWriter().use { writer ->
                if (input != null) writer.write(input)
            }
            val exitCode = waitFor(startedProcess, "workflow pin verifier")
            return VerifierResult(
                exitCode,
                Files.readString(stdoutFile),
                Files.readString(stderrFile),
            )
        } finally {
            process?.takeIf { it.isAlive }?.destroyForcibly()
            Files.deleteIfExists(stdoutFile)
            Files.deleteIfExists(stderrFile)
        }
    }

    private fun runGit(directory: Path, vararg arguments: String): String {
        val outputFile = Files.createTempFile("alitycs-git-fixture-", ".log")
        var process: Process? = null
        try {
            val builder =
                ProcessBuilder(listOf("git") + arguments)
                    .directory(directory.toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(outputFile.toFile())
            builder.environment()["GIT_CONFIG_GLOBAL"] =
                directory.resolve(".gitconfig-isolated").toAbsolutePath().toString()
            builder.environment()["GIT_CONFIG_NOSYSTEM"] = "1"
            val startedProcess = builder.start()
            process = startedProcess
            val exitCode = waitFor(startedProcess, "git ${arguments.joinToString(" ")}")
            val output = Files.readString(outputFile)
            check(exitCode == 0) { "git ${arguments.joinToString(" ")} failed: $output" }
            return output.trim()
        } finally {
            process?.takeIf { it.isAlive }?.destroyForcibly()
            Files.deleteIfExists(outputFile)
        }
    }

    private fun waitFor(process: Process, description: String): Int {
        try {
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                process.waitFor(5, TimeUnit.SECONDS)
                error("$description timed out")
            }
            return process.exitValue()
        } catch (error: InterruptedException) {
            process.destroyForcibly()
            Thread.currentThread().interrupt()
            throw error
        }
    }

    private fun deleteRecursively(path: Path) {
        Files.walk(path).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }

    private fun read(relativePath: String): String =
        Files.readString(repositoryRoot.resolve(relativePath))

    private data class VerifierResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
    )
}
