# Codex sandboxes for CRM

The launcher, rescue utility, installer, tests, general setup and host session-review
guidance live in [dotfiles](../../../dotfiles/scripts/codex-sbx/README.md)
(`~/dotfiles/scripts/codex-sbx/README.md`). Install explicitly from there:

```bash
python3 ~/dotfiles/scripts/codex-sbx/install.py
bash .mise/tasks/codex [CODEX_ARGS...]
```

The task wrapper uses the installed `codex-sbx` command.

## Android MCP

Register MCP with `sbx mcp`; set `CODEX_SBX_MCP=android-tests` on first creation
to select the CRM runner. Existing sandboxes retain their MCP set. Follow the
[host runner runbook](../scripts/host-mcp/README.md) for a moved inbox or refresh.
After a checkout move, verify that runner health's `inbox` points to that
checkout's `ignored/mcp-inbox`; refreshing preserves existing configuration.

Run `bash .mise/tasks/tooling-test` to validate the CRM tooling and task wrapper.
