package com.awareframework.android.sensor.processor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.util.Log
import com.awareframework.android.core.AwareSensor
import com.awareframework.android.core.db.Engine
import com.awareframework.android.core.model.SensorConfig
import com.awareframework.android.sensor.processor.model.ProcessorData
import java.io.*


/**
 * Service that logs CPU activity on the device
 *
 * @author  sercant
 * @date 25/07/2018
 */
class ProcessorService : AwareSensor() {

    companion object {
        const val TAG = "AwareProcessorService"

        const val ACTION_AWARE_PROCESSOR_START = "com.awareframework.android.sensor.processor.ACTION_AWARE_PROCESSOR_START"
        const val ACTION_AWARE_PROCESSOR_STOP = "com.awareframework.android.sensor.processor.ACTION_AWARE_PROCESSOR_STOP"

        const val ACTION_AWARE_PROCESSOR_SET_LABEL = "com.awareframework.android.sensor.processor.ACTION_AWARE_PROCESSOR_SET_LABEL"
        const val ACTION_AWARE_PROCESSOR_SYNC = "com.awareframework.android.sensor.processor.ACTION_AWARE_PROCESSOR_SYNC"
        const val EXTRA_LABEL = "label"

        /**
         * Broadcasted event: when there is new processor usage information
         */
        const val ACTION_AWARE_PROCESSOR = "ACTION_AWARE_PROCESSOR"

        /**
         * Broadcasted event: fired when the processor idle is below 10%
         */
        const val ACTION_AWARE_PROCESSOR_STRESSED = "ACTION_AWARE_PROCESSOR_STRESSED"

        /**
         * Broadcasted event: fired when the processor idle is above 90%
         */
        const val ACTION_AWARE_PROCESSOR_RELAXED = "ACTION_AWARE_PROCESSOR_RELAXED"

        fun startService(context: Context, config: ProcessorConfig? = null) {
            if (config != null)
                CONFIG.replaceWith(config)
            context.startService(Intent(context, ProcessorService::class.java))
        }

        fun stopService(context: Context) {
            context.stopService(Intent(context, ProcessorService::class.java))
        }

        val CONFIG = ProcessorConfig()
    }

    private var lastData: ProcessorLoad? = null

    private val mHandler = Handler()
    private val mRunnable: Runnable = Runnable {
        val processorNow = getProcessorLoad()

        var userPercentage = 0f
        var systemPercentage = 0f
        var idlePercentage = 0f

        lastData?.let { oldLoad ->
            val delta = processorNow - oldLoad
            try {
                val multiplier = 100.0f / (delta.user + delta.system + delta.idle).toFloat()
                userPercentage = delta.user * multiplier
                systemPercentage = delta.system * multiplier
                idlePercentage = delta.idle * multiplier
            } catch (e: ArithmeticException) {
            }
        }

        logd("USER: $userPercentage% IDLE: $idlePercentage% Total: ${userPercentage + systemPercentage + idlePercentage}")

        val data = ProcessorData().apply {
            timestamp = System.currentTimeMillis()
            deviceId = CONFIG.deviceId

            lastUserTicks = processorNow.user
            lastSystemTicks = processorNow.system
            lastIdleTicks = processorNow.idle

            userLoad = userPercentage
            systemLoad = systemPercentage
            idleLoad = idlePercentage
        }

        dbEngine?.save(data, ProcessorData.TABLE_NAME)

        CONFIG.sensorObserver?.onChanged(data)

        sendBroadcast(Intent(ACTION_AWARE_PROCESSOR))

        if (idlePercentage <= 10) {
            sendBroadcast(Intent(ACTION_AWARE_PROCESSOR_STRESSED))

            CONFIG.sensorObserver?.onOverloaded()
        } else if (idlePercentage >= 90) {
            sendBroadcast(Intent(ACTION_AWARE_PROCESSOR_RELAXED))

            CONFIG.sensorObserver?.onIdle()
        }

        schedule()
    }

    private val processorReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent ?: return
            when (intent.action) {
                ACTION_AWARE_PROCESSOR_SET_LABEL -> {
                    intent.getStringExtra(EXTRA_LABEL)?.let {
                        CONFIG.label = it
                    }
                }

                ACTION_AWARE_PROCESSOR_SYNC -> onSync(intent)
            }
        }
    }

    private fun schedule() {
        mHandler.postDelayed(mRunnable, CONFIG.frequency * 1000L)
    }

    override fun onCreate() {
        super.onCreate()

        dbEngine = Engine.Builder(this)
                .setPath(CONFIG.dbPath)
                .setType(CONFIG.dbType)
                .setHost(CONFIG.dbHost)
                .setEncryptionKey(CONFIG.dbEncryptionKey)
                .build()

        registerReceiver(processorReceiver, IntentFilter().apply {
            addAction(ACTION_AWARE_PROCESSOR_SET_LABEL)
            addAction(ACTION_AWARE_PROCESSOR_SYNC)
        })

        logd("Processor service created.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            logd("Processor service is not allowed by Google, buuuu. Disabling sensor...")

            stopSelf()
            return START_NOT_STICKY
        }

        mHandler.removeCallbacks(mRunnable)
        mHandler.post(mRunnable)

        logd("Processor service active: ${CONFIG.frequency}s")

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()

        mHandler.removeCallbacks(mRunnable)

        dbEngine?.close()

        unregisterReceiver(processorReceiver)

        logd("Processor service terminated...")
    }

    interface ProcessorObserver {
        /**
         * CPU load is >=90%
         */
        fun onOverloaded()

        /**
         * CPU load is <=10%
         */
        fun onIdle()

        /**
         * CPU load updated
         *
         * @param data
         */
        fun onChanged(data: ProcessorData)
    }

    data class ProcessorConfig(
            var sensorObserver: ProcessorObserver? = null,
            var frequency: Int = 10
    ) : SensorConfig(dbPath = "aware_processor") {

        override fun <T : SensorConfig> replaceWith(config: T) {
            super.replaceWith(config)

            if (config is ProcessorConfig) {
                sensorObserver = config.sensorObserver
            }
        }
    }

    override fun onSync(intent: Intent?) {
        dbEngine?.startSync(ProcessorData.TABLE_NAME)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private data class ProcessorLoad(
            var user: Int = 0,
            var system: Int = 0,
            var idle: Int = 0
    ) {
        operator fun minus(other: ProcessorLoad): ProcessorLoad {
            return ProcessorLoad(
                    user - other.user,
                    system - other.system,
                    idle - other.idle)
        }
    }

    /**
     * Get processor load from /proc/stat and returns them in a ProcessorLoad.
     *
     * @return [ProcessorLoad] with user, system and idle keys and values
     */
    private fun getProcessorLoad(): ProcessorLoad {
        val load = ProcessorLoad()
        try {
            val reader = BufferedReader(InputStreamReader(FileInputStream("/proc/stat")), 5000)
            reader.readLine()?.let {
                val items = it.split(" ".toRegex())
                load.user = items[2].toInt() + items[3].toInt()
                load.system = items[4].toInt()
                load.idle = items[5].toInt()
            }
            //NOTE: CPU  USER NICE SYSTEM IDLE - there are two spaces after CPU
            reader.close()
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }

        return load
    }

    class ProcessorServiceBroadcastReceiver : AwareSensor.SensorBroadcastReceiver() {

        override fun onReceive(context: Context?, intent: Intent?) {
            context ?: return

            logd("Sensor broadcast received. action: " + intent?.action)

            when (intent?.action) {
                AwareSensor.SensorBroadcastReceiver.SENSOR_START_ENABLED -> {
                    logd("Sensor enabled: " + CONFIG.enabled)

                    if (CONFIG.enabled) {
                        startService(context)
                    }
                }

                ACTION_AWARE_PROCESSOR_STOP,
                AwareSensor.SensorBroadcastReceiver.SENSOR_STOP_ALL -> {
                    logd("Stopping sensor.")
                    stopService(context)
                }

                ACTION_AWARE_PROCESSOR_START -> {
                    startService(context)
                }
            }
        }
    }
}

private fun logd(text: String) {
    if (ProcessorService.CONFIG.debug)
        Log.d(ProcessorService.TAG, text)
}