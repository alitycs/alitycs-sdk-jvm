## Summary

Describe the user-visible change and why it is needed.

## Compatibility

- [ ] Kotlin and Java API impact is documented.
- [ ] No wire-contract change, or the coordinated contract change is explained below.
- [ ] The SDK still sends to the worker `/events` endpoint with credentials kept out of source.

## Verification

- [ ] Tests added or updated.
- [ ] `./gradlew test`
- [ ] `./gradlew koverVerify`
- [ ] `./gradlew build`
- [ ] `./gradlew publishToMavenLocal`
