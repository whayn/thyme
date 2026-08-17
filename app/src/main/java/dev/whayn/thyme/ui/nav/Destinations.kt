package dev.whayn.thyme.ui.nav

import kotlinx.serialization.Serializable

object Destinations {
    @Serializable
    data object Today

    @Serializable
    data object Medications

    @Serializable
    data object Stats

    @Serializable
    data object Settings

    /**
     * Both null adds a new medication; a medicationId with no regimenId adds
     * another course of an existing one; both set edits that course.
     */
    @Serializable
    data class MedicationEditor(
        val medicationId: Long? = null,
        val regimenId: Long? = null,
    )

    /** One day's doses, drilled into from the Stats calendar — a separate route from
     *  [Today] so it doesn't get tangled in the bottom nav's tab-switch back-stack logic. */
    @Serializable
    data class DayDetail(val epochDay: Long)
}
