# Prepared submission copy

Team name: **Pocket Memory**  
Member: **Manthan Dhole (Manthanx), solo**  
Track: **Indie Developer Track**  
Theme: **Build for Your World**

## Project description

Pocket Memory turns scattered saved instructions into an inspectable, reviewed task batch on Android. For a fictional client demo, Meta Llama 3.2 3B selects preparation, arrival, venue and follow-up actions while excluding a separate party. Every proposed task copies exact source text and includes its evidence. The model cannot create tasks by itself.

The app enforces explicit preparation/arrival/access/follow-up phase rules. The new hackathon workflow snapshots source versions, validates grammar-constrained action IDs, waits for human review and commits the selected tasks atomically. If a venue note changes, the old proposal is blocked with zero partial tasks. Repeated apply is idempotent; guarded undo protects completed or changed tasks. Plans and task links persist across process restart.

This is Pocket Memory’s Llama hackathon edition. Pocket Memory's concept/design palette and the public llama.cpp runtime are pre-existing foundations; the focused planner app, JNI adapter, review/execution controller, tests and demo were built for this edition. The demo highlights the new workflow only. Actual Meta inference was verified in an isolated ARM64 emulator in airplane mode. Physical-phone profiling and broader selection-quality evaluation remain pending.

## Public GitHub repository

https://github.com/MANTHAN137/pocket-memory-llama

## Three-minute demo

https://github.com/MANTHAN137/pocket-memory-llama/releases/download/v0.6.0-plans/PocketMemory-Plans-Demo.mp4

## Describe use of Meta models and tools

Meta Llama 3.2 3B Instruct, Q4_K_M GGUF, runs directly inside the Android app through llama.cpp and a new JNI adapter. Llama receives a goal and bounded source-action candidates, then selects relevant IDs. Application rules order the workflow phases. A restricted decoder and strict ID validator constrain output; the app copies approved candidate text rather than accepting invented task text. Human approval, source freshness and SQLite transactions control execution. The sole offered language model is Meta Llama. Model import verifies a pinned SHA-256. No hosted Meta API, Muse model or credit redemption was used.

## Why Meta models are important

Llama supplies the semantic relevance step that keyword matching alone cannot provide. The verified example contains an unrelated event sharing the same client name; the actual model selects the client-demo actions and excludes the party. Running open weights on the device lets the planner work without uploading notes or requiring a hosted inference service. Structural guards bound model authority while leaving relevance and ordering visible for the user to review. The APK has no INTERNET permission. The 3B model was chosen for the multi-source example; this prototype makes no production accuracy or phone-speed claim.

## API survey fields

No hosted Meta API was used. Leave optional ratings/comparison/production-use questions blank. For required API likelihood/opinion fields, Neutral is the prepared response because no API experience is being claimed. Optional feedback, if used: “This entry uses Meta open weights locally rather than the hosted API, so hosted API feedback is not applicable.”

Do not add a fabricated X/viral post. Do not accept the final organizer permission checkbox without the participant's action-time confirmation.
