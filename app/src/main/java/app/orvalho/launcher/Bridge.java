package app.orvalho.launcher;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.AudioManager;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Ponte entre a interface (HTML/JS) e o Android.
 * Só usa APIs públicas, sem root e sem permissões especiais.
 */
final class Bridge {
    private final Activity act;
    private volatile boolean typing = false;

    Bridge(Activity act) {
        this.act = act;
    }

    boolean isTyping() {
        return typing;
    }

    // ---------- apps ----------

    @JavascriptInterface
    public String apps() {
        PackageManager pm = act.getPackageManager();
        Map<String, JSONObject> out = new LinkedHashMap<>();
        try {
            collect(pm, out, Intent.CATEGORY_LEANBACK_LAUNCHER, true);
            collect(pm, out, Intent.CATEGORY_LAUNCHER, false);
        } catch (Exception ignored) {
        }
        JSONArray arr = new JSONArray();
        for (JSONObject o : out.values()) arr.put(o);
        return arr.toString();
    }

    private void collect(PackageManager pm, Map<String, JSONObject> out, String category, boolean tv) throws Exception {
        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(category);
        List<ResolveInfo> list = pm.queryIntentActivities(q, 0);
        for (ResolveInfo ri : list) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(act.getPackageName()) || out.containsKey(pkg)) continue;
            ApplicationInfo ai = ri.activityInfo.applicationInfo;
            String[] c = Icons.colors(act, pkg);
            JSONObject o = new JSONObject();
            o.put("pkg", pkg);
            o.put("name", String.valueOf(ri.loadLabel(pm)));
            o.put("cat", category(ai, pkg));
            o.put("tv", tv);
            o.put("sys", (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0);
            o.put("c1", c[0]);
            o.put("c2", c[1]);
            out.put(pkg, o);
        }
    }

    private static String category(ApplicationInfo ai, String pkg) {
        switch (ai.category) {
            case ApplicationInfo.CATEGORY_GAME:
                return "game";
            case ApplicationInfo.CATEGORY_AUDIO:
                return "music";
            case ApplicationInfo.CATEGORY_VIDEO:
                return "video";
            default:
                break;
        }
        String p = pkg.toLowerCase(Locale.ROOT);
        if (p.contains("music") || p.contains("spotify") || p.contains("deezer") || p.contains("radio")) return "music";
        String[] video = {"netflix", "youtube", "amazonvideo", "primevideo", "disney", "hbo", "wbd", "globo",
                "cbs", "paramount", "pluto", "crunchyroll", "twitch", "starplus", "apple.atve", "mubi", "vix", "tubi"};
        for (String v : video) if (p.contains(v)) return "video";
        if ((ai.flags & ApplicationInfo.FLAG_IS_GAME) != 0) return "game";
        return "tool";
    }

    @JavascriptInterface
    public boolean launch(String pkg) {
        PackageManager pm = act.getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        if (i == null) return false;
        final Intent intent = i;
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        act.runOnUiThread(() -> {
            try {
                act.startActivity(intent);
            } catch (Exception ignored) {
            }
        });
        return true;
    }

    @JavascriptInterface
    public void tvSettings() {
        act.runOnUiThread(() -> {
            try {
                act.startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ignored) {
            }
        });
    }

    // ---------- entradas da TV ----------

    @JavascriptInterface
    public String inputs() {
        JSONArray arr = new JSONArray();
        try {
            TvInputManager tim = (TvInputManager) act.getSystemService(Context.TV_INPUT_SERVICE);
            if (tim == null) return "[]";
            for (TvInputInfo info : tim.getTvInputList()) {
                if (info.isHidden(act)) continue;
                CharSequence custom = info.loadCustomLabel(act);
                String name = custom != null && custom.length() > 0 ? custom.toString() : String.valueOf(info.loadLabel(act));
                String type;
                switch (info.getType()) {
                    case TvInputInfo.TYPE_TUNER:
                        type = "tuner";
                        break;
                    case TvInputInfo.TYPE_HDMI:
                        type = "hdmi";
                        break;
                    default:
                        type = "other";
                }
                JSONObject o = new JSONObject();
                o.put("id", info.getId());
                o.put("name", name);
                o.put("type", type);
                o.put("passthrough", info.isPassthroughInput());
                arr.put(o);
            }
        } catch (Exception ignored) {
        }
        return arr.toString();
    }

    @JavascriptInterface
    public boolean switchInput(String id) {
        try {
            TvInputManager tim = (TvInputManager) act.getSystemService(Context.TV_INPUT_SERVICE);
            if (tim == null) return false;
            TvInputInfo info = tim.getTvInputInfo(id);
            if (info == null) return false;
            Uri uri = info.isPassthroughInput()
                    ? TvContract.buildChannelUriForPassthroughInput(id)
                    : TvContract.buildChannelsUriForInput(id);
            Intent i = new Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (i.resolveActivity(act.getPackageManager()) == null) {
                // Plano B: abre o app de TV que fornece essa entrada.
                String pkg = info.getServiceInfo().packageName;
                Intent li = act.getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
                if (li == null) return false;
                i = li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            final Intent intent = i;
            act.runOnUiThread(() -> {
                try {
                    act.startActivity(intent);
                } catch (ActivityNotFoundException ignored) {
                }
            });
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ---------- volume ----------

    private AudioManager audio() {
        return (AudioManager) act.getSystemService(Context.AUDIO_SERVICE);
    }

    @JavascriptInterface
    public String volume() {
        try {
            AudioManager am = audio();
            int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int cur = am.getStreamVolume(AudioManager.STREAM_MUSIC);
            JSONObject o = new JSONObject();
            o.put("v", max > 0 ? Math.round(cur * 100f / max) : 0);
            o.put("max", max);
            o.put("mute", am.isStreamMute(AudioManager.STREAM_MUSIC));
            o.put("fixed", am.isVolumeFixed());
            return o.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    @JavascriptInterface
    public void setVolume(int pct) {
        try {
            AudioManager am = audio();
            int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int idx = Math.round(Math.max(0, Math.min(100, pct)) * max / 100f);
            am.setStreamVolume(AudioManager.STREAM_MUSIC, idx, 0);
        } catch (Exception ignored) {
        }
    }

    @JavascriptInterface
    public void setMute(boolean mute) {
        try {
            audio().adjustStreamVolume(AudioManager.STREAM_MUSIC,
                    mute ? AudioManager.ADJUST_MUTE : AudioManager.ADJUST_UNMUTE, 0);
        } catch (Exception ignored) {
        }
    }

    @JavascriptInterface
    public void setCap(int pct) {
        VolumeGuard.setCap(act, pct);
    }

    // ---------- diversos ----------

    @JavascriptInterface
    public void setTyping(boolean t) {
        typing = t;
    }

    @JavascriptInterface
    public String info() {
        try {
            JSONObject o = new JSONObject();
            o.put("model", Build.MODEL);
            o.put("android", Build.VERSION.RELEASE);
            o.put("sdk", Build.VERSION.SDK_INT);
            o.put("build", Build.DISPLAY);
            android.content.pm.PackageInfo wv = WebView.getCurrentWebViewPackage();
            o.put("webview", wv != null ? wv.versionName : "?");
            o.put("isHome", isDefaultHome());
            o.put("version", act.getPackageManager().getPackageInfo(act.getPackageName(), 0).versionName);
            return o.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    private boolean isDefaultHome() {
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo ri = act.getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
        return ri != null && ri.activityInfo != null && act.getPackageName().equals(ri.activityInfo.packageName);
    }
}
