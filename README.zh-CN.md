# Vote

语言：[English](README.md) | **简体中文**

![Paper](https://img.shields.io/badge/Paper-1.21.4-2ea44f)
![Java](https://img.shields.io/badge/Java-21-f89820)
![Status](https://img.shields.io/badge/status-ready-blue)

Vote 是一个简洁的 Paper 投票插件。玩家可以通过 GUI 或命令投票，投票记录会永久保存，投票通过后可自动执行服务器命令。

## 特性

- GUI 投票菜单，管理员可在菜单中创建、编辑、删除投票。
- 玩家可用 `/vote accept` 和 `/vote reject` 直接投票。
- 永久保存进行中、已通过、已截止的投票记录。
- 支持 `amount:1/2`、固定票数 `amount:10p`、`amount:all` 通过条件。
- 支持 `timeout:30m`、`timeout:1.5h`、`timeout:2d` 自动截止。
- 通过后由控制台执行 `command:` 命令，兼容 EssentialsX 等 Bukkit 命令拓展。
- 支持排除假人：`vote.fake` 权限、metadata key、名称正则均可配置。

## 命令

```text
/vote
/vote accept [标题或ID]
/vote reject [标题或ID]
/vote create summary:<标题> details:<详细内容> [command:<命令;命令>] [timeout:<时间|none>] amount:<比例|票数p|all>
/vote edit <标题或ID> [summary:<新标题>] [details:<新内容>] [command:<命令;命令|none>] [timeout:<时间|none>] [amount:<比例|票数p|all>]
/vote remove <标题或ID>
/vote confirm
/vote list
```

参数参考 CoreProtect 的 `key:value` 风格。创建投票时 `summary`、`details`、`amount` 必填，`command`、`timeout` 选填。`amount:10p` 表示 10 个赞成票即可通过。

## 示例

```text
/vote create summary:重启投票 details:是否今晚 23:00 重启服务器 command:say 投票通过;restart timeout:2h amount:1/2
/vote create summary:开放地狱 details:是否开放地狱门 amount:10p
/vote create summary:开启白名单 details:是否开启白名单 amount:all
/vote accept 重启投票
/vote reject 重启投票
/vote edit 重启投票 command:none timeout:none amount:2/3
/vote remove 重启投票
/vote confirm
```

`command:` 后面的命令不需要 `/`。如果误写 `/eco give Steve 100`，插件会自动去掉开头的 `/`。

## 权限

| 权限 | 默认 | 说明 |
| --- | --- | --- |
| `vote.vote` | `true` | 允许打开菜单并投票 |
| `vote.accept` | `true` | 允许投赞成票 |
| `vote.reject` | `true` | 允许投反对票 |
| `vote.admin` | `false` | 管理投票；OP 等级 >= 2 也可管理 |
| `vote.fake` | `false` | 标记假人，不计入在线人数 |

## 构建

```powershell
.\build.ps1
```

构建产物：

```text
build\libs\Vote-1.0.1.jar
```

## 运行环境

- Paper `1.21.4`
- Java `21+`

