package dev.whayn.thyme.data

/**
 * How loudly a medication asks for attention. Stored as a String rather than an
 * ordinal: `colorIndex` and `form` are ints because they index *palettes* whose
 * contents can be reordered, whereas this is a closed semantic set. Strings also
 * keep the Database Inspector readable, which matters because `dose_alerts` is
 * the whole alert state machine and reading it live is how snooze bugs get found.
 *
 * Orthogonal to [Medication.critical]: this governs how an alert *arrives*,
 * critical governs how you get *out* of it. A quiet-but-critical vitamin and a
 * loud-but-skippable painkiller are both expressible on purpose.
 */
enum class AlertTier(val label: String, val description: String) {
    NONE("Off", "Never alerts. It still appears on Today."),
    LIGHT("Light", "A notification and a short sound."),
    MEDIUM("Medium", "Takes over the screen with a short sound."),
    STRONG("Strong", "A real alarm: full screen, and it keeps ringing."),
}

/**
 * What happened to a dose. There is deliberately no MISSED: a dose with no log
 * row *is* missed, which is what keeps midnight rollover job-free.
 *
 * This is the discriminator that lets `dose_logs` stay event-sourced while
 * recording non-takes. The unique index on (scheduledDoseId, forDate) already
 * guarantees one row per dose per day, so putting the outcome on that row makes
 * taken and skipped mutually exclusive by construction - something a separate
 * skips table could not enforce.
 */
enum class DoseOutcome { TAKEN, SKIPPED }

/** Where an alert has got to. Only ever set on doses that actually fired. */
enum class AlertState { FIRED, SNOOZED, EXPIRED }

/** Just enough of a medication to decide how one of its doses should ring. */
data class DoseAlertSettings(
    val scheduledDoseId: Long,
    val alertTier: AlertTier,
    val critical: Boolean,
)
