package com.radamski.networkmonitor.state

import com.joaomgcd.taskerpluginlibrary.input.TaskerInputField
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputObject
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputRoot
import com.radamski.networkmonitor.Network.HostBean
import com.radamski.networkmonitor.R


@TaskerInputRoot
class DetectionMode @JvmOverloads constructor(
    @field:TaskerInputField(OPERATOR_KEY, labelResId = R.string.blank) val operator: Int? = null,
    @field:TaskerInputField(STATE_KEY, labelResId = R.string.blank) val state: Int? = null,
) {

    companion object {
        const val OPERATOR_KEY = "operator"
        const val STATE_KEY = "state"
        const val ALL = 0
        const val ANY = 1
        const val ON = 2
        const val OFF = 3
    }
}

@TaskerInputRoot
class DeviceUpdate @JvmOverloads constructor(
    @field:TaskerInputObject("device") var host: HostBean = HostBean(),
    //@field:TaskerInputField("device") var host: String,
    @field:TaskerInputField("isConnected") var isConnected: Boolean? = null,
    @field:TaskerInputField("premature") var premature: Boolean? = false,
)