package com.scottibyte.incusmobile;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.view.View;

import org.json.JSONObject;
import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class MainActivity extends Activity {
    private static final String API_BASE_URL = "https://incusmobile.scottibyte.com";
    private static final String APP_VERSION = "0.2.1";
    private static final String PREFS_NAME = "scottibyte_incus_mobile";
    private static final String PREF_DEVICE_ID = "device_id";
    private static final String PREF_BEARER_TOKEN = "bearer_token";
    private static final String PREF_DEVICE_NAME = "device_name";

    private SharedPreferences prefs;
    private TextView statusView;
    private TextView deviceIdView;
    private TextView tokenView;
    private Button healthButton;
    private Button requestPairingButton;
    private Button checkApprovalButton;
    private Button summaryButton;
    private Button instancesButton;
    private Button resetButton;
    private TextView dashboardView;
    private TextView remoteSummaryView;
    private TextView selectedServerView;
    private TextView instancesView;
    private TextView instanceDetailView;
    private EditText serverFilterInput;
    private EditText instanceFilterInput;
    private JSONArray lastInstances = null;
    private EditText deviceNameInput;
    private final Handler pairingHandler = new Handler(Looper.getMainLooper());
    private boolean pairingPollingActive = false;
    private static final long PAIRING_POLL_INTERVAL_MS = 5000;

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

        healthButton = new Button(this);
        healthButton.setText("Server Health");
        healthButton.setOnClickListener(v -> testHealth());

        requestPairingButton = new Button(this);
        requestPairingButton.setText("Request Pairing");
        requestPairingButton.setOnClickListener(v -> requestPairing());

        checkApprovalButton = new Button(this);
        checkApprovalButton.setText("Check Pairing Status");
        checkApprovalButton.setOnClickListener(v -> checkPairingStatus());

        summaryButton = new Button(this);
        summaryButton.setText("Refresh");
        summaryButton.setOnClickListener(v -> loadAuthorizedHome());

        instancesButton = new Button(this);
        instancesButton.setText("View Instances");
        instancesButton.setOnClickListener(v -> loadInstances());

        resetButton = new Button(this);
        resetButton.setText("Reset Pairing");
        resetButton.setOnClickListener(v -> resetLocalToken());

        tokenView = new TextView(this);
        tokenView.setTextSize(14);

        dashboardView = new TextView(this);
        dashboardView.setTextSize(18);
        dashboardView.setTypeface(Typeface.DEFAULT_BOLD);
        dashboardView.setText("\nHome\nNot paired yet.");

        remoteSummaryView = new TextView(this);
        remoteSummaryView.setTextSize(14);
        remoteSummaryView.setText("\nServers\nTap View Instances to load server summary.");

        selectedServerView = new TextView(this);
        selectedServerView.setTextSize(14);
        selectedServerView.setText("\nSelected Server\nNo server selected.");

        instancesView = new TextView(this);
        instancesView.setTextSize(14);
        instancesView.setText("\nInstances: not loaded");

        serverFilterInput = new EditText(this);
        serverFilterInput.setHint("Server filter, for example mondo");
        serverFilterInput.setSingleLine(true);
        serverFilterInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence value, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence value, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable value) {
                if (instancesView != null && instanceDetailView != null && lastInstances != null) {
                    renderInstancesList();
                }
            }
        });

        instanceFilterInput = new EditText(this);
        instanceFilterInput.setHint("Instance filter");
        instanceFilterInput.setSingleLine(true);
        instanceFilterInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence value, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence value, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable value) {
                if (instancesView != null && instanceDetailView != null && lastInstances != null) {
                    renderInstancesList();
                }
            }
        });

        instanceDetailView = new TextView(this);
        instanceDetailView.setTextSize(14);
        instanceDetailView.setText("\nSelected Instance\nNo instance selected.");

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
        layout.addView(instancesButton);
        layout.addView(resetButton);
        layout.addView(tokenView);
        layout.addView(dashboardView);
        layout.addView(remoteSummaryView);
        layout.addView(selectedServerView);
        layout.addView(serverFilterInput);
        layout.addView(instanceFilterInput);
        layout.addView(instancesView);
        layout.addView(instanceDetailView);
        layout.addView(statusView);

        scroll.addView(layout);
        setContentView(scroll);

        refreshTokenStatus();
        updateAuthUiVisibility();

        if (hasBearerToken()) {
            loadAuthorizedHome();
        }
    }

    private void updateAuthUiVisibility() {
        boolean authorized = hasBearerToken();

        if (requestPairingButton != null) {
            requestPairingButton.setVisibility(authorized ? View.GONE : View.VISIBLE);
        }

        if (checkApprovalButton != null) {
            checkApprovalButton.setVisibility(authorized ? View.GONE : View.VISIBLE);
        }

        if (summaryButton != null) {
            summaryButton.setVisibility(authorized ? View.VISIBLE : View.GONE);
        }

        if (instancesButton != null) {
            instancesButton.setVisibility(authorized ? View.VISIBLE : View.GONE);
        }

        if (resetButton != null) {
            resetButton.setVisibility(View.VISIBLE);
        }

        if (healthButton != null) {
            healthButton.setVisibility(View.VISIBLE);
        }
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

    private boolean hasBearerToken() {
        String token = getBearerToken();
        return token != null && !token.trim().isEmpty();
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
        updateAuthUiVisibility();
        loadAuthorizedHome();
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
        stopPairingPolling();
        prefs.edit().remove(PREF_BEARER_TOKEN).apply();
        refreshTokenStatus();
        dashboardView.setText("\nHome\nNot paired yet.");
        lastInstances = null;
        serverFilterInput.setText("");
        instanceFilterInput.setText("");
        remoteSummaryView.setText("\nServers\nTap View Instances to load server summary.");
        selectedServerView.setText("\nSelected Server\nNo server selected.");
        instancesView.setText("\nInstances: not loaded");
        instanceDetailView.setText("\nSelected Instance\nNo instance selected.");
        setStatus("Local token removed. Server approval was not changed. Request pairing again if needed.");
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


    private void startPairingPolling() {
        if (pairingPollingActive) {
            return;
        }

        pairingPollingActive = true;
        pairingHandler.postDelayed(pairingPollRunnable, PAIRING_POLL_INTERVAL_MS);
    }

    private void stopPairingPolling() {
        pairingPollingActive = false;
        pairingHandler.removeCallbacks(pairingPollRunnable);
    }

    private final Runnable pairingPollRunnable = new Runnable() {
        @Override
        public void run() {
            if (!pairingPollingActive) {
                return;
            }

            checkPairingStatus(true);

            if (pairingPollingActive) {
                pairingHandler.postDelayed(this, PAIRING_POLL_INTERVAL_MS);
            }
        }
    };

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

                if (result.code >= 200 && result.code < 300) {
                    JSONObject json = new JSONObject(result.body);
                    String status = json.optString("status", "");

                    if ("pending".equals(status)) {
                        setStatus("Pairing request submitted.\n\nWaiting for admin approval...");
                        startPairingPolling();
                        return;
                    }

                    if ("approved".equals(status)) {
                        setStatus("Device is already approved. Checking for token...");
                        checkPairingStatus(true);
                        return;
                    }

                    if ("revoked".equals(status)) {
                        stopPairingPolling();
                    }
                }

                setStatus(result.toDisplayString());
            } catch (Exception e) {
                setStatus(errorText(e));
            }
        }).start();
    }

    private void checkPairingStatus() {
        checkPairingStatus(false);
    }

    private void checkPairingStatus(boolean automatic) {
        if (!automatic) {
            setStatus("Checking pairing status...");
        }

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
                            stopPairingPolling();
                            setStatus("Approved. Token claimed and stored locally.\n\n" + result.toDisplayString());
                            return;
                        }
                    }

                    String status = json.optString("status", "");
                    if ("pending".equals(status) && automatic) {
                        setStatus("Waiting for admin approval...");
                        return;
                    }

                    if ("revoked".equals(status)) {
                        stopPairingPolling();
                    }
                }

                setStatus(result.toDisplayString());
            } catch (Exception e) {
                setStatus(errorText(e));
            }
        }).start();
    }

    private void loadAuthorizedHome() {
        String token = getBearerToken();

        if (token == null || token.trim().isEmpty()) {
            dashboardView.setText("\nNot paired yet.");
            setStatus("No bearer token stored. Request pairing and wait for admin approval.");
            return;
        }

        setStatus("Loading mobile summary...");

        new Thread(() -> {
            try {
                HttpResult result = httpRequest(
                    "GET",
                    "/api/mobile/summary",
                    null,
                    token
                );

                if (result.code >= 200 && result.code < 300) {
                    JSONObject json = new JSONObject(result.body);
                    if (json.optBoolean("ok")) {
                        setDashboardFromSummary(json);
                        setStatus("Summary updated.");
                        return;
                    }
                }

                setStatus(result.toDisplayString());
            } catch (Exception e) {
                setStatus(errorText(e));
            }
        }).start();
    }

    private void setDashboardFromSummary(JSONObject json) {
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                JSONObject client = json.optJSONObject("client");
                JSONObject summary = json.optJSONObject("summary");

                String device = "Unknown device";
                String role = "unknown";

                if (client != null) {
                    device = client.optString("display_name",
                        client.optString("device_name",
                            client.optString("device_id", "Unknown device")
                        )
                    );
                    role = client.optString("role", "unknown");
                }

                int total = summary != null ? summary.optInt("instances_total", 0) : 0;
                int running = summary != null ? summary.optInt("running", 0) : 0;
                int stopped = summary != null ? summary.optInt("stopped", 0) : 0;
                int notRunning = summary != null ? summary.optInt("not_running", 0) : 0;
                int containers = summary != null ? summary.optInt("containers_total", 0) : 0;
                int vms = summary != null ? summary.optInt("virtual_machines_total", 0) : 0;
                int errors = summary != null ? summary.optInt("errors", 0) : 0;

                String dashboard =
                    "\nAuthorized" +
                    "\nDevice: " + device +
                    "\nRole: " + role +
                    "\n" +
                    "\nInstances" +
                    "\nTotal: " + total +
                    "\nRunning: " + running +
                    "\nStopped: " + stopped +
                    "\nNot Running: " + notRunning +
                    "\nContainers: " + containers +
                    "\nVMs: " + vms +
                    "\nErrors: " + errors;

                dashboardView.setText(dashboard);
            } catch (Exception e) {
                dashboardView.setText("\nUnable to render summary.");
                setStatus(errorText(e));
            }
        });
    }


    private void loadInstances() {
        String token = getBearerToken();

        if (token == null || token.trim().isEmpty()) {
            setStatus("No bearer token stored. Request pairing and wait for admin approval.");
            return;
        }

        setStatus("Loading instances...");

        new Thread(() -> {
            try {
                HttpResult result = httpRequest(
                    "GET",
                    "/api/mobile/instances",
                    null,
                    token
                );

                if (result.code >= 200 && result.code < 300) {
                    JSONObject json = new JSONObject(result.body);
                    if (json.optBoolean("ok")) {
                        setInstancesFromResponse(json);
                        setStatus("Instances updated.");
                        return;
                    }
                }

                setStatus(result.toDisplayString());
            } catch (Exception e) {
                setStatus(errorText(e));
            }
        }).start();
    }

    private void setInstancesFromResponse(JSONObject json) {
        lastInstances = json.optJSONArray("instances");
        renderRemoteSummary();
        renderInstancesList();
    }

    private void renderRemoteSummary() {
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                if (remoteSummaryView == null) {
                    return;
                }

                if (lastInstances == null || lastInstances.length() == 0) {
                    remoteSummaryView.setText("\nServers\nNo server data loaded.");
                    return;
                }

                Map<String, int[]> counts = new LinkedHashMap<>();

                for (int i = 0; i < lastInstances.length(); i++) {
                    JSONObject item = lastInstances.optJSONObject(i);
                    if (item == null) {
                        continue;
                    }

                    String remote = item.optString("remote", "unknown");
                    if (remote.trim().isEmpty()) {
                        remote = "unknown";
                    }

                    int[] row = counts.get(remote);
                    if (row == null) {
                        // total, running, stopped, errors
                        row = new int[] {0, 0, 0, 0};
                        counts.put(remote, row);
                    }

                    if (item.optBoolean("error")) {
                        row[3]++;
                        continue;
                    }

                    row[0]++;

                    String status = item.optString("status", "");
                    if ("Running".equalsIgnoreCase(status)) {
                        row[1]++;
                    } else if ("Stopped".equalsIgnoreCase(status)) {
                        row[2]++;
                    }
                }

                ArrayList<String> remotes = new ArrayList<>(counts.keySet());
                Collections.sort(remotes);

                StringBuilder out = new StringBuilder();
                out.append("\nServers\n");

                for (String remote : remotes) {
                    int[] row = counts.get(remote);
                    out.append("\n")
                       .append(remote)
                       .append("\n  ")
                       .append(row[0])
                       .append(" instances");

                    if (row[1] > 0 || row[2] > 0) {
                        out.append(" / ")
                           .append(row[1])
                           .append(" running / ")
                           .append(row[2])
                           .append(" stopped");
                    }

                    if (row[3] > 0) {
                        out.append(" / ")
                           .append(row[3])
                           .append(" errors");
                    }

                    out.append("\n");
                }

                remoteSummaryView.setText(out.toString());
            } catch (Exception e) {
                remoteSummaryView.setText("\nServers\nUnable to render server summary.");
            }
        });
    }

    private void renderSelectedServerSummary(String serverFilter) {
        if (selectedServerView == null) {
            return;
        }

        try {
            if (lastInstances == null || lastInstances.length() == 0) {
                selectedServerView.setText("\nSelected Server\nNo server data loaded.");
                return;
            }

            if (serverFilter == null || serverFilter.trim().isEmpty()) {
                selectedServerView.setText("\nSelected Server\nNo server selected. Type a server filter to drill down.");
                return;
            }

            String normalizedFilter = serverFilter.trim().toLowerCase();

            String selectedRemote = "";
            int total = 0;
            int running = 0;
            int stopped = 0;
            int errors = 0;

            for (int i = 0; i < lastInstances.length(); i++) {
                JSONObject item = lastInstances.optJSONObject(i);
                if (item == null) {
                    continue;
                }

                String remote = item.optString("remote", "unknown");
                if (remote == null || remote.trim().isEmpty()) {
                    remote = "unknown";
                }

                if (!remote.toLowerCase().contains(normalizedFilter)) {
                    continue;
                }

                if (selectedRemote.isEmpty()) {
                    selectedRemote = remote;
                }

                if (item.optBoolean("error")) {
                    errors++;
                    continue;
                }

                total++;

                String status = item.optString("status", "");
                if ("Running".equalsIgnoreCase(status)) {
                    running++;
                } else if ("Stopped".equalsIgnoreCase(status)) {
                    stopped++;
                }
            }

            if (selectedRemote.isEmpty() && errors == 0 && total == 0) {
                selectedServerView.setText(
                    "\nSelected Server\nNo server matching \"" + serverFilter + "\"."
                );
                return;
            }

            StringBuilder out = new StringBuilder();
            out.append("\nSelected Server\n")
               .append(selectedRemote.isEmpty() ? serverFilter : selectedRemote)
               .append("\n")
               .append("Instances: ")
               .append(total)
               .append("\n")
               .append("Running: ")
               .append(running)
               .append("\n")
               .append("Stopped: ")
               .append(stopped)
               .append("\n")
               .append("Errors: ")
               .append(errors);

            selectedServerView.setText(out.toString());
        } catch (Exception e) {
            selectedServerView.setText("\nSelected Server\nUnable to render server selection.");
        }
    }

    private void renderInstancesList() {
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                if (instancesView == null || instanceDetailView == null) {
                    return;
                }

                if (lastInstances == null || lastInstances.length() == 0) {
                    instancesView.setText("\nInstances\nNo instances returned.");
                    return;
                }

                String serverFilter = serverFilterInput != null
                    ? serverFilterInput.getText().toString().trim().toLowerCase()
                    : "";

                String filter = instanceFilterInput != null
                    ? instanceFilterInput.getText().toString().trim().toLowerCase()
                    : "";

                renderSelectedServerSummary(serverFilter);

                StringBuilder out = new StringBuilder();
                int matched = 0;
                int shown = 0;
                int maxShown = 50;
                JSONObject firstMatch = null;

                out.append("\nInstances\n");

                if (!serverFilter.isEmpty()) {
                    out.append("Server: ").append(serverFilter).append("\n");
                }

                if (!filter.isEmpty()) {
                    out.append("Filter: ").append(filter).append("\n");
                }

                for (int i = 0; i < lastInstances.length(); i++) {
                    JSONObject item = lastInstances.optJSONObject(i);
                    if (item == null) {
                        continue;
                    }

                    String remote = item.optString("remote", "");
                    String name = item.optString("name", item.optString("instance", item.optString("id", "")));
                    String type = item.optString("type", "");
                    String status = item.optString("status", "");
                    String error = item.optString("error", "");

                    String remoteSearchable = remote.toLowerCase();

                    if (!serverFilter.isEmpty() && !remoteSearchable.contains(serverFilter)) {
                        continue;
                    }

                    String searchable = (remote + " " + name + " " + type + " " + status + " " + error).toLowerCase();

                    if (!filter.isEmpty() && !searchable.contains(filter)) {
                        continue;
                    }

                    matched++;

                    if (firstMatch == null) {
                        firstMatch = item;
                    }

                    if (shown >= maxShown) {
                        continue;
                    }

                    shown++;

                    if (item.optBoolean("error")) {
                        out.append("\n")
                           .append(remote.isEmpty() ? "unknown" : remote)
                           .append(": ERROR - ")
                           .append(error.isEmpty() ? "unknown error" : error)
                           .append("\n");
                        continue;
                    }

                    out.append("\n")
                       .append(remote)
                       .append(":")
                       .append(name)
                       .append("\n  ")
                       .append(status)
                       .append(" / ")
                       .append(type)
                       .append("\n");
                }

                if (matched == 0) {
                    out.append("\nNo matching instances.");
                    setInstanceDetail(null);
                } else {
                    out.append("\nShowing ")
                       .append(shown)
                       .append(" of ")
                       .append(matched)
                       .append(" matching instances.");

                    if (lastInstances.length() != matched) {
                        out.append("\nTotal loaded: ")
                           .append(lastInstances.length());
                    }
                }

                instancesView.setText(out.toString());
                setInstanceDetail(firstMatch);
            } catch (Exception e) {
                instancesView.setText("\nUnable to render instances.");
                setStatus(errorText(e));
            }
        });
    }

    private void setInstanceDetail(JSONObject item) {
        if (instanceDetailView == null) {
            return;
        }

        try {
            if (item == null) {
                instanceDetailView.setText("\nSelected Instance\nNo instance selected.");
                return;
            }

            if (item.optBoolean("error")) {
                String remote = item.optString("remote", "unknown");
                String error = item.optString("error", "unknown error");

                instanceDetailView.setText(
                    "\nSelected Instance\n" +
                    remote + ": ERROR\n" +
                    "Error: " + error
                );
                return;
            }

            String remote = item.optString("remote", "");
            String project = item.optString("project", "");
            String name = item.optString("name", item.optString("instance", item.optString("id", "")));
            String type = item.optString("type", "");
            String status = item.optString("status", "");
            String architecture = item.optString("architecture", "");
            String location = item.optString("location", "");
            String createdAt = item.optString("created_at", "");
            String lastUsedAt = item.optString("last_used_at", "");

            StringBuilder detail = new StringBuilder();
            detail.append("\nSelected Instance\n");

            if (!remote.isEmpty() || !name.isEmpty()) {
                detail.append(remote).append(":").append(name).append("\n");
            }

            appendDetailLine(detail, "Remote", remote);
            appendDetailLine(detail, "Project", project);
            appendDetailLine(detail, "Name", name);
            appendDetailLine(detail, "Type", type);
            appendDetailLine(detail, "Status", status);
            appendDetailLine(detail, "Architecture", architecture);
            appendDetailLine(detail, "Location", location);
            appendDetailLine(detail, "Created", createdAt);
            appendDetailLine(detail, "Last Used", lastUsedAt);

            instanceDetailView.setText(detail.toString());
        } catch (Exception e) {
            instanceDetailView.setText("\nSelected Instance\nUnable to render instance detail.");
        }
    }

    private void appendDetailLine(StringBuilder builder, String label, String value) {
        if (value == null || value.trim().isEmpty() || "null".equalsIgnoreCase(value.trim())) {
            return;
        }

        builder.append(label).append(": ").append(value).append("\n");
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
    @Override
    protected void onDestroy() {
        stopPairingPolling();
        super.onDestroy();
    }

}
