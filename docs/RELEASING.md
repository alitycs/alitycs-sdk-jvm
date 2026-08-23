# Releasing

1. Update `version` in `build.gradle.kts` and the changelog in a pull request.
2. Run `./gradlew test koverVerify build publishToMavenLocal`.
3. Merge the release pull request to `main`.
4. Create and push an annotated tag matching the project version, for example `v1.1.0`.
5. The `Release` workflow validates the tag, produces and attests reproducible JAR and sources
   artifacts, generates the Maven POM and checksums, and creates a GitHub Release.

Maven Central publication is intentionally not attempted until ownership of `com.alitycs`, artifact
signing, and Central Portal credentials are configured. Add that deployment only after those
prerequisites exist; never weaken the existing test or coverage gates for a release.
