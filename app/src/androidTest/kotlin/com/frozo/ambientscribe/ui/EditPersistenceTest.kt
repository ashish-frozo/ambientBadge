package com.frozo.ambientscribe.ui

import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.frozo.ambientscribe.R
import com.frozo.ambientscribe.ai.LLMService
import com.frozo.ambientscribe.data.EncounterRepository
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*

/**
 * Tests for edit persistence and validation
 * Implements ST-3.8: Test edit persistence and validation error display
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class EditPersistenceTest {

    private lateinit var scenario: ActivityScenario<ReviewActivity>
    private lateinit var mockRepository: EncounterRepository

    @Before
    fun setup() {
        // Initialize mock repository
        mockRepository = mock(EncounterRepository::class.java)
        `when`(mockRepository.getCurrentEncounter()).thenReturn(createTestEncounterNote())

        // Launch activity
        val intent = Intent(ApplicationProvider.getApplicationContext(), ReviewActivity::class.java)
        scenario = ActivityScenario.launch(intent)
    }

    @After
    fun cleanup() {
        scenario.close()
    }

    /**
     * Test persistence across configuration changes
     */
    @Test
    fun testConfigurationChangePersistence() {
        // Make edits
        onView(withId(R.id.subjective_edit_text))
            .perform(typeText(" - Additional note"), closeSoftKeyboard())

        onView(withId(R.id.medication_name))
            .perform(typeText("New Med"), closeSoftKeyboard())

        // Simulate configuration change
        scenario.recreate()

        // Verify edits persist
        onView(withId(R.id.subjective_edit_text))
            .check(matches(withText(containsString("Additional note"))))

        onView(withId(R.id.medication_name))
            .check(matches(withText("New Med")))
    }

    /**
     * Test persistence across process death
     */
    @Test
    fun testProcessDeathPersistence() {
        // Make edits
        onView(withId(R.id.subjective_edit_text))
            .perform(typeText(" - Process death test"), closeSoftKeyboard())

        // Save state
        val savedState = Bundle()
        scenario.onActivity { activity ->
            activity.onSaveInstanceState(savedState)
        }

        // Destroy activity
        scenario.moveToState(Lifecycle.State.DESTROYED)

        // Relaunch with saved state
        val intent = Intent(ApplicationProvider.getApplicationContext(), ReviewActivity::class.java)
        scenario = ActivityScenario.launch<ReviewActivity>(intent).apply {
            onActivity { activity ->
                activity.onCreate(savedState)
            }
        }

        // Verify edits persist
        onView(withId(R.id.subjective_edit_text))
            .check(matches(withText(containsString("Process death test"))))
    }

    /**
     * Test validation error display
     */
    @Test
    fun testValidationErrors() {
        // Test required field validation
        onView(withId(R.id.medication_name))
            .perform(clearText(), closeSoftKeyboard())
            .check(matches(hasError(R.string.required_field_error)))

        // Test dosage format validation
        onView(withId(R.id.medication_dosage))
            .perform(typeText("invalid"), closeSoftKeyboard())
            .check(matches(hasError(R.string.invalid_dosage_error)))

        // Test frequency format validation
        onView(withId(R.id.medication_frequency))
            .perform(typeText("invalid"), closeSoftKeyboard())
            .check(matches(hasError(R.string.invalid_frequency_error)))

        // Test duration format validation
        onView(withId(R.id.medication_duration))
            .perform(typeText("invalid"), closeSoftKeyboard())
            .check(matches(hasError(R.string.invalid_duration_error)))
    }

    /**
     * Test edit conflict resolution
     */
    @Test
    fun testEditConflictResolution() {
        // Make local edit
        onView(withId(R.id.medication_name))
            .perform(typeText("Local Edit"), closeSoftKeyboard())

        // Simulate background update
        `when`(mockRepository.getCurrentEncounter()).thenReturn(
            createTestEncounterNote().copy(
                prescription = LLMService.Prescription(
                    medications = listOf(
                        LLMService.Medication(
                            name = "Background Update",
                            dosage = "200mg",
                            frequency = "daily",
                            duration = "5 days",
                            instructions = "Take with water",
                            confidence = 0.9f,
                            isGeneric = true
                        )
                    ),
                    instructions = listOf("New instructions"),
                    followUp = "New follow up",
                    confidence = 0.88f
                )
            )
        )

        // Trigger refresh
        onView(withId(R.id.refresh_button)).perform(click())

        // Verify conflict dialog shown
        onView(withText(R.string.edit_conflict_title))
            .check(matches(isDisplayed()))

        // Choose to keep local changes
        onView(withId(R.id.keep_local_button))
            .perform(click())

        // Verify local changes preserved
        onView(withId(R.id.medication_name))
            .check(matches(withText("Local Edit")))
    }

    /**
     * Test auto-save functionality
     */
    @Test
    fun testAutoSave() {
        // Make edits
        onView(withId(R.id.medication_name))
            .perform(typeText("Auto-save test"), closeSoftKeyboard())

        // Wait for auto-save
        Thread.sleep(2000) // Auto-save interval

        // Verify save indicator shown
        onView(withId(R.id.save_indicator))
            .check(matches(withText(R.string.changes_saved)))

        // Verify repository called
        verify(mockRepository, timeout(3000)).saveEncounter(any())
    }

    /**
     * Test validation state persistence
     */
    @Test
    fun testValidationStatePersistence() {
        // Trigger validation error
        onView(withId(R.id.medication_dosage))
            .perform(typeText("invalid"), closeSoftKeyboard())

        // Verify error shown
        onView(withId(R.id.medication_dosage))
            .check(matches(hasError(R.string.invalid_dosage_error)))

        // Rotate screen
        scenario.recreate()

        // Verify error persists
        onView(withId(R.id.medication_dosage))
            .check(matches(hasError(R.string.invalid_dosage_error)))
    }

    private fun createTestEncounterNote(): LLMService.EncounterNote {
        return LLMService.EncounterNote(
            soap = LLMService.SOAPNote(
                subjective = listOf("Initial complaint"),
                objective = listOf("Initial finding"),
                assessment = listOf("Initial diagnosis"),
                plan = listOf("Initial plan"),
                confidence = 0.85f
            ),
            prescription = LLMService.Prescription(
                medications = listOf(
                    LLMService.Medication(
                        name = "Initial medication",
                        dosage = "100mg",
                        frequency = "twice daily",
                        duration = "7 days",
                        instructions = "Take with food",
                        confidence = 0.9f,
                        isGeneric = true
                    )
                ),
                instructions = listOf("Complete course"),
                followUp = "Follow up in 1 week",
                confidence = 0.88f
            ),
            metadata = LLMService.EncounterMetadata(
                speakerTurns = 4,
                totalDuration = 120000,
                processingTime = 3000,
                modelVersion = "test-model",
                fallbackUsed = false,
                encounterId = "test-encounter-123",
                patientId = "test-patient-456"
            )
        )
    }
}
