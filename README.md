# System

English | [简体中文](https://github.com/IAFEnvoy/System-Mod/blob/master/README-zh.md)

Bring the “System” from web novels into Minecraft: an assistant for quests, rewards, shops, travel and everyday
questions. Use interactive cards, type a request or speak to your system. Many features work without AI.

## Features

- **Player and item lookup:** check your information, search items by name or tooltip, and browse recipes and ways
  to obtain materials.
- **Quests and commissions:** accept delivery commissions, check requirements, hand in materials and claim rewards.
  With FTB Quests installed, browse its quests by chapter, submit tasks and receive completion or unlock notifications.
  Pin tasks to keep their progress on screen.
- **System Space:** keep rewards and deposited items in your personal storage. Click to take one item, hold Shift
  for a stack, or request a withdrawal by voice. Some rewards let you choose from several options.
- **Shops and wallets:** buy supplies, sell items and manage multiple currencies. Shops can rotate their stock,
  limit purchases or offer items based on your progress.
- **Lotteries:** preview prizes, make single or multiple draws, check your draw history and work toward guaranteed
  rewards where the pack provides them.
- **Daily rewards and progress:** check in for supplies, earn streak and milestone rewards, complete achievements
  and register items in collections without consuming them.
- **Equipment workshop:** inspect held equipment, repair it or salvage it for materials after reviewing the cost
  and result.
- **Online jobs:** queue gathering or production jobs, supply the required materials and collect the results.
  Progress pauses while you are offline.
- **Teleport anchors:** save and rename destinations, then travel from a card or by voice. Your server can provide
  public destinations and set travel costs or limits.
- **Conversation and voice:** choose how much AI participates, ask questions, chat freely or use optional voice
  input and spoken replies.
- **Cards and HUD:** switch between a full workspace and a compact dialogue view, choose a theme and move HUD panels
  with Saturn’s HUD editor. A panda avatar can show emotions and animate while speaking.
- **Timed activities:** shops, lotteries, check-in rewards and commissions can open on selected dates or at scheduled
  times. Cards show their availability and next opening.

Available content, rewards and limits depend on your modpack or server. AI is optional for local queries, browsing
and the built-in activities above.

## Getting Started

1. Install the mod and its required dependencies, then enter a world.
2. An administrator must bind a system with `/system grant system:default [player]`. In single-player, omit the
   player name to bind yourself. Servers and modpacks may provide other systems.
3. Run `/system` to open the assistant. Try asking for your player information, looking up an item or browsing quests.
4. Use the sidebar and card buttons to explore the available features. Enter `/settings` inside the assistant to
   change your preferences; settings save automatically.

Use `/system full` for the full workspace or `/system simple` for the compact view. You can assign **Open system
interface** in Minecraft’s controls; it is unbound by default.

While a notification is visible, open Minecraft chat (**T by default**) to use its buttons. Notifications stay until
you finish or dismiss them.

## AI Modes

Choose a mode beside the message box:

| Mode               | What it does                                                                               |
|--------------------|--------------------------------------------------------------------------------------------|
| Offline            | Uses local features without calling AI; offers clickable options when a request is unclear |
| Standard (default) | Uses local features first, with AI helping select actions or support features that need it |
| Extended           | Tries local features first, then starts an AI conversation if no action matches            |
| Free               | Lets AI lead the conversation and use the features available to you                        |

Your server controls which modes are available. Unavailable modes appear greyed out with an explanation. When no
usable model is configured, you can still use Offline mode. Purchases and other actions that need confirmation
keep their confirmation steps.

### Enabling AI

In single-player or as the LAN host, open **API Key management** in `/settings`, add your provider’s credentials,
then select them under **Text interaction**. DeepSeek is the default provider; other compatible providers are
supported too.

On a dedicated server, the server owner configures the text AI. Joining players use that server configuration
and cannot change its credentials from their clients. Web research is available only when the server enables it;
questions check local information first.

## Useful Commands

Run these **inside the assistant’s message box**, rather than Minecraft chat. You can also use natural language
or the sidebar. Command completion shows what is available to you.

| Command                      | Action                                                 |
|------------------------------|--------------------------------------------------------|
| `/help`                      | Show available features and help                       |
| `/settings`                  | Open settings                                          |
| `/player`                    | View your information                                  |
| `/quests [query]`            | Browse or search quests                                |
| `/commissions`               | Browse delivery commissions                            |
| `/submittable [query]`       | Find tasks ready for manual submission or checking off |
| `/claimable`                 | Find quest rewards you can claim                       |
| `/space`                     | Open System Space                                      |
| `/space_inventory`           | Choose inventory items to deposit                      |
| `/deposit [amount or stack]` | Deposit items from your main hand                      |
| `/records`                   | View progress and milestone rewards                    |
| `/usage`                     | View your usage                                        |

## Optional Voice

Voice input and automatic speech are disabled by default. Add your own credentials in **API Key management**, then
select them under **Voice interaction** in `/settings`. Both DeepSeek and Aliyun are supported.

Hold **F8** to record and release it to recognize your speech. You can choose whether the result fills the draft or
sends immediately. Voice requests first try the current notification’s buttons, then go to the main conversation.
Speech output can be previewed before you enable automatic playback. Notification sounds have their own switch.

Voice credentials and usage stay on your client, separate from the server’s text AI. External providers may charge
for AI or voice services; the mod’s usage estimates do not replace your provider’s bill.

## For Server Owners and Pack Makers

Use `/system grant` to give players a system, `/system module` to manage their features and `/system ai` to control
AI access and usage limits. `/system ai default <mode>` sets the world’s default mode and ordinary players’ mode
ceiling. Players can still choose a lower mode.

Data packs can customize systems, commissions, currencies, shops, rewards and activities. Resource packs can change
card layouts, translations, themes and avatar textures. These choices determine the content players see.

## Documentation

Browse the [Documentation](https://docs.iafenvoy.com/docs/mod/system) for detailed setup, server commands, customization
guides and development information.
