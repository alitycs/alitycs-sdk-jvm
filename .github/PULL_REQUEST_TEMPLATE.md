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

## Review gate

- [ ] All blocking CodeRabbit findings are resolved (human-authored pull requests).
- [ ] CodeRabbit approved the latest commit, or this is a supported bot pull request with human
      maintainer approval.
- [ ] `Alitycs CodeRabbit Gate` passed for the latest pushed commit.
- [ ] Any administrative break-glass use is explained and linked to a follow-up issue.

See the
[CodeRabbit gate operations guide](https://github.com/alitycs/alitycs-sdk-jvm/blob/main/docs/coderabbit.md)
for gate operations and upgrade procedures.
