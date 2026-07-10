package com.geogva.kalmartroubleshooting;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int PLATFORM_PICKER_REQUEST = 1101;
    private static final int WEB_FILE_CHOOSER_REQUEST = 1102;
    private static final String LOCAL_ORIGIN = "https://appassets.androidplatform.net";

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private WebView webView;
    private LinearLayout setupPanel;
    private ProgressBar setupProgress;
    private TextView setupStatus;
    private ValueCallback<Uri[]> webFileCallback;
    private Uri pendingCameraUri;
    private File platformFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        platformFile = new File(new File(getFilesDir(), "platform"), "index.html");
        buildRootView();
        configureWebView();

        Uri incomingPlatform = getIntent() == null ? null : getIntent().getData();
        if (incomingPlatform != null) {
            showSetup();
            importPlatform(incomingPlatform);
        } else if (platformFile.isFile() && platformFile.length() > 1024) {
            showPlatform();
        } else {
            showSetup();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        Uri incomingPlatform = intent == null ? null : intent.getData();
        if (incomingPlatform != null) {
            showSetup();
            importPlatform(incomingPlatform);
        }
    }

    private void buildRootView() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.WHITE);

        webView = new WebView(this);
        webView.setBackgroundColor(Color.WHITE);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        root.addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        setupPanel = new LinearLayout(this);
        setupPanel.setOrientation(LinearLayout.VERTICAL);
        setupPanel.setGravity(Gravity.CENTER_HORIZONTAL);
        setupPanel.setPadding(dp(28), dp(48), dp(28), dp(28));
        setupPanel.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(this);
        title.setText("Kalmar Troubleshooting");
        title.setTextSize(25);
        title.setTextColor(Color.rgb(11, 58, 99));
        title.setGravity(Gravity.CENTER);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        setupPanel.addView(title, matchWrap(0, dp(18)));

        TextView instructions = new TextView(this);
        instructions.setText(
                "აირჩიე Kalmar_Mobile_Offline_v11_1.html ფაილი. აპი მას ერთხელ გადაიტანს საკუთარ მეხსიერებაში და შემდეგ სრულად ოფლაინ იმუშავებს.\n\n" +
                "Choose the Kalmar_Mobile_Offline_v11_1.html file. The app imports it once and then works fully offline.\n\n" +
                "Выберите файл Kalmar_Mobile_Offline_v11_1.html. Приложение импортирует его один раз и затем работает полностью офлайн.");
        instructions.setTextSize(16);
        instructions.setTextColor(Color.rgb(52, 65, 82));
        instructions.setGravity(Gravity.CENTER);
        instructions.setLineSpacing(0, 1.15f);
        setupPanel.addView(instructions, matchWrap(0, dp(28)));

        Button chooseButton = new Button(this);
        chooseButton.setText("აირჩიე HTML / Choose HTML / Выбрать HTML");
        chooseButton.setTextSize(15);
        chooseButton.setAllCaps(false);
        chooseButton.setOnClickListener(v -> choosePlatformFile());
        setupPanel.addView(chooseButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(56)));

        setupProgress = new ProgressBar(this);
        setupProgress.setVisibility(View.GONE);
        setupPanel.addView(setupProgress, matchWrap(0, dp(14)));

        setupStatus = new TextView(this);
        setupStatus.setTextSize(14);
        setupStatus.setTextColor(Color.DKGRAY);
        setupStatus.setGravity(Gravity.CENTER);
        setupPanel.addView(setupStatus, matchWrap(0, dp(8)));

        root.addView(setupPanel, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);
    }

    private LinearLayout.LayoutParams matchWrap(int top, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, top, 0, bottom);
        return p;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");
        webView.setWebViewClient(new LocalPlatformClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(
                    WebView view,
                    ValueCallback<Uri[]> filePathCallback,
                    FileChooserParams fileChooserParams) {
                if (webFileCallback != null) {
                    webFileCallback.onReceiveValue(null);
                }
                webFileCallback = filePathCallback;
                launchWebFileChooser(fileChooserParams);
                return true;
            }
        });
    }

    private void choosePlatformFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES,
                new String[]{"text/html", "application/xhtml+xml", "application/octet-stream"});
        startActivityForResult(intent, PLATFORM_PICKER_REQUEST);
    }

    private void importPlatform(Uri uri) {
        setupProgress.setVisibility(View.VISIBLE);
        setupStatus.setText("Importing… / მიმდინარეობს იმპორტი… / Импорт…");
        ioExecutor.execute(() -> {
            File directory = platformFile.getParentFile();
            File temporary = new File(directory, "index.importing");
            try {
                if (!directory.exists() && !directory.mkdirs()) {
                    throw new IOException("Unable to create app storage directory");
                }
                try (InputStream input = new BufferedInputStream(
                        getContentResolver().openInputStream(uri));
                     OutputStream output = new BufferedOutputStream(new FileOutputStream(temporary))) {
                    if (input == null) {
                        throw new IOException("Selected file cannot be opened");
                    }
                    byte[] buffer = new byte[128 * 1024];
                    int count;
                    while ((count = input.read(buffer)) >= 0) {
                        output.write(buffer, 0, count);
                    }
                }
                if (temporary.length() < 1024 || !looksLikeHtml(temporary)) {
                    throw new IOException("The selected file does not appear to be a valid HTML platform file");
                }
                if (platformFile.exists() && !platformFile.delete()) {
                    throw new IOException("Unable to replace the previous platform file");
                }
                if (!temporary.renameTo(platformFile)) {
                    copyFile(temporary, platformFile);
                    temporary.delete();
                }
                runOnUiThread(this::showPlatform);
            } catch (Exception exception) {
                temporary.delete();
                runOnUiThread(() -> {
                    setupProgress.setVisibility(View.GONE);
                    setupStatus.setText(exception.getMessage());
                    Toast.makeText(this, "Import failed: " + exception.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private boolean looksLikeHtml(File file) throws IOException {
        byte[] bytes = new byte[512];
        int count;
        try (InputStream input = new FileInputStream(file)) {
            count = input.read(bytes);
        }
        if (count <= 0) return false;
        String start = new String(bytes, 0, count, java.nio.charset.StandardCharsets.UTF_8)
                .toLowerCase(Locale.ROOT);
        return start.contains("<!doctype html") || start.contains("<html");
    }

    private void copyFile(File source, File destination) throws IOException {
        try (InputStream input = new BufferedInputStream(new FileInputStream(source));
             OutputStream output = new BufferedOutputStream(new FileOutputStream(destination))) {
            byte[] buffer = new byte[128 * 1024];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                output.write(buffer, 0, count);
            }
        }
    }

    private void showSetup() {
        webView.setVisibility(View.GONE);
        setupPanel.setVisibility(View.VISIBLE);
        setupProgress.setVisibility(View.GONE);
    }

    private void showPlatform() {
        setupPanel.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
        webView.loadUrl(LOCAL_ORIGIN + "/index.html");
    }

    private void launchWebFileChooser(WebChromeClient.FileChooserParams params) {
        Intent contentIntent;
        try {
            contentIntent = params.createIntent();
        } catch (Exception ignored) {
            contentIntent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            contentIntent.addCategory(Intent.CATEGORY_OPENABLE);
            contentIntent.setType("*/*");
        }

        String[] accepted = params.getAcceptTypes();
        boolean imageRequest = false;
        if (accepted != null) {
            for (String type : accepted) {
                if (type != null && type.toLowerCase(Locale.ROOT).contains("image")) {
                    imageRequest = true;
                    break;
                }
            }
        }

        Intent chooser = Intent.createChooser(contentIntent, "Choose file");
        if (imageRequest) {
            Intent camera = createCameraIntent();
            if (camera != null) {
                chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{camera});
            }
        }
        try {
            startActivityForResult(chooser, WEB_FILE_CHOOSER_REQUEST);
        } catch (Exception exception) {
            webFileCallback.onReceiveValue(null);
            webFileCallback = null;
            Toast.makeText(this, "Unable to open file picker", Toast.LENGTH_LONG).show();
        }
    }

    private Intent createCameraIntent() {
        Intent camera = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (camera.resolveActivity(getPackageManager()) == null) return null;
        try {
            File directory = getExternalCacheDir();
            if (directory == null) directory = getCacheDir();
            String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            File photo = File.createTempFile("KALMAR_" + stamp + "_", ".jpg", directory);
            pendingCameraUri = FileProvider.getUriForFile(
                    this, getPackageName() + ".fileprovider", photo);
            camera.putExtra(MediaStore.EXTRA_OUTPUT, pendingCameraUri);
            camera.setClipData(ClipData.newRawUri("Kalmar photo", pendingCameraUri));
            camera.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            return camera;
        } catch (Exception exception) {
            pendingCameraUri = null;
            return null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PLATFORM_PICKER_REQUEST) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                Uri uri = data.getData();
                try {
                    getContentResolver().takePersistableUriPermission(
                            uri, data.getFlags() &
                                    (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION));
                } catch (Exception ignored) {
                }
                importPlatform(uri);
            }
            return;
        }

        if (requestCode == WEB_FILE_CHOOSER_REQUEST && webFileCallback != null) {
            Uri[] results = null;
            if (resultCode == RESULT_OK) {
                if (data == null || (data.getData() == null && data.getClipData() == null)) {
                    if (pendingCameraUri != null) results = new Uri[]{pendingCameraUri};
                } else if (data.getClipData() != null) {
                    ClipData clips = data.getClipData();
                    results = new Uri[clips.getItemCount()];
                    for (int i = 0; i < clips.getItemCount(); i++) {
                        results[i] = clips.getItemAt(i).getUri();
                    }
                } else if (data.getData() != null) {
                    results = new Uri[]{data.getData()};
                }
            }
            webFileCallback.onReceiveValue(results);
            webFileCallback = null;
            pendingCameraUri = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (setupPanel.getVisibility() == View.VISIBLE) {
            super.onBackPressed();
        } else if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        ioExecutor.shutdownNow();
        if (webView != null) webView.destroy();
        super.onDestroy();
    }

    private final class LocalPlatformClient extends WebViewClient {
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            if ("appassets.androidplatform.net".equalsIgnoreCase(uri.getHost()) &&
                    "/index.html".equals(uri.getPath()) && platformFile.isFile()) {
                try {
                    return new WebResourceResponse(
                            "text/html", "UTF-8", new BufferedInputStream(new FileInputStream(platformFile)));
                } catch (IOException ignored) {
                }
            }
            return super.shouldInterceptRequest(view, request);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            if ("appassets.androidplatform.net".equalsIgnoreCase(uri.getHost())) return false;
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            } catch (Exception ignored) {
            }
            return true;
        }
    }

    public final class AndroidBridge {
        @JavascriptInterface
        public void shareText(String title, String text) {
            runOnUiThread(() -> {
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("text/plain");
                intent.putExtra(Intent.EXTRA_SUBJECT, title);
                intent.putExtra(Intent.EXTRA_TEXT, text);
                startActivity(Intent.createChooser(intent, title));
            });
        }

        @JavascriptInterface
        public void reimportPlatform() {
            runOnUiThread(() -> {
                setupPanel.setVisibility(View.VISIBLE);
                setupProgress.setVisibility(View.GONE);
                setupStatus.setText("Choose a newer Kalmar HTML file to update the app.");
            });
        }
    }
}
