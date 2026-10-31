# Architecture

The new planner compiles saved instructions into a reviewed task batch. Model output never becomes arbitrary executable text.

## State and inference boundaries

`PlanStore.snapshot()` returns immutable source records and a hash of that same list. `LlamaPlanner` ranks notes by goal terms, extracts explicit action sentences, and assigns candidate IDs. Relevant notes are bounded to eight, candidates to sixteen and selected actions to six.

`LocalLlama` calls a new JNI adapter around the pinned public llama.cpp runtime. Llama 3.2 3B receives the goal, chosen date, candidate contexts and a system instruction treating notes as untrusted data. A grammar limits output to a single JSON object containing allowed integer IDs. The Java parser rejects extra fields, unknown IDs, duplicates, fractions and prose. One format-repair attempt is allowed; refusal never falls back to invented tasks.

For accepted IDs, the app copies the original action text, source ID/version/title/body into an immutable proposal. Llama provides semantic selection. The app stable-sorts selected candidates by explicit phase: Bring/Pack/Collect/Buy/Prepare, then other actions including Arrive, then Use/access, then source contexts beginning After/Following. Model order is preserved within a phase. These narrow rules are not a general dependency solver. The model cannot edit source notes, infer new dates, create tasks, send messages or invoke shell commands.

## Review and atomic execution

The user chooses actions and explicitly approves a batch. Immediately inside a SQLite transaction, `apply()` compares the current source hash with the proposal snapshot. Any source change rejects the entire operation. A deduplication key includes source identity/version, exact action and date. Batch links record whether each task was newly created or reused. Applying an already applied plan returns existing links.

`undo()` checks all newly created tasks before deleting any. If a created task is missing, completed or changed in title/date, the transaction refuses the whole undo. Reused tasks are preserved. Undo is based on current task state, not a claim of tracking all historical edits. Reapplying an undone, fresh plan requires explicit review.

## Cancellation and persistence

Each inference job receives an epoch ticket and its own cancellation flag. The native abort/progress callbacks enforce ticket cancellation and a monotonic deadline. A watchdog requests stop after 150 seconds; native calls are individually bounded to at most 120 seconds. Model/context/samplers are released on exit. Android process termination is still outside application control.

Notes, proposals, source snapshots, raw model output, task links and task status live in independent `llama-plans.db`. Restart restores persisted state without automatic inference or task execution.

## Privacy and deployment

The manifest requests no permissions, including no INTERNET permission. A browser handles the optional pinned model download; Android's document picker grants access to a user-selected model file. Import verifies size and SHA-256 before enabling inference. No weights are distributed in the APK or Git repository. Debug signing is for evaluation; no production key is used.

See [limits](../README.md#limits-and-next-steps) and [verification](VERIFICATION.md) for the supported scope and recorded evidence.
