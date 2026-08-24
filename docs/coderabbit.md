# CodeRabbit reviews

This repository owns its complete CodeRabbit policy in `.coderabbit.yaml`; it does not inherit
configuration from another repository. CodeRabbit reviews ready pull requests through its native
GitHub App integration. There is no Alitycs-specific gate App, reconciliation workflow, private
key, environment, or central policy repository.

## Merge policy

- `reviews.auto_review.enabled: true` starts reviews automatically, and incremental review remains
  enabled after new commits.
- `reviews.request_changes_workflow: true` lets CodeRabbit submit a formal approval or request
  changes. Resolve blocking findings and wait for the latest push to be reviewed.
- `reviews.fail_commit_status: true` makes CodeRabbit's native commit status fail when its review
  processing fails. No usernames are excluded, so Dependabot, Renovate, and other bot pull requests
  are eligible for the same status.
- The native `CodeRabbit` status is a completion signal, not an approval proxy. A successful status
  means CodeRabbit finished processing the commit; the formal pull-request review carries its
  approval or changes-requested decision.
- Normal GitHub review protection remains authoritative. It requires one approval, dismisses stale
  approvals, requires approval after the latest push, resolves conversations, and requires code
  owner review for protected governance files. An eligible human approval can satisfy the review
  requirement; this policy intentionally does not claim that only CodeRabbit can unlock a merge.

After a repository-specific canary confirms that CodeRabbit publishes its native status for the
latest pull-request head, require the `CodeRabbit` status alongside the deterministic `Verify` and
`Review` checks. Keep strict branch updates enabled and keep bot exclusions disabled so dependency
updates receive the same status. Do not interpret that required status as a substitute for the
formal review state.

## Protected governance

`.github/CODEOWNERS` assigns `.coderabbit.yaml`, the complete `.github/` tree, CodeRabbit validation
assets, the workflow-pin verifier, this guide, and the contribution policy to `@bulanovdm`. Keep
`require_code_owner_reviews` enabled after the ownership file reaches `main`. Future maintainer
teams may replace the individual owner only after the team has write access to the repository.

Policy validation is repository-owned and credential-free:

- `scripts/coderabbit-schema.v2.json` is a reviewed CodeRabbit schema snapshot.
- `scripts/coderabbit-validator-requirements.txt` locks the validator and all transitive Python
  dependencies by hash.
- `scripts/validate-coderabbit.sh` verifies the schema digest and validates `.coderabbit.yaml` in an
  isolated environment with CPython 3.11 through 3.14. CI pins CPython 3.14.7; locally, set
  `PYTHON_BIN` when another supported interpreter should be used.
- `.github/workflows/coderabbit-schema-drift.yml` compares the snapshot with CodeRabbit's live
  schema weekly and on manual dispatch. It is a maintenance alert, not a required merge check.

`scripts/verify-workflow-pins.rb` independently checks the repository's workflow supply chain.
Actions and reusable workflows must use full commit SHAs, Docker images must use immutable SHA-256
digests, and hosted jobs must use explicit versioned runner labels. The verifier also rejects
duplicate keys, unsupported YAML merge keys, mutable container or service images, and malformed
workflow shapes. Run both validators for governance changes:

```bash
./scripts/validate-coderabbit.sh
./scripts/verify-workflow-pins.rb
```

## Adding another SDK

Each public `alitycs-sdk-*` repository owns its own policy and automation. For a new SDK:

1. Install CodeRabbit for the repository.
2. Add a repository-specific `.coderabbit.yaml` with automatic and incremental review,
   `request_changes_workflow: true`, `fail_commit_status: true`, and no bot exclusions.
3. Add CODEOWNERS protection for the policy, workflows, ownership file, and validation assets.
4. Add deterministic CI, dependency review, security scanning, release automation, and the
   credential-free policy and workflow validators appropriate to that repository.
5. Protect `main` with strict required CI checks, one approval, stale-review dismissal,
   latest-push approval, code-owner review, conversation resolution, linear history, administrator
   enforcement, and no force-pushes or deletion. Commit signatures remain optional.
6. Run a canary pull request and confirm that the native `CodeRabbit` status covers its latest
   head. Require that status only after the canary passes, and continue treating it as review
   completion rather than approval.

No shared policy repository or custom GitHub App is required for this rollout.
