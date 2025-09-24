package com.frozo.ambientscribe.performance

/**
 * Interface for listening to thermal state changes
 */
interface ThermalStateListener {
    fun onThermalStateChanged(state: ThermalManager.ThermalState)
}
