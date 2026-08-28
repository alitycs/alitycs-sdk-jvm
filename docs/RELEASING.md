# Releasing

1. Update `version` in `build.gradle.kts` and the changelog in a pull request.
2. Run `./gradlew test koverVerify build publishToMavenLocal`.
3. Merge the release pull request to `main`.
4. Create and push an annotated tag on the merged `main` commit matching the full project version:
   `vMAJOR.MINOR.PATCH` for a stable release or `vMAJOR.MINOR.PATCH-PRERELEASE` for a prerelease.
   For example, use `v1.1.0` or `v1.1.0-rc.1`. Tags whose commit is not reachable from remote
   `main` are rejected. The active `Immutable release tags` repository ruleset prevents updates or
   deletion of `refs/tags/v*` without any bypass actor.
5. The `Release` workflow validates, builds, and generates the Maven POM and checksums in a
   read-only job. A separate minimal-permission job downloads the reproducible JAR, sources,
   Javadoc, POM, and checksums, fetches and rechecks the tag identity, then attests the artifacts
   and creates the GitHub Release. When Maven Central publishing is enabled, a protected
   environment job signs those exact artifacts, rechecks the immutable tag again, uploads one
   Central Portal bundle, and waits for validation. The workflow intentionally has no Actions
   concurrency group because
   those names are repository-global and a pull-request-controlled workflow could reserve one to
   block a tag run. Immutable tags, the fresh identity recheck, and immutable tag and version
   publication identities provide duplicate-release safety without that repository-global lock.

## One-time Maven Central bootstrap

Leave the repository variable `MAVEN_CENTRAL_PUBLISH_ENABLED` unset until all of these steps are
complete:

1. Verify ownership of the `com.alitycs` namespace in Central Portal.
2. Create a dedicated OpenPGP signing key, publish its public key, and store the armored private key
   and passphrase as `MAVEN_GPG_PRIVATE_KEY` and `MAVEN_GPG_PASSPHRASE` secrets in the protected
   `maven-central` GitHub environment.
3. Generate a Central Portal user token and store its two values as `CENTRAL_PORTAL_USERNAME` and
   `CENTRAL_PORTAL_PASSWORD` environment secrets.
4. Protect `maven-central` with required maintainers and allow deployments from `v*` tags only.
5. Set `MAVEN_CENTRAL_PUBLISH_ENABLED=true`. Leave `MAVEN_CENTRAL_PUBLISHING_TYPE` unset for the
   first release so the deployment stops at `VALIDATED`; inspect and publish it in Central Portal.
   After that succeeds, set `MAVEN_CENTRAL_PUBLISHING_TYPE=AUTOMATIC` for later releases.

The publishing job does not rebuild the package. It signs the checksummed artifacts produced by
the release build, assembles the required Maven repository layout, uploads it through the Central
Portal API, and fails on validation errors or timeouts. Never enable the job with partial or
personal credentials, and never weaken the existing test or coverage gates for a release.
