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
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * Adapter for displaying SOAP sections
 */
class SOAPSectionAdapter(
    private val initialSoapNote: LLMService.SOAPNote,
    private val onSectionEdited: (LLMService.SOAPNote) -> Unit,
    private val onConfidenceOverride: (SOAPSection, Int, Float) -> Unit
) : ListAdapter<SOAPSectionItem, SOAPSectionAdapter.ViewHolder>(SOAPSectionDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_soap_section, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleText: TextView = itemView.findViewById(R.id.section_title)
        private val itemsRecyclerView: RecyclerView = itemView.findViewById(R.id.items_recycler_view)
        private val addButton: MaterialButton = itemView.findViewById(R.id.add_item_button)
        private val confidenceIndicator: ImageView = itemView.findViewById(R.id.confidence_indicator)

        fun bind(item: SOAPSectionItem) {
            titleText.text = when (item.section) {
                SOAPSection.SUBJECTIVE -> "Subjective"
                SOAPSection.OBJECTIVE -> "Objective"
                SOAPSection.ASSESSMENT -> "Assessment"
                SOAPSection.PLAN -> "Plan"
            }

            val adapter = SOAPItemAdapter(
                onItemEdit = { index, text ->
                    val updatedNote = when (item.section) {
                        SOAPSection.SUBJECTIVE -> initialSoapNote.copy(
                            subjective = initialSoapNote.subjective.toMutableList().apply {
                                set(index, text)
                            }
                        )
                        SOAPSection.OBJECTIVE -> initialSoapNote.copy(
                            objective = initialSoapNote.objective.toMutableList().apply {
                                set(index, text)
                            }
                        )
                        SOAPSection.ASSESSMENT -> initialSoapNote.copy(
                            assessment = initialSoapNote.assessment.toMutableList().apply {
                                set(index, text)
                            }
                        )
                        SOAPSection.PLAN -> initialSoapNote.copy(
                            plan = initialSoapNote.plan.toMutableList().apply {
                                set(index, text)
                            }
                        )
                    }
                    onSectionEdited(updatedNote)
                },
                onItemDelete = { index ->
                    val updatedNote = when (item.section) {
                        SOAPSection.SUBJECTIVE -> initialSoapNote.copy(
                            subjective = initialSoapNote.subjective.toMutableList().apply {
                                removeAt(index)
                            }
                        )
                        SOAPSection.OBJECTIVE -> initialSoapNote.copy(
                            objective = initialSoapNote.objective.toMutableList().apply {
                                removeAt(index)
                            }
                        )
                        SOAPSection.ASSESSMENT -> initialSoapNote.copy(
                            assessment = initialSoapNote.assessment.toMutableList().apply {
                                removeAt(index)
                            }
                        )
                        SOAPSection.PLAN -> initialSoapNote.copy(
                            plan = initialSoapNote.plan.toMutableList().apply {
                                removeAt(index)
                            }
                        )
                    }
                    onSectionEdited(updatedNote)
                }
            )
            itemsRecyclerView.adapter = adapter
            adapter.submitList(item.items)

            addButton.setOnClickListener {
                val updatedNote = when (item.section) {
                    SOAPSection.SUBJECTIVE -> initialSoapNote.copy(
                        subjective = initialSoapNote.subjective.toMutableList().apply {
                            add("")
                        }
                    )
                    SOAPSection.OBJECTIVE -> initialSoapNote.copy(
                        objective = initialSoapNote.objective.toMutableList().apply {
                            add("")
                        }
                    )
                    SOAPSection.ASSESSMENT -> initialSoapNote.copy(
                        assessment = initialSoapNote.assessment.toMutableList().apply {
                            add("")
                        }
                    )
                    SOAPSection.PLAN -> initialSoapNote.copy(
                        plan = initialSoapNote.plan.toMutableList().apply {
                            add("")
                        }
                    )
                }
                onSectionEdited(updatedNote)
            }

            confidenceIndicator.setImageResource(
                when {
                    item.confidence >= 0.8f -> R.color.confidence_green
                    item.confidence >= 0.6f -> R.color.confidence_amber
                    else -> R.color.confidence_red
                }
            )
        }
    }
}

/**
 * Data class for SOAP section items
 */
data class SOAPSectionItem(
    val section: SOAPSection,
    val items: List<String>,
    val confidence: Float
)

/**
 * DiffUtil callback for SOAP sections
 */
private class SOAPSectionDiffCallback : DiffUtil.ItemCallback<SOAPSectionItem>() {
    override fun areItemsTheSame(oldItem: SOAPSectionItem, newItem: SOAPSectionItem): Boolean {
        return oldItem.section == newItem.section
    }

    override fun areContentsTheSame(oldItem: SOAPSectionItem, newItem: SOAPSectionItem): Boolean {
        return oldItem == newItem
    }
}

/**
 * Adapter for individual SOAP items
 */
private class SOAPItemAdapter(
    private val onItemEdit: (Int, String) -> Unit,
    private val onItemDelete: (Int) -> Unit
) : ListAdapter<String, SOAPItemAdapter.ViewHolder>(SOAPItemDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_soap_editable_text, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, position)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textInputLayout: TextInputLayout = itemView.findViewById(R.id.text_input_layout)
        private val editText: TextInputEditText = itemView.findViewById(R.id.edit_text)
        private val deleteButton: MaterialButton = itemView.findViewById(R.id.delete_button)

        fun bind(item: String, position: Int) {
            editText.setText(item)
            editText.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    onItemEdit(position, editText.text.toString())
                }
            }
            deleteButton.setOnClickListener {
                onItemDelete(position)
            }
        }
    }
}

/**
 * DiffUtil callback for SOAP items
 */
private class SOAPItemDiffCallback : DiffUtil.ItemCallback<String>() {
    override fun areItemsTheSame(oldItem: String, newItem: String): Boolean {
        return oldItem == newItem
    }

    override fun areContentsTheSame(oldItem: String, newItem: String): Boolean {
        return oldItem == newItem
    }
}