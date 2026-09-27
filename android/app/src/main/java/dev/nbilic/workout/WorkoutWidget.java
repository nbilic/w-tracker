package dev.nbilic.workout;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.RemoteViews;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Home-screen widget: what's next (or the workout in progress), this week's training days, and a
 * button that opens straight into it. The web app pushes the data (WorkoutNativePlugin.widget);
 * the week strip is computed here so "today" stays right between app launches.
 *
 * Data: { eyebrow, title, meta, actionLabel, actionUrl, dates: ["2026-09-21", ...] }
 */
public class WorkoutWidget extends AppWidgetProvider {

    private static final String PREFS = "workout_widget";
    private static final String KEY = "data";
    private static final int[] DAY_IDS = { R.id.w_d0, R.id.w_d1, R.id.w_d2, R.id.w_d3, R.id.w_d4, R.id.w_d5, R.id.w_d6 };
    private static final String[] DAY_LETTERS = { "M", "T", "W", "T", "F", "S", "S" };
    private static final int INK = 0xFFF5F1EC, INK_3 = 0xFF85807A, INK_4 = 0xFF5C5851, ACCENT = 0xFFE97A52;

    static void save(Context ctx, String json) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, json).apply();
        updateAll(ctx);
    }

    static void updateAll(Context ctx) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, WorkoutWidget.class));
        if (ids.length == 0) return;
        RemoteViews views = build(ctx);
        for (int id : ids) mgr.updateAppWidget(id, views);
    }

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        RemoteViews views = build(ctx);
        for (int id : ids) mgr.updateAppWidget(id, views);
    }

    private static RemoteViews build(Context ctx) {
        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget_workout);
        JSONObject d;
        try {
            d = new JSONObject(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "{}"));
        } catch (Exception e) {
            d = new JSONObject();
        }

        v.setTextViewText(R.id.w_eyebrow, d.optString("eyebrow", "WORKOUT"));
        v.setTextViewText(R.id.w_title, d.optString("title", "Open the app"));
        v.setTextViewText(R.id.w_meta, d.optString("meta", ""));
        String actionLabel = d.optString("actionLabel", "Open");
        v.setTextViewText(R.id.w_action, actionLabel);

        // This week, Monday first; days you trained are highlighted, today is bold.
        Set<String> dates = new HashSet<>();
        JSONArray arr = d.optJSONArray("dates");
        if (arr != null) for (int i = 0; i < arr.length(); i++) dates.add(arr.optString(i));
        Calendar day = Calendar.getInstance();
        int todayIdx = (day.get(Calendar.DAY_OF_WEEK) + 5) % 7; // Monday = 0
        day.add(Calendar.DAY_OF_MONTH, -todayIdx);
        for (int i = 0; i < 7; i++) {
            String iso = String.format(Locale.US, "%04d-%02d-%02d", day.get(Calendar.YEAR), day.get(Calendar.MONTH) + 1, day.get(Calendar.DAY_OF_MONTH));
            boolean done = dates.contains(iso);
            v.setTextViewText(DAY_IDS[i], i == todayIdx ? "•" + DAY_LETTERS[i] + "•" : DAY_LETTERS[i]);
            v.setTextColor(DAY_IDS[i], done ? ACCENT : (i > todayIdx ? INK_4 : INK_3));
            v.setInt(DAY_IDS[i], "setBackgroundResource", done ? R.drawable.widget_day_done : 0);
            day.add(Calendar.DAY_OF_MONTH, 1);
        }

        // Whole widget opens the app; the button goes straight to the action.
        Intent open = new Intent(ctx, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        v.setOnClickPendingIntent(R.id.w_root, PendingIntent.getActivity(ctx, 10, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        String url = d.optString("actionUrl", "");
        Intent act = url.isEmpty() ? open : new Intent(Intent.ACTION_VIEW, Uri.parse(url)).setClass(ctx, MainActivity.class)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        v.setOnClickPendingIntent(R.id.w_action, PendingIntent.getActivity(ctx, 11, act, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        return v;
    }
}
