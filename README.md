# HyperZoneLogin

> **Downstream Modifications**
>
> This repository is a downstream fork of [HyperZoneLogin/HyperzoneLogin](https://github.com/HyperZoneLogin/HyperzoneLogin).
> It tracks upstream weekly and applies a small set of downstream-specific changes on top.
>
> **Post-auth target resolution** — For both the `outpre` and `backend` waiting-area flows, the post-auth target server is resolved with the following priority:
>
> 1. **Forced Host** — `forcedHosts` entry matching the player's virtual host in `velocity.toml`
> 2. **Connection order** — the `try` list (`attemptConnectionOrder`) in `velocity.toml`
> 3. **Remembered target** — the server the player attempted to join during authentication
> 4. **Any non-auth server** — final fallback
>
> The authentication server itself is always skipped, and servers not registered in Velocity are ignored. This keeps post-auth routing consistent with the proxy's own `velocity.toml` connection order.

**HyperZoneLogin** is a Velocity-based authentication framework for Minecraft proxy networks.
It is designed for servers that need to support multiple login flows under one unified profile system.

**Languages:** [English](README.md) | [简体中文](README.zh-CN.md)

[![GitHub Release](https://img.shields.io/github/v/release/HyperZoneLogin/HyperzoneLogin?label=Release)](https://github.com/HyperZoneLogin/HyperzoneLogin/releases)
[![License](https://img.shields.io/github/license/HyperZoneLogin/HyperzoneLogin?label=License)](./LICENSE)
[![Discord](https://img.shields.io/discord/1492467475810484244.svg?logo=discord&label=Discord)](https://discord.gg/dCAeNyR9TA)
[![QQ Group](https://img.shields.io/badge/QQ%20Group-832210691-12B7F5?logo=tencentqq&logoColor=white)](https://qm.qq.com/q/GZWVfEyokS)
[![Proxy Stats](https://img.shields.io/bstats/servers/30691?logo=minecraft&label=Servers)](https://bstats.org/plugin/velocity/HyperZoneLogin/30691)
[![Proxy Stats](https://img.shields.io/bstats/players/30691?logo=minecraft&label=Players)](https://bstats.org/plugin/velocity/HyperZoneLogin/30691)

## What it does

HyperZoneLogin helps you build a consistent authentication layer for a Velocity network.

- Supports multiple authentication methods in one project
- Keeps players in a controlled auth / waiting flow until authentication is complete
- Unifies profile creation, profile binding, and post-auth routing
- Can be extended with optional modules for security, migration, and skin handling

## Supported authentication methods

HyperZoneLogin currently provides these authentication modules:

| Method | Module | Summary |
|---|---|---|
| Local / Offline accounts | `auth-offline` | Register, log in, change password, unregister, email recovery, optional short sessions, and optional TOTP 2FA |
| Yggdrasil authentication | `auth-yggd` | Authenticate with Mojang-style or compatible Yggdrasil services, including configurable multiple entries |
| Floodgate / Bedrock authentication | `auth-floodgate` | Recognize Floodgate players and treat Bedrock access as a trusted authentication channel |

In other words, the project is suitable for networks that need one or more of the following:

- classic offline/local account login
- Yggdrasil-based premium or custom online authentication
- Bedrock player access through Floodgate / Geyser setups

## Optional modules

Besides authentication, the repository also includes several supporting modules:

| Module | Purpose |
|---|---|
| `safe` | Entry-layer protection such as rate limiting, abnormal name checks, and temporary cooldowns |
| `data-merge` | Migration tools for importing data from older systems such as MinecraftLink / legacy ML and AuthMe |
| `profile-skin` | Skin extraction, repair, caching, and profile skin application for authenticated profiles |

## Documentation

If you are new to the project, start here:

- **User documentation:** <https://docs.h2l.icu>
- **Chinese README:** [`README.zh-CN.md`](README.zh-CN.md)

For module-specific details, see the READMEs inside:

- [`velocity`](./velocity/README.md)
- [`auth-offline`](./auth-offline/README.md)
- [`auth-yggd`](./auth-yggd/README.md)
- [`auth-floodgate`](./auth-floodgate/README.md)
- [`safe`](./safe/README.md)
- [`data-merge`](./data-merge/README.md)
- [`profile-skin`](./profile-skin/README.md)

## Community

- GitHub Issues: <https://github.com/HyperZoneLogin/HyperzoneLogin/issues>
- Discord: <https://discord.gg/dCAeNyR9TA>
- QQ Group: <https://qm.qq.com/q/GZWVfEyokS>

## Support the project

If HyperZoneLogin helps your network, you can support development and maintenance here:

[![Ko-fi](https://img.shields.io/badge/Ko-fi-Support%20Project-FF5E5B?logo=kofi&logoColor=white)](https://ko-fi.com/ksqeib)
[![爱发电](https://img.shields.io/badge/%E7%88%B1%E5%8F%91%E7%94%B5-%E6%94%AF%E6%8C%81%E9%A1%B9%E7%9B%AE-946CE6)](https://afdian.com/a/ksqeib445)

If you do not want to sponsor, you can still help by:

- reporting bugs and feedback through Issues
- improving documentation
- submitting high-quality pull requests
- recommending the project to other server owners