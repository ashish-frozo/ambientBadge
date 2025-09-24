package com.frozo.ambientscribe.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.frozo.ambientscribe.R
import com.frozo.ambientscribe.ai.LLMService
import com.frozo.ambientscribe.ai.PrescriptionValidator
import com.frozo.ambientscribe.databinding.ActivityReviewBinding
import com.frozo.ambientscribe.telemetry.MetricsCollector
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize

/**
 * Activity for reviewing and editing SOAP notes and prescriptions
 */
class ReviewActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ReviewActivity"
        private const val EXTRA_ENCOUNTER = "encounter"
        private const val EXTRA_TRANSCRIPT = "transcript"

        fun createIntent(context: Context, encounter: LLMService.EncounterNote, transcript: String): Intent {
            return Intent(context, ReviewActivity::class.java).apply {
                putExtra(EXTRA_ENCOUNTER, encounter as Parcelable)
                putExtra(EXTRA_TRANSCRIPT, transcript)
            }
        }
    }

    private lateinit var binding: ActivityReviewBinding
    private lateinit var soapAdapter: SOAPSectionAdapter
    private lateinit var prescriptionAdapter: PrescriptionTableAdapter
    private lateinit var metricsCollector: MetricsCollector
    private lateinit var prescriptionValidator: PrescriptionValidator

    private var encounterNote: LLMService.EncounterNote? = null
    private var transcript: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Get data from intent
        encounterNote = intent.getParcelableExtra<LLMService.EncounterNote>(EXTRA_ENCOUNTER)
        transcript = intent.getStringExtra(EXTRA_TRANSCRIPT)

        if (encounterNote == null || transcript == null) {
            finish()
            return
        }

        // Initialize views
        binding = ActivityReviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize metrics
        metricsCollector = MetricsCollector(this)
        prescriptionValidator = PrescriptionValidator(this)

        // Setup toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Setup adapters
        setupSOAPAdapter()
        setupPrescriptionAdapter()

        // Update UI
        updateConfidenceDisplay()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_review, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            R.id.action_abandon -> {
                abandonSession()
                true
            }
            R.id.action_help -> {
                showHelp()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupSOAPAdapter() {
        encounterNote?.let { note ->
            soapAdapter = SOAPSectionAdapter(
                initialSoapNote = note.soap,
                onSectionEdited = { updatedSoap ->
                    encounterNote = encounterNote?.copy(soap = updatedSoap)
                    updateConfidenceDisplay()
                },
                onConfidenceOverride = { section, index, confidence ->
                    val updatedNote = when (section) {
                        SOAPSection.SUBJECTIVE -> note.copy(
                            soap = note.soap.copy(confidence = confidence)
                        )
                        SOAPSection.OBJECTIVE -> note.copy(
                            soap = note.soap.copy(confidence = confidence)
                        )
                        SOAPSection.ASSESSMENT -> note.copy(
                            soap = note.soap.copy(confidence = confidence)
                        )
                        SOAPSection.PLAN -> note.copy(
                            soap = note.soap.copy(confidence = confidence)
                        )
                    }
                    encounterNote = updatedNote
                    updateConfidenceDisplay()

                    // Log confidence override
                    lifecycleScope.launch {
                        metricsCollector.logEvent(
                            "confidence_override",
                            mapOf(
                                "section" to section.name,
                                "index" to index.toString(),
                                "confidence" to confidence.toString(),
                                "metadata" to note.metadata.toString()
                            )
                        )
                    }
                }
            )

            binding.soapRecyclerView.adapter = soapAdapter
            soapAdapter.submitList(listOf(
                SOAPSectionItem(
                    section = SOAPSection.SUBJECTIVE,
                    items = note.soap.subjective,
                    confidence = note.soap.confidence
                ),
                SOAPSectionItem(
                    section = SOAPSection.OBJECTIVE,
                    items = note.soap.objective,
                    confidence = note.soap.confidence
                ),
                SOAPSectionItem(
                    section = SOAPSection.ASSESSMENT,
                    items = note.soap.assessment,
                    confidence = note.soap.confidence
                ),
                SOAPSectionItem(
                    section = SOAPSection.PLAN,
                    items = note.soap.plan,
                    confidence = note.soap.confidence
                )
            ))
        }
    }

    private fun setupPrescriptionAdapter() {
        encounterNote?.let { note ->
            prescriptionAdapter = PrescriptionTableAdapter(
                initialPrescription = note.prescription,
                prescriptionValidator = prescriptionValidator,
                onMedicationEdited = { updatedPrescription ->
                    encounterNote = encounterNote?.copy(prescription = updatedPrescription)
                    updateConfidenceDisplay()
                },
                onBrandGenericToggle = { index, isGeneric ->
                    val updatedMedications = note.prescription.medications.toMutableList().apply {
                        set(index, get(index).copy(isGeneric = isGeneric))
                    }
                    val updatedPrescription = note.prescription.copy(medications = updatedMedications)
                    encounterNote = encounterNote?.copy(prescription = updatedPrescription)
                    updateConfidenceDisplay()
                }
            )

            binding.prescriptionRecyclerView.adapter = prescriptionAdapter
            prescriptionAdapter.submitList(note.prescription.medications)
        }
    }

    private fun updateConfidenceDisplay() {
        encounterNote?.let { note ->
            // Update SOAP confidence
            val soapConfidence = note.soap.confidence
            binding.soapConfidenceIndicator.setImageResource(
                when {
                    soapConfidence >= 0.8f -> R.color.confidence_green
                    soapConfidence >= 0.6f -> R.color.confidence_amber
                    else -> R.color.confidence_red
                }
            )

            // Update prescription confidence
            val rxConfidence = note.prescription.confidence
            binding.prescriptionConfidenceIndicator.setImageResource(
                when {
                    rxConfidence >= 0.8f -> R.color.confidence_green
                    rxConfidence >= 0.6f -> R.color.confidence_amber
                    else -> R.color.confidence_red
                }
            )
        }
    }

    private fun abandonSession() {
        lifecycleScope.launch {
            metricsCollector.logEvent(
                "session_abandoned",
                mapOf("reason" to "user_action")
            )
        }
        finish()
    }

    private fun showHelp() {
        // TODO: Show help dialog
    }
}