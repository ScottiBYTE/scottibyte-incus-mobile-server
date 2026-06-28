package com.scottibyte.incusmobile;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class MainActivity extends Activity {
    private static final String API_BASE_URL = "https://incusmobile.scottibyte.com";
    private static final String APP_VERSION = "0.1.2";
    private static final String PREFS_NAME = "scottibyte_incus_mobile";
    private static final String PREF_DEVICE_ID = "device_id";
    private static final String PREF_BEARER_TOKEN = "bearer_token";
    private static final String PREF_DEVICE_NAME = "device_name";

    private SharedPreferences prefs;
    private TextView statusView;
    private TextView deviceIdView;
    private TextView tokenView;
    private EditText deviceNameInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        ensureDeviceId();

        ScrollView scroll = new ScrollView(this);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(36, 48, 36, 36);

        TextView title = new TextView(this);
        title.setText("ScottiBYTE Incus Mobile");
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView apiUrl = new TextView(this);
        apiUrl.setText("\nAPI Base URL:\n" + API_BASE_URL);
        apiUrl.setTextSize(15);

        deviceIdView = new TextView(this);
        deviceIdView.setText("\nDevice ID:\n" + getLocalDeviceId());
        deviceIdView.setTextSize(14);

        deviceNameInput = new EditText(this);
        deviceNameInput.setHint("Device name");
        deviceNameInput.setSingleLine(true);
        deviceNameInput.setText(getDeviceName());

        Button healthButton = new Button(this);
        healthButton.setText("Test Server Health");
        healthButton.setOnClickListener(v -> testHealth());

        Button requestPairingButton = new Button(this);
        requestPairingButton.setText("Request Pairing");
        requestPairingButton.setOnClickListener(v -> requestPairing());

        Button checkApprovalButton = new Button(this);
        checkApprovalButton.setText("Check Approval / Claim Token");
        checkApprovalButton.setOnClickListener(v -> checkPairingStatus());

        Button summaryButton = new Button(this);
        summaryButton.setText("Test Authorized Summary");
        summaryButton.setOnClickListener(v -> testAuthorizedSummary());

        Button resetButton = new Button(this);
        resetButton.setText("Reset Local Token");
        resetButton.setOnClickListener(v -> resetLocalToken());

        tokenView = new TextView(this);
        tokenView.setTextSize(14);

        statusView = new TextView(this);
        statusView.setText("\nStatus: Ready");
        statusView.setTextSize(16);

        layout.addView(title);
        layout.addView(apiUrl);
        layout.addView(deviceIdView);
        layout.addView(deviceNameInput);
        layout.addView(healthButton);
        layout.addView(requestPairingButton);
        layout.addView(checkApprovalButton);
        layout.addView(summaryButton);
        layout.addView(resetButton);
        layout.addView(tokenView);
        layout.addView(statusView);

        scroll.addView(layout);
        setContentView(scroll);

        refreshTokenStatus();
    }

    private void ensureDeviceId() {
        if (prefs.getString(PREF_DEVICE_ID, null) == null) {
            String id = "android-" + UUID.randomUUID().toString();
            prefs.edit().putString(PREF_DEVICE_ID, id).apply();
        }
    }

    private String getLocalDeviceId() {
        return prefs.getString(PREF_DEVICE_ID, "");
    }

    private String getDeviceName() {
        String saved = prefs.getString(PREF_DEVICE_NAME, null);

        if (saved != null && !saved.trim().isEmpty()) {
            return saved;
        }

        return "Scott Android Phone";
    }

    private String getBearerToken() {
        return prefs.getString(PREF_BEARER_TOKEN, null);
    }

    private void saveDeviceName() {
        prefs.edit()
            .putString(PREF_DEVICE_NAME, deviceNameInput.getText().toString().trim())
            .apply();
    }

    private void saveBearerToken(String token) {
        prefs.edit()
            .putString(PREF_BEARER_TOKEN, token)
            .apply();

        refreshTokenStatus();
    }

    private void refreshTokenStatus() {
        String token = getBearerToken();

        if (token == null || token.trim().isEmpty()) {
            tokenView.setText("\nToken: Not stored");
        } else {
            tokenView.setText("\nToken: Stored locally");
        }
    }

    private void resetLocalToken() {
        prefs.edit().remove(PREF_BEARER_TOKEN).apply();
        refreshTokenStatus();
        setStatus("Local token removed. Server approval was not changed.");
    }

    private void setStatus(String message) {
        new Handler(Looper.getMainLooper()).post(() ->
            statusView.setText("\nStatus:\n" + message)
        );
    }

    private void testHealth() {
        setStatus("Testing public health endpoint...");

        new Thread(() -> {
            try {
                HttpResult result = httpRequest("GET", "/api/mobile/health", null, null);
                setStatus(result.toDisplayString());
            } catch (Exception e) {
                setStatus(errorText(e));
            }
        }).start();
    }

    private void requestPairing() {
        saveDeviceName();

        String deviceName = getDeviceName();

        if (deviceName.trim().isEmpty()) {
            setStatus("Device name is required.");
            return;
        }

        setStatus("Requesting pairing approval...");

        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("device_id", getLocalDeviceId());
                body.put("device_name", deviceName);
                body.put("app_version", APP_VERSION);

                HttpResult result = httpRequest(
                    "POST",
                    "/api/pairing/request",
                    body.toString(),
                    null
                );

                setStatus(result.toDisplayString());
            } catch (Exception e) {
                setStatus(errorText(e));
            }
        }).start();
    }

    private void checkPairingStatus() {
        setStatus("Checking pairing status...");

        new Thread(() -> {
            try {
                String encodedDeviceId = URLEncoder.encode(getLocalDeviceId(), "UTF-8");
                HttpResult result = httpRequest(
                    "GET",
                    "/api/pairing/status/" + encodedDeviceId,
                    null,
                    null
                );

                if (result.code >= 200 && result.code < 300) {
                    JSONObject json = new JSONObject(result.body);

                    if (json.optBoolean("ok") && json.has("token")) {
                        String token = json.optString("token", "");
                        if (!token.trim().isEmpty()) {
                            saveBearerToken(token);
                        }
                    }
                }

                setStatus(result.toDisplayString());
            } catch (Exception e) {
                setStatus(errorText(e));
            }
        }).start();
    }

    private void testAuthorizedSummary() {
        String token = getBearerToken();

        if (token == null || token.trim().isEmpty()) {
            setStatus("No bearer token stored. Request pairing, approve the device, then claim the token.");
            return;
        }

        setStatus("Testing authorized mobile summary...");

        new Thread(() -> {
            try {
                HttpResult result = httpRequest(
                    "GET",
                    "/api/mobile/summary",
                    null,
                    token
                );

                setStatus(result.toDisplayString());
            } catch (Exception e) {
                setStatus(errorText(e));
            }
        }).start();
    }

    private HttpResult httpRequest(String method, String path, String jsonBody, String bearerToken) throws Exception {
        URL url = new URL(API_BASE_URL + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        conn.setRequestMethod(method);
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(15000);
        conn.setRequestProperty("Accept", "application/json");

        if (bearerToken != null && !bearerToken.trim().isEmpty()) {
            conn.setRequestProperty("Authorization", "Bearer " + bearerToken);
        }

        if (jsonBody != null) {
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");

            byte[] out = jsonBody.getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(out.length);

            OutputStream stream = conn.getOutputStream();
            stream.write(out);
            stream.flush();
            stream.close();
        }

        int code = conn.getResponseCode();

        BufferedReader reader = new BufferedReader(
            new InputStreamReader(
                code >= 200 && code < 400
                    ? conn.getInputStream()
                    : conn.getErrorStream()
            )
        );

        StringBuilder response = new StringBuilder();
        String line;

        while ((line = reader.readLine()) != null) {
            response.append(line).append("\n");
        }

        reader.close();
        conn.disconnect();

        return new HttpResult(code, response.toString().trim());
    }

    private String errorText(Exception e) {
        return "ERROR\n\n" + e.getClass().getSimpleName() + ": " + e.getMessage();
    }

    private static class HttpResult {
        final int code;
        final String body;

        HttpResult(int code, String body) {
            this.code = code;
            this.body = body;
        }

        String toDisplayString() {
            return "HTTP " + code + "\n\n" + body;
        }
    }
}
