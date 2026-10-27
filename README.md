# TxnSense

An offline, privacy-first Android expense tracker that records transactions from incoming financial SMS and lets you organize, track, and manage your spending locally.

**Key Highlights:**
- **100% Offline & Private:** Zero Internet permission (`android.permission.INTERNET` is not requested). No accounts, cloud sync, analytics, or trackers.
- **On-Device Storage:** Encrypted database (Room + SQLCipher) with keys managed securely via Android Keystore.
- **Deterministic SMS Parsing:** Fast, on-device template matching without machine-learning bloat or external API calls.

---

## Install via F-Droid

TxnSense is distributed through a self-hosted F-Droid repository. This is the current alpha channel.

**One-click add** (from an Android device with F-Droid installed):

[![Add to F-Droid](https://img.shields.io/badge/Add_to_F_Droid-TxnSense_Alpha-blue?style=for-the-badge&logo=android)](fdroidrepos://commitandquit.github.io/txn-sense/fdroid/repo)

Or manually:

1. Install the [F-Droid app](https://f-droid.org/) on your device.
2. Open F-Droid and go to **Settings → Repositories**.
3. Tap **+** and enter:
   ```
   https://commitandquit.github.io/txn-sense/fdroid/repo
   ```
4. Verify and accept the repository signing key fingerprint shown by F-Droid.
5. Search for **TxnSense** and install it.

**Current alpha:** `0.2.0-alpha.1`.

**One-time reinstall required:** earlier GitHub debug APKs were signed with ephemeral CI keys. Uninstall the old debug build once before installing this release. Uninstalling deletes the local ledger. Releases from `0.2.0-alpha.1` onward will update in place.

The official `f-droid.org` catalog submission is deferred while the self-hosted repository is the active channel.

---

## Roadmap

### Completed Features

- [x] **Automatic Financial SMS Capture**: On-device capture for UPI, Credit/Debit cards, NetBanking, Wallets, ATMs, and Bank Transfers.
- [x] **Deterministic Template Engine**: ~90+ bank layouts with sender-based bank and payment channel derivation.
- [x] **Manual Transaction Management**: Add, edit, categorize, or delete cash and digital transactions with custom timestamps and tags.
- [x] **Spend & Income Tracking**: Daily, monthly, and per-payment-method spend analytics with refund netting.
- [x] **Transaction Review Queue**: Safeguards uncertain amounts or unrecognized formats for manual verification before booking.
- [x] **Past SMS Import**: Optional on-device scan of the last 3 months of inbox messages with safety previews and undo support.
- [x] **Encrypted Local Storage**: Hardware-backed key protection using SQLCipher and Room database migrations.
- [x] **Modern Compose UI**: Material 3 UI featuring light/dark themes, responsive layouts, and unsaved-changes warnings.
- [x] **Automated CI/CD**: GitHub Actions workflow for automated APK building, tagging, and releases.

---

### Current Work: Self-Hosted F-Droid Repository

- [x] **Configure Repository Settings**: Configured [`fdroid/config.yml`](fdroid/config.yml) with repository metadata, URL (`https://commitandquit.github.io/txn-sense/fdroid/repo`), and 512x512 branded [`fdroid/icon.png`](fdroid/icon.png).
- [x] **Automate GitHub Actions Workflow**: Added [`.github/workflows/fdroid-repo.yml`](.github/workflows/fdroid-repo.yml) to index signed releases and deploy to GitHub Pages with a landing page and `fdroidrepos://` badge.
- [x] **Prepare Signed Release Pipeline**: Added a manually dispatched release workflow that signs APKs with a persistent key and publishes checksums, signing certificate, and notices.
- [ ] **Add Persistent Signing Keys**: Add `ALPHA_KEYSTORE_BASE64`, `ALPHA_STORE_PASSWORD`, `FDROID_KEYSTORE_BASE64`, and `FDROID_STORE_PASSWORD` to repository Actions secrets. See [`docs/RELEASING.md`](docs/RELEASING.md).
- [ ] **Enable GitHub Pages**: Set repository Pages source to **GitHub Actions** in GitHub settings.
- [ ] **Test Client Subscription**: Verify adding the `fdroidrepos://` repository and installing/updating the app with the official F-Droid client.
- [ ] **Screenshots**: Capture and add 3-5 app screenshots under `fdroid/metadata/in.txnsense.app/en-US/images/phoneScreenshots/`.

### Deferred: Official F-Droid Catalog

The `fdroiddata` recipe is not being submitted yet. The app currently uses a separate signing identity for the self-hosted channel, and the same application ID must not be published to `f-droid.org` with a different key.

---

## Build from Source

Requirements: **JDK 17** and **Android SDK 36**.

```bash
cd android-app
./gradlew assembleDebug
```

Run unit tests:
```bash
./gradlew test
```

---

## License

This project is licensed under the [MIT License](LICENSE).
