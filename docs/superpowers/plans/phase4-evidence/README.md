# Phase 4 Diagnostic Evidence

Capture JSONs from each spike replay. Files in this directory are gitignored
except for this README.

## Layout

- `baseline-run{1,2,3}.json` — current main, no changes (Task 2)
- `spikeA-run{1,2,3}.json` — narrator-voice style exemplar (Task 3)
- `spikeB-run{1,2,3}.json` — frequency_penalty 0.1 (Task 4)
- `spikeC-run{1,2,3}.json` — RECENT PLAYER CHOICES block (Task 5)
- `spikeD-run{1,2,3}.json` — RECENT STORY 4×600 (Task 6)
- `combined-run{1,2,3}.json` — winning spikes layered (Task 7, optional)
- `<variant>-notes.md` — per-variant eyeball verdict

Each JSON is the response from `GET /ai/debug-log` after replaying the 10-turn
script in the parent plan.

## Reading the captures

The array contains one entry per turn with `userPromptSent`, `systemPromptSent`,
and `rawAiResponse`. Compare across variants by eyeball — see the "Comparison
metrics" section in `../2026-04-30-issue-triage-phase4.md`.
