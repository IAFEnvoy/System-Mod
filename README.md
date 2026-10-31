# System

English | [简体中文](README-zh.md)

A NeoForge mod that brings the "System" from web novels into Minecraft. The Agent runs on the server and handles data
queries, quests, daily check-ins and shop interactions. The client provides a full workspace, a minimal dialogue view
and HUD notifications that keep gameplay uninterrupted, with optional speech output.

## Features

- **Player and item queries:** view your own status and public online-player names, or search for items by name or
  registry ID.
- **FTB Quests integration:** browse visible, unlocked, actionable and unfinished quests in a compact list. Open details
  by number or name, then explicitly select and claim rewards. Quest completion and newly unlocked quests appear as
  queued System notifications.
- **Explicit web research:** use `/system search <question>` or the Web search page to request a lookup. Ordinary
  questions do not trigger automatic browsing. Search shares the DeepSeek key by default, has a separately configurable
  loop budget, and meters Search and Fetch requests separately.
- **AI daily check-in:** the model selects rewards from a server-approved pool. Rewards enter an inbox for manual
  collection; the model cannot invent items or change quantities.
- **System shop:** browse offers, review quotes and confirm payment through Saturn Costs, including item exchanges,
  resources and registered extensions. There is no built-in currency economy.
- **Data-driven lottery:** browse pools, review prize weights and single-draw costs, then confirm one draw. Rewards
  enter the inbox. Data packs configure Saturn Costs or free draws.
- **Configurable content and workflows:** data packs define system names, descriptions, enabled modules, check-in
  rewards, shop offers and lottery pools. Modules use a synchronized `system_module` registry: server behavior is
  selected by `server.type`, client UI by a separate `client` ID. Systems reference module instances or tags, with
  conditions. Server rules can trigger bounded
  workflows when players complete advancements.
- **HUD notifications and speech:** notifications do not take control of the mouse or keyboard. Optional Aliyun
  streaming TTS reads message bodies; closing a view, dismissing a notification or switching messages ends the
  corresponding
  speech session.
- **Usage and privacy controls:** server AI and web usage by player, private local TTS / ASR usage, history management,
  external-AI opt-out, UUID-isolated data and limits on model and tool execution.

## Quick Start

1. Install this mod and its required dependencies. Add FTB Quests and its dependencies if you want quest integration.
2. Start the server or open a single-player world once to generate the server configuration files under
   `config/system/`.
3. To use AI, set `SYSTEM_DEEPSEEK_API_KEY` in the server process's startup environment, then restart. In single-player,
   the Minecraft process must inherit the variable. Configuration stores the environment-variable name, not the key.
4. Install a server data pack defining a system, then an administrator binds it with
   `/system grant <system ID> [player]`, for example `/system grant example:daily Steve`. Omit the player to bind
   yourself. Players start unbound and can currently bind at most one system. Built-in `system:default` uses the
   default module tag; the FTB Quests and web-search module definitions and tag additions are development-only.
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

| Command                                      | Action                                                                                               |
|----------------------------------------------|------------------------------------------------------------------------------------------------------|
| `/system`                                    | Open the last selected view                                                                          |
| `/system full` / `/system simple`            | Open the full / minimal view                                                                         |
| `/system settings`                           | Open unified client settings directly; no binding or administrator permission required               |
| `/system ask <text>`                         | Submit a natural-language request                                                                    |
| `/system search <question>`                  | Explicitly start paid web research; no automatic browsing                                            |
| `/system player`                             | View your own status                                                                                 |
| `/system submittable [query]`                | List FTB quests with pending manual or checkmark targets                                             |
| `/system usage` / `/system usage all`        | View personal usage / server AI and web usage as an administrator; voice totals always stay personal |
| `/system grant <system ID> [player]`         | Bind one data-pack system as an administrator; omit the player to bind yourself                      |
| `/system revoke [player]`                    | Revoke access and clear System records as an administrator; retain cumulative usage                  |
| `/system reset [player]`                     | Clear old-format System records and binding; cumulative usage is retained, then grant again          |
| `/system module add <player> <module ID>`    | Enable an additional module for a player as an administrator                                         |
| `/system module remove <player> <module ID>` | Disable a module for a player as an administrator                                                    |
| `/system module list [player]`               | List registered modules or a player's enabled modules as an administrator                            |
| `/system routing`                            | View routing diagnostics as an administrator                                                         |
| `/system help`                               | Show available actions                                                                               |

System IDs, players and module arguments support completion. Granting the same system again does not reset player
data; revoke the current binding before switching to another system. Manual module overrides take precedence over
data-pack conditions and persist until revoked or reset. Required modules, including core, binding and usage, cannot
be removed manually.

Notifications appear at the bottom of the HUD. Open vanilla chat with its configured key (**T by default**) to click
"Confirm" or an action button. Consecutive notifications keep the panel expanded while the content fades out, changes
and fades in. Confirming, timing out or switching notifications immediately cuts off the old speech, so the next message
does not wait for it to finish. Closing an assistant view also ends its speech session.

## Optional Speech

**All speech features run on the client and use paid APIs.**

All speech features currently use Aliyun APIs. Support for more providers is planned.

### Voice Input

**Requires a separate Aliyun NLS project AppKey and AccessKey ID / Secret. Temporary Tokens are acquired, cached and
refreshed automatically on demand.**

**Installing Simple Voice Chat is recommended for a better experience.**

Voice input is disabled by default. Use `/system settings`, or enter `/settings` in the assistant's input box, then
select **Voice input** and enable it. Changes are saved automatically. Hold **F8** to record and release to finish;
the key opens the assistant during gameplay. `/listen` and the **Voice input** button are also available. Final
recognition fills the draft, then Enter sends it; enable **Send after recognition** to send it immediately. Recording
lasts at most 60 seconds and is cancelled when the view closes.

While a notification is visible, recognized speech first attempts to match its confirmation or action buttons;
unmatched text goes to the main chat. Simple Voice Chat is optional; Java Sound records audio when it is not installed.

### Speech Output

**Requires a separate Aliyun Model Studio API key and the matching region.**

Open **Settings → Speech output** in the full workspace, or use `/system settings` or the assistant's `/settings`
command and select the tab.

1. Enter an Aliyun Model Studio API key and select its region, or set `DASHSCOPE_API_KEY` for the client process.
2. Set the model, voice, language and volume. Defaults are `qwen3-tts-flash`, `Cherry` and the game language.
3. Preview, then enable automatic speech. Changes are saved automatically. Custom voice IDs must match the model they
   were created for.

Enter exported voice parameters directly in the settings fields. TTS does not support JSON import or a settings reload
button. Local settings are saved in `config/system/system-speech.json`. The notification sound has its own switch,
enabled by default, and does not require a TTS request.

The Python 3.10+ utility `tools/voice_design.py` supports voice design, cloning, test synthesis and temporary uploads.
It uses the standard library.

```powershell
python tools/voice_design.py --lang en --help
```

## Content and Extensions

System definitions live at `data/<namespace>/system/system/<path>.json`. Names and descriptions use client translation
keys. For example, create `data/example/system/system/daily.json`:

```json
{
  "name": "system.definition.default.name",
  "description": "system.definition.default.description",
  "modules": [
    "#example:basic",
    "example:daily"
  ]
}
```

Define `example:daily` at `data/example/system/system_module/daily.json`:

```json
{
  "server": {
    "type": "system:signin"
  },
  "client": "system:signin"
}
```

`server.type` selects a registered module codec; any parameters belong beside `type`. Built-in codecs currently use
parameterless singletons. `client` names a local client implementation. Either side may be omitted.

Place the corresponding module tag at `data/example/tags/system/system_module/basic.json`:

```json
{
  "replace": false,
  "values": [
    "system:player",
    "system:items",
    "system:inbox",
    "system:shop",
    "system:lottery"
  ]
}
```

Module lists also accept objects with a `condition`; `id` can name a single module or a `#tag`. If several entries match
the same module, any satisfied condition enables it. Module availability controls workspace entries, commands and
execution permissions. FTB features also require their dependencies.
See [system definitions and module tags](docs/datapacks.md#系统定义).

Check-in rewards, shop offers and lottery pools are defined entirely by data packs, including the bundled defaults:

```text
data/<namespace>/system/signin_rewards/<path>.json
data/<namespace>/system/shop_offers/<path>.json
data/<namespace>/system/lottery_pools/<path>.json
```

These are server-only Data Pack Registries. Each has tags under
`data/<namespace>/tags/system/<registry-name>/<path>.json`. In a module's `server` definition,
`rewards` (check-in), `offers` (shop), and `pools` (lottery) accept an ID, `#tag`, or a mixed array.
Bundled modules use their respective `#system:default` tags. Entry or module changes require reopening the world
or restarting the server; tag membership follows vanilla reload behavior.
See [catalog options and examples](docs/datapacks.md#模块目录可选项) for details.

## Data and Privacy

### Configuration Files

| Location                                          | Contents                                                                                  |
|---------------------------------------------------|-------------------------------------------------------------------------------------------|
| Server `config/system/system-agent.json`          | Execution budgets, concurrency, daily model limits and time zone                          |
| Server `config/system/system-model.json`          | Model connection and provider options                                                     |
| Server `config/system/system-search.json`         | DeepSeek search connection; shares the model key environment variable by default          |
| Server `config/system/system-search-pricing.json` | Search / Fetch rates per 1,000 requests                                                   |
| Server `config/system/system-semantic.json`       | Local intent routing, thresholds, examples and cache                                      |
| Server `config/system/system-advancements.json`   | Trusted advancement rules                                                                 |
| `<world>/system-agent/<UUID>.json`                | Binding, conversations, preferences, rewards, orders and workflow records                 |
| `<world>/data/system/token_usage.dat`             | Server AI / web usage by player; retained after revoking access or clearing memory        |
| Client `config/system/speech-usage/<UUID>.json`   | Personal TTS / ASR totals for this local account, retained across servers; never uploaded |
| Client `config/system/system-speech-pricing.json` | Personal TTS / ASR rates; copied from bundled defaults if missing                         |
| Client `config/system/system-client.json`         | View preference                                                                           |
| Client `config/system/system-speech.json`         | Local speech settings and any key entered in the UI                                       |
| Client `config/system/system-asr.json`            | Local recognition switch, NLS AppKey, AccessKey ID / Secret and region                    |

Configuration files are not synchronized directly between the client and server. System definitions and available
modules are synchronized through Minecraft networking. Player data files in the world folder are managed by the
server; clients cannot modify them directly.

### API Usage

AI usage comes from server-side model responses. The server gateway tracks Search / Fetch request counts and estimated
costs separately. The all-player view contains server AI and web records only. TTS and ASR totals stay on the client,
per local account; they are never uploaded to the Minecraft server, and administrators cannot view other players'
voice usage records.

Search / Fetch rates are configured on the server and can be reloaded with `/reload` after initialization. Voice rates
are configured on the client; reload resources with F3+T or restart after editing them. ASR defaults to an estimate of
CNY 3.5 per 1,000 successful recognitions. TTS uses the character or token usage returned by the selected model; the two
are not charged together.

Empty search results receive no cost estimate. Unknown outcomes, such as timeouts or network interruptions, are recorded
separately and do not prove that the provider charged nothing. Rate changes do not recalculate historical totals;
provider billing is authoritative. See [usage and billing](docs/token-usage.md) and
the [external API request list](docs/external-apis.md).
