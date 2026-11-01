# Verification — Pocket Memory Llama 0.8.0

These checks cover Pocket Memory Llama 0.8.0.

## Completed

- Android build and lint completed with the pinned local toolchain.
- Packaged ARM64 libraries passed 16 KB LOAD-segment alignment checks; APK signature and ZIP alignment checks passed.
- Isolated Android 15 ARM64 emulator: actual Memory, Ask, Chat, Writing, Today and Models screens captured.
- Only Meta Llama 3.2 3B is offered in the model catalog.
- Real saved-memory inference: the fictional Juniper note produced a purple-adapter answer and a memory reference.
- Real Chat inference returned a local Llama reply.
- Saved memory remained present after inference.
- Separate Android package and signing identity verified; original Pocket Memory release/source were not changed.

## Download, web and companion checks

- In-app download: resumed the last 1 MiB through real HTTPS, checked the complete pinned 2 GB file, stayed on Models, then selected **Use this model** without import.
- Restart: a fresh process retained the explicit model choice, downloaded weights and fictional Juniper memory.
- Live MCP: one actual Exa search followed by a successful local Llama answer about Android DownloadManager, with checked source IDs.
- Adversarial guards: repeated normalized searches, unknown tools, oversized traffic, cancellation, unsafe source URL forms, obvious retrieved injection, invalid IDs, extra authority fields and invented links were rejected. A valid cited answer remained usable.
- Native repetition guards passed normal, stuck-token and repeated-cycle checks.
- Real browser WebMCP: discovered all five tools, checked Llama readiness, found the fictional Juniper memory and received a local Llama answer through ask_memory.
- Companion Stop: a running browser-started reply stopped and controls returned to idle.
- Local HTTP checks: unauthenticated API calls, cross-origin POSTs, invalid Host, reused connection code and oversized bodies were rejected.

Evidence is under [evidence](evidence/). Actual screenshots are under [images](images/). The companion browser test used emulator loopback forwarding. The Android service itself listens only on phone loopback.

## Reproduce locally

Build using scripts/build.sh. EditionInstrumentation runs only on an isolated emulator and requires the pinned model fixture and accepted model terms. Stages: show, core, web, download, restart, guards, companion. The download stage resumes a deliberately truncated fixture and checks the full resulting SHA-256. Never run fictional/destructive fixtures on a physical phone.

No physical-phone speed, battery, accuracy or production-readiness measurement is claimed. A successful structural source-ID check does not prove answer truth. WebMCP browser compatibility must be reported from actual testing.
