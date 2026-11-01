# Try Pocket Memory with Llama

Use fictional data for evaluation.

1. In Models, download Llama and tap **Use this model** after verification.
2. Add a Memory titled **Juniper demo kit** with: **Bring the purple adapter to the Juniper demo.**
3. Ask: **What should I bring to the Juniper demo?** The actual verified Llama answer identified the purple adapter and returned a saved-memory reference.
4. Open Chat and ask **Say hello in one short sentence.** This uses the same local Llama model.
5. In Chat, enable **Search the web** and ask a short public-information question. Only this message is sent to Exa. Inspect the returned source URLs.
6. Open Settings → **Browser companion · WebMCP**. Review what the browser agent can read, then choose **Open companion**. Search for **Juniper** in the browser.
7. In a WebMCP-capable browser, an agent can call get_model_status or search_memories, or ask_memory with the same question. Ordinary browser buttons work without WebMCP support.
8. Turn off the companion from its notification or Settings.

After model setup, Memory, Chat and Writing use on-device inference. Downloads and optional web research need a network. The browser companion works on the same phone while its session is open; it is not a permanent public API URL.

A demonstration must show actual Pocket Memory screens and disclose any editing, narration or sped-up waits.
