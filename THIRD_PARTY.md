# Components and notices

The public llama.cpp runtime is vendored under vendor/llama.cpp with its upstream license and notices. The Android build uses CPU inference and llama-common; server, tool and example binaries are disabled. Meta Llama 3.2 3B Instruct is separately downloaded under its Community License and Acceptable Use Policy. The pinned bartowski Q4_K_M artifact, revision, bytes and SHA-256 are recorded in models.json and README.

The existing Pocket Memory host includes a vendored whisper.cpp native voice engine under its upstream license; no voice-model download is offered in this Llama-only catalog. Google ML Kit Latin and Devanagari OCR and their transitive Android dependencies retain their upstream terms. Gradle, AGP, Kotlin BOM and the Android platform retain their respective terms. APK notices include applicable Llama, llama.cpp, whisper.cpp and Apache license texts.

Exa supplies the optional remote MCP search service under its own policies and provider-controlled limits. No Exa server software or API key is distributed. A narrow JSON-RPC/Streamable HTTP client is implemented in Java; no external MCP SDK is bundled.

Documentation screenshots use fictional data captured from the actual app. No stock media is used. Any walkthrough assembled from screenshots must disclose editing and make no inference-speed claim.

Pocket Memory's pre-existing product source and branding remain with their owner. The earlier repository MIT license must not be taken as permission to relicense private product code copied into this local working edition. The owner authorized publishing the copied core in this repository on 1 November 2026. The pre-existing core retains its copyright; LICENSE identifies the new standalone integration files covered by LICENSE-MIT. Vendored code and model terms are independent.
