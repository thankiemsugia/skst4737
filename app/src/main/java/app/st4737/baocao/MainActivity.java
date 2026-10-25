package app.st4737.baocao;

import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.webkit.WebViewAssetLoader;

import java.io.IOException;
import java.io.OutputStream;

public class MainActivity extends AppCompatActivity {

    private static final String HOST = "appassets.androidplatform.net";
    private static final String START_URL = "https://" + HOST + "/assets/index.html";
    private static final String SAVE_FOLDER = "BCKD4737";

    // Chặn thao tác tải file (blob:/data: + thuộc tính download) của trang và chuyển cho app lưu vào máy.
    private static final String HOOK_JS =
            "(function(){if(window.__apkHook)return;window.__apkHook=true;"
            + "function save(a){var name=a.getAttribute('download')||'file';"
            + "fetch(a.href).then(function(r){return r.blob();}).then(function(b){"
            + "var fr=new FileReader();fr.onloadend=function(){var s=String(fr.result);"
            + "AndroidBridge.saveFile(name,b.type||'application/octet-stream',s.substring(s.indexOf(',')+1));};"
            + "fr.readAsDataURL(b);}).catch(function(e){AndroidBridge.fail(String(e));});}"
            + "var orig=HTMLAnchorElement.prototype.click;"
            + "HTMLAnchorElement.prototype.click=function(){"
            + "if(this.hasAttribute('download')&&/^(blob|data):/.test(this.href)){save(this);return;}"
            + "return orig.apply(this,arguments);};"
            + "document.addEventListener('click',function(e){"
            + "var a=e.target&&e.target.closest?e.target.closest('a[download]'):null;"
            + "if(a&&/^(blob|data):/.test(a.href)){e.preventDefault();save(a);}},true);"
            + "})();";

    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;

    private final ActivityResultLauncher<Intent> fileChooser = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (filePathCallback != null) {
                    Uri[] uris = WebChromeClient.FileChooserParams.parseResult(
                            result.getResultCode(), result.getData());
                    filePathCallback.onReceiveValue(uris);
                    filePathCallback = null;
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        setContentView(webView);

        final WebViewAssetLoader assetLoader = new WebViewAssetLoader.Builder()
                .setDomain(HOST)
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);          // localStorage
        s.setAllowFileAccess(false);
        s.setTextZoom(100);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        webView.addJavascriptInterface(new Bridge(), "AndroidBridge");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if (HOST.equals(u.getHost())) return false;
                String scheme = u.getScheme() == null ? "" : u.getScheme();
                if (scheme.equals("blob") || scheme.equals("data")
                        || scheme.equals("about") || scheme.equals("javascript")) {
                    return false;
                }
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u));   // link ngoài → mở bằng trình duyệt
                } catch (ActivityNotFoundException ignored) { }
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                view.evaluateJavascript(HOOK_JS, null);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                view.evaluateJavascript(HOOK_JS, null);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = callback;
                try {
                    fileChooser.launch(params.createIntent());
                } catch (Exception e) {
                    filePathCallback = null;
                    callback.onReceiveValue(null);
                }
                return true;
            }
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else {
            webView.loadUrl(START_URL);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }

    private void toast(final String msg) {
        runOnUiThread(() -> Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show());
    }

    private class Bridge {
        @JavascriptInterface
        public void fail(String message) {
            toast("Không lưu được file: " + message);
        }

        @JavascriptInterface
        public void saveFile(final String rawName, final String mime, final String base64) {
            new Thread(() -> {
                try {
                    String name = rawName.replaceAll("[\\\\/:*?\"<>|]", "_");
                    byte[] data = Base64.decode(base64, Base64.DEFAULT);
                    boolean isImage = mime.startsWith("image/");

                    ContentResolver cr = getContentResolver();
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                    v.put(MediaStore.MediaColumns.MIME_TYPE, mime);
                    v.put(MediaStore.MediaColumns.IS_PENDING, 1);

                    Uri collection;
                    String where;
                    if (isImage) {
                        v.put(MediaStore.MediaColumns.RELATIVE_PATH,
                                Environment.DIRECTORY_PICTURES + "/" + SAVE_FOLDER);
                        collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
                        where = "Pictures/" + SAVE_FOLDER;
                    } else {
                        v.put(MediaStore.MediaColumns.RELATIVE_PATH,
                                Environment.DIRECTORY_DOWNLOADS + "/" + SAVE_FOLDER);
                        collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
                        where = "Download/" + SAVE_FOLDER;
                    }

                    Uri uri = cr.insert(collection, v);
                    if (uri == null) throw new IOException("Không tạo được file");
                    try (OutputStream os = cr.openOutputStream(uri)) {
                        if (os == null) throw new IOException("Không mở được file");
                        os.write(data);
                    }
                    ContentValues done = new ContentValues();
                    done.put(MediaStore.MediaColumns.IS_PENDING, 0);
                    cr.update(uri, done, null, null);

                    toast("Đã lưu: " + where + "/" + name);
                } catch (Exception e) {
                    toast("Không lưu được file: " + e.getMessage());
                }
            }).start();
        }
    }
}
