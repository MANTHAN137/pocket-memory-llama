# Reproduce the new planner example

Use the `com.pocketmemory.llama` hackathon preview. Import the pinned Meta Llama 3.2 3B model and review its terms before inference. No API key is required.

## Fictional source pack

Tap **Load fictional example** in Sources when empty. All names, places and events below are invented.

| Note | Instruction |
|---|---|
| Arrival | The Lakeview client demo is on 2 November 2026. Arrive at Riverside Hub at 09:45 for the 10:00 demo. |
| Demo kit | Bring the prototype tablet, USB-C hub and printed storyboard to the Lakeview client demo. |
| Demo venue | Use the east entrance to reach room Cedar for the Lakeview client demo. |
| Follow-up | After the Lakeview client demo, send Mira the revised storyboard for approval before production. |
| Separate party | This is a different event, not the client demo. Bring blue balloons to the Lakeview team party on 20 November 2026. |

Goal: **Prepare for and follow up on the Lakeview client demo.** Task date: **2026-11-02**.

## Walkthrough

1. Build with Llama. It selects relevant IDs from allowed source actions. The app orders explicit workflow phases. Planning itself creates no tasks.
2. Inspect each action's evidence. Uncheck any action you do not want. Review relevance and order yourself.
3. Before applying, edit the venue note to: **Use the west entrance to reach room Orchid for the Lakeview client demo.**
4. Return to Plan. The old proposal is stale and its apply button is disabled. The transaction also refuses an outdated proposal even if invoked directly.
5. Build again. Inspect the current Orchid source and approve the selected tasks.
6. Open Tasks. Repeated apply reuses the batch and does not duplicate work.
7. Undo the batch while its created tasks remain unchanged and incomplete. Completing a created task blocks the entire undo. Uncomplete it to try undo, then explicitly reapply if desired.
8. Reopen the app: tasks and batch links remain; the app does not run Llama or apply again automatically.

The real offline emulator verification exercises this sequence, including the irrelevant party. Selection quality can vary with notes and goal. The edited video uses the verified screenshots and does not represent elapsed inference time.

## Three-minute demo

[Watch/download the narrated MP4](https://github.com/MANTHAN137/pocket-memory-llama/releases/download/v0.6.0-plans/PocketMemory-Plans-Demo.mp4). All scenes show the new hackathon workflow. Voice: locally synthesized system narration; no soundtrack. [Media provenance](evidence/media-provenance.json) records source image and final video hashes.
