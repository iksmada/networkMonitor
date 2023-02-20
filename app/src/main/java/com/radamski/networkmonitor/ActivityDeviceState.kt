package com.radamski.networkmonitor

import android.content.Context
import android.os.Bundle
import android.widget.RadioButton
import android.widget.RadioGroup
import com.joaomgcd.taskerpluginlibrary.condition.TaskerPluginRunnerConditionEvent
import com.joaomgcd.taskerpluginlibrary.config.*
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.runner.*
import com.radamski.networkmonitor.state.DeviceUpdate
import com.radamski.networkmonitor.state.DetectionMode

class BasicStateHelper(config: TaskerPluginConfig<DetectionMode>) : TaskerPluginConfigHelper<DetectionMode, Unit, BasicStateRunner>(config) {
    override val runnerClass = BasicStateRunner::class.java
    override val inputClass = DetectionMode::class.java
    override val outputClass = Unit::class.java

    //Since Operator Mode is an int it wouldn't look right in the String Blurb. Here you can change how it looks in the blurb by adding a translation for its input key
    override val inputTranslationsForStringBlurb = HashMap<String, (Any?) -> String?>().apply {
        put(DetectionMode.OPERATOR_KEY) {
            if (it == DetectionMode.ALL) {
                config.context.getString(R.string.all)
            } else {
                config.context.getString(R.string.any)
            }
        }
        put(DetectionMode.STATE_KEY) {
            if (it == DetectionMode.ON) {
                config.context.getString(R.string.on)
            } else {
                config.context.getString(R.string.off)
            }
        }
    }

    override val addDefaultStringBlurb: Boolean
        get() = super.addDefaultStringBlurb

    override fun addToStringBlurb(input: TaskerInput<DetectionMode>, blurbBuilder: StringBuilder) {
        super.addToStringBlurb(input, blurbBuilder)
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

    override fun getSatisfiedCondition(context: Context, input: TaskerInput<DetectionMode>, update: DeviceUpdate?): TaskerPluginResultCondition<Unit> {
        val host = update?.host ?: return TaskerPluginResultConditionUnknown()
        // TODO check if isConnected is not null
        var output: TaskerPluginResultCondition<Unit> = TaskerPluginResultConditionUnsatisfied()
        when(input.regular.operator)
        {
            DetectionMode.ALL -> {
                // TODO implement ALL Operator
                // how to know the state of others?
                // Check it here or in the service?
                // output =  TaskerPluginResultConditionSatisfied(context, Unit)
            }
            DetectionMode.ANY -> {
                output = if(update.isConnected == (input.regular.state == DetectionMode.ON)) {
                    TaskerPluginResultConditionSatisfied(context)
                } else {
                    TaskerPluginResultConditionUnsatisfied();
                }
            }
        }
        return output
    }
}