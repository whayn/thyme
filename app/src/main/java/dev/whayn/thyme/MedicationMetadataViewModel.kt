package dev.whayn.thyme

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.whayn.thyme.data.DoseDao
import dev.whayn.thyme.data.Medication
import dev.whayn.thyme.data.ThymeDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MedicationMetadataState(
    val loading: Boolean = true,
    val name: String = "",
    val strength: String = "",
    val colorIndex: Int = 0, // Left side / primary color
    val colorIndexRight: Int = 0, // Right side color
    val form: Int = 0,
    /**
     * Whether the two halves are being edited as one colour.
     *
     * Explicit state rather than `colorIndex == colorIndexRight`, because the
     * two are equal for every new medication deriving it would make the
     * second picker appear and disappear on its own as you happened to match
     * the halves, and leave nothing to explain why changing the left side also
     * moved the right.
     */
    val linkedColors: Boolean = true,
    val dirty: Boolean = false,
    val saved: Boolean = false,
) {
    val canSave: Boolean get() = name.isNotBlank()
}

/** Name, strength, shape, colours, the identity of a medication. */
class MedicationMetadataViewModel(
    private val dao: DoseDao,
    private val medicationId: Long?,
) : ViewModel() {
    private val _state = MutableStateFlow(MedicationMetadataState(loading = medicationId != null))
    val state: StateFlow<MedicationMetadataState> = _state.asStateFlow()

    init {
        if (medicationId != null) {
            viewModelScope.launch {
                val loaded = dao.getMedication(medicationId)?.medication
                _state.value = if (loaded != null) {
                    MedicationMetadataState(
                        loading = false,
                        name = loaded.name,
                        strength = loaded.strength.orEmpty(),
                        colorIndex = loaded.colorIndex,
                        colorIndexRight = loaded.colorIndexRight,
                        form = loaded.form,
                        linkedColors = loaded.colorIndex == loaded.colorIndexRight,
                    )
                } else {
                    _state.value.copy(loading = false)
                }
            }
        } else {
            _state.value = _state.value.copy(loading = false)
        }
    }

    fun setName(name: String) {
        _state.update { it.copy(name = name, dirty = true) }
    }

    fun setStrength(strength: String) {
        _state.update { it.copy(strength = strength, dirty = true) }
    }

    fun setColor(colorIndex: Int) {
        _state.update {
            it.copy(
                colorIndex = colorIndex,
                colorIndexRight = if (it.linkedColors) colorIndex else it.colorIndexRight,
                dirty = true,
            )
        }
    }

    fun setColorRight(colorIndexRight: Int) {
        _state.update { it.copy(colorIndexRight = colorIndexRight, dirty = true) }
    }

    /** Linking collapses the two pickers into one and pulls the right half to the left. */
    fun setLinkedColors(linked: Boolean) {
        _state.update {
            it.copy(
                linkedColors = linked,
                colorIndexRight = if (linked) it.colorIndex else it.colorIndexRight,
                dirty = true,
            )
        }
    }

    fun setForm(form: Int) {
        _state.update { it.copy(form = form, dirty = true) }
    }

    /** Inserts or updates, then hands back the id so a new medication can open its detail. */
    fun save(onSaved: (Long) -> Unit) {
        val current = state.value
        if (!current.canSave) return

        val medication = Medication(
            id = medicationId ?: 0,
            name = current.name.trim(),
            strength = current.strength.trim().ifBlank { null },
            colorIndex = current.colorIndex,
            colorIndexRight = current.colorIndexRight,
            form = current.form,
        )

        viewModelScope.launch {
            val id =
                if (medication.id == 0L) dao.insertMedication(medication)
                else medication.id.also { dao.updateMedication(medication) }
            _state.update { it.copy(saved = true, dirty = false) }
            onSaved(id)
        }
    }

    companion object {
        fun factory(context: Context, medicationId: Long?) = viewModelFactory {
            initializer {
                MedicationMetadataViewModel(ThymeDatabase.get(context).doseDao(), medicationId)
            }
        }
    }
}
