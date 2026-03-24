package com.posprod.webviewapp;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.pm.PackageManager;
import java.util.ArrayList;
import java.util.List;
import android.os.Build;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;

import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private WebView webView;
    private static final String URL = "https://script.google.com/macros/s/AKfycbx8IHfpCTKmSDbO5-F0xQxd0uGmch8jVS5TjQmJCWYsxAMVMui7wiAsfyTcNT8rvkkz0g/exec";

    // Standard SPP UUID for Bluetooth serial (used by most thermal printers)
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private static final int REQUEST_BT_PERMISSIONS = 1001;

    private BluetoothAdapter bluetoothAdapter;



    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize Bluetooth adapter
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            BluetoothManager bm = getSystemService(BluetoothManager.class);
            bluetoothAdapter = (bm != null) ? bm.getAdapter() : null;
        } else {
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        }

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

        // Register bridge as "AndroidPrint" to match web code expectations
        webView.addJavascriptInterface(new BluetoothPrintBridge(), "AndroidPrint");
        webView.loadUrl(URL);

        // Request Bluetooth permissions at startup
        requestBluetoothPermissions();
    }

    // ------------------------------------------------------------------
    // Permission handling
    // ------------------------------------------------------------------
    private void requestBluetoothPermissions() {
        List<String> needed = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
                needed.add(android.Manifest.permission.BLUETOOTH_CONNECT);
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED)
                needed.add(android.Manifest.permission.BLUETOOTH_SCAN);
        } else {
            // Android < 12 needs location for BT discovery
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                needed.add(android.Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (!needed.isEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toArray(new String[0]), REQUEST_BT_PERMISSIONS);
        }
    }

    private boolean hasBtPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_BT_PERMISSIONS) {
            for (int r : grantResults) {
                if (r != PackageManager.PERMISSION_GRANTED) {
                    runOnUiThread(() -> Toast.makeText(this, "Bluetooth permissions required for printing", Toast.LENGTH_LONG).show());
                    return;
                }
            }
        }
    }



    // ------------------------------------------------------------------
    // JavaScript bridge — exposed as window.AndroidPrint
    // ------------------------------------------------------------------
    public class BluetoothPrintBridge {

        /** Check if native Bluetooth printing is available */
        @JavascriptInterface
        public boolean isAvailable() {
            return bluetoothAdapter != null && hasBtPermissions();
        }

        /** Return JSON array of paired Bluetooth device names */
        @SuppressLint("MissingPermission")
        @JavascriptInterface
        public String getPairedPrinters() {
            JSONArray arr = new JSONArray();
            if (bluetoothAdapter == null || !hasBtPermissions()) return arr.toString();
            Set<BluetoothDevice> paired = bluetoothAdapter.getBondedDevices();
            if (paired != null) {
                for (BluetoothDevice d : paired) {
                    String name = d.getName();
                    if (name != null) arr.put(name);
                }
            }
            return arr.toString();
        }

        /** Return JSON array of paired devices as "Name|MAC" */
        @SuppressLint("MissingPermission")
        @JavascriptInterface
        public String getPairedDevices() {
            JSONArray arr = new JSONArray();
            if (bluetoothAdapter == null || !hasBtPermissions()) return arr.toString();
            Set<BluetoothDevice> paired = bluetoothAdapter.getBondedDevices();
            if (paired != null) {
                for (BluetoothDevice d : paired) {
                    String name = d.getName();
                    String addr = d.getAddress();
                    arr.put((name != null ? name : "Unknown") + "|" + addr);
                }
            }
            return arr.toString();
        }

        /**
         * Return JSON array of all paired Bluetooth devices as "Name|MAC".
         * Printers must be paired via Android Settings first.
         * Returns JSON string or "error:reason".
         */
        @SuppressLint("MissingPermission")
        @JavascriptInterface
        public String discoverPrinters() {
            if (bluetoothAdapter == null) return "error:Bluetooth not available";
            if (!hasBtPermissions()) return "error:Bluetooth permissions not granted";
            if (!bluetoothAdapter.isEnabled()) return "error:Bluetooth is off. Please enable it in Settings.";

            JSONArray arr = new JSONArray();
            Set<BluetoothDevice> paired = bluetoothAdapter.getBondedDevices();
            if (paired != null) {
                for (BluetoothDevice d : paired) {
                    String name = d.getName();
                    String addr = d.getAddress();
                    arr.put((name != null ? name : "Unknown") + "|" + addr);
                }
            }
            return arr.toString();
        }

        /**
         * Print ESC/POS data to a Bluetooth printer by name.
         * Finds the printer among paired devices and sends raw bytes via SPP.
         * Returns "ok" or "error:reason".
         */
        @SuppressLint("MissingPermission")
        @JavascriptInterface
        public String printEscPos(String printerName, String escPosData) {
            if (bluetoothAdapter == null) return "error:Bluetooth not available";
            if (!hasBtPermissions()) return "error:Bluetooth permissions not granted";
            if (!bluetoothAdapter.isEnabled()) return "error:Bluetooth is off";

            // Find the target device among paired devices
            Set<BluetoothDevice> paired = bluetoothAdapter.getBondedDevices();
            BluetoothDevice target = null;
            if (paired != null) {
                for (BluetoothDevice d : paired) {
                    if (printerName.equals(d.getName()) || printerName.equals(d.getAddress())) {
                        target = d;
                        break;
                    }
                }
            }
            if (target == null) return "error:Printer '" + printerName + "' not found in paired devices";

            // Cancel discovery to speed up connection
            if (bluetoothAdapter.isDiscovering()) bluetoothAdapter.cancelDiscovery();

            BluetoothSocket socket = null;
            try {
                socket = target.createRfcommSocketToServiceRecord(SPP_UUID);
                socket.connect();
                OutputStream os = socket.getOutputStream();
                os.write(escPosData.getBytes("ISO-8859-1")); // ESC/POS uses single-byte encoding
                os.flush();
                return "ok";
            } catch (Exception e) {
                return "error:" + e.getMessage();
            } finally {
                if (socket != null) {
                    try { socket.close(); } catch (Exception ignored) {}
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Navigation & lifecycle
    // ------------------------------------------------------------------
    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    private class CustomWebViewClient extends WebViewClient {
        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            Toast.makeText(MainActivity.this, "Network error: " + error.getDescription(), Toast.LENGTH_LONG).show();
        }
    }
}
