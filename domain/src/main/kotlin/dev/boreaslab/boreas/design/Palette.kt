package dev.boreaslab.boreas.design

/**
 * The color tokens, as pure values with a law attached.
 *
 * These live in the pure module rather than beside the Compose code for one
 * reason: the accessibility floor is a property of the palette, not of Android,
 * so it can be stated as a total function over data and checked by an ordinary
 * JVM test. `:app` derives every Compose `Color` from the roles below, so there
 * is one source of truth and the interface cannot render a color the law has
 * never seen.
 *
 * Steps come from the supplied palette's four ramps. The two exceptions are
 * documented at [MutedLight] and [MutedDark].
 */
public object Ramp {
    public val Indigo800: Srgb = Srgb.of(0x19254D)
    public val Indigo900: Srgb = Srgb.of(0x0D1226)
    public val Indigo950: Srgb = Srgb.of(0x090D1B)

    public val Beige50: Srgb = Srgb.of(0xF8F9EC)
    public val Beige100: Srgb = Srgb.of(0xF2F3D8)
    public val Beige200: Srgb = Srgb.of(0xE5E7B1)
    public val Beige300: Srgb = Srgb.of(0xD8DA8B)
    public val Beige800: Srgb = Srgb.of(0x4C4E18)

    public val Bronze300: Srgb = Srgb.of(0xDDA388)
    public val Bronze400: Srgb = Srgb.of(0xD28460)
    public val Bronze500: Srgb = Srgb.of(0xC76538)
    public val Bronze600: Srgb = Srgb.of(0x9F512D)
    public val Bronze700: Srgb = Srgb.of(0x773D22)

    public val Rose300: Srgb = Srgb.of(0xD19495)
    public val Rose600: Srgb = Srgb.of(0x8F3D3F)
}

/** space-indigo-800 blended 27% toward beige-50, the ratio light needs to clear body text on its densest surface. */
private val MutedLight = Srgb.of(0x555E78)

/** The same derivation at 55%, which is what dark needs. */
private val MutedDark = Srgb.of(0x949AA4)

/**
 * Every color role, for one theme.
 *
 * A product of roles rather than a map keyed by name: a missing role is a
 * compile error, and no call site can ask for a role that does not exist.
 */
public data class ColorRoles(
    val canvas: Srgb,
    val surface: Srgb,
    val surfaceStrong: Srgb,
    val sessionSurface: Srgb,
    val ink: Srgb,
    val body: Srgb,
    val muted: Srgb,
    val sessionInk: Srgb,
    val sessionMuted: Srgb,
    val sessionPrimary: Srgb,
    val primary: Srgb,
    val primaryPressed: Srgb,
    val onPrimary: Srgb,
    val danger: Srgb,
    val warning: Srgb,
    val hairline: Srgb,
    val border: Srgb,
    val focus: Srgb,
)

public val LightRoles: ColorRoles = ColorRoles(
    canvas = Ramp.Beige50,
    surface = Ramp.Beige100,
    surfaceStrong = Ramp.Beige200,
    sessionSurface = Ramp.Indigo900,
    ink = Ramp.Indigo900,
    body = Ramp.Indigo800,
    muted = MutedLight,
    sessionInk = Ramp.Beige50,
    sessionMuted = MutedDark,
    sessionPrimary = Ramp.Bronze400,
    primary = Ramp.Bronze600,
    primaryPressed = Ramp.Bronze700,
    onPrimary = Ramp.Beige50,
    danger = Ramp.Rose600,
    warning = Ramp.Beige800,
    hairline = Ramp.Beige200,
    border = MutedLight,
    focus = Ramp.Indigo900,
)

public val DarkRoles: ColorRoles = ColorRoles(
    canvas = Ramp.Indigo950,
    surface = Ramp.Indigo900,
    surfaceStrong = Ramp.Indigo800,
    sessionSurface = Ramp.Indigo800,
    ink = Ramp.Beige50,
    body = Ramp.Beige100,
    muted = MutedDark,
    sessionInk = Ramp.Beige50,
    sessionMuted = MutedDark,
    sessionPrimary = Ramp.Bronze300,
    primary = Ramp.Bronze400,
    primaryPressed = Ramp.Bronze500,
    onPrimary = Ramp.Indigo950,
    danger = Ramp.Rose300,
    warning = Ramp.Beige300,
    hairline = Ramp.Indigo800,
    border = MutedDark,
    focus = Ramp.Beige50,
)

/**
 * Every pairing the interface renders, with the threshold each must clear.
 *
 * This is the contract the design owes its readers, written as data so a test
 * can fold over it. Adding a role without adding its pairings here is the one
 * gap this cannot catch, which is why the pairing count is asserted too.
 *
 * The focus ring is measured against the canvas rather than against the control
 * it surrounds: it is drawn outside the control with a gap, so the canvas is
 * what sits behind the ring pixels.
 */
public fun ColorRoles.pairings(): List<Pairing> = listOf(
    Pairing("heading on page", ink, canvas, ContrastRequirement.BodyText),
    Pairing("heading on card", ink, surface, ContrastRequirement.BodyText),
    Pairing("heading on selected row", ink, surfaceStrong, ContrastRequirement.BodyText),
    Pairing("running text on page", body, canvas, ContrastRequirement.BodyText),
    Pairing("running text on card", body, surface, ContrastRequirement.BodyText),
    Pairing("secondary text on page", muted, canvas, ContrastRequirement.BodyText),
    Pairing("secondary text on card", muted, surface, ContrastRequirement.BodyText),
    Pairing("secondary text on selected row", muted, surfaceStrong, ContrastRequirement.BodyText),
    Pairing("accent text on page", primary, canvas, ContrastRequirement.BodyText),
    Pairing("accent text on card", primary, surface, ContrastRequirement.BodyText),
    Pairing("button label", onPrimary, primary, ContrastRequirement.BodyText),
    Pairing("pressed button label", onPrimary, primaryPressed, ContrastRequirement.BodyText),
    Pairing("error text on page", danger, canvas, ContrastRequirement.BodyText),
    Pairing("error text on card", danger, surface, ContrastRequirement.BodyText),
    Pairing("warning text on page", warning, canvas, ContrastRequirement.BodyText),
    Pairing("warning text on card", warning, surface, ContrastRequirement.BodyText),
    Pairing("session headline", sessionInk, sessionSurface, ContrastRequirement.BodyText),
    Pairing("session label", sessionMuted, sessionSurface, ContrastRequirement.BodyText),
    Pairing("running headline", sessionPrimary, sessionSurface, ContrastRequirement.BodyText),
    Pairing("control border on page", border, canvas, ContrastRequirement.NonText),
    Pairing("control border on card", border, surface, ContrastRequirement.NonText),
    Pairing("focus ring against canvas", focus, canvas, ContrastRequirement.NonText),
)
