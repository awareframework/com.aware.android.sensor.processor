# AWARE Processor

<!-- [![jitpack-badge](https://jitpack.io/v/awareframework/com.aware.android.sensor.processor.svg)](https://jitpack.io/#awareframework/com.aware.android.sensor.processor) -->

The processor sensor measures the mobile phone’s processor load. It provides the average processors load (for multi-core devices) dedicated to the user processes, system processes and idle (i.e., no load). It also broadcasts when the processor is under stress or relaxed.

> Due to [security restrictions](https://issuetracker.google.com/issues/37140047), processor sensor doesn't work on the devices over android version N right now.

## Public functions

### ProcessorSensor

+ `startService(context: Context, config: ProcessorConfig?)`: Starts the processor sensor with the optional configuration.
+ `stopService(context: Context)`: Stops the service.

### ProcessorConfig

Class to hold the configuration of the sensor.

#### Fields

+ `debug: Boolean`: enable/disable logging to `Logcat`. (default = false)
+ `host: String`: Host for syncing the database. (default = null)
+ `key: String`: Encryption key for the database. (default = no encryption)
+ `host: String`: Host for syncing the database. (default = null)
+ `type: EngineDatabaseType)`: Which db engine to use for saving data. (default = NONE)
+ `path: String`: Path of the database.
+ `deviceId: String`: Id of the device that will be associated with the events and the sensor. (default = "")
+ `sensorObserver: ProcessorObserver`: Callback for live data updates.
+ `frequency: Int`: Frequency of the data querying in seconds. (default = 10)

## Broadcasts

+ `ProcessorSensor.ACTION_AWARE_PROCESSOR` when there is new processor usage information.
+ `ProcessorSensor.ACTION_AWARE_PROCESSOR_STRESSED` fired when the processor idle is below 10%.
+ `ProcessorSensor.ACTION_AWARE_PROCESSOR_RELAXED` fired when the processor idle is above 90%.

## Data Representations

### Processor Data

Contains the CPU load data.

| Field           | Type   | Description                                             |
| --------------- | ------ | ------------------------------------------------------- |
| lastUserTicks   | Int    | last user CPU ticks                                     |
| lastSystemTicks | Int    | last system CPU ticks                                   |
| lastIdleTicks   | Int    | last idle CPU ticks                                     |
| userLoad        | Float  | percentage of CPU load dedicated to user’s processes   |
| systemLoad      | Float  | percentage of CPU load dedicated to system’s processes |
| idleLoad        | Float  | percentage of idle CPU load                             |
| deviceId        | String | AWARE device UUID                                       |
| timestamp       | Long   | unixtime milliseconds since 1970                        |
| timezone        | Int    | [Raw timezone offset][1] of the device                  |
| os              | String | Operating system of the device (ex. android)            |

[1]: https://developer.android.com/reference/java/util/TimeZone#getRawOffset()

## Example usage

```kotlin
// To start the service.
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
    // more configuration...
})

// To stop the service
ProcessorSensor.stopService(appContext)
```

## License

Copyright (c) 2018 AWARE Mobile Context Instrumentation Middleware/Framework (http://www.awareframework.com)

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
