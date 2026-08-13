# S1601 research 01 - Current UI and data flow

## Scope

Read-only inspection on 2026-08-12 of the Network Monitor summary, six detail screens, data model and address resolvers.

## Findings

- The summary currently renders transport/name and Internet reachability in separate views. It takes only the first local IPv4 address from the active link.
- The external address resolver is explicitly invoked from the Internet screen. Its result is intentionally neither persisted nor logged. The UI must not add an automatic resolver call.
- Every Network Monitor detail screen has portrait and landscape layouts. Their cards are not ordered consistently: GNSS puts position and chart before track controls; Internet puts its path and traffic before the chart and action details.
- Wi-Fi, mobile subscriptions and Bluetooth expose an explicit `NoPermission` availability reason. The monitor does not own a local runtime-permission launcher today.
- The custom path view puts a node label and its value on two text baselines at 12sp and 11sp. It changes to vertical topology on narrow widths, so a single combined node value can remain readable without reducing type.
- The general monitor drawable is a circular, steering-wheel-like vector. Wi-Fi, Bluetooth and history already have conventional drawables; mobile, location and Internet need consistent standard symbols.

## Decision constraints

- Do not change monitoring cadence or create a background external-address request.
- Preserve the semantic content description of the path diagram when its visual label is combined.
- Update portrait and landscape resources together for every reordered detail section.
