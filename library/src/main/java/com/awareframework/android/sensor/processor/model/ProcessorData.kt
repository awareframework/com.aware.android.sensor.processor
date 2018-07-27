package com.awareframework.android.sensor.processor.model

import com.awareframework.android.core.model.AwareObject
import com.google.gson.Gson

/**
 * Contains the CPU load data.
 *
 * @author  sercant
 * @date 25/07/2018
 */
class ProcessorData(
        var lastUserTicks: Int = 0,
        var lastSystemTicks: Int = 0,
        var lastIdleTicks: Int = 0,
        var userLoad: Float = 0.0f,
        var systemLoad: Float = 0.0f,
        var idleLoad: Float = 0.0f
) : AwareObject(jsonVersion = 1) {
    companion object {
        const val TABLE_NAME = "processorData"
    }

    override fun toString(): String = Gson().toJson(this)
}