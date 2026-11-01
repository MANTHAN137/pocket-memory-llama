# Practical examples in the demo

The 2:05 walkthrough uses fictional data, real app output and the locally synthesized Rishi voice. It is edited from actual Android/browser captures, including genuine saved-answer/history views. It makes no inference-speed claim.

## 1. Find travel essentials

Save **Weekend stay · spare keys**:

> The spare keys for the weekend stay are in the blue pouch inside the front pocket of my grey backpack. Check-in starts after 3 pm.

Ask **Where did I keep the spare keys for the weekend stay?**

The recorded Llama answer identifies the blue pouch, front pocket and grey backpack, and cites the saved note. This helps recall a small detail at the moment it is needed.

## 2. Keep a client update traceable

Save a Cedar Studio brief for Friday morning with English captions. Edit the same memory to:

> Cedar Studio updated the video brief. Deliver the product video by Monday evening with Hindi captions. Do not add background music.

Ask **Which day should I deliver Cedar Studio's video, and which caption language should I use?**

The recorded run could not verify a generated answer. The demo explicitly shows its **source-only safety fallback**, containing the current Monday/Hindi instructions. Earlier wording remains in Memory history. This is not presented as a successful generated summary.

## 3. Research before a trip

In Chat, enable **Search the web** and ask:

> How do I download an area in Google Maps for offline use on Android? Use Google's official help.

One actual MCP search returned Google Maps Help excerpts. Local Llama produced the displayed answer with checked source IDs and URLs. Review the linked official guidance before following it. The private key-location note and conversation history are not attached to the query.

## 4. Find the same client brief from the browser

Open **Settings → Browser companion · WebMCP** on the same phone and review the access notice. In a supporting browser, a permitted agent calls **search_memories** with **Cedar Studio**. The recorded tool call returns the current saved brief from Android. Ordinary browser buttons work without WebMCP support.

The companion runs on phone loopback, expires after 15 minutes, and has bounded operations and model generations. A browser agent may process returned content under its provider's policy. Turn it off when finished.

## Setup and reproduction

Download Llama in **Models**, wait for full-file verification, then tap **Use this model**. After setup, saved-memory answers run offline. Downloads and optional web search require a connection. There is no permanent public phone API URL.

Demo fixtures live only in Android test code and run on an isolated emulator. Stages **demo**, **demo_maps**, **demo_details** and **companion** capture actual results without changing the production APK. The Writing experiment returned the input unchanged and is not presented as an improvement.

[Case evidence](evidence/demo-use-cases.json) · [Narration](DEMO-NARRATION.md) · [Media provenance](evidence/media-provenance.json)
