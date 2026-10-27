# System

English | [简体中文](README-zh.md)

A Minecraft NeoForge mod that brings a personal, web-novel-style "System" assistant into the game. The Agent runs on the server and handles queries, quests, daily rewards and shop interactions. The client provides a full workspace, a minimal dialogue view and HUD notifications with optional speech.

DeepSeek is the default model provider. Other OpenAI Chat Completions-compatible services can be configured. Explicit commands and confident local read-only routing reduce model calls; each module has its own execution budget.

## Features

- **Player and item queries:** view your status, public online-player names, and items by name or registry ID.
- **FTB Quests integration:** browse visible, unlocked, actionable and unfinished quests as compact lists. Open details by number or name, inspect rewards and explicitly claim them. Completion and unlock events appear as queued System notifications.
- **AI daily check-in:** the model selects a reward from a server-approved pool. Rewards enter an inbox for manual collection; the model cannot invent items or quantities.
- **System shop:** browse offers, review a quote and confirm an item-for-item exchange. A system-currency payment interface is reserved, with no built-in currency economy.
- **Data-driven lottery:** browse pools, review prize weights and single-draw costs, then confirm one draw. Prizes enter the inbox; item costs, free draws and reserved system-coin payments are configured through data packs.
- **Configurable content and workflows:** data packs define check-in rewards, shop offers and lottery pools; server rules can trigger bounded modules after advancements.
- **Two interfaces:** a full workspace with history and action cards, or a minimal view containing the current exchange and a single-line composer. Both support local command completion and native IME input.
- **HUD notifications and speech:** notifications do not take control away from gameplay. Optional Aliyun streaming TTS reads message bodies and stops when their view closes or their message ends or is replaced.
- **Usage and privacy controls:** per-player AI/TTS/ASR usage, history controls, external-AI opt-out, UUID-isolated storage and bounded model/tool execution.

The local router uses domain aliases, sparse TF-IDF and per-player exact intent caching. It ships without ONNX Runtime, DJL, native inference libraries or downloadable model weights. Low-confidence requests still fall back to the model; similarity scores are not calibrated probabilities.

## Requirements

| Component | Target |
| --- | --- |
| Minecraft | 26.1.2 |
| NeoForge | 26.1.2.114 or later for the same Minecraft version |
| Java | 25 |
| System | Install on both server and client |
| ApricityUI | Client dependency, 1.2.6 or later compatible with Minecraft 26.1.2 |
| Simple Voice Chat | Optional client microphone backend; developed against 2.6.26+26.1.2; not bundled or required on the server |
| FTB Quests | Optional; configured development version 26.1.2.8, with FTB Library 26.1.2.9 and FTB Teams 26.1.2.4 |

Install the dependencies required by your chosen mod versions as well. FTB features are registered only when FTB Quests is present; the other assistant features work without it. See the [development guide](docs/development.md) for build dependencies.

The network protocol is **3**. Update the client and server together; requests, responses, result cards and actions use registered Codec dispatch. Packets carry typed objects instead of JSON strings or special request IDs. Development saves use the current codecs directly, without legacy migration.

## Quick Start

1. Install System on both sides and ApricityUI on the client. Add FTB Quests and its dependencies if you want quest integration.
2. Start the server, or open a single-player world, once to generate the server configuration files under `config/system/`.
3. To enable AI, set `SYSTEM_DEEPSEEK_API_KEY` in the environment of the server process, then restart it. For single-player, the Minecraft process must inherit the variable. Configuration stores the environment-variable name, not the key.
4. An administrator grants access with `/system grant <player>`. Players are unbound by default.
5. Run `/system`, `/system full` or `/system simple`, then use the controls or type a request.

For example, in PowerShell, set the variable before launching the server from the same environment:

```powershell
$env:SYSTEM_DEEPSEEK_API_KEY = "<your-api-key>"
```

Explicit queries, shop browsing and item exchanges do not require a model key. AI check-in and model-based routing fallback do. `/system usage` remains available without a binding.

### Model Configuration

Model connection settings live in `config/system/system-model.json`. The default fields are shown below; restart the server after changes.

```json
{
  "providerType": "auto",
  "baseUrl": "https://api.deepseek.com",
  "model": "deepseek-chat",
  "apiKeyEnvironment": "SYSTEM_DEEPSEEK_API_KEY",
  "apiKeyRequired": true,
  "jsonMode": true,
  "outputTokenParameter": "max_tokens",
  "thinkingEnabled": false
}
```

`baseUrl` is the API prefix, without `/chat/completions`; the adapter appends that path. To use another compatible service, change the prefix, model ID and key environment variable, and select `providerType: "openai"` if needed. `auto` enables DeepSeek-specific parameters only for its official domain; DeepSeek proxies can explicitly use `deepseek`.

The text-model interface currently uses non-streaming JSON decisions. Streaming applies to TTS audio. See [provider compatibility](docs/model-api.md) for local endpoints, JSON-mode options and token-limit parameters.

## Commands and Interaction

The following commands run in Minecraft chat:

| Command | Action |
| --- | --- |
| `/system` | Open the last selected view |
| `/system full` / `/system simple` | Open a specific view |
| `/system ask <text>` | Submit a natural-language request |
| `/system player` | Query your status |
| `/system item <query>` | Search items |
| `/system quests [query]` | List ongoing quests, optionally filtered |
| `/system view <number or name>` | Open quest details |
| `/system claimable` | List quests with claimable rewards |
| `/system signin` / `/system inbox` | Check in or open the reward inbox |
| `/system shop [query]` | Browse shop offers |
| `/system buy <offer-id>` | Preview an exchange; payment still requires confirmation |
| `/system lottery [query]` | Browse or search lottery pools |
| `/system draw <pool-id>` | Preview prizes, odds and cost; a separate confirmation draws once |
| `/system usage` / `/system usage all` | View your usage, or all players' usage as an administrator |
| `/system grant [player]` / `/system revoke [player]` | Administrator binding and clearing operations |
| `/system reset [player]` | Clear old-format System records and binding; usage is retained, then grant again |
| `/system routing` | Administrator routing diagnostics |
| `/system help` | Show available actions |

`grant`, `revoke` and `reset` target yourself when the player argument is omitted. From the console, specify an online player. **`revoke` and `reset` delete the player's System business records**, including conversations, check-in records, inbox rewards and orders. Cumulative AI/TTS/ASR usage is retained. `reset` is intended for an old-format file that the current Codec cannot read; run `grant` again after it completes. Items already delivered, FTB progress and external payments are not rolled back.

Inside the **assistant's input box**, you can also use:

- `/select 1` or `1` to execute an action from the current reply.
- `/view 1` to inspect a quest from the current quest-list page.
- `/mode simple` or `/mode full` to switch views.
- `/memory on|off`, `/forget` and `/ai on|off` to control history and external AI.
- `/settings` to open tabbed settings; `/tts` or `/voice` selects Speech output, `/asr` selects Voice input; `/usage` opens usage; `/close` closes the view.

These local view commands are not standalone server commands. Enter sends; Up/Down chooses a completion; Tab or a click fills it. IME candidate confirmation takes precedence over sending.

Notifications appear at the bottom of the HUD. Open vanilla chat with its configured key, **T by default**, to click "Confirm" or an action. Consecutive notifications share the expanded panel and fade between messages. Confirming, timing out or replacing a notification cuts off its speech, so old audio cannot hold up the next message. Closing an assistant view also ends its speech session.

## Optional Speech

Voice input is disabled by default. Configure it with `/asr`, then hold **F8** to record and release to finish (remappable in Controls). The key opens the assistant from gameplay; `/listen` and the **Voice input** button remain available. Optional Simple Voice Chat is detected in settings: when installed, capture uses its selected device; otherwise it uses Java Sound. SVC is not required on the server. Enable it with an NLS project AppKey and temporary Token; DashScope TTS keys do not work here. Final recognition fills the draft, then Enter sends it; enable Send after recognition to send it automatically. Recording ends after 60 seconds and is cancelled with its screen. ASR estimates use the requested CNY 3.5 / 1,000 successful calls; add a CALLS entry to an existing pricing config. See [the recognition guide](docs/asr.md).

Open **Settings → Speech output** in the full workspace or type `/tts` in the assistant composer. Settings groups General, Speech output and Voice input in one screen. Switching tabs preserves drafts; **Save current tab** applies only that tab. General contains view, history and external AI preferences. Speech is disabled by default.

1. Enter an Aliyun Model Studio API key and choose its region, or provide `DASHSCOPE_API_KEY` to the client process.
2. Choose a model, voice, language and volume. Defaults are `qwen3-tts-flash`, `Cherry` and the game language.
3. Preview, enable automatic speech and save. Custom voice IDs must match the model they were created for.

After editing `config/system/system-speech.json`, click **Reload settings** to refresh speech settings and discard unsaved changes in this tab. Merge exported voice parameters into that file while preserving credentials and local preferences.

SSE audio is played incrementally, using Minecraft's Voice sound category first and direct OpenAL as a fallback. Session cancellation stops the request, buffered PCM and playback; late callbacks cannot restart the old audio. Known usage remains recorded.

The Python 3.10+ utility [`tools/voice_design.py`](tools/voice_design.py) supports voice design, cloning, synthesis and temporary upload. It uses the standard library; consult [the speech guide](docs/tts.md) for model compatibility and optional audio preprocessing.

```powershell
python tools/voice_design.py --lang en --help
```

## Content and Extensions

Check-in rewards, shop offers and lottery pools are defined entirely by data packs, including the bundled defaults:

```text
data/<namespace>/system/signin_rewards/<path>.json
data/<namespace>/system/shop_offers/<path>.json
data/<namespace>/system/lottery_pools/<path>.json
```

Copy the [example commerce pack](examples/commerce-pack) into the world's `datapacks/` directory and load it through the normal data-pack flow. Entries with new IDs are added; matching resource paths override bundled entries. Use `enabled: false` to disable an entry. Reloading content invalidates old shop and lottery confirmations, while rewards already credited retain their original contents.

Agent code lives under `com.iafenvoy.system.agent`. The root package contains execution, contracts, registries, commands and request/result types; `builtin`, `routing`, `model`, `store`, `commerce` and `prompt` group related interfaces, data and implementations. Built-in inputs and outputs remain standalone types in `agent.builtin.input` and `agent.builtin.output`. Shared Codec, Schema and JSON utilities live in `com.iafenvoy.system.util`, and configuration stays in `com.iafenvoy.system.config`. Trusted Java extensions register each module once through `AssistantRegistrationEvent`; its tool list and `ModuleRouting` declaration supply commands, semantic profiles, model visibility and shortcuts. Command execution, help and completion share the same registry. See [Agent runtime](docs/agent-runtime.md). Each module has independent model-call, tool-call, write-step, token and time limits. FTB integration calls typed local Java tools directly; it does not require stdio or HTTP MCP transport.

See [data packs and advancement rules](docs/datapacks.md) and [architecture and extension APIs](docs/development.md). System currency is an extension point only; balances, earning, transfers and top-ups are not implemented.

## Data and Privacy

All mod configuration files live in `config/system/` within their respective server or client instance. Missing configuration files are copied from bundled `defaults/<filename>.json` resources before decoding; existing files are preserved.

| Location | Purpose |
| --- | --- |
| Server `config/system/system-agent.json` | Execution budgets, concurrency, daily model limits and time zone |
| Server `config/system/system-model.json` | Model connection and provider options |
| Server `config/system/system-semantic.json` | Local intent routing, thresholds, examples and cache |
| Server `config/system/system-advancements.json` | Trusted advancement rules |
| Server `config/system/system-speech-pricing.json` | TTS rates; copied from bundled defaults if missing, loaded on server startup |
| `<world>/system-agent/<UUID>.json` | Binding, conversations, preferences, rewards, orders and workflow records |
| `<world>/data/system/token_usage.dat` | Persistent per-player AI/TTS/ASR usage; survives revoke and memory cleanup |
| Client `config/system/system-client.json` | View preference |
| Client `config/system/system-speech.json` | Local speech settings and any key entered in the UI |
| Client `config/system/system-asr.json` | Local ASR switch, NLS AppKey, temporary Token and region |

Player identity comes from the authenticated connection. Server checks, tool allowlists and bounded modules govern actions; model output cannot authorize arbitrary commands or prices. Conversations use bounded recent history and deterministic compaction, without an additional summarization-model call.

Server model keys stay on the server. Client speech keys stay on the client; keys entered in the UI are stored in plaintext in the local settings file. Necessary context is sent to the configured model service, and spoken text is sent to the speech provider. Clearing local history does not delete provider-side records.

AI usage comes from server-side API responses. TTS usage is reported by the client and is informational, not a trusted basis for payments or rewards. Fees use server-configured rates; editing rates takes effect after a server `/reload` or restart and does not recalculate past totals. Provider billing is authoritative. Reward delivery and payment involve separate game and ledger writes, so ambiguous crash states require manual reconciliation rather than automatic reissue. See [usage accounting](docs/token-usage.md) and [reward/payment behavior](docs/usage.md).

## Build

Use JDK 25 and the included Gradle wrapper:

```powershell
.\gradlew.bat compileJava testModClasses jar
```

On Linux or macOS, use `./gradlew` instead. The mod JAR is written to `build/libs/`; development-only `src/test-mod/` content is not included in the release JAR.

This command compiles the main and test-mod source sets and packages the mod; it does not run tests or launch Minecraft. In-game acceptance checks are separate.

## Documentation

Detailed documentation is currently in Chinese. Start with the [documentation index](docs/README.md).

| Guide | Topics |
| --- | --- |
| [Usage](docs/usage.md) | Installation, commands, interfaces, quests, shop, lottery and privacy |
| [Model API](docs/model-api.md) | Compatible providers and DeepSeek-specific handling |
| [Local routing](docs/semantic-routing.md) | TF-IDF, thresholds, intent caching and fallback |
| [Content configuration](docs/datapacks.md) | Rewards, offers, lottery pools, event rules and execution policies |
| [Speech](docs/tts.md) | Streaming playback, voices and Python tools |
| [AI/TTS/ASR usage](docs/token-usage.md) | Storage, accounting and cost estimates |
| [UI and localization](docs/ui-localization.md) | Shared styling, input, notifications and language files |
| [Development](docs/development.md) | Modules, tools, persistence and implementation boundaries |
| [Verification](docs/testing.md) | Compilation and in-game acceptance scope |
