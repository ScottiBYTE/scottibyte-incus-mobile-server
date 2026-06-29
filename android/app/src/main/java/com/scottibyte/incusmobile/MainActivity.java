package com.scottibyte.incusmobile;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.view.View;
import android.view.WindowManager;

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
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class MainActivity extends Activity {
    private static final String API_BASE_URL = "https://incusmobile.scottibyte.com";
    private static final String APP_VERSION = "0.3.16";
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
    private Button backToServersButton;
    private Button backToInstancesButton;
    private Button resetButton;
    private TextView dashboardView;
    private TextView remoteSummaryView;
    private TextView serversSectionView;
    private LinearLayout fixedHeaderLayout;
    private TextView brandTitleView;
    private TextView headerStatsView;
    private Button headerDetailsButton;
    private TextView headerDetailsView;
    private boolean headerDetailsVisible = false;
    private ScrollView mainScrollView;
    private LinearLayout serverCardsContainer;
    private TextView selectedServerView;
    private TextView instancesView;
    private LinearLayout instanceCardsContainer;
    private TextView instanceDetailView;
    private LinearLayout selectedInstanceCardContainer;
    private EditText serverFilterInput;
    private EditText instanceFilterInput;
    private JSONArray lastInstances = null;
    private String selectedInstanceKey = "";
    private boolean suppressFilterEvents = false;
    private EditText deviceNameInput;
    private final Handler pairingHandler = new Handler(Looper.getMainLooper());
    private boolean pairingPollingActive = false;
    private static final long PAIRING_POLL_INTERVAL_MS = 5000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(0xFF050716);
        getWindow().setNavigationBarColor(0xFF050716);
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        ensureDeviceId();

        LinearLayout rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setFitsSystemWindows(true);
        rootLayout.setBackgroundColor(0xFF050716);

        fixedHeaderLayout = new LinearLayout(this);
        fixedHeaderLayout.setOrientation(LinearLayout.VERTICAL);
        fixedHeaderLayout.setPadding(16, 0, 16, 8);
        fixedHeaderLayout.setBackground(makeRoundedBackground(0xFF050716, 0xFF0F172A, 1, 0));

        LinearLayout brandRow = new LinearLayout(this);
        brandRow.setOrientation(LinearLayout.HORIZONTAL);
        brandRow.setGravity(Gravity.CENTER_VERTICAL);
        brandRow.setPadding(0, 0, 0, 6);

        ImageView brandLogoView = new ImageView(this);
        brandLogoView.setImageResource(R.drawable.app_icon);
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(108, 108);
        logoParams.setMargins(0, 0, 16, 0);
        brandLogoView.setLayoutParams(logoParams);

        LinearLayout brandTextColumn = new LinearLayout(this);
        brandTextColumn.setOrientation(LinearLayout.VERTICAL);
        brandTextColumn.setLayoutParams(new LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1
        ));

        brandTitleView = new TextView(this);
        brandTitleView.setText("ScottiBYTE Incus Mobile");
        brandTitleView.setTextSize(22);
        brandTitleView.setTypeface(Typeface.DEFAULT_BOLD);
        brandTitleView.setTextColor(0xFFFFFFFF);

        headerStatsView = new TextView(this);
        headerStatsView.setText("Loading servers...");
        headerStatsView.setTextSize(14);
        headerStatsView.setTextColor(0xFFD1D5DB);

        brandTextColumn.addView(brandTitleView);
        brandTextColumn.addView(headerStatsView);

        brandRow.addView(brandLogoView);
        brandRow.addView(brandTextColumn);

        headerDetailsButton = new Button(this);
        headerDetailsButton.setText("Connection details ▸");
        styleBubbleButton(headerDetailsButton);

        LinearLayout.LayoutParams headerDetailsButtonParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        headerDetailsButtonParams.setMargins(0, 2, 0, 8);
        headerDetailsButton.setLayoutParams(headerDetailsButtonParams);

        headerDetailsButton.setOnClickListener(v -> {
            headerDetailsVisible = !headerDetailsVisible;
            updateHeaderDetailsView();
        });

        headerDetailsView = new TextView(this);
        headerDetailsView.setTextSize(13);
        headerDetailsView.setTextColor(0xFFE5E7EB);
        headerDetailsView.setGravity(Gravity.START);
        headerDetailsView.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        headerDetailsView.setPadding(22, 18, 22, 18);
        headerDetailsView.setBackground(makeGlassBackground(0xDD10233F, 0xBB07111F, 0x6638BDF8, 1, 24));
        headerDetailsView.setElevation(7f);

        LinearLayout.LayoutParams headerDetailsViewParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        headerDetailsViewParams.setMargins(0, 8, 0, 12);
        headerDetailsView.setLayoutParams(headerDetailsViewParams);

        headerDetailsView.setVisibility(View.GONE);

        fixedHeaderLayout.addView(brandRow);
        fixedHeaderLayout.addView(headerDetailsButton);
        fixedHeaderLayout.addView(headerDetailsView);

        ScrollView scroll = new ScrollView(this);
        mainScrollView = scroll;

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(36, 18, 36, 36);

        TextView title = new TextView(this);
        title.setText("ScottiBYTE Incus Mobile");
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        title.setVisibility(View.GONE);

        TextView apiUrl = new TextView(this);
        apiUrl.setText("\nAPI Base URL:\n" + API_BASE_URL);
        apiUrl.setTextSize(15);
        apiUrl.setVisibility(View.GONE);

        deviceIdView = new TextView(this);
        deviceIdView.setText("\nDevice ID:\n" + getLocalDeviceId());
        deviceIdView.setTextSize(14);
        deviceIdView.setVisibility(View.GONE);

        deviceNameInput = new EditText(this);
        deviceNameInput.setHint("Device name");
        deviceNameInput.setSingleLine(true);
        deviceNameInput.setText(getDeviceName());
        deviceNameInput.setVisibility(View.GONE);

        healthButton = new Button(this);
        healthButton.setText("Server Health");
        healthButton.setOnClickListener(v -> testHealth());
        healthButton.setVisibility(View.GONE);

        requestPairingButton = new Button(this);
        requestPairingButton.setText("Request Pairing");
        requestPairingButton.setOnClickListener(v -> requestPairing());

        checkApprovalButton = new Button(this);
        checkApprovalButton.setText("Check Pairing Status");
        checkApprovalButton.setOnClickListener(v -> checkPairingStatus());

        summaryButton = new Button(this);
        summaryButton.setText("Refresh");
        summaryButton.setOnClickListener(v -> loadAuthorizedHome());
        summaryButton.setVisibility(View.GONE);

        instancesButton = new Button(this);
        instancesButton.setText("Load / Refresh");
        instancesButton.setOnClickListener(v -> loadInstances());
        instancesButton.setVisibility(View.GONE);

        backToServersButton = new Button(this);
        backToServersButton.setText("Back to Servers");
        styleBubbleButton(backToServersButton);

        LinearLayout.LayoutParams backToServersParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        backToServersParams.setMargins(0, 8, 0, 8);
        backToServersButton.setLayoutParams(backToServersParams);

        backToServersButton.setOnClickListener(v -> showServerListView());

        backToInstancesButton = new Button(this);
        backToInstancesButton.setText("Back to Instances");
        styleBubbleButton(backToInstancesButton);

        LinearLayout.LayoutParams backToInstancesParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        backToInstancesParams.setMargins(0, 0, 0, 14);
        backToInstancesButton.setLayoutParams(backToInstancesParams);

        backToInstancesButton.setOnClickListener(v -> {
            selectedInstanceKey = "";
            showServerDrilldownView();
            renderInstancesList();
        });
        backToInstancesButton.setVisibility(View.GONE);

        resetButton = new Button(this);
        resetButton.setText("Reset Pairing");
        resetButton.setOnClickListener(v -> resetLocalToken());
        resetButton.setVisibility(View.GONE);

        tokenView = new TextView(this);
        tokenView.setTextSize(14);
        tokenView.setVisibility(View.GONE);

        dashboardView = new TextView(this);
        dashboardView.setTextSize(18);
        dashboardView.setVisibility(View.GONE);
        dashboardView.setTypeface(Typeface.DEFAULT_BOLD);
        dashboardView.setText("\nHome\nNot paired yet.");

        remoteSummaryView = new TextView(this);
        remoteSummaryView.setTextSize(14);
        remoteSummaryView.setText("\nServers\nTap Load / Refresh to load server cards.");
        remoteSummaryView.setVisibility(View.GONE);

        serversSectionView = new TextView(this);
        serversSectionView.setText("\nServers");
        serversSectionView.setTextSize(18);
        serversSectionView.setTypeface(Typeface.DEFAULT_BOLD);
        serversSectionView.setTextColor(0xFFFFFFFF);

        serverCardsContainer = new LinearLayout(this);
        serverCardsContainer.setOrientation(LinearLayout.VERTICAL);

        selectedServerView = new TextView(this);
        selectedServerView.setTextSize(14);
        selectedServerView.setText("\nSelected Server\nNo server selected.");

        instancesView = new TextView(this);
        instancesView.setTextSize(14);
        instancesView.setText("\nInstances: not loaded");
        instancesView.setVisibility(View.GONE);

        instanceCardsContainer = new LinearLayout(this);
        instanceCardsContainer.setOrientation(LinearLayout.VERTICAL);

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
                if (suppressFilterEvents) {
                    return;
                }

                if (instancesView != null && instanceDetailView != null && lastInstances != null) {
                    selectedInstanceKey = "";
                    renderRemoteSummary();
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
                if (suppressFilterEvents) {
                    return;
                }

                if (instancesView != null && instanceDetailView != null && lastInstances != null) {
                    selectedInstanceKey = "";
                    renderInstancesList();
                }
            }
        });

        instanceDetailView = new TextView(this);
        instanceDetailView.setTextSize(14);
        instanceDetailView.setText("");

        selectedInstanceCardContainer = new LinearLayout(this);
        selectedInstanceCardContainer.setOrientation(LinearLayout.VERTICAL);

        statusView = new TextView(this);
        statusView.setText("\nStatus: Ready");
        statusView.setTextSize(16);
        statusView.setVisibility(View.GONE);

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
        layout.addView(serversSectionView);
        layout.addView(remoteSummaryView);
        layout.addView(serverCardsContainer);
        layout.addView(selectedServerView);
        layout.addView(backToServersButton);
        layout.addView(backToInstancesButton);
        layout.addView(serverFilterInput);
        layout.addView(instanceFilterInput);
        layout.addView(instancesView);
        layout.addView(instanceCardsContainer);
        layout.addView(instanceDetailView);
        layout.addView(selectedInstanceCardContainer);
        layout.addView(statusView);

        scroll.addView(layout);

        rootLayout.addView(fixedHeaderLayout);
        rootLayout.addView(scroll, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1
        ));

        setContentView(rootLayout);

        refreshTokenStatus();
        updateHeaderDetailsView();
        updateAuthUiVisibility();
        showServerListView();

        if (hasBearerToken()) {
            loadInstances();
        }
    }

    private void scrollContentToTop() {
        if (mainScrollView != null) {
            mainScrollView.post(() -> mainScrollView.smoothScrollTo(0, 0));
        }
    }

    private void showServerListView() {
        selectedInstanceKey = "";
        suppressFilterEvents = true;

        if (serverFilterInput != null) {
            serverFilterInput.setText("");
            serverFilterInput.setVisibility(View.GONE);
        }

        if (instanceFilterInput != null) {
            instanceFilterInput.setText("");
            instanceFilterInput.setVisibility(View.GONE);
        }

        if (serversSectionView != null) {
            serversSectionView.setVisibility(View.VISIBLE);
        }

        if (serverCardsContainer != null) {
            serverCardsContainer.setVisibility(View.VISIBLE);
        }

        if (selectedServerView != null) {
            selectedServerView.setText("\nSelected Server\nNo server selected.");
            selectedServerView.setVisibility(View.GONE);
        }

        if (backToServersButton != null) {
            backToServersButton.setVisibility(View.GONE);
        }

        if (backToInstancesButton != null) {
            backToInstancesButton.setVisibility(View.GONE);
        }

        if (remoteSummaryView != null) {
            remoteSummaryView.setVisibility(View.GONE);
        }

        if (instancesView != null) {
            instancesView.setText("\nInstances\nSelect a server to view instances.");
            instancesView.setVisibility(View.GONE);
        }

        if (instanceCardsContainer != null) {
            instanceCardsContainer.removeAllViews();
            instanceCardsContainer.setVisibility(View.GONE);
        }

        if (instanceDetailView != null) {
            instanceDetailView.setText("");
            instanceDetailView.setVisibility(View.GONE);
        }

        if (selectedInstanceCardContainer != null) {
            selectedInstanceCardContainer.removeAllViews();
            selectedInstanceCardContainer.setVisibility(View.GONE);
        }

        suppressFilterEvents = false;
        hidePrototypeTextViews();
        scrollContentToTop();
    }

    private void showServerDrilldownView() {
        if (serversSectionView != null) {
            serversSectionView.setVisibility(View.GONE);
        }

        if (serverCardsContainer != null) {
            serverCardsContainer.setVisibility(View.GONE);
        }

        if (selectedServerView != null) {
            selectedServerView.setVisibility(View.GONE);
        }

        if (backToServersButton != null) {
            backToServersButton.setVisibility(View.VISIBLE);
        }

        if (backToInstancesButton != null) {
            backToInstancesButton.setVisibility(View.GONE);
        }

        if (serverFilterInput != null) {
            serverFilterInput.setVisibility(View.GONE);
        }

        if (instanceFilterInput != null) {
            instanceFilterInput.setVisibility(View.GONE);
        }

        if (remoteSummaryView != null) {
            remoteSummaryView.setVisibility(View.GONE);
        }

        if (instancesView != null) {
            instancesView.setVisibility(View.GONE);
        }

        if (instanceCardsContainer != null) {
            instanceCardsContainer.setVisibility(View.VISIBLE);
        }

        if (instanceDetailView != null) {
            instanceDetailView.setText("");
            instanceDetailView.setVisibility(View.GONE);
        }

        if (selectedInstanceCardContainer != null) {
            selectedInstanceCardContainer.removeAllViews();
            selectedInstanceCardContainer.setVisibility(View.GONE);
        }

        hidePrototypeTextViews();
        scrollContentToTop();
    }

    private void showInstanceDetailView() {
        if (serversSectionView != null) {
            serversSectionView.setVisibility(View.GONE);
        }

        if (serverCardsContainer != null) {
            serverCardsContainer.setVisibility(View.GONE);
        }

        if (selectedServerView != null) {
            selectedServerView.setVisibility(View.GONE);
        }

        if (backToServersButton != null) {
            backToServersButton.setVisibility(View.VISIBLE);
        }

        if (backToInstancesButton != null) {
            backToInstancesButton.setVisibility(View.VISIBLE);
        }

        if (serverFilterInput != null) {
            serverFilterInput.setVisibility(View.GONE);
        }

        if (instanceFilterInput != null) {
            instanceFilterInput.setVisibility(View.GONE);
        }

        if (remoteSummaryView != null) {
            remoteSummaryView.setVisibility(View.GONE);
        }

        if (instancesView != null) {
            instancesView.setVisibility(View.GONE);
        }

        if (instanceCardsContainer != null) {
            instanceCardsContainer.removeAllViews();
            instanceCardsContainer.setVisibility(View.GONE);
        }

        if (instanceDetailView != null) {
            instanceDetailView.setText("");
            instanceDetailView.setVisibility(View.GONE);
        }

        if (selectedInstanceCardContainer != null) {
            selectedInstanceCardContainer.setVisibility(View.VISIBLE);
        }

        hidePrototypeTextViews();
        scrollContentToTop();
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
            summaryButton.setVisibility(View.GONE);
        }

        if (instancesButton != null) {
            instancesButton.setVisibility(View.GONE);
        }

        if (resetButton != null) {
            resetButton.setVisibility(View.GONE);
        }

        if (healthButton != null) {
            healthButton.setVisibility(View.GONE);
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
        loadInstances();
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
        if (serverCardsContainer != null) {
            serverCardsContainer.removeAllViews();
        }
        selectedServerView.setText("\nSelected Server\nNo server selected.");
        instancesView.setText("\nInstances: not loaded");
        if (instanceCardsContainer != null) {
            instanceCardsContainer.removeAllViews();
        }
        instanceDetailView.setText("");
        setStatus("Local token removed. Server approval was not changed. Request pairing again if needed.");
    }

    private void setStatus(String message) {
        new Handler(Looper.getMainLooper()).post(() ->
            statusView.setText("\nStatus:\n" + message)
        );
    }

    private void hidePrototypeTextViews() {
        if (tokenView != null) {
            tokenView.setVisibility(View.GONE);
        }

        if (dashboardView != null) {
            dashboardView.setVisibility(View.GONE);
        }

        if (remoteSummaryView != null) {
            remoteSummaryView.setVisibility(View.GONE);
        }

        if (instancesView != null) {
            instancesView.setVisibility(View.GONE);
        }

        if (serverFilterInput != null) {
            serverFilterInput.setVisibility(View.GONE);
        }

        if (statusView != null) {
            statusView.setVisibility(View.GONE);
        }
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
                dashboardView.setVisibility(View.GONE);
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

        new Handler(Looper.getMainLooper()).post(() -> {
            String serverFilter = serverFilterInput != null
                ? serverFilterInput.getText().toString().trim()
                : "";

            renderHeaderStats();

            if (serverFilter.isEmpty()) {
                showServerListView();
                renderRemoteSummary();
                setStatus("Servers updated.");
            } else {
                showServerDrilldownView();
                renderRemoteSummary();
                renderInstancesList();
                setStatus("Instances updated.");
            }
        });
    }

    private GradientDrawable makeRoundedBackground(int fillColor, int strokeColor, int strokeWidth, int radius) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(fillColor);
        bg.setCornerRadius(radius);
        bg.setStroke(strokeWidth, strokeColor);
        return bg;
    }

    private GradientDrawable makeGlassBackground(int startColor, int endColor, int strokeColor, int strokeWidth, int radius) {
        GradientDrawable bg = new GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            new int[] { startColor, endColor }
        );
        bg.setCornerRadius(radius);
        bg.setStroke(strokeWidth, strokeColor);
        return bg;
    }

    private void styleBubbleButton(Button button) {
        if (button == null) {
            return;
        }

        button.setAllCaps(false);
        button.setTextColor(0xFFE5E7EB);
        button.setTextSize(15);
        button.setPadding(28, 13, 28, 13);
        button.setBackground(makeGlassBackground(0xEE112744, 0xCC081827, 0xFF38BDF8, 2, 999));
        button.setElevation(9f);
    }

    private void styleGlassCard(LinearLayout card, boolean selected) {
        if (card == null) {
            return;
        }

        int start = selected ? 0xDD1E3A5F : 0x99111827;
        int end = selected ? 0xAA0B2342 : 0x7707111F;
        int stroke = selected ? 0xFF38BDF8 : 0x5538BDF8;

        card.setPadding(30, 24, 30, 24);
        card.setBackground(makeGlassBackground(start, end, stroke, selected ? 3 : 2, 32));
        card.setElevation(selected ? 14f : 9f);
    }
    private String formatTimestampLocal(String value) {
        if (value == null || value.trim().isEmpty() || "null".equalsIgnoreCase(value.trim())) {
            return "Never";
        }

        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd h:mm a z");
            return formatter.withZone(ZoneId.systemDefault()).format(Instant.parse(value));
        } catch (Exception e) {
            return value;
        }
    }
    private void updateHeaderDetailsView() {
        if (headerDetailsButton == null || headerDetailsView == null) {
            return;
        }

        if (!headerDetailsVisible) {
            headerDetailsButton.setText("Connection details ▸");
            headerDetailsView.setVisibility(View.GONE);
            return;
        }

        headerDetailsButton.setText("Connection details ▾");

        String deviceName = getDeviceName();
        if (deviceName == null || deviceName.trim().isEmpty()) {
            deviceName = "Unnamed device";
        }

        headerDetailsView.setText(
            "Server\n" + API_BASE_URL +
            "\n\nClient\n" + deviceName +
            "\n\nVersion\n" + APP_VERSION
        );
        headerDetailsView.setVisibility(View.VISIBLE);
    }
    private void renderHeaderStats() {
        if (headerStatsView == null) {
            return;
        }

        try {
            if (lastInstances == null || lastInstances.length() == 0) {
                headerStatsView.setText("No server data loaded.");
                return;
            }

            java.util.HashSet<String> servers = new java.util.HashSet<>();
            java.util.HashSet<String> reachableServers = new java.util.HashSet<>();

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

                servers.add(remote);

                if (item.optBoolean("error")) {
                    errors++;
                    continue;
                }

                reachableServers.add(remote);

                String status = item.optString("status", "");
                if ("Running".equalsIgnoreCase(status)) {
                    running++;
                } else if ("Stopped".equalsIgnoreCase(status)) {
                    stopped++;
                }
            }

            headerStatsView.setText(
                servers.size() + " servers / " +
                reachableServers.size() + " reachable\n" +
                running + " running / " +
                stopped + " stopped" +
                (errors > 0 ? " / " + errors + " inventory errors" : "")
            );
        } catch (Exception e) {
            headerStatsView.setText("Unable to render summary.");
        }
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
                           .append(" inventory errors");
                    }

                    out.append("\n");
                }

                remoteSummaryView.setText("\nServers");
                renderServerCards(remotes, counts);
            } catch (Exception e) {
                remoteSummaryView.setText("\nServers\nUnable to render server summary.");
            }
        });
    }

    private void renderServerCards(ArrayList<String> remotes, Map<String, int[]> counts) {
        if (serverCardsContainer == null) {
            return;
        }

        serverCardsContainer.removeAllViews();

        for (String remote : remotes) {
            int[] row = counts.get(remote);
            if (row == null) {
                continue;
            }

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(30, 24, 30, 24);

            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            );
            cardParams.setMargins(0, 10, 0, 10);
            card.setLayoutParams(cardParams);

            String selectedServer = serverFilterInput != null
                ? serverFilterInput.getText().toString().trim()
                : "";

            boolean selected = !selectedServer.isEmpty()
                && remote.equalsIgnoreCase(selectedServer);

            styleGlassCard(card, selected);

            TextView title = new TextView(this);
            title.setText(remote);
            title.setTextSize(20);
            title.setTypeface(Typeface.DEFAULT_BOLD);
            title.setTextColor(0xFFFFFFFF);

            TextView countsView = new TextView(this);
            String serverStats = row[0] + (row[0] == 1 ? " instance\n" : " instances\n") +
                row[1] + " running / " +
                row[2] + " stopped";

            if (row[3] > 0) {
                serverStats += "\n" + row[3] + (row[3] == 1 ? " remote query issue" : " remote query issues");
            }

            countsView.setText(serverStats);
            countsView.setTextSize(15);
            countsView.setTextColor(0xFFCBD5E1);
            countsView.setPadding(0, 8, 0, 0);

            card.setClickable(true);
            card.setFocusable(true);
            card.setOnClickListener(v -> {
                selectedInstanceKey = "";
                suppressFilterEvents = true;

                if (serverFilterInput != null) {
                    serverFilterInput.setText(remote);
                    serverFilterInput.setSelection(serverFilterInput.getText().length());
                }

                suppressFilterEvents = false;
                showServerDrilldownView();
                renderInstancesList();
            });

            TextView chevron = new TextView(this);
            chevron.setText("›");
            chevron.setTextSize(34);
            chevron.setTextColor(0xFF7DD3FC);
            chevron.setGravity(Gravity.END);

            card.addView(title);
            card.addView(countsView);
            card.addView(chevron);

            serverCardsContainer.addView(card);
        }
    }

    private boolean remoteMatchesServerFilter(String remote, String serverFilter) {
        if (serverFilter == null || serverFilter.trim().isEmpty()) {
            return true;
        }

        String normalizedFilter = serverFilter.trim().toLowerCase();
        String remoteLower = remote == null ? "" : remote.trim().toLowerCase();

        if (remoteLower.isEmpty()) {
            return false;
        }

        if (hasExactRemoteMatch(normalizedFilter)) {
            return remoteLower.equals(normalizedFilter);
        }

        return remoteLower.contains(normalizedFilter);
    }

    private boolean hasExactRemoteMatch(String serverFilter) {
        if (serverFilter == null || serverFilter.trim().isEmpty() || lastInstances == null) {
            return false;
        }

        String normalized = serverFilter.trim().toLowerCase();

        try {
            for (int i = 0; i < lastInstances.length(); i++) {
                JSONObject item = lastInstances.optJSONObject(i);
                if (item == null) {
                    continue;
                }

                String remote = item.optString("remote", "");
                if (remote != null && remote.trim().toLowerCase().equals(normalized)) {
                    return true;
                }
            }
        } catch (Exception ignored) {
            return false;
        }

        return false;
    }

    private void renderSelectedServerSummary(String serverFilter) {
        if (selectedServerView == null) {
            return;
        }

        try {
            if (lastInstances == null || lastInstances.length() == 0) {
                selectedServerView.setText("\nNo server data loaded.");
                return;
            }

            if (serverFilter == null || serverFilter.trim().isEmpty()) {
                selectedServerView.setText("\nNo server selected.");
                return;
            }

            int total = 0;
            int running = 0;
            int stopped = 0;
            int errors = 0;
            String displayName = serverFilter;

            for (int i = 0; i < lastInstances.length(); i++) {
                JSONObject item = lastInstances.optJSONObject(i);
                if (item == null) {
                    continue;
                }

                String remote = item.optString("remote", "");
                if (!remoteMatchesServerFilter(remote, serverFilter)) {
                    continue;
                }

                if (displayName.equals(serverFilter) && remote != null && !remote.trim().isEmpty()) {
                    displayName = remote;
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

            if (total == 0 && errors == 0) {
                selectedServerView.setText("\n" + serverFilter + "\nNo matching server.");
                return;
            }

            StringBuilder out = new StringBuilder();
            out.append("\n")
               .append(displayName)
               .append("\n")
               .append(total)
               .append(" instances")
               .append("\n")
               .append(running)
               .append(" running / ")
               .append(stopped)
               .append(" stopped");

            if (errors > 0) {
                out.append(" / ").append(errors).append(" inventory errors");
            }

            selectedServerView.setText(out.toString());
        } catch (Exception e) {
            selectedServerView.setText("\nUnable to render selected server.");
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
                ArrayList<JSONObject> matchedInstances = new ArrayList<>();
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

                    if (!remoteMatchesServerFilter(remote, serverFilter)) {
                        continue;
                    }

                    String searchable = (remote + " " + name + " " + type + " " + status + " " + error).toLowerCase();

                    if (!filter.isEmpty() && !searchable.contains(filter)) {
                        continue;
                    }

                    matched++;
                    matchedInstances.add(item);

                    if (firstMatch == null) {
                        firstMatch = item;
                    }

                    if (shown >= maxShown) {
                        continue;
                    }

                    shown++;

                    // Instance details are rendered as graphical cards below.
                }

                if (matched == 0) {
                    out.append("\nNo matching instances.");
                    renderInstanceCards(matchedInstances);
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
                renderInstanceCards(matchedInstances);
                setInstanceDetail(null);
            } catch (Exception e) {
                instancesView.setText("\nUnable to render instances.");
                setStatus(errorText(e));
            }
        });
    }

    private String getInstanceKey(JSONObject item) {
        if (item == null) {
            return "";
        }

        String remote = item.optString("remote", "");
        String project = item.optString("project", "");
        String name = item.optString("name", item.optString("instance", item.optString("id", "")));

        return remote + ":" + project + ":" + name;
    }

    private void renderInstanceCards(ArrayList<JSONObject> instances) {
        if (instanceCardsContainer == null) {
            return;
        }

        instanceCardsContainer.removeAllViews();

        if (instances == null || instances.size() == 0) {
            return;
        }

        int maxCards = Math.min(instances.size(), 50);

        for (int i = 0; i < maxCards; i++) {
            JSONObject item = instances.get(i);
            if (item == null) {
                continue;
            }

            String remote = item.optString("remote", "");
            String project = item.optString("project", "");
            String name = item.optString("name", item.optString("instance", item.optString("id", "")));
            String type = item.optString("type", "");
            String status = item.optString("status", "");
            String error = item.optString("error", "");
            String instanceKey = getInstanceKey(item);
            boolean selected = !selectedInstanceKey.isEmpty() && selectedInstanceKey.equals(instanceKey);

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(28, 22, 28, 22);

            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            );
            cardParams.setMargins(0, 8, 0, 8);
            card.setLayoutParams(cardParams);

            boolean running = "Running".equalsIgnoreCase(status);
            boolean stopped = "Stopped".equalsIgnoreCase(status);
            boolean hasError = item.optBoolean("error") || !error.isEmpty();

            GradientDrawable background = new GradientDrawable();
            background.setCornerRadius(32);

            if (selected) {
                background.setStroke(4, 0xFF60A5FA);
                background.setColor(0xFF1E3A5F);
            } else if (hasError) {
                background.setStroke(3, 0xFFF87171);
                background.setColor(0x99111827);
            } else if (running) {
                background.setStroke(2, 0x5534D399);
                background.setColor(0xFF10261E);
            } else if (stopped) {
                background.setStroke(2, 0x55FBBF24);
                background.setColor(0xFF2A2110);
            } else {
                background.setStroke(2, 0xFF64748B);
                background.setColor(0xAA111827);
            }

            card.setBackground(background);

            TextView title = new TextView(this);
            title.setText(remote + ":" + name);
            title.setTextSize(17);
            title.setTypeface(Typeface.DEFAULT_BOLD);
            title.setTextColor(0xFFFFFFFF);

            TextView meta = new TextView(this);
            if (hasError) {
                meta.setText("ERROR: " + (error.isEmpty() ? "unknown error" : error));
            } else {
                StringBuilder metaText = new StringBuilder();
                metaText.append(status.isEmpty() ? "Unknown" : status)
                    .append(" / ")
                    .append(type.isEmpty() ? "unknown type" : type);

                if (!project.isEmpty()) {
                    metaText.append("\nProject: ").append(project);
                }

                meta.setText(metaText.toString());
            }

            meta.setTextSize(14);
            meta.setTextColor(0xFFD1D5DB);

            card.setClickable(true);
            card.setFocusable(true);
            card.setOnClickListener(v -> {
                selectedInstanceKey = getInstanceKey(item);
                setInstanceDetail(item);
                showInstanceDetailView();
            });

            card.addView(title);
            card.addView(meta);

            instanceCardsContainer.addView(card);
        }
    }

    private void renderSelectedInstanceCard(JSONObject item) {
        if (selectedInstanceCardContainer == null) {
            return;
        }

        selectedInstanceCardContainer.removeAllViews();

        if (item == null) {
            TextView empty = new TextView(this);
            empty.setText("No instance selected.");
            empty.setTextSize(14);
            empty.setTextColor(0xFFD1D5DB);
            selectedInstanceCardContainer.addView(empty);
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
        String error = item.optString("error", "");

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(30, 24, 30, 24);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 10, 0, 16);
        card.setLayoutParams(params);

        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(26);
        background.setStroke(4, 0xFF60A5FA);
        background.setColor(0xAA111827);
        card.setBackground(background);

        TextView title = new TextView(this);
        title.setText(name.isEmpty() ? "Selected Instance" : name);
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(0xFFFFFFFF);

        TextView details = new TextView(this);
        StringBuilder out = new StringBuilder();

        if (item.optBoolean("error") || !error.isEmpty()) {
            out.append("Remote: ").append(remote).append("\n");
            out.append("Error: ").append(error.isEmpty() ? "unknown error" : error).append("\n");
        } else {
            appendDetailLine(out, "Remote", remote);
            appendDetailLine(out, "Project", project);
            appendDetailLine(out, "Type", type);
            appendDetailLine(out, "Status", status);
            appendDetailLine(out, "Architecture", architecture);
            appendDetailLine(out, "Location", location);
            appendDetailLine(out, "Created", formatTimestampLocal(createdAt));
            if (!"Running".equalsIgnoreCase(item.optString("status", ""))) {
                appendDetailLine(out, "Last Used", formatTimestampLocal(lastUsedAt));
            }
        }

        details.setText(out.toString().trim());
        details.setTextSize(14);
        details.setTextColor(0xFFD1D5DB);

        card.addView(title);
        card.addView(details);

        selectedInstanceCardContainer.addView(card);
    }

    private void setInstanceDetail(JSONObject item) {
        if (instanceDetailView == null) {
            return;
        }

        try {
            renderSelectedInstanceCard(item);

            if (item == null) {
                instanceDetailView.setText("");
                return;
            }

            if (item.optBoolean("error")) {
                String remote = item.optString("remote", "unknown");
                String error = item.optString("error", "unknown error");

                instanceDetailView.setText(
                    "" +
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
            detail.append("");

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
            appendDetailLine(detail, "Created", formatTimestampLocal(createdAt));
            if (!"Running".equalsIgnoreCase(item.optString("status", ""))) {
                appendDetailLine(detail, "Last Used", formatTimestampLocal(lastUsedAt));
            }

            instanceDetailView.setText("");
        } catch (Exception e) {
            instanceDetailView.setText("Unable to render instance detail.");
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
