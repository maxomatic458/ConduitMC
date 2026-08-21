# Conduit

Host and join Minecraft servers without port forwarding — share a static connection ID instead of an
IP, and Conduit tunnels the game peer-to-peer over the [iroh](https://iroh.computer) network.


## How to use

**Important** Conduit needs to be installed on the server and on all clients that want to connect.

- **To host**, open a world to LAN or start a dedicated server as usual. Conduit prints your connect
  ID to chat (click to copy) or to the server console.
- **To join**, paste a connect ID into the multiplayer *Direct Connect* or *Add Server* field where
  you would normally type an IP.

Normal IP addresses keep working exactly as before

## Commands

### `/conduit-info`

Shows your own node ID, the ID you are hosting under, and every active connection: how it is routed
(**Direct**, **Mixed**, **Relayed** or **Connecting**), round-trip time, bytes transferred, and each
individual network path with the one currently carrying data marked. Connect IDs are click-to-copy.

Use it to check whether you actually got a direct connection or are going through a relay.

### `/conduit-config`

**Server-side** Run without arguments to print every
setting plus your connect ID.

| Subcommand | Value | Description |
|---|---|---|
| `enabled <true\|false>` | | Take part in iroh networking at all. |
| `hostAutomatically <true\|false>` | | Start hosting on *Open to LAN* / server start. |
| `connectTimeoutSeconds <n>` | 5–300 | How long to wait for a host before giving up on a join. |
| `tunnelIdleTimeoutSeconds <n>` | 30–86400 | How long an unused tunnel is kept alive. |
| `relayUrls [urls…]` | | Relay servers, space or comma separated. Omit the argument to restore the defaults. |
| `resetIdentity confirm` | | Generate a brand new identity. |

> **`resetIdentity` is irreversible.** It permanently invalidates your current connect ID — anyone
> who saved it will need the new one. The `confirm` word is required for exactly that reason.

## Configuration

Settings can also be edited from a screen in-game: **Options → Conduit**. 
By default Conduit uses n0's public relay servers, listed explicitly in the config so you can see
and replace them:
```
https://aps1-1.relay.n0.iroh.link./
https://euc1-1.relay.n0.iroh.link./
https://use1-1.relay.n0.iroh.link./
https://usw1-1.relay.n0.iroh.link./
```

## Credits

Built on [iroh](https://iroh.computer) by [n0](https://n0.computer).
