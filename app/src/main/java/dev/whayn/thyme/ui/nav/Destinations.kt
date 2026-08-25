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
     * A single medication's management screen: edit its identity, add/edit/stop/
     * delete its courses, or delete the medication.
     */
    @Serializable
    data class MedicationDetail(val medicationId: Long)

    /** Add (regimenId null) or edit a course of an existing medication. */
    @Serializable
    data class CourseEditor(val medicationId: Long, val regimenId: Long? = null)

    /** Create (medicationId null) or edit a medication's name/strength/colour. */
    @Serializable
    data class MedicationMetadata(val medicationId: Long? = null)

    /** Everything that decides whether an alarm actually goes off. */
    @Serializable
    data object AlertSetup
}
