# MineParty

Party (squad) mod for Minecraft. **Demo build for Fabric 1.21.1.**

In this demo the mod must be installed on both the server and the clients
(on single player it just works). Relay mode for servers without the mod,
Trust Group, voice chat integration and other versions come later.

## Requirements

- Minecraft 1.21.1
- Fabric Loader 0.16+
- Fabric API

## Controls

| Key | Action |
| --- | --- |
| `P` | Party menu: players on the left, your parties on the right, ⚙ opens party settings |
| `I` | Invite mode on/off. While on, middle click on a player invites them |
| Middle click | Outside creative, while in a party: ping. Click = warning (yellow ⚠), double click = danger (red !) |
| `V` (hold) | Ping wheel: warning, danger, here (green ↓), invite, invite mode, party menu. Release to choose |
| `Y` | Party chat on/off. While on, chat messages go to the active party |
| `U` | Switch the active party (you can be in up to 3) |
| `H` | Move and resize the party HUD (drag, mouse wheel to scale) |

All keys can be changed in Options → Controls → MineParty.

## Commands

- `/party create [name]`
- `/party invite <player> [party number]`
- `/party accept <id>` / `/party decline <id>` (clickable in chat)
- `/party leave [party number]`
- `/pc [party number] <message>` – party chat

## Rules

- Up to 15 players in a party, up to 3 parties per player.
- Only the leader can change marker settings (symbol, colour, through walls, distance).
- The leader can name a deputy. Leader and deputy can invite.

## Building

```
./gradlew build
```

The jar appears in `build/libs/`. Every push is also built by GitHub Actions,
and the jar can be downloaded from the run's artifacts.

## License

MIT
