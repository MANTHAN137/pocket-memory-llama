# Third-party components and model

## Runtime

The public llama.cpp source is vendored under `vendor/llama.cpp`, pinned in `UPSTREAM`, under its MIT license. Its upstream dependency notices remain in the vendor tree. The build uses the CPU inference runtime; server, tools, examples and common utilities are disabled. See `native/llm/CMakeLists.txt`.

## Meta model

Built with Llama. Only Meta Llama 3.2 3B Instruct is offered. The Llama 3.2 Community License and Acceptable Use Policy are included under `app/src/main/assets/licenses/`. The model is a separate user download, not part of the APK or source archive. The pinned Q4_K_M artifact comes from bartowski's public GGUF repository; attribution and exact revision/hash are in README and `models.json`.

## Build and media

Android/Java/SQLite are platform facilities. Gradle and Android Gradle Plugin are build dependencies governed by their upstream terms. No runtime Maven dependencies are declared. Gradle Wrapper licensing is provided in its source distribution.

All example data, app screenshots, graphics and narration scripts are prepared for this project. The video is an edited walkthrough of actual app captures with locally synthesized macOS system narration and no music. Hashes and edit disclosure are in `docs/evidence/media-provenance.json`. No third-party stock imagery or video is used.

New application source, scripts, documentation and graphics are covered by this repository's MIT license. That grant does not cover the model, vendored third-party code or Pocket Memory trademarks.
