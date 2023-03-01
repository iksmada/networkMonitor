package com.radamski.networkmonitor

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.RadioButton
import android.widget.RadioGroup
import com.joaomgcd.taskerpluginlibrary.condition.TaskerPluginRunnerConditionEvent
import com.joaomgcd.taskerpluginlibrary.config.*
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.runner.*
import com.radamski.networkmonitor.Network.HostBean
import com.radamski.networkmonitor.Utils.TinyDB
import com.radamski.networkmonitor.state.DetectionMode
import com.radamski.networkmonitor.state.DeviceUpdate

class BasicStateHelper(config: TaskerPluginConfig<DetectionMode>) : TaskerPluginConfigHelper<DetectionMode, Unit, BasicStateRunner>(config) {
    override val runnerClass = BasicStateRunner::class.java
    override val inputClass = DetectionMode::class.java
    override val outputClass = Unit::class.java

    override val addDefaultStringBlurb: Boolean
        get() = false

    override fun addToStringBlurb(input: TaskerInput<DetectionMode>, blurbBuilder: StringBuilder) {
        val operator =
            if (input.regular.operator == DetectionMode.ALL) {
                config.context.getString(R.string.all) + " devices are"
            } else {
                config.context.getString(R.string.any) + " device is"
            }
        val state =
            if (input.regular.state == DetectionMode.ON) {
                config.context.getString(R.string.on)
            } else {
                config.context.getString(R.string.off)
            }
        super.addToStringBlurb(input, blurbBuilder)
        blurbBuilder.append("When %s %s".format(operator, state))
        blurbBuilder.append("\nTips go here")
    }
}

class ActivityDeviceState : ActivityConfigTaskerNoOutput<DetectionMode, BasicStateRunner, BasicStateHelper >() {
    var radiosState: RadioGroup? = null
    var radiosOperator: RadioGroup? = null
    override fun assignFromInput(input: TaskerInput<DetectionMode>) {
        if (input.regular.operator == DetectionMode.ALL) {
            findViewById<RadioButton>(R.id.radioButtonAll).isChecked = true
        }
        else {
            findViewById<RadioButton>(R.id.radioButtonAny).isChecked = true
        }
        if (input.regular.state == DetectionMode.ON) {
            findViewById<RadioButton>(R.id.radioButtonOn).isChecked = true
        }
        else {
            findViewById<RadioButton>(R.id.radioButtonOff).isChecked = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setContentView(R.layout.activity_config_device_mode)
        super.onCreate(savedInstanceState)
        radiosOperator = findViewById(R.id.radioGroupOperator)
        radiosState = findViewById(R.id.radioGroupState)
    }

    override val inputForTasker get() = TaskerInput(DetectionMode(radiosOperator?.let { modeFromIdOp(it.checkedRadioButtonId) }, radiosState?.let { modeFromIdState(it.checkedRadioButtonId) }))

    private fun modeFromIdOp(checkedRadioButtonId: Int): Int {
        return if(R.id.radioButtonAll == checkedRadioButtonId) {
            DetectionMode.ALL
        } else {
            DetectionMode.ANY
        }
    }
    private fun modeFromIdState(checkedRadioButtonId: Int): Int {
        return if(R.id.radioButtonOn == checkedRadioButtonId) {
            DetectionMode.ON
        } else {
            DetectionMode.OFF
        }
    }

    override val layoutResId = R.layout.activity_config_device_mode
    override fun getNewHelper(config: TaskerPluginConfig<DetectionMode>) = BasicStateHelper(config)
}

class BasicStateRunner :  TaskerPluginRunnerConditionEvent<DetectionMode, Unit, DeviceUpdate>() {
    private val TAG = "NetworkSniffEvents"

    override fun getSatisfiedCondition(context: Context, input: TaskerInput<DetectionMode>, update: DeviceUpdate?): TaskerPluginResultCondition<Unit> {
        Log.i(TAG, "Received update from %s with flags:{isConnected:%s, premature:%s}".format(update?.host?.hostname, update?.isConnected, update?.premature))
        val output: TaskerPluginResultCondition<Unit> =
            if(update?.isConnected == (input.regular.state == DetectionMode.ON)) {
                when(input.regular.operator)
                {
                    DetectionMode.ANY -> {
                        if(update.premature == false && update.isConnected == true) // only send connected event when detected prematurely
                            TaskerPluginResultConditionUnsatisfied()
                        else
                            TaskerPluginResultConditionSatisfied(context)
                    }
                    DetectionMode.ALL -> {
                        // TODO implement ALL Operator
                        // how to know the state of others?
                        // Check it here or in the service?
                        // output =  TaskerPluginResultConditionSatisfied(context, Unit)
                        val tinydb = TinyDB(context)
                        val connected =
                            tinydb.getListObject(
                                AbstractDiscoveryTask.CONNECTED_TRACKED_DEVICES,
                                HostBean::class.java
                            )
                        if(update.premature == true)
                        {
                            TaskerPluginResultConditionUnsatisfied()
                        } else {
                            val tracked =
                                tinydb.getListObject(
                                    AbstractDiscoveryTask.TRACKED_DEVICES,
                                    HostBean::class.java
                                )
                            val comply: Boolean =
                                when (input.regular.state) {
                                    DetectionMode.ON -> {
                                        tracked.all { host -> connected.contains(host) }
                                    }
                                    DetectionMode.OFF -> {
                                        tracked.all { host -> !connected.contains(host) }
                                    }
                                    else -> {
                                        false
                                    }
                                }
                            if (comply)
                                TaskerPluginResultConditionSatisfied(context)
                            else
                                TaskerPluginResultConditionUnsatisfied()
                        }
                    }
                    else -> {TaskerPluginResultConditionUnsatisfied()}
                }
            } else {
                TaskerPluginResultConditionUnsatisfied()
            }
        val state = if (input.regular.state == DetectionMode.ON) "connected" else "disconnected"
        val operator = if (input.regular.operator == DetectionMode.ALL) "ALL" else "ANY"
        val result = if (output.success) "satisfied" else "unsatisfied"
        Log.i(TAG, "Monitoring %s %s is %s".format( operator, state, result));
        return output
    }
}