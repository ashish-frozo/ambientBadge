package com.frozo.ambientscribe.integration

import android.os.StrictMode
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.frozo.ambientscribe.MainActivity
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Ensures StrictMode penalties are fatal during instrumentation to catch
 * accidental main-thread I/O or leaked closables in CI runs.
 */
@RunWith(AndroidJUnit4::class)
class StrictModePolicyTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val originalThreadPolicy = StrictMode.getThreadPolicy()
    private val originalVmPolicy = StrictMode.getVmPolicy()

    @Test
    fun strictModePoliciesAreEnforcedDuringMainFlows() {
        instrumentation.runOnMainSync {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectNetwork()
                    .penaltyLog()
                    .penaltyDeath()
                    .build()
            )

            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedClosableObjects()
                    .penaltyLog()
                    .penaltyDeath()
                    .build()
            )
        }

        ActivityScenario.launch(MainActivity::class.java).use {
            instrumentation.waitForIdleSync()
        }
    }

    @After
    fun tearDownStrictModePolicies() {
        instrumentation.runOnMainSync {
            StrictMode.setThreadPolicy(originalThreadPolicy)
            StrictMode.setVmPolicy(originalVmPolicy)
        }
    }
}
