# Release signing

The APK used on real phones should be a **release build signed with one stable private key**.

Why this matters:

- Android sees every future update as coming from the same app identity.
- Release builds are not debuggable.
- It avoids shipping Android's generic debug certificate.
- It reduces unnecessary security/reputation warnings compared with development APKs.

It cannot remove Android's one-time **Install unknown apps** permission when an APK is sideloaded outside an app store. Xiaomi/MIUI/HyperOS or Google Play Protect may also independently scan a sideloaded APK.

## Stable signing identity

The production signing certificate SHA-256 is:

```text
F8:45:DB:90:80:06:0A:59:9D:80:00:99:34:52:93:D7:4C:7E:C4:24:51:49:E5:18:C8:43:0E:0D:AC:B4:B6:E0
```

Future stable APKs must be signed with the same private key.

## GitHub Actions: recommended setup

The repository includes `.github/workflows/release.yml`.

It builds a Release APK, reconstructs the keystore only inside the temporary GitHub Actions runner, verifies that the certificate fingerprint matches the stable identity above, signs the APK, verifies the APK signature, publishes an artifact, and creates a GitHub Release when the workflow is triggered by a version tag.

The signing material is stored in a GitHub Environment named:

```text
release
```

Create these **Environment secrets**:

```text
RELEASE_KEYSTORE_B64
RELEASE_STORE_PASSWORD
RELEASE_KEY_PASSWORD
RELEASE_KEY_ALIAS
```

### Secret values

- `RELEASE_KEYSTORE_B64` — the complete `transcribe-release.jks` file encoded as Base64 with no line wrapping.
- `RELEASE_STORE_PASSWORD` — password for the keystore.
- `RELEASE_KEY_PASSWORD` — password for the signing key.
- `RELEASE_KEY_ALIAS` — the key alias. For the current stable identity this is `transcribe`.

The private key and passwords must never be committed to the repository.

### Encode the keystore

Linux:

```bash
base64 -w 0 transcribe-release.jks
```

macOS:

```bash
base64 < transcribe-release.jks | tr -d '\n'
```

PowerShell:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("transcribe-release.jks"))
```

Copy the resulting single-line string into `RELEASE_KEYSTORE_B64`.

## Configure GitHub

In the repository:

```text
Settings
  → Environments
  → New environment
  → release
  → Environment secrets
```

Add the four secrets listed above.

The workflow explicitly declares:

```yaml
environment: release
```

so normal CI jobs and pull-request builds do not receive the production signing credentials.

## Publishing a stable version

Create and push a version tag such as:

```bash
git tag v0.2.2
git push origin v0.2.2
```

GitHub Actions will then:

```text
checkout source
    ↓
restore keystore from release secrets
    ↓
verify expected certificate fingerprint
    ↓
build Release / optimized native code
    ↓
verify APK signature
    ↓
generate SHA-256 checksum
    ↓
publish GitHub Release + official APK
```

The workflow can also be started manually from the Actions tab. Manual runs build a signed APK artifact but do not create a versioned GitHub Release.

## Local release build

For local development, copy:

```text
keystore.properties.example
```

to:

```text
keystore.properties
```

and fill in the local signing values.

Both `keystore.properties` and `*.jks` are ignored by Git.

Then run:

```bash
./gradlew :app:assembleRelease
```

## Backup

GitHub Secrets are for automated use, not archival storage. GitHub does not let you reveal secret values again after saving them.

Keep at least one protected offline backup of:

- the `.jks` file;
- the keystore password;
- the key password;
- the alias.

Losing the production signing key means installed copies of the app cannot be updated with the same Android package identity.

The application package remains:

```text
com.emh01.transcribeoffline
```

Keep both this package name and the signing key stable across releases.
