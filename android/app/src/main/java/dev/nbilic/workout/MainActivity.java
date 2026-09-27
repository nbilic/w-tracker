package dev.nbilic.workout;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        // App-local plugins must be registered before the bridge starts.
        registerPlugin(ApkInstallerPlugin.class);
        registerPlugin(WorkoutNativePlugin.class);
        super.onCreate(savedInstanceState);
    }
}
