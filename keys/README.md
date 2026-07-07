# Signing keys

- **`public-gpg.asc`** — public GPG key (safe to share for signature verification).
- **Private keys must never be committed.** Use `gradle.secrets.properties` (gitignored) for local publish/signing.

## If a private key was exposed (archive, zip, or git history)

Treat the key as **compromised** and rotate immediately:

1. **Revoke the old key** on the keyserver (if published) and in your password manager.
2. **Sonatype / Maven Central:** open a [Central Portal](https://central.sonatype.com/) ticket to disable the compromised signing key and register a new public key.
3. **Generate a new GPG key pair** outside the repository; store the private key only in a secrets manager or encrypted local path **not** under this repo.
4. **Rotate Maven Central credentials** (username token / password) if they appeared in any shared artifact or history.
5. After `git filter-repo` (or BFG) rewrites history, **force-push only after team coordination** — everyone must re-clone or reset.

Do not distribute release archives built by zipping a working directory (they may include `.git/`, `build/`, or ignored secrets). Use:

```bash
git archive --format=zip --output ../bidscube-sdk-android-clean.zip HEAD
```
