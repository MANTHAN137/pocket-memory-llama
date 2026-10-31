# Hackathon entry and new-work disclosure

**Project:** Pocket Memory · Built with Llama  
**Developer:** Manthan Dhole (solo)  
**Track:** Indie Developer Track  
**Theme:** Build for Your World

## Problem and contribution

Instructions for one real-world goal often live across several notes. A useful assistant must select relevant actions, preserve evidence and handle changes before writing tasks. Pocket Memory adds a native, local planning and execution workflow: constrained Meta Llama action selection, application-owned phase ordering, user review, freshness checks, atomic task batches, deduplication, guarded undo and restart persistence.

## New work versus foundation

| Built for this hackathon edition | Pre-existing foundation |
|---|---|
| Focused Java Android app and planner screens | Pocket Memory product concept and visual palette |
| Immutable source snapshots and goal-based candidate extraction | Public llama.cpp inference runtime, pinned and attributed |
| New JNI grammar-constrained Llama adapter with per-job cancellation | Meta's Llama 3.2 3B model and third-party GGUF quantization |
| Proposal validation, review and source-change blocking | Android, SQLite, Gradle and standard toolchains |
| Atomic local task application, idempotency and guarded undo | Android, SQLite and the standard Android build toolchain |
| Actual offline emulator tests, GIF, narrated demo and project brief | Standard development and media tooling |

The participant guide excludes basic RAG applications. This submission therefore demonstrates the newly built planning/execution state machine rather than generic note Q&A. The organizer makes the final eligibility decision; no qualification or prize is guaranteed.

Only Meta Llama 3.2 3B performs language-model inference. No hosted Meta API or Muse model was used, and this entry does not claim a model-specific Muse bonus prize or redeemed API credits.

## Submission materials

- Public repository: https://github.com/MANTHAN137/pocket-memory-llama
- Demo: GitHub Release asset `PocketMemory-Plans-Demo.mp4`, under three minutes
- Evaluation APK/checksum and PDF: release `v0.6.0-plans`
- Exact form copy: [APPLICATION-ANSWERS.md](APPLICATION-ANSWERS.md)
- Reproducible evidence: [VERIFICATION.md](VERIFICATION.md)

## Official event and consent

[Event](https://cerebralvalley.ai/e/meta-ai-developer-hackathon) · [Participant guide](https://cerebralvalley.ai/e/meta-ai-developer-hackathon/details) · [Waiver](https://cerebralvalley.ai/e/meta-ai-developer-hackathon/waiver) · [Submission](https://cerebralvalley.ai/e/meta-ai-developer-hackathon/hackathon/submit)

The authenticated guide requires public source and a three-minute demo highlighting new hackathon work. Deadline shown: 4 November 2026, 9 PM EST (5 November, 7:30 AM IST). Recorded preparation date: 1 November 2026.

Submission status: **prepared; final organizer permissions require the participant's confirmation.** The organizer waiver includes broad non-exclusive, perpetual, irrevocable, worldwide, royalty-free rights to use/adapt/modify/publish/distribute submission materials. Ownership is retained. Final acceptance must be reviewed by the participant before submission.
