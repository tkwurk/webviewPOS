package com.posprod.webviewapp;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.util.Base64;
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

    // Outlet deployment URLs
    private static final String URL_MANONJAYA = "https://script.google.com/macros/s/AKfycbzFnOfWUIO0KZYQS8HLXCY_vYHNGK6j_wLbw0-HM-9fr9PrE0MH15AFhNnziFroUFsCFg/exec";
    private static final String URL_PAMARICAN = "https://script.google.com/macros/s/AKfycbx8IHfpCTKmSDbO5-F0xQxd0uGmch8jVS5TjQmJCWYsxAMVMui7wiAsfyTcNT8rvkkz0g/exec";

    private static final String PREFS_NAME = "posprod_prefs";
    private static final String KEY_OUTLET = "selected_outlet";

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

        // Always show outlet picker on launch so any user can choose
        showOutletPicker();

        // Request Bluetooth permissions at startup
        requestBluetoothPermissions();
    }

    private void loadOutletUrl(String outlet) {
        String url = "pamarican".equals(outlet) ? URL_PAMARICAN : URL_MANONJAYA;
        webView.loadUrl(url);
    }

    private void showOutletPicker() {
        String[] outlets = {"Manonjaya", "Pamarican"};
        // Pre-select the last used outlet
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String saved = prefs.getString(KEY_OUTLET, null);
        int defaultIdx = "pamarican".equals(saved) ? 1 : 0;

        new AlertDialog.Builder(this)
            .setTitle("Select Outlet")
            .setCancelable(false)
            .setSingleChoiceItems(outlets, defaultIdx, null)
            .setPositiveButton("OK", (dialog, which) -> {
                int selected = ((AlertDialog) dialog).getListView().getCheckedItemPosition();
                String outlet = (selected == 0) ? "manonjaya" : "pamarican";
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putString(KEY_OUTLET, outlet)
                    .apply();
                loadOutletUrl(outlet);
            })
            .show();
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

        /** Return the currently selected outlet name */
        @JavascriptInterface
        public String getOutlet() {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            return prefs.getString(KEY_OUTLET, "");
        }

        /** Show the outlet picker dialog to switch outlets */
        @JavascriptInterface
        public void switchOutlet() {
            runOnUiThread(() -> showOutletPicker());
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
         * Print Base64-encoded ESC/POS data to a Bluetooth printer by name.
         * The data must be Base64-encoded in JavaScript to preserve control bytes.
         * Returns "ok:NNbytes" or "error:reason".
         */
        @SuppressLint("MissingPermission")
        @JavascriptInterface
        public String printEscPos(String printerName, String escPosBase64) {
            if (bluetoothAdapter == null) return "error:Bluetooth not available";
            if (!hasBtPermissions()) return "error:Bluetooth permissions not granted";
            if (!bluetoothAdapter.isEnabled()) return "error:Bluetooth is off";

            // Decode Base64 to raw bytes
            byte[] rawData;
            try {
                rawData = Base64.decode(escPosBase64, Base64.DEFAULT);
            } catch (Exception e) {
                return "error:Invalid data encoding: " + e.getMessage();
            }

            if (rawData.length == 0) return "error:Empty print data (0 bytes from base64 length " + escPosBase64.length() + ")";

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
                socket = connectToDevice(target);
                OutputStream os = socket.getOutputStream();

                // Single write, same as testPrint which works
                os.write(rawData);
                os.flush();

                // Wait for printer to fully process before closing
                Thread.sleep(1500);

                return "ok:" + rawData.length + "bytes";
            } catch (Exception e) {
                return "error:" + e.getMessage();
            } finally {
                if (socket != null) {
                    try { socket.close(); } catch (Exception ignored) {}
                }
            }
        }

        /**
         * Connect to Bluetooth device — tries standard SPP UUID first,
         * then falls back to reflection-based RFCOMM on port 1.
         */
        @SuppressLint("MissingPermission")
        private BluetoothSocket connectToDevice(BluetoothDevice device) throws Exception {
            BluetoothSocket socket;
            try {
                socket = device.createRfcommSocketToServiceRecord(SPP_UUID);
                socket.connect();
                return socket;
            } catch (Exception e1) {
                // Fallback: use reflection to connect on RFCOMM channel 1
                try {
                    socket = (BluetoothSocket) device.getClass()
                        .getMethod("createRfcommSocket", int.class)
                        .invoke(device, 1);
                    if (socket != null) {
                        socket.connect();
                        return socket;
                    }
                } catch (Exception e2) {
                    // ignore fallback error, throw original
                }
                throw new Exception("Could not connect: " + e1.getMessage() + ". Make sure printer is ON and nearby.");
            }
        }

        /**
         * Send a simple test print to verify the printer works.
         * Sends plain text "TEST PRINT OK" with a line feed and paper cut.
         */
        @SuppressLint("MissingPermission")
        @JavascriptInterface
        public String testPrint(String printerName) {
            if (bluetoothAdapter == null) return "error:Bluetooth not available";
            if (!hasBtPermissions()) return "error:Bluetooth permissions not granted";
            if (!bluetoothAdapter.isEnabled()) return "error:Bluetooth is off";

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
            if (target == null) return "error:Printer not found";

            if (bluetoothAdapter.isDiscovering()) bluetoothAdapter.cancelDiscovery();

            BluetoothSocket socket = null;
            try {
                socket = connectToDevice(target);
                OutputStream os = socket.getOutputStream();
                // ESC @ = init, plain text, line feeds, paper cut
                byte[] testData = new byte[] {
                    0x1B, 0x40,                           // ESC @ = Initialize printer
                    0x1B, 0x61, 0x01,                     // ESC a 1 = Center align
                    'T', 'E', 'S', 'T', ' ',
                    'P', 'R', 'I', 'N', 'T', ' ',
                    'O', 'K', 0x0A,                       // LF
                    '-', '-', '-', '-', '-', '-', '-', '-',
                    '-', '-', '-', '-', '-', '-', '-', '-', 0x0A,
                    'P', 'r', 'i', 'n', 't', 'e', 'r', ' ',
                    'W', 'o', 'r', 'k', 'i', 'n', 'g', '!', 0x0A,
                    0x0A, 0x0A, 0x0A,                     // Feed paper
                    0x1D, 0x56, 0x00                      // GS V 0 = Full cut
                };
                os.write(testData);
                os.flush();
                Thread.sleep(1000);
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
