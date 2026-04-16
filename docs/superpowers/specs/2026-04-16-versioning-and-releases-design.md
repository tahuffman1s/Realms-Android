# Versioning & Release Pipeline Design

## Context

Realms of Fate is a single-developer Android RPG currently in alpha. No releases
have been published. The app will eventually target the Google Play Store, which
requires strictly increasing `versionCode` integers and meaningful `versionName`
strings.

The goal is to establish a versioning system and automated release pipeline now
so that every release from alpha through production follows a consistent,
industry-standard process.

## Version Scheme: Semantic Versioning

Format: `MAJOR.MINOR.PATCH` per [semver.org](https://semver.org).

Starting version: **0.1.0** (pre-1.0 signals alpha/development).

### Bump rules

| Bump | When | Example |
|------|------|---------|
| MAJOR | Breaking changes or graduation to production release | `0.x.y` -> `1.0.0` |
| MINOR | New features, new game systems, significant UI changes | `0.1.0` -> `0.2.0` |
| PATCH | Bug fixes, balance tweaks, prompt adjustments, cosmetic changes | `0.1.0` -> `0.1.1` |

### Milestones

- `0.x.y` -- Alpha. Active development, features being added.
- `1.0.0` -- First public Play Store release. Feature-complete enough for
  strangers to play.
- `1.x.y+` -- Post-launch. Semver rules apply normally.

## versionCode Strategy

Computed deterministically from the version string:

```
versionCode = MAJOR * 10000 + MINOR * 100 + PATCH
```

| versionName | versionCode |
|-------------|-------------|
| 0.1.0       | 100         |
| 0.1.1       | 101         |
| 0.2.0       | 200         |
| 0.12.3      | 1203        |
| 1.0.0       | 10000       |
| 2.1.5       | 20105       |

This guarantees strictly increasing codes for any semver-legal sequence. Supports
up to 99 patches per minor and 99 minors per major, which is more than sufficient.

## Git Tags

Format: `vMAJOR.MINOR.PATCH` (e.g., `v0.1.0`).

Tags are the sole release trigger. Pushing a tag matching `v*` to GitHub kicks
off the release pipeline. No other mechanism creates releases.

## GitHub Actions Release Pipeline

Workflow file: `.github/workflows/release.yml`

Trigger: tag push matching `v*`.

### Steps

1. **Checkout** the tagged commit.
2. **Set up JDK 21**.
3. **Run tests** (`gradle test`). Fail fast -- no release if tests fail.
4. **Build release APK** (`gradle assembleRelease`). Unsigned for now; signing
   config will be added when preparing for Play Store submission.
5. **Create GitHub Release** using the tag name as the release title.
6. **Attach APK** as a release asset.
7. **Auto-generate release notes** from commits since the previous tag.

### Future additions (not in scope now)

- APK signing with upload keystore (required for Play Store).
- AAB (Android App Bundle) build for Play Store submission.
- Play Store upload via Gradle Play Publisher plugin.

## CLAUDE.md Integration

A "Versioning & Releases" section will be added to CLAUDE.md with instructions
for Claude to follow automatically:

### On release request

When the user says "release", "cut a release", "tag a release", or similar:

1. Determine bump type (major/minor/patch) from the work done since the last
   tag. If ambiguous, ask the user.
2. Compute the new version string and versionCode.
3. Update `versionName` and `versionCode` in `app/build.gradle.kts`.
4. Commit with message: `Release vX.Y.Z`.
5. Create an annotated git tag: `vX.Y.Z`.
6. Push the commit and tag.

### On normal commits

Do NOT bump versions. Versions only change at release time.

### Version source of truth

`app/build.gradle.kts` is the single source of truth. The `versionCode` is
always derived from `versionName` using the formula above -- never set
independently.
