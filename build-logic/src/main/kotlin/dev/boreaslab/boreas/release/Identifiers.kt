package dev.boreaslab.boreas.release

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * A UTC instant rendered `yyyy-mm-dd.hh-mm-ss`.
 *
 * Fixed width and zero padded, and both halves of that are load-bearing. SemVer
 * compares a hyphen-bearing identifier as ASCII, so lexical order on the
 * rendering has to equal chronological order on the instant. Unpadded, an hour
 * of `9` sorts above an hour of `11` and two builds ninety minutes apart come
 * back in the wrong order, silently, forever.
 *
 * The padding is in the pattern rather than in a comment: `MM`, `dd`, `HH`,
 * `mm`, and `ss` are two-digit fields by definition of the letter, and `uuuu` is
 * four. Nothing else in this file has to be trusted for the ordering to hold.
 */
@JvmInline
value class Stamp private constructor(val text: String) : Comparable<Stamp> {

    override fun compareTo(other: Stamp): Int = text.compareTo(other.text)

    override fun toString(): String = text

    companion object {

        private val FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("uuuu-MM-dd.HH-mm-ss").withZone(ZoneOffset.UTC)

        private val SHAPE = Regex("""\d{4}-\d{2}-\d{2}\.\d{2}-\d{2}-\d{2}""")

        /** The clock, which is the only thing about a publish that is not a fact about the tree. */
        fun at(instant: Instant): Stamp = Stamp(FORMAT.format(instant))

        /**
         * A stamp decided by an earlier job and handed to this one.
         *
         * The pipeline resolves the identity once and passes the instant down,
         * because everything else that names a build is a fact about the commit
         * and recomputes identically. Two jobs reading their own clocks would
         * publish under one tag and stamp the binary with another.
         */
        fun parse(text: String): Stamp? = if (SHAPE.matches(text)) Stamp(text) else null
    }
}

/**
 * Seven hex digits of the commit, rendered behind a literal `g`.
 *
 * The prefix is not decoration. SemVer compares an all-digit identifier
 * *numerically* and ranks it below every alphanumeric one, so a commit that
 * abbreviates to `0012345` would sort beneath its siblings and a consumer taking
 * the newest tag would take the wrong one. `g` is what `git describe` writes,
 * and it makes an all-digit identifier unrepresentable rather than unlikely.
 */
@JvmInline
value class Sha private constructor(val digits: String) {

    override fun toString(): String = "g$digits"

    companion object {

        private const val WIDTH = 7

        /** A full or already-abbreviated object name, of which seven digits are kept. */
        fun abbreviate(objectName: String): Sha? {
            val trimmed = objectName.trim()
            val hex = trimmed.length >= WIDTH && trimmed.all { it.isHexDigit() }
            return if (hex) Sha(trimmed.substring(0, WIDTH)) else null
        }

        private fun Char.isHexDigit(): Boolean =
            this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
    }
}
