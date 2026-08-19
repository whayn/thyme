package dev.whayn.thyme.ui.theme

import androidx.compose.ui.unit.dp

/**
 * The few measurements that have to agree across screens.
 *
 * These existed before as literals typed at each call site, which is how the
 * app ended up with headers at 24dp and the cards they introduce at 20dp, a
 * 4dp wobble visible on four different screens. A name is harder to mistype
 * than a number.
 */
object ThymeDimens {

    /**
     * The single page gutter. Everything that touches the left or right edge of
     * a screen uses this: headers, section labels, cards, list rows.
     */
    val PageGutter = 20.dp

    /** Minimum square Material will accept as a tap target. */
    val TouchTarget = 48.dp

    /** The medication pill icon as it appears in lists and rows. */
    val PillIcon = 32.dp

    /** The tinted circle the pill icon sits in inside a card. */
    val PillIconContainer = 44.dp
}
