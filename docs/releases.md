# Manual local releases

Releases are signed APKs distributed through GitHub Releases. There is no hosted CI,
Play integration, automatic commit, tag, push or upload. Choose the app's public name
before later Google Play account/release work. Naming does not change the installed
identity: keep `com.redloon.prm` and the signing key stable, including
across future display-name changes.

## Where commands run

- **Host**: your normal terminal outside Docker Sandbox. It owns the authoritative
  signing key and SSH access used for pushes.
- **Sandbox**: the persistent `codex-sbx` environment. It owns the Android toolchain
  and builds, verifies and stages APKs. If `mise` is unavailable, run the executable
  task directly, for example `bash .mise/tasks/release-build`.
- **Codex MCP**: Android test tool calls made from the Codex sandbox session. These
  are not shell commands.
- **Browser**: the GitHub website on the host.

The checkout is shared, so commits, tags and release bundles created in either shell
are visible to both. Host files outside the checkout are not visible in the sandbox.

## One-time setup

Install the development prerequisites in [development.md](development.md), including
Android SDK Command-line Tools and Build Tools. `release-build` must fetch tags from
the sandbox, while pushes use the host's SSH credentials.

### Host: configure the remote

```bash
git remote set-url origin https://github.com/redloon/personal-relationship-manager.git
git remote set-url --push origin git@github.com:redloon/personal-relationship-manager.git
git remote -v
```

The expected output uses HTTPS for fetch and SSH for push. Run all pushes from the
host. Ordinary development and debug builds need no release credentials.

### Host: create and back up the signing key

Create the key **outside the checkout**, using the interactive password prompts:

```bash
mkdir -p "$HOME/.local/share/prm-signing"
chmod 700 "$HOME/.local/share/prm-signing"
keytool -genkeypair -v \
  -keystore "$HOME/.local/share/prm-signing/release.p12" \
  -storetype PKCS12 -alias prm -keyalg RSA -keysize 4096 -validity 10000
keytool -list -v \
  -keystore "$HOME/.local/share/prm-signing/release.p12" \
  -alias prm
```

Record the certificate's **SHA256** fingerprint. Back up the key, password and
fingerprint securely outside the checkout, with an independently recoverable copy.
Check that copy with `keytool -list` before relying on it.

### Host: copy the key into the persistent sandbox

Keep the host key and its independent backup as the authoritative copies. Copy the
key directly into the sandbox without passing through the checkout:

```bash
SBX_NAME=SANDBOX_NAME_FROM_SBX_LS
HOST_KEY="$HOME/.local/share/prm-signing/release.p12"
SBX_KEY=/home/agent/.local/share/prm-signing/release.p12

sbx exec "$SBX_NAME" mkdir -p /home/agent/.local/share/prm-signing
sbx exec "$SBX_NAME" sh -c 'umask 077; cat > "$1"' sh "$SBX_KEY" < "$HOST_KEY"
sbx exec "$SBX_NAME" stat "$SBX_KEY"
```

The sandbox is persistent, but a replacement sandbox needs this copy again.

### Sandbox: configure release signing

Create `app/release-signing.properties`, which is Git-ignored:

```properties
storeFile=/home/agent/.local/share/prm-signing/release.p12
storePassword=YOUR_LOCAL_PASSWORD
keyAlias=prm
keyPassword=YOUR_LOCAL_PASSWORD
certificateSha256=SHA256_FINGERPRINT_FROM_KEYTOOL
```

Use the actual absolute path (no `$HOME` expansion in the file). Fingerprints may
include colons. Properties use one UTF-8 `key=value` per line, without escapes or
continuations; avoid leading/trailing spaces and backslashes in passwords. PKCS12
uses the same store and key password. Restrict local configuration permissions:

```bash
chmod 600 app/release-signing.properties
```

Replacing the key prevents ordinary APK updates for existing users.
Never put credentials in command arguments, Git, release notes or release bundles.
The signing invocation disables Gradle configuration caching.

The application ID remains `com.redloon.prm`; changing the checkout,
repository, signing-key alias or release filename does not change the installed app.

## Sandbox: choose, review and commit a version

The development starting point is `0.0.0`, Android code `1`. The first release is:

```bash
git fetch --tags origin
bash .mise/tasks/release-version minor
# Produces versionName=0.1.0 and versionCode=2.
git diff -- app/version.properties
```

For subsequent releases choose `patch`, `minor` or `major`. Standard SemVer resets
lower components; every bump also increments Android's version code. Prerelease
suffixes are unsupported. Both fields are updated atomically under the build lock.
Review the intended changes, stage them and commit, for example:

```bash
git add app/version.properties
git commit -m "Release 0.1.0"
git status --short
```

The build requires an entirely clean, committed tree, including untracked files.
Commit other intended source changes first. The tooling rejects an existing local
or fetched `vMAJOR.MINOR.PATCH` tag, a version older than any release tag, or a code
that does not exceed all earlier release codes. Release tags must point to commits
containing their matching `app/version.properties`.

## Sandbox: build the exact artifact

```bash
bash .mise/tasks/release-build
```

This holds the checkout build lock through `verify`, release lint/build, signature
and metadata verification, and export. It verifies the expected certificate,
application ID, version/name/code and `debuggable=false`. Changed inputs or any
failed gate block export. It never overwrites an existing version bundle.

The read-only bundle is `ignored/releases/v0.1.0/`:

- `prm-0.1.0.apk`: signed APK.
- `SHA256SUMS`: checksum for that exact APK.
- `manifest.json`: APK hash, version, Android code, commit, source fingerprint,
  application ID and certificate fingerprint.

Keep this bundle. If a local validation attempt has already produced a bundle,
inspect/archive it outside the bundle directory before retrying intentionally;
the command will not replace it. A release build does not prove runtime behavior.

While the dedicated host runner is idle, follow the [host runbook](../scripts/host-mcp/README.md).
Confirm health reports runner v6, the expected source hash, `android_tests` and
`busy=false`. In the **sandbox shell**, stage the existing signed APK without
rebuilding it:

```bash
bash .mise/tasks/test-stage-app ignored/releases/v0.1.0/prm-0.1.0.apk
```

From the **Codex sandbox session**, use the printed `app_sha256` with the Android
MCP tools. Do not paste these into a host or sandbox shell:

```text
start_recovery_test(app_sha256="PRINTED_HASH")
get_status(job_id="RETURNED_ID", wait_seconds=30)
read_logs(job_id="RETURNED_ID", offset=0)
```

Omitting `case` runs saved, edited and deleted records. Poll through terminal cleanup
and read all log chunks using integer offsets. Require `passed`, `test_count=3`,
changed PIDs for all three cases and no cleanup errors. Review the retained
screenshots and confirm capture, recall, follow-ups and recovery behaved correctly
on that same APK. Record the job ID, counts, timings and checksum. App-only staging carries the
**original release manifest**; it never substitutes the current checkout's commit.
Run the complete instrumentation suite separately with the debug APK pair via
`test-stage`/`start_test`; instrumentation cannot be attached to the signed release.

The emulator contains disposable fictional data. Signed/debug key changes can
require resetting those two app packages on the dedicated emulator, as described
in the runbook. Nothing resets a phone. Draft loss is outside recovery acceptance;
device reboot and sudden power loss remain deferred.

## Host: tag and push

Only proceed after reviewing validation evidence for the bundle's exact hash.
In a **host shell** at the shared checkout, read its recorded commit, then create
and push the matching tag at that commit:

```bash
RELEASE_COMMIT="$(python3 -c 'import json; print(json.load(open("ignored/releases/v0.1.0/manifest.json"))["git_revision"])')"
git show --stat "$RELEASE_COMMIT"
git merge-base --is-ancestor "$RELEASE_COMMIT" main
git push -u origin main
git tag -a v0.1.0 "$RELEASE_COMMIT" -m "Release 0.1.0"
git push origin v0.1.0
```

The branch push publishes the exact release commit before its tag. On the first
publication it also records `origin/main` as the local branch's upstream; later
releases use the same command to update it.

## Browser: publish the GitHub release

In the GitHub repository:

1. Open **Releases → Draft a new release**.
2. In **Choose a tag**, select the existing **v0.1.0**. Check that it resolves to
   the manifest's commit; do not create a new tag from a branch in this form.
3. Set the title to **v0.1.0** and write concise notes describing user-visible
   changes and any known limitations.
4. Upload the bundle's `prm-0.1.0.apk` and `SHA256SUMS`.
5. Review the assets and notes, then click **Publish release**.

## Host: verify published downloads

Download both published assets into a new host directory using their GitHub links.
Compare the downloaded `SHA256SUMS` to the retained local bundle, change into the
download directory, and run:

```bash
sha256sum -c SHA256SUMS
```

The APK hash must match the trusted local manifest too. Install future releases as
updates with the same application ID/certificate and a strictly larger code.
Update README download information only after this actual release exists.
Automated GitHub drafts can wait until several manual releases establish the flow.
