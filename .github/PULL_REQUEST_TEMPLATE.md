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

## Automated review

- [ ] The native `CodeRabbit` status passed for the latest push (review completion, not approval).
- [ ] All blocking CodeRabbit findings are resolved and its formal review state was checked.
- [ ] Governance changes have the required code-owner approval.

See the
[CodeRabbit review policy](https://github.com/alitycs/alitycs-sdk-jvm/blob/main/docs/coderabbit.md)
for review behavior, validation, and branch protection.
