package dev.nbilic.workout;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import androidx.core.content.ContextCompat;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;

/**
 * Bridge from the web app to Android: the live workout notification (WorkoutService),
 * launcher shortcuts, and the home-screen widget's data.
 */
@CapacitorPlugin(name = "WorkoutNative")
public class WorkoutNativePlugin extends Plugin {

    private static WorkoutNativePlugin instance;
    private String lastShortcuts = "";

    @Override
    public void load() {
        instance = this;
        WorkoutService.createChannel(getContext());
    }

    /** Called from RestActionReceiver: tells the app a notification button was tapped. */
    static void emitRestAction(String action) {
        if (instance == null) return;
        JSObject ev = new JSObject();
        ev.put("action", action);
        instance.notifyListeners("restAction", ev);
    }

    /** { active, title, startedAt, restEndsAt }: shows, updates or removes the live notification. */
    @PluginMethod
    public void session(PluginCall call) {
        Context ctx = getContext();
        boolean active = Boolean.TRUE.equals(call.getBoolean("active", false));
        if (!active) {
            if (WorkoutService.instance != null) WorkoutService.instance.stopNow();
            call.resolve();
            return;
        }
        String title = call.getString("title", "Workout");
        long startedAt = call.getLong("startedAt", System.currentTimeMillis());
        long restEndsAt = call.getLong("restEndsAt", 0L);
        if (WorkoutService.instance != null) {
            WorkoutService.instance.apply(title, startedAt, restEndsAt);
        } else {
            Intent i = new Intent(ctx, WorkoutService.class)
                .putExtra(WorkoutService.EXTRA_TITLE, title)
                .putExtra(WorkoutService.EXTRA_STARTED, startedAt)
                .putExtra(WorkoutService.EXTRA_REST_ENDS, restEndsAt);
            try {
                ContextCompat.startForegroundService(ctx, i);
            } catch (Exception e) {
                // Android refuses to start it from the background; the next sync in the foreground will.
                call.reject("Couldn't start the workout notification: " + e.getMessage());
                return;
            }
        }
        call.resolve();
    }

    /** { items: [{ id, label, url }] }: the long-press shortcuts on the app icon. */
    @PluginMethod
    public void shortcuts(PluginCall call) {
        JSArray items = call.getArray("items", new JSArray());
        String key = items.toString();
        if (key.equals(lastShortcuts)) { call.resolve(); return; } // the launcher rate-limits updates
        Context ctx = getContext();
        List<ShortcutInfoCompat> list = new ArrayList<>();
        try {
            for (int i = 0; i < items.length() && i < 4; i++) {
                JSONObject it = items.getJSONObject(i);
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(it.getString("url"))).setClass(ctx, MainActivity.class);
                String label = it.getString("label");
                list.add(new ShortcutInfoCompat.Builder(ctx, it.getString("id"))
                    .setShortLabel(label.length() > 24 ? label.substring(0, 24) : label)
                    .setLongLabel(label)
                    .setIcon(IconCompat.createWithResource(ctx, iconFor(it.optString("icon"))))
                    .setIntent(intent)
                    .setRank(i)
                    .build());
            }
            ShortcutManagerCompat.setDynamicShortcuts(ctx, list);
            lastShortcuts = key;
            call.resolve();
        } catch (Exception e) {
            call.reject("Couldn't update shortcuts: " + e.getMessage());
        }
    }

    private static int iconFor(String name) {
        if ("continue".equals(name)) return R.drawable.ic_shortcut_continue;
        if ("add".equals(name)) return R.drawable.ic_shortcut_add;
        return R.drawable.ic_shortcut_start;
    }

    /** Widget data (see WorkoutWidget for the fields); stored and pushed to any placed widgets. */
    @PluginMethod
    public void widget(PluginCall call) {
        WorkoutWidget.save(getContext(), call.getData().toString());
        call.resolve();
    }
}
