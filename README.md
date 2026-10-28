# System

English | [简体中文](README-zh.md)

A NeoForge mod that brings the "System" from web novels into Minecraft. The Agent runs on the server and handles data
queries, quests, daily check-ins and shop interactions. The client provides a full workspace, a minimal dialogue view
and HUD notifications that keep gameplay uninterrupted, with optional speech output.

## Features

- **Player and item queries:** view your own status and public online-player names, or search for items by name or
  registry ID.
- **FTB Quests integration:** browse visible, unlocked, actionable and unfinished quests in a compact list. Open details
  by
  number or name, then explicitly select and claim rewards. Quest completion and newly unlocked quests appear as queued
  System notifications.
- **Explicit web research:** use `/system search <question>`, the Web search page, or a lookup request such as
  "look up how a comparator works." The routing model recognizes flexible wording and prioritizes local queries;
  its instructions exclude ordinary questions and history as search consent. Search and Fetch share the default DeepSeek key, have a separately configurable loop and are billed
  separately at CNY 5 per 1,000 successful requests with results; model tokens are additional.
- **AI daily check-in:** the model selects rewards from a server-approved pool. Rewards enter an inbox for manual
  collection; the model cannot invent items or change quantities.
- **System shop:** browse offers, review quotes and confirm payment through Saturn Costs, including item exchanges,
  resources and registered extensions. There is no built-in currency economy.
- **Data-driven lottery:** browse pools, review prize weights and single-draw costs, then confirm one draw. Rewards
  enter
  the inbox. Data packs configure Saturn Costs or free draws.
- **Configurable content and workflows:** data packs define check-in rewards, shop offers and lottery pools. Server
  rules
  can trigger bounded modules when players complete advancements.
- **HUD notifications and speech:** notifications do not take control of the mouse or keyboard. Optional Aliyun
  streaming
  TTS reads message bodies; closing a view, dismissing a notification or switching messages ends the corresponding
  speech session.
- **Usage and privacy controls:** server AI and web usage by player, private local TTS / ASR usage, history management,
  external-AI opt-out,
  UUID-isolated
  data and limits on model and tool execution.

## Quick Start

1. Install this mod and its required dependencies. Add FTB Quests and its dependencies if you want quest integration.
2. Start the server or open a single-player world once to generate the server configuration files under
   `config/system/`.
3. To use AI, set `SYSTEM_DEEPSEEK_API_KEY` in the server process's startup environment, then restart. In single-player,
   the Minecraft process must inherit the variable. Configuration stores the environment-variable name, not the key.
4. Install a server data pack defining a system, then an administrator binds it with
   `/system grant <system ID> [player]`,
   for example `/system grant example:daily Steve`. Use `/system grant <system ID>` for yourself. Each player can
   currently
   bind one system. The default system and its module tag are development-only resources in `src/test-mod/`; they are
   excluded from the release JAR.
5. Run `/system`, `/system full` or `/system simple`, then use the controls or type a request.

For example, set the variable in PowerShell, then launch the server from the same environment:

```powershell
$env:SYSTEM_DEEPSEEK_API_KEY = "<your-api-key>"
```

Explicit queries, shop browsing and item exchanges do not require a model key. AI check-in and model-based routing
fallback do. `/system usage` does not require a binding.

### Model Configuration

Model settings live in `config/system/system-model.json`. The default fields are shown below; restart the server after
changes.

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

Set `baseUrl` to the API prefix without `/chat/completions`; the adapter appends that path automatically. To use another
compatible service, change the URL, model ID and key environment-variable name, and explicitly set
`providerType: "openai"` if needed. `auto` enables DeepSeek-specific parameters only for the official DeepSeek domain;
when using a proxy for DeepSeek, explicitly select `deepseek`.

## Commands and Interaction

The following commands run in Minecraft chat:

| Command                               | Action                                                                                               |
|---------------------------------------|------------------------------------------------------------------------------------------------------|
| `/system`                             | Open the last selected view                                                                          |
| `/system full` / `/system simple`     | Open the full / minimal view                                                                         |
| `/system settings`                    | Open unified client settings directly; no binding required                                           |
| `/system ask <text>`                  | Submit a natural-language request                                                                    |
| `/system search <question>`           | Explicitly start paid web research; no automatic browsing                                            |
| `/system player`                      | View your own status                                                                                 |
| `/system usage` / `/system usage all` | View personal usage / server AI and web usage as an administrator; voice totals always stay personal |
| `/system grant <system ID> [player]`  | Bind one data-pack system as an administrator; omit the player to bind yourself                      |
| `/system revoke [player]`             | Revoke access and clear System records as an administrator                                           |
| `/system reset [player]`              | Clear old-format System records and binding; cumulative usage is retained, then grant again          |
| `/system routing`                     | View routing diagnostics as an administrator                                                         |
| `/system help`                        | Show available actions                                                                               |

Notifications appear at the bottom of the HUD. Open vanilla chat with its configured key (**T by default**) to click
"Confirm" or an action button. Consecutive notifications keep the panel expanded while the content fades out, changes
and fades in. Confirming, timing out or switching notifications immediately cuts off the old speech, so the next message
does not wait for it to finish. Closing an assistant view also ends its speech session.

## Optional Speech

**All speech features run on the client and use paid APIs.**

All speech features currently use Aliyun APIs. Support for more providers is planned.

### Voice Input

**Requires a separate Aliyun NLS project AppKey and temporary Token.**

**Installing Simple Voice Chat is recommended for a better experience.**

Voice input is disabled by default. Enter `/settings` in the assistant's input box, select **Voice input** and enable
it;
changes are saved automatically. Hold **F8** to record and release to finish. The key opens the assistant during
gameplay;
`/listen` and the **Voice input** button are also available. Final recognition fills the draft, then Enter sends it.
Enable
**Send after recognition** to send it immediately. Recording lasts at most 60 seconds and is cancelled when the view
closes.

### Speech Output

**Requires a separate Aliyun Model Studio API key and the matching region.**

Open **Settings → Speech output** in the full workspace, or enter `/settings` in the assistant's input box and select
the tab.

1. Enter an Aliyun Model Studio API key and select its region, or set `DASHSCOPE_API_KEY` for the client process.
2. Set the model, voice, language and volume. Defaults are `qwen3-tts-flash`, `Cherry` and the game language.
3. Preview, then enable automatic speech. Changes are saved automatically. Custom voice IDs must match the model they
   were created for.

Enter exported voice parameters directly in the settings fields. TTS does not support JSON import or a settings reload
button. Persistent settings remain in `config/system/system-speech.json`.

The Python 3.10+ utility `tools/voice_design.py` supports voice design, cloning, test synthesis and temporary uploads.
It uses the standard library.

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

See the examples in this GitHub repository for details.

## Data and Privacy

### Configuration Files

| Location                                          | Contents                                                                                  |
|---------------------------------------------------|-------------------------------------------------------------------------------------------|
| Server `config/system/system-agent.json`          | Execution budgets, concurrency, daily model limits and time zone                          |
| Server `config/system/system-model.json`          | Model connection and provider options                                                     |
| Server `config/system/system-semantic.json`       | Local intent routing, thresholds, examples and cache                                      |
| Server `config/system/system-advancements.json`   | Trusted advancement rules                                                                 |
| Client `config/system/system-speech-pricing.json` | Personal TTS / ASR rates; copied from bundled defaults if missing                         |
| `<world>/system-agent/<UUID>.json`                | Binding, conversations, preferences, rewards, orders and workflow records                 |
| `<world>/data/system/token_usage.dat`             | Server AI usage by player; retained after revoking access or clearing memory              |
| Client `config/system/speech-usage/<UUID>.json`   | Personal TTS / ASR totals for this local account, retained across servers; never uploaded |
| Client `config/system/system-client.json`         | View preference                                                                           |
| Client `config/system/system-speech.json`         | Local speech settings and any key entered in the UI                                       |
| Client `config/system/system-asr.json`            | Local recognition switch, NLS AppKey, temporary Token and region                          |

Configuration files are not synchronized between the client and server. Player data files in the world folder are
managed by the server; clients cannot modify them directly.

### API Usage

AI usage comes from server-side API responses. TTS and ASR totals stay on the client, per local account; they are never
uploaded to the game server. The all-player view contains AI only; voice panels always show your own local totals.
Voice fees use client-configured rates. Reload client resources with F3+T or restart after editing rates; past totals
are unchanged. Provider billing is authoritative.
