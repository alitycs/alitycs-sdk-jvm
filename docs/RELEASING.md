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
   read-only job. A separate minimal-permission job downloads and attests the reproducible JAR,
   sources, POM, and checksums, then creates the GitHub Release. Before publication, it fetches the
   tag again and requires its annotated-tag object and target commit to match the identities
   recorded by the build job.

Maven Central publication is intentionally not attempted until ownership of `com.alitycs`, artifact
signing, and Central Portal credentials are configured. Add that deployment only after those
prerequisites exist; never weaken the existing test or coverage gates for a release.
