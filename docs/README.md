# Android Documentation

This is the Android handoff pack for the shared-core, native-shell design. It
names decisions that are ready for implementation and distinguishes them from
interfaces that still require a `boreas-core` change.

| Document | Read when working on |
|---|---|
| [Core Contract](core-contract.md) | shared responsibilities, lifecycle ordering, FFI shape, packet and control boundaries |
| [Platform Integration](platform-integration.md) | `VpnService`, descriptor transfer, socket bypass, Android lifecycle and UI |
| [Implementation Plan](implementation-plan.md) | work order, dependency edges, acceptance gates and test matrix |
| [Verified Inputs](verified-inputs.md) | fact-checked platform inputs, source links, and unresolved decisions |
| [Build Inputs](build-inputs.md) | the A0 record: pinned versions, SDK levels, module split, dependencies |
| [Design System](design-system.md) | tokens, measured contrast, type scale, component inventory |

The related core specifications are [platforms](https://github.com/BoreasLab/boreas-core/blob/main/docs/platforms.md), [architecture](https://github.com/BoreasLab/boreas-core/blob/main/docs/architecture.md), and the [verification ledger](https://github.com/BoreasLab/boreas-core/blob/main/docs/verification.md).