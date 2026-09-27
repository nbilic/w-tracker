package dev.nbilic.workout;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** +30 / Skip buttons on the workout notification. */
public class RestActionReceiver extends BroadcastReceiver {

    static final String ACTION = "dev.nbilic.workout.REST_ACTION";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getStringExtra("action");
        if (action == null) return;
        if (WorkoutService.instance != null) WorkoutService.instance.applyAction(action);
        WorkoutNativePlugin.emitRestAction(action);
    }
}
