package com.frozo.ambientscribe.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.frozo.ambientscribe.R
import com.frozo.ambientscribe.ai.LLMService
import com.frozo.ambientscribe.ai.PrescriptionValidator
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * Adapter for displaying prescription medications
 */
class PrescriptionTableAdapter(
    private val initialPrescription: LLMService.Prescription,
    private val prescriptionValidator: PrescriptionValidator,
    private val onMedicationEdited: (LLMService.Prescription) -> Unit,
    private val onBrandGenericToggle: (Int, Boolean) -> Unit
) : ListAdapter<LLMService.Medication, PrescriptionTableAdapter.ViewHolder>(PrescriptionDiffCallback()) {

    init {
        submitList(initialPrescription.medications)
    }

    private var isEditable = true
    private var isGenericPreferred = true
    private var confidenceThreshold = 0.6f

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_prescription_medication, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, position)
    }

    /**
     * Set editable state
     */
    fun setEditable(editable: Boolean) {
        isEditable = editable
        notifyDataSetChanged()
    }

    /**
     * Set generic preference
     */
    fun setGenericPreferred(preferred: Boolean) {
        isGenericPreferred = preferred
        notifyDataSetChanged()
    }

    /**
     * Set confidence threshold
     */
    fun setConfidenceThreshold(threshold: Float) {
        confidenceThreshold = threshold
        notifyDataSetChanged()
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameLayout: TextInputLayout = itemView.findViewById(R.id.name_layout)
        private val nameEdit: TextInputEditText = itemView.findViewById(R.id.name_edit)
        private val dosageLayout: TextInputLayout = itemView.findViewById(R.id.dosage_layout)
        private val dosageEdit: TextInputEditText = itemView.findViewById(R.id.dosage_edit)
        private val frequencyLayout: TextInputLayout = itemView.findViewById(R.id.frequency_layout)
        private val frequencyEdit: TextInputEditText = itemView.findViewById(R.id.frequency_edit)
        private val durationLayout: TextInputLayout = itemView.findViewById(R.id.duration_layout)
        private val durationEdit: TextInputEditText = itemView.findViewById(R.id.duration_edit)
        private val instructionsLayout: TextInputLayout = itemView.findViewById(R.id.instructions_layout)
        private val instructionsEdit: TextInputEditText = itemView.findViewById(R.id.instructions_edit)
        private val genericSwitch: SwitchMaterial = itemView.findViewById(R.id.generic_switch)
        private val deleteButton: MaterialButton = itemView.findViewById(R.id.delete_button)
        private val confidenceIndicator: ImageView = itemView.findViewById(R.id.confidence_indicator)

        fun bind(item: LLMService.Medication, position: Int) {
            nameEdit.setText(item.name)
            nameEdit.isEnabled = isEditable
            nameEdit.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    updateMedication(position) { it.copy(name = nameEdit.text.toString()) }
                }
            }

            dosageEdit.setText(item.dosage)
            dosageEdit.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    updateMedication(position) { it.copy(dosage = dosageEdit.text.toString()) }
                }
            }

            frequencyEdit.setText(item.frequency)
            frequencyEdit.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    updateMedication(position) { it.copy(frequency = frequencyEdit.text.toString()) }
                }
            }

            durationEdit.setText(item.duration)
            durationEdit.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    updateMedication(position) { it.copy(duration = durationEdit.text.toString()) }
                }
            }

            instructionsEdit.setText(item.instructions)
            instructionsEdit.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    updateMedication(position) { it.copy(instructions = instructionsEdit.text.toString()) }
                }
            }

            genericSwitch.isChecked = isGenericPreferred
            genericSwitch.setOnCheckedChangeListener { _, isChecked ->
                isGenericPreferred = isChecked
                onBrandGenericToggle(position, isChecked)
            }

            deleteButton.setOnClickListener {
                val updatedMedications = initialPrescription.medications.toMutableList().apply {
                    removeAt(position)
                }
                onMedicationEdited(initialPrescription.copy(medications = updatedMedications))
            }

            confidenceIndicator.setImageResource(
                when {
                    confidenceThreshold >= 0.8f -> R.color.confidence_green
                    confidenceThreshold >= 0.6f -> R.color.confidence_amber
                    else -> R.color.confidence_red
                }
            )

            // Validate medication
            val validationResult = prescriptionValidator.validateMedication(item)
            nameLayout.error = validationResult.nameError
            dosageLayout.error = validationResult.dosageError
            frequencyLayout.error = validationResult.frequencyError
            durationLayout.error = validationResult.durationError
            instructionsLayout.error = validationResult.instructionsError
        }

        private fun updateMedication(position: Int, update: (LLMService.Medication) -> LLMService.Medication) {
            val updatedMedications = initialPrescription.medications.toMutableList().apply {
                set(position, update(get(position)))
            }
            onMedicationEdited(initialPrescription.copy(medications = updatedMedications))
        }
    }
}

/**
 * DiffUtil callback for prescriptions
 */
private class PrescriptionDiffCallback : DiffUtil.ItemCallback<LLMService.Medication>() {
    override fun areItemsTheSame(oldItem: LLMService.Medication, newItem: LLMService.Medication): Boolean {
        return oldItem == newItem
    }

    override fun areContentsTheSame(oldItem: LLMService.Medication, newItem: LLMService.Medication): Boolean {
        return oldItem == newItem
    }
}