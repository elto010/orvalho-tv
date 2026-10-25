package app.orvalho.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

/**
 * Aplica o volume máximo permitido: se o volume subir acima do limite
 * (pelo controle remoto ou por outro app), volta para o limite.
 * Funciona enquanto o processo do launcher estiver vivo, o que é o normal
 * para a tela inicial. Não exige root nem permissão especial.
 */
final class VolumeGuard {
    private static final String PREFS = "orvalho";
    private static final String KEY_CAP = "volume_cap_pct";
    private static boolean started = false;

    private VolumeGuard() {}

    static synchronized void start(Context ctx) {
        if (started) return;
        started = true;
        final Context app = ctx.getApplicationContext();
        ContentObserver observer = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override
            public void onChange(boolean selfChange) {
                enforce(app);
            }
        };
        app.getContentResolver().registerContentObserver(Settings.System.CONTENT_URI, true, observer);
    }

    static void setCap(Context ctx, int pct) {
        int v = Math.max(0, Math.min(100, pct));
        prefs(ctx).edit().putInt(KEY_CAP, v).apply();
        enforce(ctx);
    }

    static int getCap(Context ctx) {
        return prefs(ctx).getInt(KEY_CAP, 100);
    }

    static void enforce(Context ctx) {
        int cap = getCap(ctx);
        if (cap >= 100) return;
        AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        if (am == null || am.isVolumeFixed()) return;
        int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int cur = am.getStreamVolume(AudioManager.STREAM_MUSIC);
        int capIndex = Math.round(cap * max / 100f);
        if (cur > capIndex) {
            am.setStreamVolume(AudioManager.STREAM_MUSIC, capIndex, AudioManager.FLAG_SHOW_UI);
        }
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
