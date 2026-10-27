# Releasing debug alphas

The alpha channel distributes signed **debuggable** APKs from GitHub Releases and
indexes them into a self-hosted F-Droid repository. It is separate from a future
general-use/production distribution decision. Release names are
`vMAJOR.MINOR.PATCH-alpha.N`; the Android version name omits the leading `v`.

## One-time signing setup

Two signing identities are used:

- **App signing key** (`finance-alpha`): signs the APK so Android can update an
  installed alpha. The alias is required by `android-app/app/build.gradle.kts`.
- **Repository signing key** (`repo_key`): signs the F-Droid repository index. The
  alias is required by `fdroid/config.yml`.

Both keys can live in one PKCS12 keystore as long as it contains both aliases and the
store/key password is the same. Keep the keystore and password in a safe offline
backup; GitHub does not let you download an Actions secret later. Never commit the
keystore, base64 copy, or passwords; `.gitignore` excludes `*.p12`, `*.jks`, and
`*.keystore`. Write any base64 copy outside the repository, for example under `/tmp`.

Generate the keystore once:

```bash
keytool -genkeypair -storetype PKCS12 -keystore finance-alpha.p12 \
  -alias finance-alpha -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=TxnSense, O=CommitAndQuit, C=IN"

keytool -genkeypair -storetype PKCS12 -keystore finance-alpha.p12 \
  -alias repo_key -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=TxnSense F-Droid Repo, O=CommitAndQuit, C=IN"
```

Base64-encode the keystore for the secret value:

```bash
base64 -w0 finance-alpha.p12 > /tmp/finance-alpha.p12.b64
```

Repository Actions secrets:

- `ALPHA_KEYSTORE_BASE64`: base64 of the app-signing keystore.
- `ALPHA_STORE_PASSWORD`: the app-signing keystore/key password.
- `FDROID_KEYSTORE_BASE64`: base64 of the repository-signing keystore. This may be
  the same value as `ALPHA_KEYSTORE_BASE64` if one keystore holds both aliases.
- `FDROID_STORE_PASSWORD`: the repository-signing keystore/key password.

The release workflow passes `FM_ALPHA_KEYSTORE`, `FM_ALPHA_STORE_PASSWORD` and
`FM_REQUIRE_ALPHA_SIGNING=true` into Gradle. A normal local build uses the developer's
standard debug signer. Official alpha release builds fail if the persistent signer is
absent. Restrict repository write access because trusted release workflow code can
access these secrets.

## Each release

1. Update `android-app/version.properties`: choose a new `versionName` and a
   `versionCode` greater than every previous downloadable APK. Never reuse an old code.
2. Update `CHANGELOG.md`, including known issues and any upgrade restrictions.
3. Update `fdroid/metadata/in.txnsense.app.yml` `CurrentVersion` and
   `CurrentVersionCode` to match the new release, then add the localized
   `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` entry.
4. Review the source changes and stage only intended files. Run
   `python tools/verify_public_tree.py`; no private corpus or signing files may be tracked.
5. Run relevant JVM and emulator tests. Build and lint with
   `./gradlew testDebugUnitTest lintDebug assembleDebug` from `android-app`.
6. For schema changes, supply a non-destructive Room migration and test installing the
   next APK over the previous official version with sample records preserved. Never
   use a destructive fallback to conceal a migration failure.
7. Merge/push the reviewed source to `main`.
8. In **Actions → Release Signed Alpha APK → Run workflow**, select `main`. The
   workflow validates the version, creates the `v<versionName>` tag, builds and signs
   the APK, and publishes a pre-release with the APK, SHA256SUMS, signing certificate
   and notices.
9. Publishing the release triggers **Deploy F-Droid Repository**, which downloads the
   APK, signs the repository index, and deploys the GitHub Pages repository.
10. If a release is incorrect, do not force-move the tag or replace a published APK.
    Bump the version and publish a new release.

The release workflow runs only from `main` and is manually dispatched; pull-request
workflows cannot access release secrets. Tags are created by GitHub Releases and never
force-moved. Local builds use the developer debug signer and are not release artifacts.

## F-Droid repository

The self-hosted repository is published at:

```
https://commitandquit.github.io/txn-sense/fdroid/repo
```

Set the repository Pages source to **GitHub Actions**. The deploy workflow fails
instead of generating a new repository identity when the keystore secrets are missing.
`fdroid update` indexes the signed APK and uses `fdroid/config.yml`, which reads the
repository password from `FDROID_STORE_PASSWORD`.

The repository client link is:

```
fdroidrepos://commitandquit.github.io/txn-sense/fdroid/repo
```

`fdroidrepo://` is the HTTP scheme; use `fdroidrepos://` for this HTTPS repository.
The deployed landing page appends the repository key fingerprint when available.

Existing releases before `0.2.0-alpha.1` used ephemeral CI signing keys. Users of
those builds must uninstall once before installing a release signed with the new
persistent key. Document this in the release notes; uninstalling deletes the local
ledger. Do not later publish the same application ID to the official
`f-droid.org` catalog with a different signing key.

## Emulator verification

Use a dedicated emulator, not a personal phone. The instrumentation suite temporarily
creates sample rows and grants SMS/notification permissions. It cleans its own rows;
Android permission grants can remain afterward.

Standard tests: `./gradlew connectedDebugAndroidTest`. One host-driven SMS test is
intentionally skipped in that command because it needs a coordinated emulator SMS.

To run that test, target the emulator and add
`-Pandroid.testInstrumentationRunnerArguments.class=in.txnsense.app.SmsBroadcastE2eTest`
and `-Pandroid.testInstrumentationRunnerArguments.runSmsE2e=true`. While it runs,
wait until `adb -s emulator-5554 shell run-as in.txnsense.app test -f files/synthetic_sms_test_ready`
returns exit code 0, then send exactly one synthetic message:

```sh
adb -s emulator-5554 emu sms send 5551234 "INR 314.15 debited from your account via UPI"
```

The test has a 45-second receive window and checks receiver → encrypted record →
notification. Ordinary service calls and JVM parsing do not prove that platform path.

## Downloads and future production builds

### Signed upgrade verification

Use a disposable emulator with no personal ledger and always select its explicit
ADB serial. Install the published previous APK first. Build the candidate and its
instrumentation APK with the same dedicated alpha signer; never install an ordinary
developer-signed build over an official alpha.

Install the matching test APK with `adb -s SERIAL install -t TEST_APK`, then run:

```sh
adb -s SERIAL shell am instrument -w -e class in.txnsense.app.OfficialUpgradeTest -e upgradeStage seed in.txnsense.app.test/androidx.test.runner.AndroidJUnitRunner
adb -s SERIAL install -r CANDIDATE_APK
adb -s SERIAL shell am instrument -w -e class in.txnsense.app.OfficialUpgradeTest -e upgradeStage verify in.txnsense.app.test/androidx.test.runner.AndroidJUnitRunner
```

The seed stage creates and edits one synthetic record using the previous app's API.
The verify stage checks the amount, correction history, migrated schema and reopened
encrypted storage. Require `OK (1 test)` from both stages; ADB exit status alone is
not sufficient. Do not uninstall between stages. Repeat verification after stopping
the process to exercise cold database reopening. This is not proof that Android will
deliver SMS to a force-stopped app; force-stop and ordinary background process death
have different platform semantics.

In the previous `0.1.0-alpha` line, this protocol passed over the preceding
published APK on an isolated Android 16 emulator, including cold process reopening.
Physical-phone upgrades and OEM-specific receiver behavior still need validation.

### Repository concurrency checks

Run `LedgerConcurrencyTest` and `LedgerIntegrationTest` on the isolated emulator
with the candidate's matching instrumentation APK. These tests use random database
namespaces and erase only their synthetic ledgers. The concurrency checks exercise
24 simultaneous duplicate deliveries, duplicates racing a correction, 12 concurrent
edits with a persisted audit chain, and erasure competing with an active capture
callback and 16 queued captures. They also verify that erased database keys stay
absent until a new explicit manual entry creates fresh storage.

Require `OK (14 tests)` for the combined classes. These are single-process repository
checks, not exhaustive thread scheduling, physical-device, or Android notification
cancellation tests. Do not run test suites against a personal phone ledger.

### Multipart timing and background process recovery

On the same isolated emulator, run `SmsBroadcastE2eTest` with `runSmsE2e=true`,
`smsRepetitions=20`, and `expectMultipart=true`. The host must watch
`files/synthetic_sms_test_ready` using `run-as` and send exactly one synthetic SMS
per unique `UUID:sequence` marker. Use a unique sample label and enough neutral
padding to produce multiple segments, ending with the test's INR 314.15 debit.
Require `OK (1 test)` and inspect the delivered segment counts and timing output.
The test keeps the five-second assertion and reports all samples before applying it.

Timing is test-observer broadcast arrival to observing the encrypted record and
active notification, not exact production receiver entry or carrier-to-phone delay.
On Android 16, 20 three-segment samples passed at median 149 ms, p95 217 ms and max
1,830 ms with no concurrent build. An earlier run with a concurrent Gradle build
exceeded five seconds on sample three; host contention is a hypothesis, not a proven
root cause. Neither run establishes real-phone performance or the PRD latency gate.

For ordinary process recovery, run `ProcessRestartSmsTest` with `restartStage=prepare`
after the signed-upgrade seed exists. Launch the app, send it Home, then use
`adb -s SERIAL shell am kill in.txnsense.app`. Confirm `pidof` is empty before
sending `INR 701.23 credited via UPI; AvlBal: Rs9999.99` through the emulator console.
Observe the restarted process and notification before starting instrumentation again.
Run the same test with `restartStage=verify` and require `OK (1 test)`. It checks the
credit, notification and existing upgrade record, then deletes its synthetic credit
and restores preferences. Use only a disposable ledger. This protocol passed on the
isolated Android 16 emulator; it does not cover force-stop, reboot or OEM restrictions.

Users install the next official APK over the old one. The same package name, signer,
compatible schema and higher version code are necessary for an update. A development
build signed with a different key can conflict; do not tell users to uninstall
without explaining that their unbacked ledger will be deleted.

Before general use, verify real devices, upgrade paths, recovery/export strategy,
privacy behavior and a hardened non-debug build. A successful debug build alone
does not close those requirements.
