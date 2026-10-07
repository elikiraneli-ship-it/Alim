package com.ogrenci.isleri;

import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.*;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.webkit.WebViewAssetLoader;
import java.io.OutputStream;

public class MainActivity extends AppCompatActivity {
    private WebView web;
    private ValueCallback<Uri[]> chooser;
    private static final int PICK = 77;

    // Sayfadaki blob indirmelerini (Kaydet, Yedek Al) Android'e yönlendirir
    private static final String HOOK =
        "(function(){if(window.__hook)return;window.__hook=1;var o=HTMLAnchorElement.prototype.click;" +
        "HTMLAnchorElement.prototype.click=function(){var a=this;if(a.download&&a.href&&a.href.indexOf('blob:')===0){" +
        "fetch(a.href).then(function(r){return r.blob()}).then(function(b){var f=new FileReader();" +
        "f.onload=function(){Android.save(a.download,b.type||'application/octet-stream',String(f.result).split(',')[1])};" +
        "f.readAsDataURL(b)});return}return o.apply(this,arguments)}})();";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        web = new WebView(this);
        setContentView(web);
        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this)).build();
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setSupportZoom(true);
        web.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void save(String name, String mime, String base64) { saveFile(name, mime, base64); }
        }, "Android");
        web.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                return loader.shouldInterceptRequest(r.getUrl());
            }
            @Override public void onPageFinished(WebView v, String url) { v.evaluateJavascript(HOOK, null); }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView w, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (chooser != null) chooser.onReceiveValue(null);
                chooser = cb;
                try {
                    Intent i = p.createIntent();
                    i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                    startActivityForResult(i, PICK);
                } catch (Exception e) { chooser = null; return false; }
                return true;
            }
        });
        web.loadUrl("https://appassets.androidplatform.net/assets/index.html");
    }

    private void saveFile(String name, String mime, String base64) {
        try {
            byte[] data = Base64.decode(base64, Base64.DEFAULT);
            ContentValues v = new ContentValues();
            v.put(MediaStore.Downloads.DISPLAY_NAME, name);
            v.put(MediaStore.Downloads.MIME_TYPE, mime);
            v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            try (OutputStream o = getContentResolver().openOutputStream(uri)) { o.write(data); }
            runOnUiThread(() -> Toast.makeText(this, "İndirilenler klasörüne kaydedildi: " + name, Toast.LENGTH_LONG).show());
        } catch (Exception e) {
            runOnUiThread(() -> Toast.makeText(this, "Kaydedilemedi", Toast.LENGTH_LONG).show());
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent d) {
        super.onActivityResult(req, res, d);
        if (req != PICK || chooser == null) return;
        Uri[] r = null;
        if (res == RESULT_OK && d != null) {
            if (d.getClipData() != null) {
                int n = d.getClipData().getItemCount();
                r = new Uri[n];
                for (int i = 0; i < n; i++) r[i] = d.getClipData().getItemAt(i).getUri();
            } else if (d.getData() != null) r = new Uri[]{d.getData()};
        }
        chooser.onReceiveValue(r);
        chooser = null;
    }

    @Override
    public void onBackPressed() {
        web.evaluateJavascript("(function(){var v=document.getElementById('viewer');if(v&&v.classList.contains('show')){v.classList.remove('show');return 1}return 0})()",
            val -> { if (!"1".equals(val)) moveTaskToBack(true); });
    }
}
