# System

English | [简体中文](README-zh.md)

A NeoForge mod that brings the “System” from web novels into Minecraft. A server-side Agent handles queries, quests and
actions, while the client provides a full workspace, a minimal dialogue view, HUD notifications and optional voice
interaction. Local commands and intent matching work without AI; connect a model when needed.

## Features

- **Queries and answers:** view player information, search items, and browse recipes and item sources. Questions use the
  local database first; web access requires explicit permission.
- **FTB Quests integration:** browse actionable quests by chapter, inspect requirements and rewards, submit or claim
  manually, and pin quests to persistent cards. Includes completion/unlock notifications and local guide lookup.
- **Check-ins, shop and lottery:** AI selects daily rewards from approved pools. Shops and lotteries use Saturn Costs,
  with rewards delivered to the inbox. Content is configured through data packs.
- **Conversation and tools:** choose Offline, Standard, Extended or Free mode. Free mode can call authorized tools;
  administrators control available modes and daily usage.
- **Cards and HUD:** messages, interactive cards and persistent cards share JSON templates. Notifications leave gameplay
  controls available, and Saturn’s HUD editor adjusts their position.
- **Optional speech:** push-to-talk recording, automatic sending after recognition, streaming speech output and voice
  actions for notification buttons.

## Installation and Quick Start

### Versions and Dependencies

These requirements apply to the current branch. Use the corresponding branch or build for other Minecraft versions.

| Component            | Version / requirement                              | Install on        |
|----------------------|----------------------------------------------------|-------------------|
| System               | `1.0-beta.3`                                       | Client and server |
| Minecraft / NeoForge | `26.1.2` / `26.1.2.114+`                           | Client and server |
| Java                 | `25`                                               | Client and server |
| Saturn               | `1.2.5+`, built for the matching Minecraft version | Client and server |
| ApricityUI           | `1.2.9+`, built for the matching Minecraft version | Client            |

FTB Quests and its dependencies are **optional**, needed only for quest integration. Simple Voice Chat is an **optional
client microphone backend**; Java Sound is used when it is absent, and the server does not need SVC for this feature.
See the [development guide](docs/development.md) for development dependency versions.

### First Use

1. Install the mod and required dependencies, then open a single-player world or start the server.
2. As an administrator, run `/system grant system:default [player]` to bind the bundled system; omit the player to bind
   yourself. Each player currently has at most one system. Data packs can provide other system IDs.
3. Run `/system` to open the interface, type a request and press Enter. Start with player information or an item search.
4. Open `/system settings` for unified settings; changes save immediately. Configure a model below when you want AI
   features.

### Enable AI (Optional)

DeepSeek is the default provider. Set `SYSTEM_DEEPSEEK_API_KEY` in the **server process environment**. For example, set
it in PowerShell and launch the server from that environment:

```powershell
$env:SYSTEM_DEEPSEEK_API_KEY = "<your-api-key>"
```

For single-player, the Minecraft process must inherit the variable. Restart the process after changing its environment.
Server-side `config/system/system-model.json` stores the endpoint, model ID and the key’s **environment-variable name**.
Other OpenAI Chat Completions-compatible services are also supported.

Set `baseUrl` to the API prefix without `/chat/completions`. See [model configuration](docs/model-api.md) for all
options, DeepSeek-specific parameters and local models that do not require a key.

## Execution Modes

Select a mode beside the message box. Your choice saves automatically.

| Mode               | Behavior                                                                                              |
|--------------------|-------------------------------------------------------------------------------------------------------|
| Offline            | Local routing and intent matching only; uncertain requests offer clickable actions without calling AI |
| Standard (default) | Local routing first; AI assists routing and required modules, without free conversation               |
| Extended           | Routing first, with free AI answers when no action matches                                            |
| Free               | AI directs the conversation, selects tools and calls features                                         |

Administrators set the current world’s initial mode and ordinary players’ mode ceiling with `/system ai default <mode>`;
the default is stored in world data. Single-player users and LAN hosts can select all modes by default. Explicit
per-player `/system ai` rules still take precedence; unavailable choices are greyed out with a reason.

**If the model requires an API key and none is configured, only Offline mode is available.** Your preferred mode is
retained. Models configured to work without a key are exempt. Local queries and browsing remain available; features that
require a model, such as AI check-ins, need a working configuration.

Free mode prefers relevant local read tools, then ordinary workflows, then capabilities with additional API costs.
Purchases, lottery draws and quest submissions retain their confirmation steps. Web access still requires the search
module and **explicit permission for the current request**. Free conversation itself also consumes model usage.
See [modes, permissions and tools](docs/free-chat.md).

## Common Actions

### Player Commands

Run these in Minecraft chat. Available features depend on the enabled modules.

| Command                           | Action                                                              |
|-----------------------------------|---------------------------------------------------------------------|
| `/system`                         | Open the last selected view                                         |
| `/system full` / `/system simple` | Open the full workspace / minimal dialogue view                     |
| `/system settings`                | Open unified settings without a binding or administrator permission |
| `/system ask <text>`              | Send a natural-language request                                     |
| `/system player`                  | View your own information                                           |
| `/system submittable [query]`     | List FTB quests with pending manual or checkmark targets            |
| `/system search <question>`       | Explicitly request paid web research; requires the search module    |
| `/system usage`                   | View personal usage without a binding                               |
| `/system help`                    | Show available features and help                                    |

Inside the assistant, use `/settings` to open settings. The input supports command completion and Enter to send. You can
assign **Toggle System interface** in Minecraft’s controls; it is unbound by default.

While a notification is visible, open vanilla chat (**T by default**) to interact. Consecutive notifications keep the
panel expanded; use their buttons to confirm or continue. Finishing speech does not automatically advance the queue.
Closing, confirming or replacing a notification ends its speech session.

### Administrator Commands

| Command                                      | Action                                                                                            |
|----------------------------------------------|---------------------------------------------------------------------------------------------------|
| `/system grant <system ID> [player]`         | Bind a system defined by a data pack                                                              |
| `/system revoke [player]`                    | Revoke the binding and clear business records, retaining cumulative usage                         |
| `/system reset [player]`                     | Clear System records and the binding, including unusable old-format records; bind again afterward |
| `/system module add <player> <module ID>`    | Enable an additional module                                                                       |
| `/system module remove <player> <module ID>` | Disable a module                                                                                  |
| `/system module list [player]`               | List registered modules or a player’s enabled modules                                             |
| `/system ai default [mode]`                  | View or change the current world’s default AI mode                                                |
| `/system ai <player> …`                      | Manage AI/free-chat permissions and daily limits, inspect access or reset overrides               |
| `/system ftb reload`                         | Reindex currently loaded FTB guide data without changing quest progress                           |
| `/system usage all`                          | View server-wide AI and web usage; voice usage remains personal                                   |
| `/system routing`                            | Inspect local routing diagnostics                                                                 |

System, player and module arguments support completion. Granting the same system again does not reset the player; revoke
before switching systems. Manual module overrides persist. Required modules such as core, binding and usage cannot be
removed. See the [usage guide](docs/usage.md) for commands and [access configuration](docs/free-chat.md) for AI
subcommands.

## Optional Speech

Voice input and automatic speech are disabled by default. Both run on the **client using the player’s own Aliyun
credentials**, separately from the server model configuration.

| Feature             | Configuration                                                                                                   | Usage                                                                                                           |
|---------------------|-----------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------|
| Voice input (ASR)   | NLS project AppKey, AccessKey ID / Secret and region; temporary tokens are acquired and refreshed automatically | Hold F8 to record and release to recognize; results fill the draft, with an option to send immediately          |
| Speech output (TTS) | Model Studio API key and region, or the client’s `DASHSCOPE_API_KEY`                                            | Defaults to `qwen3-tts-flash` / `Cherry` and the game language; preview and enable automatic speech in settings |

Configure the corresponding tab in `/system settings`; changes save automatically. Custom voices must match their
creation model and region. Enter their parameters directly; JSON import is not supported. The notification sound has a
separate switch, enabled by default, and does not call TTS.

Recognized speech first tries the current notification’s buttons, then falls back to the main chat.
See [voice recognition](docs/asr.md) and [speech synthesis](docs/tts.md) for setup details.

The repository includes a Python 3.10+ standard-library utility for voice design, cloning, previews and temporary
uploads:

```powershell
python tools/voice_design.py --lang en --help
```

## Configuration and Extensions

Runtime configuration lives under each instance’s `config/system/`. Missing files are copied from bundled defaults. The
server manages models, permissions, execution budgets and pricing; the client manages views, themes and personal speech
settings. See the [configuration directory](docs/usage.md#配置目录) for the full file list.

- **Data packs:** define translation keys for system names and descriptions, module parameters and conditions, check-in
  rewards, offers and lottery pools. System definitions live at `data/<namespace>/system/system/<path>.json`. Module
  references support IDs, tags and conditions; catalogue selections accept IDs, tags or mixed arrays.
  See [data-pack configuration and examples](docs/datapacks.md).
- **Resource packs:** override client card JSON, translations and styles. Templates control layout and placeholders
  supply dynamic data, with items, tooltips, Markdown and interactive buttons. See [cards](docs/cards.md) and
  the [visual editor](docs/card-editor.md).
- **Mod extensions:** register modules, tools and card nodes, or extend contexts and prompts.
  See [feature modules](docs/feature-modules.md) and the [registry reference](docs/registries.md).

FTB features require their dependencies; web research also requires a configured search module. The development source
set contains a search-module example that is excluded from the release JAR. Questions use the local database first,
searching the web only when it has no result and the current request permits it.
See [database-first questions and web research](docs/search-api.md).

## Data, Usage and Costs

- **Business data** is managed by the server per world and player. Bindings, preferences, quest interactions and rewards
  do not rely on client storage. Cumulative usage is independent and survives revoking, resetting or clearing memory.
- **AI and web usage** is tracked on the server. Administrators can view server-wide totals; model API keys are never
  synchronized to clients.
- **TTS / ASR usage** stays on the local machine per client account. It is not uploaded to the server, and
  administrators cannot aggregate other players’ voice records.
- **Costs are estimates**; provider billing is authoritative, and rate changes do not recalculate history. Search and
  Fetch are counted separately, TTS uses the model’s reported characters or tokens, and ASR estimates use successful
  recognition counts.

See the [external API request list](docs/external-apis.md) and [usage accounting](docs/token-usage.md) for triggers,
transmitted data and billing details.

## Documentation

Detailed documentation is currently in Chinese.

| Topic                                              | Guide                                                                                                        |
|----------------------------------------------------|--------------------------------------------------------------------------------------------------------------|
| Installation, controls, commands and configuration | [Usage and configuration](docs/usage.md)                                                                     |
| Modes, administrator permissions and tool calls    | [Execution modes and free chat](docs/free-chat.md)                                                           |
| Other model providers / web research               | [Model API](docs/model-api.md) · [Search API](docs/search-api.md)                                            |
| Voice input, speech and voice design               | [ASR](docs/asr.md) · [TTS](docs/tts.md)                                                                      |
| Custom systems, rewards, offers and lottery pools  | [Data packs](docs/datapacks.md)                                                                              |
| Card layout and appearance                         | [Cards](docs/cards.md) · [Editor](docs/card-editor.md)                                                       |
| Local guides and knowledge storage                 | [Quest guides](docs/quest-guides.md) · [Databases](docs/databases.md)                                        |
| Development, architecture and extension points     | [Development](docs/development.md) · [Architecture](docs/architecture.md) · [Registries](docs/registries.md) |

See [docs/README.md](docs/README.md) for the full index and [learning.md](learning.md) for the AI coding-agent
reference.
