package dev.nbilic.workout;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.RemoteViews;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

/**
 * Keeps a live notification up while a workout is running: elapsed time, or the rest countdown with
 * +30 / Skip. Being a foreground service also stops Android from killing the app mid-workout.
 * State comes from the web app via WorkoutNativePlugin; the buttons go back to it through RestActionReceiver.
 */
public class WorkoutService extends Service {

    static final String CHANNEL_ID = "workout_live";
    static final int NOTIFICATION_ID = 4201;
    static final String EXTRA_TITLE = "title", EXTRA_STARTED = "startedAt", EXTRA_REST_ENDS = "restEndsAt";

    static WorkoutService instance;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable restEnded = () -> { restEndsAt = 0; post(); };
    private String title = "Workout";
    private long startedAt = System.currentTimeMillis();
    private long restEndsAt = 0;

    static void createChannel(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Workout in progress", NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("Elapsed time and rest countdown while you train");
        ch.setShowBadge(false);
        ch.setSound(null, null);
        ch.enableVibration(false);
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        ctx.getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        createChannel(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            title = orDefault(intent.getStringExtra(EXTRA_TITLE), "Workout");
            startedAt = intent.getLongExtra(EXTRA_STARTED, System.currentTimeMillis());
            restEndsAt = intent.getLongExtra(EXTRA_REST_ENDS, 0);
        }
        Notification n = build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, n);
        }
        scheduleRestEnd();
        return START_NOT_STICKY;
    }

    /** New state from the app while the service is running. */
    void apply(String title, long startedAt, long restEndsAt) {
        this.title = orDefault(title, "Workout");
        this.startedAt = startedAt;
        this.restEndsAt = restEndsAt;
        scheduleRestEnd();
        post();
    }

    /** +30 / Skip from the notification: update it right away; the app syncs its own state too. */
    void applyAction(String action) {
        if ("add30".equals(action) && restEndsAt > System.currentTimeMillis()) restEndsAt += 30_000;
        else if ("skip".equals(action)) restEndsAt = 0;
        scheduleRestEnd();
        post();
    }

    void stopNow() {
        handler.removeCallbacksAndMessages(null);
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void scheduleRestEnd() {
        handler.removeCallbacks(restEnded);
        long left = restEndsAt - System.currentTimeMillis();
        if (left > 0) handler.postDelayed(restEnded, left);
        else restEndsAt = 0;
    }

    private void post() {
        try {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, build());
        } catch (SecurityException e) {
            // Notifications not allowed: the service still keeps the workout alive.
        }
    }

    private Notification build() {
        Intent open = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (open == null) open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_workout)
            .setColor(0xFFE97A52)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(contentIntent);

        long left = restEndsAt - System.currentTimeMillis();
        if (left > 0) {
            // Big countdown in a custom view; the Chronometer runs on the elapsed-realtime clock.
            RemoteViews rest = new RemoteViews(getPackageName(), R.layout.notif_rest);
            rest.setChronometer(R.id.n_timer, SystemClock.elapsedRealtime() + left, null, true);
            rest.setChronometerCountDown(R.id.n_timer, true);
            b.setStyle(new NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(rest)
                .setCustomBigContentView(rest)
                .setContentTitle("Rest")
                .setShowWhen(false)
                .setUsesChronometer(false)
                .addAction(0, "+30s", actionIntent("add30", 1))
                .addAction(0, "Skip rest", actionIntent("skip", 2));
        } else {
            b.setContentTitle(title)
                .setContentText("Workout in progress")
                .setWhen(startedAt)
                .setChronometerCountDown(false);
        }
        return b.build();
    }

    private PendingIntent actionIntent(String action, int requestCode) {
        Intent i = new Intent(this, RestActionReceiver.class).setAction(RestActionReceiver.ACTION).putExtra("action", action);
        return PendingIntent.getBroadcast(this, requestCode, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static String orDefault(String s, String d) { return s == null || s.isEmpty() ? d : s; }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (instance == this) instance = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
