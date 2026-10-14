# Pipe filtering and routing

PowerFactor 0.2.0 adds endpoint filters and destination priorities to existing item, fluid and steam pipes. Ordinary unconfigured pipes continue to transport everything their pipe type supports.

## Open and configure

Sneak-use a pipe with an empty main hand. The selected face initially follows the arm you clicked. The six direction buttons are world directions: Down, Up, North, South, West and East. Use the connection button to cycle insertion, extraction and disabled states, just as an ordinary empty-hand arm click does.

Each face has four **reference slots**. Left-click one while carrying an item to copy a reference. Shift-click an inventory stack to fill the first empty reference. Right-click a reference to clear it. Your real inventory and carried stack remain intact; the references cannot be picked up, dropped, cloned or extracted.

| Setting | Behaviour |
| --- | --- |
| No filter | All supported resources pass; reference slots may remain stored |
| Allow list | Only listed resources pass; an empty list blocks everything |
| Block list | Everything except listed resources passes; an empty list blocks nothing |
| Match type | Ignores names, damage, enchantments and other components |
| Match components | Requires the same item/fluid and component values as a reference |

Adding a reference while **No filter** is selected automatically enables **Allow list**. Clearing references preserves the selected mode and priority. An empty allow list shows a warning so a cleared filter cannot silently admit unwanted resources.

For a fluid pipe, copy a filled bucket, a PowerFactor tank carrying fluid, or another supported filled container. The filter reads its liquid; container size and stored amount do not set a transfer quota. Dry items and fluids unsupported by the pipe type are rejected. Steam pipes still carry only fluids recognised by their existing steam compatibility rule.

## Sort a network

Filters apply to the pipe face connected to an inventory or fluid handler, including extract connections. Pipe-to-pipe links pass resources through. Filters on an unused face remain saved in case a machine or tank is connected there later.

For example, connect a chest to an extract face. Give one destination face an iron allow list and another a dirt allow list. Each resource follows its matching branch. Destination priority ranges from **−5** to **+5**: higher priorities fill first, and blocked or full destinations fall back to lower priorities. Equal-priority destinations rotate between transfer batches.

To create overflow storage, leave its destination unrestricted and set its priority below the sorting destinations. Item pipes retain their existing batch limit of 16 items per extract face every 10 ticks; fluid pipes move 1,000 mB and steam pipes 4,000 mB. Sorting does not change item or fluid components during transport.

## Save and move pipes

Routing lists, matching mode, destination priorities and disabled faces survive world saves. Mining or wrench-packing a configured pipe carries these settings in the dropped pipe item. Placing it again restores them. The dropped item contains reference data rather than actual sample inventory. Older saved pipes keep their existing disabled-face mask and begin with unrestricted filters.

Pipe traversal checks reciprocal connections and disabled masks immediately, including while neighbour updates are pending, and skips unloaded destinations.

## Validation

Minecraft GameTests cover filtered sorting with overflow, source filters, exact named-item matching, save/pack restoration, fluid identity and component preservation, ghost-slot clicks and distance checks, disabled reciprocal links, and equal-priority distribution. The routing screen follows the existing PowerFactor panel style and fits inside the 320 × 240 minimum GUI area. A graphical client pass remains needed to confirm the new screen's appearance and mouse interactions.
