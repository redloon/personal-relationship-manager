# Lint baseline review

`app/ktlint-baseline.xml` is empty. The mechanical cleanup expanded wildcard
imports and formatted Kotlin while preserving string literals, SQL and test
identities. Hooks remain read-only; `.editorconfig` and the pinned tools define
style. Do not add baseline entries to hide new findings.

The Android baseline retains exactly **32** reviewed exceptions:

- 22 dependency-update findings: compatible versions remain pinned, including
  AGP and the Slack Compose lint registry. No dependency upgrade is part of this
  cleanup. Version-catalog moves preserve the selected dependency versions.
- 7 collection-stability suggestions: avoid wrappers or new immutable-collection
  dependencies without demonstrated performance need.
- 3 intentional shortcut attributes on minSdk 24.

Actionable modifier, state, resource/window access, KTX, plural, catalog and
obsolete/unused resource findings were resolved. Unused resource deletion was
checked against Kotlin and XML references, including instrumented tests. Release
lint also required a monochrome launcher layer, derived from the existing
foreground silhouette. Layout
modifiers retain their existing chains and are exposed on the relevant root.

ktlint Gradle plugin 14.2.0 supports AGP 9 built-in Kotlin source sets; 13.1.0
checked only build scripts here. Slack Compose lint 1.4.2 is compatible with this
project; 1.6.1 fails against its Kotlin analysis API. Never baseline tool failures
such as `ObsoleteLintCustomCheck` or `LintError`. Baseline changes remain build
inputs and trigger source-change detection during validation.
