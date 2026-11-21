package io.github.drawerdev.kurakagi.core;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 端末時刻のオフセット管理（手の補正）。
 * オフライン端末の時計ズレをアプリ内で透過的に補正する。
 */
public final class TimeSyncManager {

    private static final String PREF_NAME = "kurakagi_time_sync";
    private static final String KEY_OFFSET_MS = "offset_ms";

    private static TimeSyncManager sInstance;

    private final SharedPreferences prefs;
    private long timeOffsetMs;

    private TimeSyncManager(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.timeOffsetMs = prefs.getLong(KEY_OFFSET_MS, 0L);
    }

    public static synchronized TimeSyncManager getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new TimeSyncManager(context);
        }
        return sInstance;
    }

    /**
     * 現在の補正済みエポックミリ秒を返す
     */
    public long getCurrentTimeMillis() {
        return System.currentTimeMillis() + timeOffsetMs;
    }

    public long getTimeOffsetMs() {
        return timeOffsetMs;
    }

    public int getTimeOffsetSeconds() {
        return (int) (timeOffsetMs / 1000L);
    }

    public synchronized void setTimeOffsetSeconds(int seconds) {
        this.timeOffsetMs = (long) seconds * 1000L;
        prefs.edit().putLong(KEY_OFFSET_MS, timeOffsetMs).commit();
    }

    public synchronized void addTimeOffsetSeconds(int deltaSeconds) {
        this.timeOffsetMs += (long) deltaSeconds * 1000L;
        prefs.edit().putLong(KEY_OFFSET_MS, timeOffsetMs).commit();
    }

    public synchronized void resetOffset() {
        this.timeOffsetMs = 0L;
        prefs.edit().putLong(KEY_OFFSET_MS, 0L).commit();
    }
}
