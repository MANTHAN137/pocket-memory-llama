# Verification record

Prepared 1 November 2026 for the `com.pocketmemory.llama` preview, version 0.6.0 (20).

## Build

JDK 17, pinned Android tools and `scripts/build.sh`: app APK and instrumentation APK assembled; Android lint passed; APK signature and 16 KB ZIP/native alignment verified. The preview uses an Android debug certificate. CI builds and lints without weights; real inference evidence comes from the local emulator.

## Actual offline workflow

The pinned Meta Llama 3.2 3B GGUF was verified on the host and streamed into the app's emulator sandbox. This fixture supplies model consent only for that isolated test. The manual document-picker import/consent UI is available in the app; it is not the path automated by the fixture. The app has no INTERNET permission and the walkthrough runs in airplane mode. All source data is fictional.

The test checks malformed/unknown/duplicate model IDs, actual relevant-action selection (including exclusion of a separate event), application phase order, zero tasks before approval, stale-source rejection with zero partial writes, current-source rebuilding, the real review control, idempotent apply, completed-task undo refusal, successful undo and explicit reapply. A separate instrumentation invocation after actual process force-stop verifies persisted task identities/batch links and no automatic inference or duplicate execution.

Raw logs: [workflow](evidence/test-meta-llama.txt), [restart](evidence/test-planner-restart.txt). [Validation manifest](evidence/validation.json) includes the APK hash, pinned model identity, exact source hashes and distinct checks. [Media provenance](evidence/media-provenance.json) records actual capture/video hashes and narration.

## Honest boundaries

Llama selects relevant candidate IDs. The app stable-sorts selected actions by explicit preparation/arrival/access/follow-up rules. The raw model ID output and final source-derived action order are both retained, so these roles are inspectable. Phase grouping is narrow and can be wrong.

No accuracy percentage, latency or battery benchmark, physical-phone acceptance, or production traffic is claimed. Structural constraints prevent arbitrary IDs/extra fields/invented task text; they do not prove relevance. Users review all proposed tasks. Guarded undo protects created tasks based on their current state.

## Reproduce

Follow README build/model setup, start an isolated ARM64 Android 15 emulator, then run `python3 scripts/run_meta_demo.py --serial emulator-5554`. The script refuses physical-device serials. It replaces only the standalone planner's fictional fixture database and changes only the emulator's connectivity.

After successful verification, use `tools/prepare_review_media.py` for edited GIF/video generation and `tools/create_project_brief.py` for the PDF. Media helpers require the packages in `tools/requirements-preview.txt`, FFmpeg, and macOS system narration.
