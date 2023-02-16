package com.radamski.networkmonitor.state

import com.joaomgcd.taskerpluginlibrary.input.TaskerInputField
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputRoot
import com.radamski.networkmonitor.Network.HostBean
import com.radamski.networkmonitor.R


@TaskerInputRoot
class OperatorMode @JvmOverloads constructor(
    @field:TaskerInputField(OP_MODE_KEY, labelResId = R.string.operator_mode) val opMode: Int? = null
) {

    companion object {

        const val OP_MODE_KEY = "opMode"
        const val ALL = 0
        const val ANY = 1
    }
}

@TaskerInputRoot
class DeviceUpdate @JvmOverloads constructor(@field:TaskerInputField("device") var host: HostBean? = null)