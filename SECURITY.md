# Security policy

## Supported versions

Security fixes are provided for the latest `1.x` release. Older versions may be asked to upgrade.

## Reporting a vulnerability

Use [GitHub private vulnerability reporting](https://github.com/alitycs/alitycs-sdk-jvm/security/advisories/new).
Do not open a public issue or discussion for a suspected vulnerability.

Include the affected version, impact, reproduction steps, and suggested mitigation. Avoid accessing
data that is not yours and give maintainers reasonable time to investigate before disclosure.

Never ship a secret Alitycs key in client-distributed code. Rotate any credential that may have
been exposed.
