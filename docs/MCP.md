# MCP web research

The app connects to `https://mcp.exa.ai/mcp?tools=web_search_exa`. Exa documents keyless search at a free rate limit; availability and limits remain provider-controlled. No API key is embedded.

## Protocol

The client sends JSON-RPC 2.0 over HTTPS POST with `Accept: application/json, text/event-stream`. It negotiates MCP 2025-11-25 (or the supported 2025-06-18 revision), validates the tools capability, sends `notifications/initialized`, discovers tools, then checks the expected search input schema. Calls include the negotiated protocol header and any valid returned session ID. JSON and bounded SSE responses must match their request ID. Redirects are refused. This is a deliberately narrow compatibility client, not a claim to support every MCP feature or newer protocol revision.

Only `web_search_exa` is permitted. Arguments are the exact user-entered question, a fixed factual-source objective and a result count of three. The server's tool descriptions and instructions are not forwarded as system prompts. Server-initiated sampling/actions are not supported. No private notes, task data, account credentials or filesystem roots are exposed.

## Conditions that stop a run

| Boundary | Limit |
|---|---|
| Search tool calls | One; repeated fingerprints and other tools refused |
| Protocol POSTs | Four: initialize, initialized notification, list, search |
| Response traffic | 196,608 bytes total |
| SSE events | At most 32 per response |
| Network waits | 10 s connect / 15 s read, within the run deadline |
| Sources | At most three unique HTTPS references |
| Excerpts | At most 800 characters each |
| Answer | One local Llama call, 192 output tokens, 4,096-token context |
| Total elapsed research | 180 s watchdog; cancellation checked throughout |
| Retries | No automatic research or answer retries |
| Writes | Saving a chat answer as a memory remains an explicit UI action |

The native answer grammar allows only an answer string and source-ID array. Java validates fields, source bounds, duplicates and unsupported links/instructions. There are no tools available to Llama during this answer step. A web page cannot request another search or alter the run's budget.

These controls reduce authority and looping risk. They do not prove relevance, factual accuracy, completeness or immunity to prompt injection. Obvious injection filtering is conservative and can discard legitimate sources. A failed answer stops with a clear message; users can inspect sources or explicitly try a new question.

## References

- [MCP lifecycle, 2025-11-25](https://modelcontextprotocol.io/specification/2025-11-25/basic/lifecycle)
- [MCP Streamable HTTP, 2025-11-25](https://modelcontextprotocol.io/specification/2025-11-25/basic/transports)
- [Exa MCP endpoint and keyless availability](https://exa.ai/docs/get-started/exa-mcp)
- [Chrome WebMCP guide](https://developer.chrome.com/docs/modern-web-guidance/guides/webmcp/agentic-javascript-tools)

## WebMCP browser companion

This is distinct from Android's outbound MCP search. The companion serves bundled assets on phone loopback, then registers five browser tools: get_model_status, search_memories, ask_memory, chat_with_llama and search_web. Actual discovery and tool calls were verified in the in-app browser, including a real phone-side Llama memory answer.

WebMCP needs a supporting browser; its ordinary controls remain available otherwise. The app opens the browser on the same phone. This is not a public hosted API or a permanent URL. A browser agent may process returned memory content under its provider's policy; opening the companion requires an explicit notice.

| Session boundary | Limit |
|---|---|
| Network bind | 127.0.0.1 only, ephemeral port |
| Connection code | 32 random bytes, one use, 60-second expiry, URL fragment removed after exchange |
| Session | In-memory bearer token, 15-minute expiry, explicit Turn off |
| Cross-origin access | Exact Host and same Origin; no CORS |
| Operations | 24 per session; six model generations |
| Duplicate questions | Same normalized kind/question returns the existing job |
| HTTP handling | Four workers, queue of four, 8 KB body and bounded headers/read time |
| Polling | At most 95 polls at two-second intervals |
| Permissions | Read memories, ask/chat/search; no memory edits/deletes, downloads, payments or arbitrary tools |

Stop cancels the active model/network job. Failed jobs are not automatically retried. Closing or expiring the Android service clears session credentials. A page reload requires reopening the companion from Android because the bearer token is never persisted.
