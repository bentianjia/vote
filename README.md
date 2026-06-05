# Vote

Language: **English** | [简体中文](README.zh-CN.md)

![Paper](https://img.shields.io/badge/Paper-1.21.4-2ea44f)
![Java](https://img.shields.io/badge/Java-21-f89820)
![Status](https://img.shields.io/badge/status-ready-blue)

Vote is a simple Paper voting plugin for Minecraft servers. Players can vote in a GUI or with commands, all vote records are saved permanently, and approved votes can run server commands automatically.

## Features

- GUI vote menu with admin create, edit, and remove actions.
- Direct command voting with `/vote accept` and `/vote reject`.
- Persistent vote storage for active, passed, and expired votes.
- Approval thresholds: `amount:1/2`, fixed vote counts like `amount:10p`, or `amount:all`.
- Optional expiry: `timeout:30m`, `timeout:1.5h`, or `timeout:2d`.
- Approved votes dispatch `command:` as console commands, compatible with EssentialsX-style commands.
- Fake-player exclusion through `vote.fake`, metadata keys, and configured name patterns.

## Commands

```text
/vote
/vote accept [title-or-id]
/vote reject [title-or-id]
/vote create summary:<title> details:<details> [command:<command;command>] [timeout:<time|none>] amount:<ratio|countp|all>
/vote edit <title-or-id> [summary:<new-title>] [details:<new-details>] [command:<command;command|none>] [timeout:<time|none>] [amount:<ratio|countp|all>]
/vote remove <title-or-id>
/vote confirm
/vote list
```

Parameters use a CoreProtect-like `key:value` style. `summary`, `details`, and `amount` are required when creating a vote. `command` and `timeout` are optional. Use `amount:10p` to require 10 support votes.

## Examples

```text
/vote create summary:Restart details:Restart the server at 23:00 command:say Vote passed;restart timeout:2h amount:1/2
/vote create summary:OpenNether details:Open the Nether portal amount:10p
/vote create summary:Whitelist details:Enable whitelist amount:all
/vote accept Restart
/vote reject Restart
/vote edit Restart command:none timeout:none amount:2/3
/vote remove Restart
/vote confirm
```

Commands after `command:` do not need `/`. If `/eco give Steve 100` is entered by mistake, the plugin removes the leading `/` automatically.

## Permissions

| Permission | Default | Description |
| --- | --- | --- |
| `vote.vote` | `true` | Open the menu and vote |
| `vote.accept` | `true` | Cast support votes |
| `vote.reject` | `true` | Cast oppose votes |
| `vote.admin` | `false` | Manage votes; OP level >= 2 also works |
| `vote.fake` | `false` | Mark fake players so they are ignored |

## Build

```powershell
.\build.ps1
```

Build output:

```text
build\libs\Vote-1.0.1.jar
```

## Requirements

- Paper `1.21.4`
- Java `21+`

