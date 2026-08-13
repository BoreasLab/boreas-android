package dev.boreaslab.boreas.model

/**
 * A value with a stable name on disk.
 *
 * Storing `Enum.name` looks free and is not: it makes the identifier the wire
 * format. Renaming a constant during an ordinary refactor then silently discards
 * every reader's stored choice, because the old token no longer matches anything
 * and the total lookup below falls back to the default. Minification is the same
 * hazard from the other direction, since a release build is free to rename what a
 * debug build did not.
 *
 * Writing the token out makes the storage format a decision that shows up in a
 * diff. The identifier and the token are then free to differ, and only one of them
 * is a compatibility promise.
 */
public interface Persisted {
    /** The token written to storage. Never derived from the identifier. */
    public val wire: String
}

/**
 * Reads a stored token back, totally: an unknown or corrupted one resolves to
 * [fallback] rather than throwing.
 *
 * A linear scan rather than a map. Every set of these has fewer than ten members,
 * so the scan touches one cache line and allocates nothing, while a map would cost
 * more to build than every lookup it could ever serve. $O(n)$ in a bounded n.
 */
public fun <T : Persisted> String?.toPersisted(values: List<T>, fallback: T): T =
    values.firstOrNull { it.wire == this } ?: fallback
