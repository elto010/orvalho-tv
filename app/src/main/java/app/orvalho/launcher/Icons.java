package app.orvalho.launcher;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Ícones e banners oficiais de cada app, entregues pelo próprio Android. */
final class Icons {
    private static final Map<String, byte[]> CACHE = new HashMap<>();
    private static final Map<String, String[]> COLORS = new HashMap<>();

    private Icons() {}

    static byte[] icon(Context ctx, String pkg) {
        String key = "i:" + pkg;
        synchronized (CACHE) {
            if (CACHE.containsKey(key)) return CACHE.get(key);
        }
        byte[] png = null;
        try {
            Drawable d = ctx.getPackageManager().getApplicationIcon(pkg);
            png = toPng(d, 256, 256);
        } catch (Exception ignored) {
        }
        synchronized (CACHE) {
            CACHE.put(key, png);
        }
        return png;
    }

    static byte[] banner(Context ctx, String pkg) {
        String key = "b:" + pkg;
        synchronized (CACHE) {
            if (CACHE.containsKey(key)) return CACHE.get(key);
        }
        byte[] png = null;
        try {
            PackageManager pm = ctx.getPackageManager();
            Drawable d = null;
            Intent li = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (li != null) {
                try {
                    d = pm.getActivityBanner(li);
                } catch (Exception ignored) {
                }
            }
            if (d == null) {
                ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                d = ai.loadBanner(pm);
            }
            if (d != null) png = toPng(d, 640, 360);
        } catch (Exception ignored) {
        }
        synchronized (CACHE) {
            CACHE.put(key, png);
        }
        return png;
    }

    /** Cor de destaque tirada do ícone: [clara, escura] em hexadecimal. */
    static String[] colors(Context ctx, String pkg) {
        synchronized (COLORS) {
            if (COLORS.containsKey(pkg)) return COLORS.get(pkg);
        }
        String[] out = {"#4a72ff", "#141b66"};
        try {
            Drawable d = ctx.getPackageManager().getApplicationIcon(pkg);
            Bitmap b = toBitmap(d, 32, 32);
            long r = 0, g = 0, bl = 0, n = 0;
            for (int y = 0; y < 32; y++) {
                for (int x = 0; x < 32; x++) {
                    int c = b.getPixel(x, y);
                    if (Color.alpha(c) < 128) continue;
                    float[] hsv = new float[3];
                    Color.colorToHSV(c, hsv);
                    int w = hsv[1] > 0.25f && hsv[2] > 0.2f ? 4 : 1; // pesa mais as cores vivas
                    r += (long) Color.red(c) * w;
                    g += (long) Color.green(c) * w;
                    bl += (long) Color.blue(c) * w;
                    n += w;
                }
            }
            if (n > 0) {
                int avg = Color.rgb((int) (r / n), (int) (g / n), (int) (bl / n));
                float[] hsv = new float[3];
                Color.colorToHSV(avg, hsv);
                hsv[1] = Math.min(1f, Math.max(0.35f, hsv[1] * 1.25f));
                float[] light = {hsv[0], hsv[1], 0.88f};
                float[] dark = {hsv[0], Math.min(1f, hsv[1] * 1.1f), 0.32f};
                out = new String[]{hex(Color.HSVToColor(light)), hex(Color.HSVToColor(dark))};
            }
        } catch (Exception ignored) {
        }
        synchronized (COLORS) {
            COLORS.put(pkg, out);
        }
        return out;
    }

    static void warm(Context ctx) {
        try {
            PackageManager pm = ctx.getPackageManager();
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
            for (android.content.pm.ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
                colors(ctx, ri.activityInfo.packageName);
            }
        } catch (Exception ignored) {
        }
    }

    static void clear() {
        synchronized (CACHE) {
            CACHE.clear();
        }
        synchronized (COLORS) {
            COLORS.clear();
        }
    }

    private static String hex(int c) {
        return String.format(Locale.US, "#%06x", c & 0xFFFFFF);
    }

    private static Bitmap toBitmap(Drawable d, int w, int h) {
        if (d instanceof BitmapDrawable) {
            Bitmap src = ((BitmapDrawable) d).getBitmap();
            if (src != null) return Bitmap.createScaledBitmap(src, w, h, true);
        }
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        d.setBounds(0, 0, w, h);
        d.draw(cv);
        return b;
    }

    private static byte[] toPng(Drawable d, int w, int h) {
        Bitmap b = toBitmap(d, w, h);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        b.compress(Bitmap.CompressFormat.PNG, 100, out);
        return out.toByteArray();
    }
}
