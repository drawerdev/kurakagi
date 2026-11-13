package io.github.drawerdev.kurakagi.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import io.github.drawerdev.kurakagi.net.SntpClient;

/**
 * 端末時刻のオフセット管理および NTP 自動同期マネージャー。
 * オフライン端末の時計ズレをアプリ内で透過的に補正する。
 */
public final class TimeSyncManager {

    private static final String PREF_NAME = "kurakagi_time_sync";
    private static final String KEY_OFFSET_MS = "offset_ms";
    private static final String DEFAULT_NTP_HOST = "pool.ntp.org";
    private static final int NTP_TIMEOUT_MS = 5000;

    private static TimeSyncManager sInstance;

    private final SharedPreferences prefs;
    private long timeOffsetMs;
    private final Handler mainHandler;

    public interface SyncCallback {
        void onSuccess(long offsetMs);
        void onError(String errorMessage);
    }

    private TimeSyncManager(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.timeOffsetMs = prefs.getLong(KEY_OFFSET_MS, 0L);
        this.mainHandler = new Handler(Looper.getMainLooper());
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

    /**
     * SNTP サーバーと非同期通信し、時刻オフセットを補正する
     */
    public void syncWithNtp(final String hostOrNull, final SyncCallback callback) {
        final String targetHost = (hostOrNull != null && hostOrNull.length() > 0) ? hostOrNull : DEFAULT_NTP_HOST;
        new Thread(new NtpTask(this, targetHost, callback)).start();
    }

    private static class NtpTask implements Runnable {
        private final TimeSyncManager manager;
        private final String targetHost;
        private final SyncCallback callback;

        public NtpTask(TimeSyncManager manager, String targetHost, SyncCallback callback) {
            this.manager = manager;
            this.targetHost = targetHost;
            this.callback = callback;
        }

        @Override
        public void run() {
            try {
                final long ntpOffset = SntpClient.requestTimeOffset(targetHost, NTP_TIMEOUT_MS);
                synchronized (manager) {
                    manager.timeOffsetMs = ntpOffset;
                    manager.prefs.edit().putLong(KEY_OFFSET_MS, manager.timeOffsetMs).commit();
                }
                if (callback != null) {
                    manager.mainHandler.post(new SuccessNotifier(callback, ntpOffset));
                }
            } catch (final Exception e) {
                if (callback != null) {
                    String msg = e.getMessage();
                    if (msg == null || msg.length() == 0) {
                        msg = e.getClass().getSimpleName();
                    }
                    manager.mainHandler.post(new ErrorNotifier(callback, msg));
                }
            }
        }
    }

    private static class SuccessNotifier implements Runnable {
        private final SyncCallback callback;
        private final long offset;

        public SuccessNotifier(SyncCallback callback, long offset) {
            this.callback = callback;
            this.offset = offset;
        }

        @Override
        public void run() {
            callback.onSuccess(offset);
        }
    }

    private static class ErrorNotifier implements Runnable {
        private final SyncCallback callback;
        private final String errorMessage;

        public ErrorNotifier(SyncCallback callback, String errorMessage) {
            this.callback = callback;
            this.errorMessage = errorMessage;
        }

        @Override
        public void run() {
            callback.onError(errorMessage);
        }
    }
}
