# Design System

The tokens the Compose surface is built from, and the measurements behind them.
`app/src/main/java/dev/boreaslab/boreas/design/` is the source of truth; this
document records why each value is what it is.

## The read

An Android product surface for people who run a filtering VPN on their own device,
optimizing for one question, "is my traffic going through the tunnel right now",
and one action that changes the answer. Warm-editorial language: a serif display
face on a parchment canvas, deep indigo product surfaces, one bronze accent.

Dials: **variance 5**, **motion 3**, **density 6**. Variance stays middling because
a control surface earns nothing from asymmetry; the one deliberate break is the
inverted session card. Motion is low because this is a VPN client, so an animation
that costs a frame every second costs battery for the life of the session. Density
is above the middle because the reader is technical and wants counters visible
without scrolling, but the Shield stays calm because it answers one question.

## Precedence applied

1. The WCAG 2.2 AA floor, which overrode everything below it in four places.
2. The supplied palette, which overrode the design document's colors and nothing
   else.
3. The supplied design document, which kept its type roles, spacing, radius, and
   surface pacing.

## Colors

The supplied palette arrives as four stepped ramps. Roles select steps rather than
inventing values.

| Role | Light | Dark | Source |
|---|---|---|---|
| `canvas` | `#f8f9ec` | `#090d1b` | beige-50 / space-indigo-950 |
| `surface` | `#f2f3d8` | `#0d1226` | beige-100 / space-indigo-900 |
| `surfaceStrong` | `#e5e7b1` | `#19254d` | beige-200 / space-indigo-800 |
| `sessionSurface` | `#0d1226` | `#19254d` | space-indigo-900 / 800 |
| `ink` | `#0d1226` | `#f8f9ec` | space-indigo-900 / beige-50 |
| `body` | `#19254d` | `#f2f3d8` | space-indigo-800 / beige-100 |
| `muted` | `#555e78` | `#949aa4` | derived, see below |
| `primary` | `#9f512d` | `#d28460` | light-bronze-600 / 400 |
| `primaryPressed` | `#773d22` | `#c76538` | light-bronze-700 / 500 |
| `onPrimary` | `#f8f9ec` | `#090d1b` | beige-50 / space-indigo-950 |
| `danger` | `#8f3d3f` | `#d19495` | dusty-rose-600 / 300 |
| `warning` | `#4c4e18` | `#d8da8b` | beige-800 / 300 |
| `hairline` | `#e5e7b1` | `#19254d` | beige-200 / space-indigo-800 |
| `border` | `#555e78` | `#949aa4` | derived |
| `focus` | `#0d1226` | `#f8f9ec` | space-indigo-900 / beige-50 |

**One accent.** Light-bronze means action and selection and nothing else. The
prussian-blue ramp is held in reserve with no role: it is a saturated version of
roughly the same hue as space-indigo, so anything painted with it would read as a
glitched surface rather than a second accent.

**Two neutral families, on purpose.** Surfaces are warm (beige), text is cool
(space-indigo). This is the ink-on-paper pairing rather than a mixed grey, and it
is consistent within each role: no surface borrows a text tone and no text borrows
a surface tone.

**Derived values.** `muted` and the dark `border` are the only colors not taken
straight from a ramp. Both are space-indigo-800 blended toward beige-50, at 27% in
light and 55% in dark, which is the ratio each theme needs to clear 4.5:1 on its
densest surface. One derivation rule, two outputs.

### Floor conflicts and how each was resolved

Every pairing was measured from the composited values rather than assumed. Four
failed. Resolutions follow the derivation ladder: restrict by size, then take a
different ramp step, then change the on-color, then adjust lightness.

| Conflict | Measured | Resolution |
|---|---|---|
| `muted` on `surfaceStrong` (light) | 4.15:1, needs 4.5 | Ramp step. The blend ratio moved from 33% to 27%, giving `#555e78` at 5.03:1 on the densest surface and 6.06:1 on the canvas. |
| `warning` on `surface` (light) | 4.38:1, needs 4.5 | Ramp step. beige-700 to beige-800, `#4c4e18`, now 7.71:1. |
| Focus ring on a filled accent (dark) | 2.74:1, needs 3.0 | Geometry rather than color. The ring is drawn outside the control with a gap of canvas between them, so the measured pairing becomes ring against canvas: 17.46:1 light, 18.21:1 dark. The inset is reserved whether or not the control has focus, so gaining focus moves nothing. |
| `sessionSurface` against `canvas` (dark) | 1.30:1, needs 3.0 | The session card is the screen's subject, so its boundary identifies content. The fill stays; a `border`-token line carries the boundary at 6.84:1. |

After these, all 44 measured pairings pass. The check reads the hex values out of
`design/Color.kt` so it cannot drift from what ships.

### One residual limitation

`danger` and `primary` are 20 degrees apart in hue, because light-bronze and
dusty-rose are neighbouring warm hues in the supplied palette. That is a narrower
separation than a danger role usually wants. It is mitigated, not solved: every
danger and warning surface pairs the color with its own icon and a written title,
so the tone survives greyscale and does not depend on telling the two hues apart.
A palette change is the real fix if this ever needs to stand on color alone.

## Typography

Serif display, humanist sans text, monospace for anything numeric. The document
calls this split unbreakable and it is kept.

| Token | Size | Weight | Face | Use |
|---|---|---|---|---|
| `displayLg` | 34sp | 400 | serif | The session state, once per screen |
| `displayMd` | 26sp | 400 | serif | Screen titles |
| `displaySm` | 21sp | 400 | serif | Card headlines |
| `titleMd` | 18sp | 500 | sans | Lead paragraphs, empty-state titles |
| `titleSm` | 16sp | 500 | sans | Row titles, group headings |
| `bodyMd` | 16sp | 400 | sans | Running text |
| `bodySm` | 14sp | 400 | sans | Secondary text, help, errors |
| `label` | 13sp | 500 | sans | Metric labels, navigation |
| `overline` | 12sp | 500 | sans | One uppercase label, once |
| `code` | 14sp | 400 | mono | Counters, addresses, session identity |
| `button` | 14sp | 500 | sans | Button labels |

Eleven steps, from the document's fourteen. Its 64px and 48px display sizes are
desktop marketing values with no role on a handheld, and its 22px `title-lg`
collapsed into `displaySm` at this scale. Sizes are in `sp`, so they follow the
reader's text-size preference.

Counters are set in the monospaced face rather than in the sans with tabular
figures enabled. Both give stable digit columns; the mono face also says
"this is a machine-reported number" at a glance, which is worth something on a
screen whose whole job is reporting machine numbers.

## Spacing, radius, and the rest

Spacing is the document's 4dp scale unchanged: 4, 8, 12, 16, 24, 32, 48. The
document's 96px section rhythm is a desktop value; band separation on a handheld
uses 48. Radius is the document's scale unchanged. Icon sizes are three steps,
16 / 20 / 24, reduced from the five that appeared before they were tokenized.

Every touch target is at least 48dp, above the 24dp floor and at the enhanced
recommendation, because this is a one-handed surface.

Motion has one curve family and four durations: 90ms for press, 180ms for a state
change, 240ms in, 160ms out. Exits run faster than entrances because the reader has
already decided. Reduced motion is read at the point of use, so a mid-session change
takes effect, and it removes movement only: no state, affordance, or piece of
content is gated behind an animation.

## Surface pacing

The document paces a page by alternating a light floor, a one-step card, and an
inverted dark surface. On a handheld the same rhythm runs down one column:

- `canvas` is the floor.
- `surface` groups a discrete object the reader acts on as a unit. Where the
  content is running text, space and alignment do the grouping instead.
- `sessionSurface` is spent **once per screen**, on the thing the screen is about.
  On the Shield that is the tunnel state. No other screen uses it.

## Component inventory

One implementation per concept, checked mechanically.

| Component | Axis of variation |
|---|---|
| `BoreasButton` | `variant`: Primary, Secondary, Quiet, Danger |
| `BoreasCard` | `surface`: Outlined, Filled, Session |
| `ListRow` | none; trailing content is composed in |
| `NavigationRow`, `SwitchRow`, `ChoiceRow` | compose `ListRow`, do not reimplement it |
| `BoreasTextField` | none; label above, error adjacent, no placeholder parameter |
| `NoticeCard` | `tone`: Info, Warning, Danger |
| `StateContainer` | the closed container-state set |
| `MetricRow`, `SessionMetric` | separate because the session surface does not follow the theme |

`ContainerState` carries loading, failed, empty, filtered, and ready. There is no
partial or paged variant: nothing on this surface paginates, and a state that
cannot be constructed is dead code rather than thoroughness. `empty` and `filtered`
are kept apart because "you have none" and "none match what you typed" need
different words and different actions.
