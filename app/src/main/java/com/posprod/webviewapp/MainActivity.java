package com.posprod.webviewapp;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.JavascriptInterface;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private WebView webView;
    private static final String URL = "https://script.google.com/macros/s/AKfycbx8IHfpCTKmSDbO5-F0xQxd0uGmch8jVS5TjQmJCWYsxAMVMui7wiAsfyTcNT8rvkkz0g/exec";

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        webView = findViewById(R.id.webView);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setSupportMultipleWindows(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new CustomWebViewClient());
        webView.addJavascriptInterface(new BluetoothPrinterBridge(), "AndroidPrinter");
        webView.loadUrl(URL);
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    private class CustomWebViewClient extends WebViewClient {
        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            Toast.makeText(MainActivity.this, "Network error: " + error.getDescription(), Toast.LENGTH_LONG).show();
        }
    }

    public class BluetoothPrinterBridge {
        @JavascriptInterface
        public void printReceipt(String data) {
            // TODO: Implement Bluetooth ESC/POS printing logic here
            // For now, just show a toast
            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Printing: " + data, Toast.LENGTH_SHORT).show());
        }
    }
}
