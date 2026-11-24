# Codex sandboxes

```bash
python3 scripts/codex-sbx/install.py
codex-sbx [REPO_PATH] [-- CODEX_ARGS...]
codex-sbx -- resume --all
```

Requires Python 3.11+ and Docker Sandboxes v0.54.0. Pinned kits:
`sbx/codex:0.155.1` and `sbx/codex-sessions:1.0.0`. The installer copies the
launcher to `~/.local/share/codex-sbx/launcher` and links commands in
`~/.local/bin`; reinstall after changes. The private `runtime/bin/sbx` is preferred.

The launcher reuses the unique Codex sandbox with this workspace and a mounted
`/home/agent/codex-history`. Ambiguous matches fail. New sandbox names contain
the directory basename and first 12 hexadecimal digits of its absolute-path
SHA-256. History lives under `~/.local/share/codex-sbx/history/SANDBOX_NAME`;
native host Codex history stays separate. One-time symlinks retain rescued history
for existing checkouts. No registry or relocation commands are needed.

New VMs retain tools/caches, use 4 CPUs and 8 GiB, and need development toolchain
provisioning. Docker's VM is the execution boundary: inner Codex approvals and
sandboxing are disabled. Nothing automatically removes an existing VM.

Each launch refreshes compatible model, reasoning, verbosity, personality, TUI,
history and text/search preferences from `${CODEX_HOME:-~/.codex}/config.toml`.
Removed preferences revert to the saved kit baseline. Provider routing, auth,
MCP gateway and project trust remain sandbox-managed. Unsupported desktop,
plugin and local-command settings are reported. Skills use Docker's store.

Register MCP with `sbx mcp`; set `CODEX_SBX_MCP=android-tests` on first creation
to select the CRM runner. Existing sandboxes retain their MCP set. Follow the
[host runner runbook](../scripts/host-mcp/README.md) for a moved inbox or refresh.
After a checkout move, verify that runner health's `inbox` points to that
checkout's `ignored/mcp-inbox`; refreshing preserves existing configuration.
`CODEX_SBX_HOME` overrides state storage; `CODEX_SBX_BIN` selects the runtime.

## Rescue

```bash
codex-sbx-rescue backup SANDBOX_NAME
codex-sbx-rescue import BACKUP_DIRECTORY HISTORY_DIRECTORY
```

Close active sessions before final backup. Backups under `backups/` contain
transcripts, config and individually consistent SQLite snapshots, excluding auth.
`verified.json` records archive and file SHA-256 checksums. Import verifies checksums
and imports only transcripts; different existing files are refused, identical
imports are safe to repeat. Keep backups and old VMs until resume works.
`CODEX_SBX_BACKUP_BIN` selects the rescue runtime before an upgrade.

## Validate

Run `python3 -m unittest discover -s scripts -p test_codex_sbx.py` and
`bash .mise/tasks/tooling-test`. Runtime verification must resume a rescued
conversation and recall history after removing/recreating a disposable VM.
