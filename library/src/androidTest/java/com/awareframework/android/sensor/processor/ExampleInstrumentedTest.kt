package com.awareframework.android.sensor.processor;

import android.support.test.InstrumentationRegistry
import android.support.test.runner.AndroidJUnit4
import com.awareframework.android.core.db.Engine
import com.awareframework.android.sensor.processor.model.ProcessorData
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test, which will execute on an Android device.
 * <p>
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getTargetContext()
        assertEquals("com.aware.android.sensor.processor.test", appContext.packageName)

        ProcessorSensor.startService(appContext, ProcessorSensor.ProcessorConfig().apply {
            sensorObserver = object :ProcessorSensor.ProcessorObserver {
                override fun onOverloaded() {
                    // your code here...
                }

                override fun onIdle() {
                    // your code here...
                }

                override fun onChanged(data: ProcessorData) {
                    // your code here...
                }

            }
            dbType = Engine.DatabaseType.ROOM
        })
    }
}
