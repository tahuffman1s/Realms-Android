# Realms of Fate — Android

This file is a **short index**. Detailed agent instructions live in **`.cursor/rules/*.mdc`** so Cursor can load focused rules (with `alwaysApply` / `globs`) instead of one oversized document.

## Stack

Kotlin + Jetpack Compose RPG. Package **`com.realmsoffate.game`**. Gradle Kotlin DSL.

## Cursor rules (canonical)

| Rule | Role |
|------|------|
| **`project-context.mdc`** | Directory map, stack |
| **`task-workflow.mdc`** | Triage, execution, tests, finish, README cues |
| **`build-commands-env.mdc`** | `./setup-env.sh`, common `gradle` commands |
| **`build-deploy.mdc`** | `installDebug`, adb, port forward |
| **`debug-bridge-verify.mdc`** | Mandatory verification after deploy |
| **`debug-bridge-test-procedures.mdc`** | P0–P9 procedures + contextual table |
| **`debug-bridge-reference.mdc`** | Endpoints, devices, pulldebug, fish `debug` |
| **`shell-permissions.mdc`** | Non-`sudo` commands; `~/.cursor/permissions.json` |
| **`commit-after-verify.mdc`** | Commit after verification |
| **`testing.mdc`** | JVM tests (applies under `app/src/test/**`) |
| **`releases-signing.mdc`** | Semver, tags, signing (applies when editing `app/build.gradle.kts`) |
| **`skills-and-tools.mdc`** | Optional Superpowers / Context7 routing |

Start with **`project-context.mdc`** + **`task-workflow.mdc`**; follow deploy/verify rules whenever **`app/`** or app Gradle changes.

## Legacy note

Older sessions referenced this file as the full playbook. Prefer **`.cursor/rules/`** as the source of truth.
