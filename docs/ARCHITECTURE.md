# Architecture

Pocket Memory stays native Android. Java owns screens, SQLite notes/conversations/tasks, model setup, consent and job limits. JNI adapters call the pinned llama.cpp runtime. The sole offered language model is Meta Llama 3.2 3B Instruct, Q4_K_M.

## Memory, Chat and Writing

Saved memories stay in local storage. Ask retrieves relevant passages and uses local Llama to answer with references. Chat sends a bounded conversation to local Llama. Writing uses existing input/context limits, numeric preservation checks and user review. Today retains Pocket Memory's task/reminder behavior. Existing cancellation, repetition checks and generation deadlines remain in place.

## Model lifecycle

Models is one Llama card: Download/Resume → verification → Use this model. ModelDownloadService uses an Android foreground notification, HTTPS, range resume, exact byte count and full SHA-256. A verified file is moved into its final private app location. Selection is persisted; there is no external download/import handoff. Storage and low-memory checks run before starting a transfer.

## Web research

Chat's explicit web switch routes only that submitted question to McpClient. It initializes a narrow MCP Streamable HTTP session, validates the advertised search schema, then invokes only Exa web_search_exa. WebResearch selects at most three bounded excerpts and performs one grammar-constrained local Llama answer. Java validates answer fields and source IDs. The model has no tool access during answering. ModelHarness enforces a 180-second outer budget and one generation call. Native output also has token/time/repetition stops.

NativeEngine's cached model is unloaded before the separate web-answer adapter loads Llama, avoiding two concurrent copies. Stop cancels the correct network/model work. No autonomous research retry or tool recursion is implemented.

## Browser companion

Android serves bundled HTML/CSS/JS through a foreground service on 127.0.0.1 at an ephemeral port. The user opens a 15-minute session from Settings. A 32-byte, one-use code in the URL fragment is exchanged for an in-memory bearer token and removed from browser history. Requests require the exact Host and same Origin; no CORS is enabled. Static assets have a restrictive content security policy.

The browser registers five actual WebMCP tools when document.modelContext (or the earlier navigator.modelContext) is available. Both ordinary controls and WebMCP call the same phone endpoints. Model work runs on the app worker and is polled as a bounded asynchronous job. Only status, memory search, memory Q&A, chat and web research are exposed. No memory writes, model installation, filesystem roots, shell, payments or arbitrary MCP endpoints are exposed.

Sessions permit at most 24 operations and six model generations. Duplicate normalized questions reuse the same job. HTTP workers, queue, body size, headers, polls and deadlines are capped. The service closes on expiry or explicit Turn off.

Inference stays on Android. A browser agent receiving memory results may process them outside the phone according to its provider's policy; the consent screen makes that boundary explicit. This is a local companion, not a publicly hosted API.
