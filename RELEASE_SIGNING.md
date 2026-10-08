# Release signing

The APK used on real phones should be a **release build signed with one stable private key**.

Why this matters:

- Android sees every future update as coming from the same app identity.
- Release builds are not debuggable.
- It avoids shipping Android's generic debug certificate.
- It reduces unnecessary security/reputation warnings compared with development APKs.

It cannot remove Android's one-time **Install unknown apps** permission when an APK is sideloaded outside an app store. Xiaomi/MIUI/HyperOS or Google Play Protect may also independently scan a sideloaded APK.

## 1. Create the key once

Run from the repository root:

```bash
keytool -genkeypair -v \
  -keystore transcribe-release.jks \
  -alias transcribe \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000
```

Use strong passwords and store them safely.

**Do not recreate this key for each version.** Keep a protected backup. Losing it means installed copies cannot be updated with the same package identity.

## 2. Configure the local build

Copy:

```text
keystore.properties.example
```

to:

```text
keystore.properties
```

and fill in the passwords.

Both `keystore.properties` and `*.jks` are ignored by Git.

## 3. Build

```bash
./gradlew :app:assembleRelease
```

When `keystore.properties` is present, the release APK is signed with that stable key.

The application package remains:

```text
com.emh01.transcribeoffline
```

Keep this package name and the signing key stable across releases.
