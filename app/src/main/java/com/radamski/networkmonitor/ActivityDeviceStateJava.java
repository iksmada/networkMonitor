package com.radamski.networkmonitor;


import com.joaomgcd.taskerpluginlibrary.condition.TaskerPluginRunnerConditionNoOutputOrInputOrUpdateState;
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig;
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfigHelperStateNoOutputOrInputOrUpdate;
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfigNoInput;
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput;
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultCondition;
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultConditionSatisfied;
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultConditionUnsatisfied;
import android.app.Activity;
import android.content.Context;

import androidx.annotation.NonNull;

import kotlin.Unit;

public class ActivityDeviceStateJava extends Activity implements TaskerPluginConfigNoInput {

    @Override
    public Context getContext() {
        return getApplicationContext();
    }

    @Override
    public TaskerInput<Unit> getInputForTasker() {
        return null;
    }

    @Override
    public void assignFromInput(@NonNull TaskerInput<Unit> taskerInput) {

    }

    class BasicStateHelper extends TaskerPluginConfigHelperStateNoOutputOrInputOrUpdate<BasicStateRunner>
    {

        public BasicStateHelper(@NonNull TaskerPluginConfig<Unit> config) {
            super(config);
        }

        @Override
        public Class<BasicStateRunner> getRunnerClass() {
            return this.getRunnerClass();
        }
        @Override
        public void addToStringBlurb(TaskerInput<kotlin.Unit> input, StringBuilder blurbBuilder) {
            blurbBuilder.append("Will toggle between being activated and deactivated");
        }
    }

    class BasicStateRunner extends TaskerPluginRunnerConditionNoOutputOrInputOrUpdateState {

        @Override
        public TaskerPluginResultCondition<Unit> getSatisfiedCondition( Context context, TaskerInput<Unit> taskerInput, Unit unit) {
            if(isActive) {
                return new TaskerPluginResultConditionSatisfied(context, null, null);
            }
            else {
                return new TaskerPluginResultConditionUnsatisfied();
            }
        }
    }

    private Boolean isActive = false;

    public void toggleBasicState(Context context) {
        isActive = !isActive;
        //requestQuery(context, 1);
    }
}
