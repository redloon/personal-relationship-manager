# Preserve UI patterns across agent changes

## Problem

Dashboard independently implements search and settings controls, losing behavior already present in Contacts and Events. Remembering a previous conversation is not a reliable repository convention.

## Implementation

1. Extract a small shared search field in `ui/components` from the existing Contacts/Events pattern: localized hint, search icon, single line, accessible clear action visible only for a nonempty query. Clearing invokes the same query callback with an empty string. Preserve existing screen test tags and accommodate Dashboard's hint without building a configurable design system.
2. Extract a shared settings icon button using the existing localized accessibility description. Use it on Dashboard, Contacts, and Events; preserve existing semantics and test tags.
3. Replace the duplicated controls on those three screens. Inspect other actual search inputs (including the event person picker); reuse the search component where the same behavior applies. Do not change prose editors, autocomplete editors, or unrelated navigation.
4. Add a concise UI conventions section to root `AGENTS.md`: inspect sibling screens and `ui/components` before UI work; reuse shared controls; put repeated interaction fixes in shared components; test user-visible behavior; explain intentional deviations. Reference the concrete components as executable examples. Keep product choices in `docs/decisions.md`, implementation conventions in AGENTS/components.
5. Add focused Compose coverage proving clear is absent when empty, appears after typing, empties the query, and restores Dashboard's recent list. Verify the shared settings action opens settings from Dashboard and retain Contacts/Events coverage. Prefer behavioral assertions over tests that merely assert component usage.

## Validation

- Build app and instrumentation APKs using `scripts/gradle.sh`; run relevant tests, then the full instrumented suite via android-tests MCP where available. Discover tools before assuming MCP is absent. Record job ID, count, and result. If host execution is inaccessible, report that limitation accurately and complete available compilation/JVM checks.
- Inspect the final diff for preserved tags, localized descriptions, unchanged filtering/navigation behavior, and scope.

## Scope and tooling decision

Use shared components, short discoverable guidance, and behavioral regression tests now. Do not add hooks, ast-grep, a new lint dependency, a generic design system, or brittle source regex enforcement. Revisit structural enforcement only if bypassing shared controls recurs; it cannot establish good UX by itself.

## Completed validation

Shared components, screen updates, guidance, and Dashboard regressions are implemented. Dashboard: 3 tests passed (`0835d1a6ca004029b86a80e11668b553`). Full suite: 66 tests passed in 94.3 seconds (`82c219b7771e4e44b8fcb0b614939bea`). Subsequent runner validation also passed the same suite; see the MCP runbook. Phone-install/SDK and MCP hardening changes are committed separately from the UI changes.
