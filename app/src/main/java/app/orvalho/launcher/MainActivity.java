package app.orvalho.launcher;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Tela inicial Orvalho. A interface é a mesma do protótipo aprovado (HTML/CSS/JS),
 * servida a partir do próprio APK por um endereço interno (https://orvalho.local/),
 * com uma ponte nativa para apps, entradas e volume.
 */
public class MainActivity extends Activity {
    static final String HOST = "orvalho.local";
    static final String BASE = "https://" + HOST + "/";

    private WebView web;
    private Bridge bridge;

    private final BroadcastReceiver pkgReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            Icons.clear();
            js("window.__orvalhoApps&&window.__orvalhoApps()");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        hideSystemUi();

        web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);
        web.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        web.setFocusable(true);
        web.setFocusableInTouchMode(true);
        web.setVerticalScrollBarEnabled(false);
        web.setHorizontalScrollBarEnabled(false);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setTextZoom(100);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);

        bridge = new Bridge(this);
        web.addJavascriptInterface(bridge, "OrvalhoNative");
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if (!HOST.equals(u.getHost())) return null; // clima e fontes seguem pela internet
                return serve(u.getPath());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                return !HOST.equals(req.getUrl().getHost());
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                // Se o motor da interface cair (falta de memória, por exemplo), a tela inicial se recria sozinha.
                if (view.getParent() instanceof ViewGroup) ((ViewGroup) view.getParent()).removeView(view);
                view.destroy();
                if (view == web) web = null;
                recreate();
                return true;
            }
        });

        setContentView(web);
        web.requestFocus();
        web.loadUrl(BASE + "ui/index.html");

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        registerReceiver(pkgReceiver, f);
    }

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    // ---------- arquivos da interface, ícones e banners ----------

    private WebResourceResponse serve(String path) {
        try {
            if (path == null) return notFound();
            if (path.startsWith("/icon/")) {
                byte[] png = Icons.icon(this, path.substring(6));
                return png == null ? notFound() : bytes("image/png", png);
            }
            if (path.startsWith("/banner/")) {
                byte[] png = Icons.banner(this, path.substring(8));
                return png == null ? notFound() : bytes("image/png", png);
            }
            String asset = path.substring(1);
            if (asset.isEmpty() || asset.contains("..")) return notFound();
            InputStream in = getAssets().open(asset);
            return new WebResourceResponse(mime(asset), "utf-8", 200, "OK", headers(), in);
        } catch (Exception e) {
            return notFound();
        }
    }

    private static Map<String, String> headers() {
        Map<String, String> h = new HashMap<>();
        h.put("Cache-Control", "max-age=3600");
        h.put("Access-Control-Allow-Origin", "*");
        return h;
    }

    private static WebResourceResponse bytes(String mime, byte[] data) {
        return new WebResourceResponse(mime, null, 200, "OK", headers(), new ByteArrayInputStream(data));
    }

    private static WebResourceResponse notFound() {
        return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found", new HashMap<>(),
                new ByteArrayInputStream(new byte[0]));
    }

    private static String mime(String p) {
        String s = p.toLowerCase();
        if (s.endsWith(".html")) return "text/html";
        if (s.endsWith(".js")) return "text/javascript";
        if (s.endsWith(".css")) return "text/css";
        if (s.endsWith(".png")) return "image/png";
        if (s.endsWith(".jpg") || s.endsWith(".jpeg")) return "image/jpeg";
        if (s.endsWith(".webp")) return "image/webp";
        if (s.endsWith(".svg")) return "image/svg+xml";
        if (s.endsWith(".json")) return "application/json";
        if (s.endsWith(".woff2")) return "font/woff2";
        return "application/octet-stream";
    }

    // ---------- controle remoto ----------

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        String key = null;
        switch (e.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_UP:
                key = "ArrowUp";
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                key = "ArrowDown";
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                key = "ArrowLeft";
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                key = "ArrowRight";
                break;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                key = "Enter";
                break;
            case KeyEvent.KEYCODE_BACK:
                key = "Escape";
                break;
            case KeyEvent.KEYCODE_MENU:
                key = "m";
                break;
            default:
                break;
        }
        // Enquanto o teclado da tela estiver aberto na busca, só o Voltar vem para a interface.
        if (key != null && bridge != null && bridge.isTyping() && !"Escape".equals(key)) key = null;
        if (key == null) return super.dispatchKeyEvent(e);
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            js("window.__k&&window.__k('" + key + "','keydown'," + (e.getRepeatCount() > 0) + ")");
        } else if (e.getAction() == KeyEvent.ACTION_UP) {
            js("window.__k&&window.__k('" + key + "','keyup',false)");
        }
        return true; // a tela inicial nunca fecha com o Voltar
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Botão Home com o launcher já aberto: volta ao Início.
        if (intent != null && intent.hasCategory(Intent.CATEGORY_HOME)) {
            js("window.__orvalhoHome&&window.__orvalhoHome()");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUi();
        js("window.__orvalhoResume&&window.__orvalhoResume()");
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        // Libera os ícones guardados quando a TV pede memória; são refeitos quando necessário.
        if (level >= TRIM_MEMORY_RUNNING_LOW) Icons.clear();
    }

    @Override
    protected void onDestroy() {
        try {
            unregisterReceiver(pkgReceiver);
        } catch (Exception ignored) {
        }
        if (web != null) web.destroy();
        super.onDestroy();
    }

    private void js(final String code) {
        if (web == null) return;
        runOnUiThread(() -> web.evaluateJavascript(code, null));
    }
}
