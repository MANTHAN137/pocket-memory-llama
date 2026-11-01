# Security and privacy reports

Do not put private memories, databases, weights or credentials in public issues. Report reproducible concerns privately through the repository owner's available private reporting or contact channel. Use fictional data for evaluation.

Llama inference stays on Android. The model is pinned by revision, bytes and SHA-256 and downloaded through the app's foreground service. Optional MCP search sends only the explicit Chat message to Exa; it does not attach memories or conversation history.

The optional WebMCP companion can return matching memories to a permitted browser agent. The notice explains that the agent may process those results under its provider's policy. It binds to loopback, requires a one-use connection code and expiring bearer session, rejects cross-origin access and limits operations. It cannot edit/delete memories or expose arbitrary shell, filesystem, payment or model-install tools. [Exact boundaries](docs/MCP.md).

Traffic, context, output and deadlines are bounded. Retrieval and model answers remain untrusted; these controls do not prove factual accuracy or eliminate prompt injection. This is a debug-signed evaluation APK. Physical-device testing is pending.
