package com.radamski.networkmonitor

import android.content.Context
import android.os.Bundle
import android.widget.RadioButton
import android.widget.RadioGroup
import com.joaomgcd.taskerpluginlibrary.condition.TaskerPluginRunnerConditionNoOutput
import com.joaomgcd.taskerpluginlibrary.config.*
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.runner.*
import com.radamski.networkmonitor.state.DeviceUpdate
import com.radamski.networkmonitor.state.OperatorMode

class BasicStateHelper(config: TaskerPluginConfig<OperatorMode>) : TaskerPluginConfigHelperConditionNoOutput<OperatorMode, DeviceUpdate, BasicStateRunner>(config) {
    override val runnerClass = BasicStateRunner::class.java
    override val inputClass = OperatorMode::class.java

    //Since Operator Mode is an int it wouldn't look right in the String Blurb. Here you can change how it looks in the blurb by adding a translation for its input key
    override val inputTranslationsForStringBlurb = HashMap<String, (Any?) -> String?>().apply {
        put(OperatorMode.OP_MODE_KEY) {
            if (it == OperatorMode.ALL) {
                config.context.getString(R.string.all)
            } else {
                config.context.getString(R.string.any)
            }
        }
    }

    override val addDefaultStringBlurb: Boolean
        get() = super.addDefaultStringBlurb

    override fun addToStringBlurb(input: TaskerInput<OperatorMode>, blurbBuilder: StringBuilder) {
        super.addToStringBlurb(input, blurbBuilder)
        blurbBuilder.append("\nClick Invert in order to negate the operator.\nE.g. mark ALL radio button and Invert checkbox to trigger an event every time all devices are disconnected from the network")
    }
}

class ActivityDeviceState : ActivityConfigTaskerNoOutput<OperatorMode, BasicStateRunner, BasicStateHelper >() {
    var radios: RadioGroup? = null;
    override fun assignFromInput(input: TaskerInput<OperatorMode>) {
        if (input.regular.opMode == OperatorMode.ALL) {
            findViewById<RadioButton>(R.id.radioButtonAll).isChecked = true
        }
        else {
            findViewById<RadioButton>(R.id.radioButtonAny).isChecked = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setContentView(R.layout.activity_config_operator)
        super.onCreate(savedInstanceState)
        radios = findViewById(R.id.radioGroupOperator)
    }

    override val inputForTasker get() = TaskerInput(OperatorMode(radios?.let { modeFromId(it.checkedRadioButtonId) }))

    private fun modeFromId(checkedRadioButtonId: Int): Int {
        return if(R.id.radioButtonAll == checkedRadioButtonId) {
            OperatorMode.ALL
        } else {
            OperatorMode.ANY
        }
    }

    override val layoutResId = R.layout.activity_config_operator
    override fun getNewHelper(config: TaskerPluginConfig<OperatorMode>) = BasicStateHelper(config)
}

class BasicStateRunner : TaskerPluginRunnerConditionNoOutput<OperatorMode, DeviceUpdate>() {

    override fun getSatisfiedCondition(context: Context, input: TaskerInput<OperatorMode>, update: DeviceUpdate?): TaskerPluginResultCondition<Unit> {
        val host = update?.host ?: return TaskerPluginResultConditionUnknown()

        var output: TaskerPluginResultCondition<Unit> = TaskerPluginResultConditionUnsatisfied()
        when(input.regular.opMode)
        {
            OperatorMode.ALL -> {
                output =  TaskerPluginResultConditionSatisfied(context, Unit)
            }
            OperatorMode.ANY -> {
                output =  TaskerPluginResultConditionSatisfied(context, Unit)
            }
        }
        return output
    }

    override val isEvent: Boolean
        get() = false // its a state
}