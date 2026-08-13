# Boreas Android

Boreas Android is the native Android control surface and VPN lifecycle owner
for the shared Rust engine in `boreas-core`. This repository is deliberately a
documentation handoff, not an application scaffold: implementation begins only
after the choices and acceptance gates in [docs](docs/README.md) are satisfied.

The product boundary is raw IP packets. Kotlin owns Android APIs, consent, and
the `VpnService` lifecycle. Rust owns packet semantics, routing and filtering
policy, transport state, and egress. Compose configures and observes the
service; it never handles packets.

Start with [the documentation index](docs/README.md), then read
[AGENTS.md](AGENTS.md) before changing the repository.