package com.scottibyte.incusmobile;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.content.SharedPreferences;
import android.content.Intent;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.ColorDrawable;
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
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.EditorInfo;
import android.webkit.WebSettings;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

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
import java.util.HashSet;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class MainActivity extends Activity {
    private static final String PREF_SERVER_URL = "server_url";
    private static final String PREF_CLIENT_ROLE = "client_role";
    private static final String DEFAULT_API_BASE_URL = "";
    private static final String APP_VERSION = "0.5.0";
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
    private Button headerVersionButton;
    private TextView headerDetailsView;
    private boolean headerDetailsVisible = false;
    private boolean headerDetailsManuallyOpened = false;
    private final Handler headerCollapseHandler = new Handler(Looper.getMainLooper());
    private final Runnable headerCollapseRunnable = () -> {
        if (hasServerUrl() && hasBearerToken() && !headerDetailsManuallyOpened) {
            headerDetailsVisible = false;
            updateHeaderDetailsView();
        }
    };
    private LinearLayout fixedActionBarLayout;
    private LinearLayout fixedFilterRowLayout;
    private Button clearInstanceFilterButton;

    private String apiBaseUrl = "";
    private String mobileClientRole = "unknown";
    private String latestAndroidVersion = "";
    private String latestAndroidApkUrl = "";
    private String latestAndroidReleaseUrl = "";
    private boolean androidUpdateAvailable = false;
    private LinearLayout serverConfigPanel;
    private EditText serverUrlInput;
    private Button saveServerUrlButton;
    private TextView connectionStatusView;
    private ScrollView mainScrollView;
    private LinearLayout serverCardsContainer;
    private TextView selectedServerView;
    private TextView instancesView;
    private LinearLayout instanceCardsContainer;
    private TextView instanceDetailView;
    private LinearLayout selectedInstanceCardContainer;
    private LinearLayout rootLayout;
    private LinearLayout terminalLayout;
    private TextView terminalTitleView;
    private TextView terminalOutputView;
    private EditText terminalInputView;
    private ScrollView terminalScrollView;
    private Button terminalExitButton;
    private WebView terminalWebView;
    private OkHttpClient terminalHttpClient;
    private WebSocket terminalWebSocket;
    private String terminalTargetId = "";
    private EditText serverFilterInput;
    private EditText instanceFilterInput;
    private JSONArray lastInstances = null;
    private String selectedInstanceKey = "";
    private JSONObject lastSelectedInstance = null;
    private final HashSet<String> allowedOperations = new HashSet<>();
    private boolean mobileActionsEffectiveEnabled = true;
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
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        
        apiBaseUrl = getStoredServerUrl();
        mobileClientRole = prefs.getString(PREF_CLIENT_ROLE, "unknown");
ensureDeviceId();

        rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setFitsSystemWindows(false);
        rootLayout.setBackgroundColor(0xFF050716);

        fixedHeaderLayout = new LinearLayout(this);
        fixedHeaderLayout.setOrientation(LinearLayout.VERTICAL);
        fixedHeaderLayout.setPadding(16, 64, 16, 10);
        fixedHeaderLayout.setBackground(makeRoundedBackground(0xFF050716, 0xFF0F172A, 1, 0));

        LinearLayout brandRow = new LinearLayout(this);
        brandRow.setOrientation(LinearLayout.HORIZONTAL);
        brandRow.setGravity(Gravity.CENTER_VERTICAL);
        brandRow.setPadding(0, 0, 0, 6);

        ImageView brandLogoView = new ImageView(this);
        brandLogoView.setImageResource(R.drawable.app_icon);
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(88, 88);
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
        headerDetailsButtonParams.setMargins(0, 6, 0, 10);
        headerDetailsButton.setLayoutParams(headerDetailsButtonParams);

        headerDetailsButton.setOnClickListener(v -> {
            headerCollapseHandler.removeCallbacks(headerCollapseRunnable);
            headerDetailsVisible = !headerDetailsVisible;
            headerDetailsManuallyOpened = headerDetailsVisible;
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

        headerVersionButton = new Button(this);
        headerVersionButton.setText("Android App " + APP_VERSION + " • Check update ▸");
        styleBubbleButton(headerVersionButton);
        headerVersionButton.setTypeface(Typeface.DEFAULT_BOLD);

        LinearLayout.LayoutParams headerVersionButtonParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        headerVersionButtonParams.setMargins(0, 0, 0, 12);
        headerVersionButton.setLayoutParams(headerVersionButtonParams);
        headerVersionButton.setVisibility(View.GONE);
        headerVersionButton.setOnClickListener(v -> openAndroidUpdateLink());
        updateAndroidVersionButton();

        serverConfigPanel = new LinearLayout(this);
        serverConfigPanel.setOrientation(LinearLayout.VERTICAL);
        serverConfigPanel.setPadding(22, 18, 22, 18);
        serverConfigPanel.setBackground(makeGlassBackground(0xDD10233F, 0xBB07111F, 0x6638BDF8, 1, 24));
        serverConfigPanel.setElevation(7f);

        LinearLayout.LayoutParams serverConfigParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        serverConfigParams.setMargins(0, 8, 0, 12);
        serverConfigPanel.setLayoutParams(serverConfigParams);

        TextView serverConfigTitle = new TextView(this);
        serverConfigTitle.setText("Server URL");
        serverConfigTitle.setTextSize(14);
        serverConfigTitle.setTypeface(Typeface.DEFAULT_BOLD);
        serverConfigTitle.setTextColor(0xFFFFFFFF);

        TextView serverConfigHelp = new TextView(this);
        serverConfigHelp.setText("Enter this phone's Incus Mobile Server address.");
        serverConfigHelp.setTextSize(12);
        serverConfigHelp.setTextColor(0xFFCBD5E1);
        serverConfigHelp.setPadding(0, 4, 0, 10);

        serverUrlInput = new EditText(this);
        serverUrlInput.setSingleLine(true);
        serverUrlInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        serverUrlInput.setText(getApiBaseUrl());
        serverUrlInput.setHint("https://your-incus-mobile-server.example.com");
        serverUrlInput.setTextColor(0xFFFFFFFF);
        serverUrlInput.setHintTextColor(0xFF94A3B8);
        serverUrlInput.setTextSize(14);
        serverUrlInput.setPadding(18, 12, 18, 12);
        serverUrlInput.setBackground(makeGlassBackground(0xAA07111F, 0x8807111F, 0x5538BDF8, 1, 18));

        serverUrlInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                if (headerDetailsView != null) {
                    headerDetailsView.setVisibility(View.GONE);
                }
                if (headerDetailsButton != null) {
                    headerDetailsButton.setText("Connection details ▸");
                }
            }
        });

        serverUrlInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                saveServerUrlFromInput();
                return true;
            }
            return false;
        });

        serverUrlInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
                updateSaveServerUrlButtonVisibility();
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });

        updateSaveServerUrlButtonVisibility();

        saveServerUrlButton = new Button(this);
        saveServerUrlButton.setText("Save Server URL");
        styleBubbleButton(saveServerUrlButton);

        LinearLayout.LayoutParams saveServerUrlParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        saveServerUrlParams.setMargins(0, 12, 0, 0);
        saveServerUrlButton.setLayoutParams(saveServerUrlParams);

        saveServerUrlButton.setOnClickListener(v -> saveServerUrlFromInput());

        connectionStatusView = new TextView(this);
        connectionStatusView.setText("");
        connectionStatusView.setTextSize(13);
        connectionStatusView.setTextColor(0xFFE5E7EB);
        connectionStatusView.setPadding(18, 14, 18, 14);
        connectionStatusView.setBackground(makeGlassBackground(0xAA07111F, 0x8807111F, 0x5538BDF8, 1, 18));
        connectionStatusView.setVisibility(View.GONE);

        LinearLayout.LayoutParams connectionStatusParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        connectionStatusParams.setMargins(0, 12, 0, 0);
        connectionStatusView.setLayoutParams(connectionStatusParams);

        serverConfigPanel.addView(serverConfigTitle);
        serverConfigPanel.addView(serverConfigHelp);
        serverConfigPanel.addView(serverUrlInput);
        serverConfigPanel.addView(saveServerUrlButton);
        serverConfigPanel.addView(connectionStatusView);
        serverConfigPanel.setVisibility(View.GONE);


        fixedHeaderLayout.addView(brandRow);
        fixedHeaderLayout.addView(headerDetailsButton);
        fixedHeaderLayout.addView(headerDetailsView);
        fixedHeaderLayout.addView(headerVersionButton);
        fixedHeaderLayout.addView(serverConfigPanel);

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
        apiUrl.setText("\nAPI Base URL:\n" + (hasServerUrl() ? getApiBaseUrl() : "Not configured"));
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
        summaryButton.setOnClickListener(v -> refreshMobileData());
        styleBubbleButton(summaryButton);
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
        backToServersParams.setMargins(0, 6, 0, 6);
        backToServersButton.setLayoutParams(backToServersParams);

        backToServersButton.setOnClickListener(v -> showServerListView());

        backToInstancesButton = new Button(this);
        backToInstancesButton.setText("Back to Instances");
        styleBubbleButton(backToInstancesButton);

        LinearLayout.LayoutParams backToInstancesParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        backToInstancesParams.setMargins(0, 6, 0, 10);
        backToInstancesButton.setLayoutParams(backToInstancesParams);

        backToInstancesButton.setOnClickListener(v -> {
            selectedInstanceKey = "";
            lastSelectedInstance = null;
            showServerDrilldownView();
            renderInstancesList();
        });
        backToInstancesButton.setVisibility(View.GONE);

        fixedActionBarLayout = new LinearLayout(this);
        fixedActionBarLayout.setOrientation(LinearLayout.VERTICAL);
        fixedActionBarLayout.setPadding(0, 4, 0, 4);
        fixedActionBarLayout.setVisibility(View.GONE);

        LinearLayout.LayoutParams fixedActionBarParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        fixedActionBarParams.setMargins(0, 0, 0, 6);
        fixedActionBarLayout.setLayoutParams(fixedActionBarParams);

        LinearLayout.LayoutParams fixedButtonParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        fixedButtonParams.setMargins(0, 6, 0, 6);

        summaryButton.setLayoutParams(fixedButtonParams);

        LinearLayout.LayoutParams fixedBackToServersParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        fixedBackToServersParams.setMargins(0, 4, 0, 4);
        backToServersButton.setLayoutParams(fixedBackToServersParams);

        LinearLayout.LayoutParams fixedBackToInstancesParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        fixedBackToInstancesParams.setMargins(0, 4, 0, 4);
        backToInstancesButton.setLayoutParams(fixedBackToInstancesParams);

        fixedActionBarLayout.addView(summaryButton);
        fixedActionBarLayout.addView(backToServersButton);
        fixedActionBarLayout.addView(backToInstancesButton);
        fixedHeaderLayout.addView(fixedActionBarLayout);

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
                updateClearFilterButtonVisibility();

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
                updateClearFilterButtonVisibility();

                if (suppressFilterEvents) {
                    return;
                }

                if (instancesView != null && instanceDetailView != null && lastInstances != null) {
                    selectedInstanceKey = "";
                    lastSelectedInstance = null;

                    String serverFilter = serverFilterInput != null
                        ? serverFilterInput.getText().toString().trim()
                        : "";

                    String instanceFilter = instanceFilterInput != null
                        ? instanceFilterInput.getText().toString().trim()
                        : "";

                    /*
                     * Server screen: instance filter searches all servers.
                     * Instance screen: serverFilter is set, so the same filter
                     * searches only that selected server.
                     */
                    if (serverFilter.isEmpty()) {
                        if (instanceFilter.isEmpty()) {
                            if (serverCardsContainer != null) {
                                serverCardsContainer.setVisibility(View.VISIBLE);
                            }
                            if (instanceCardsContainer != null) {
                                instanceCardsContainer.removeAllViews();
                                instanceCardsContainer.setVisibility(View.GONE);
                            }
                            renderRemoteSummary();
                        } else {
                            if (serverCardsContainer != null) {
                                serverCardsContainer.setVisibility(View.GONE);
                            }
                            if (instanceCardsContainer != null) {
                                instanceCardsContainer.setVisibility(View.VISIBLE);
                            }
                            renderInstancesList();
                        }
                    } else {
                        renderInstancesList();
                    }
                }
            }
        });

        styleFilterInput(instanceFilterInput);

        fixedFilterRowLayout = new LinearLayout(this);
        fixedFilterRowLayout.setOrientation(LinearLayout.HORIZONTAL);
        fixedFilterRowLayout.setGravity(Gravity.CENTER_VERTICAL);
        fixedFilterRowLayout.setPadding(0, 0, 0, 0);
        fixedFilterRowLayout.setVisibility(View.GONE);

        LinearLayout.LayoutParams filterRowParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        filterRowParams.setMargins(0, 18, 0, 10);
        fixedFilterRowLayout.setLayoutParams(filterRowParams);

        clearInstanceFilterButton = new Button(this);
        clearInstanceFilterButton.setText("×");
        clearInstanceFilterButton.setAllCaps(false);
        clearInstanceFilterButton.setTextSize(18);
        clearInstanceFilterButton.setTextColor(0xFFFFFFFF);
        clearInstanceFilterButton.setPadding(0, 0, 0, 0);
        clearInstanceFilterButton.setBackground(makeGlassBackground(0xCC1F2937, 0xAA111827, 0x7738BDF8, 1, 22));

        LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(
            54,
            LinearLayout.LayoutParams.MATCH_PARENT
        );
        clearParams.setMargins(0, 0, 0, 0);
        clearInstanceFilterButton.setLayoutParams(clearParams);
        clearInstanceFilterButton.setVisibility(View.GONE);

        clearInstanceFilterButton.setOnClickListener(v -> {
            if (instanceFilterInput != null) {
                instanceFilterInput.setText("");
            }
            updateClearFilterButtonVisibility();
        });

        fixedFilterRowLayout.addView(instanceFilterInput);
        fixedFilterRowLayout.addView(clearInstanceFilterButton);

        if (fixedActionBarLayout != null) {
            fixedActionBarLayout.addView(fixedFilterRowLayout);
        }

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
        layout.addView(instancesButton);
        layout.addView(resetButton);
        layout.addView(tokenView);
        layout.addView(dashboardView);
        layout.addView(serversSectionView);
        layout.addView(remoteSummaryView);
        layout.addView(serverCardsContainer);
        layout.addView(selectedServerView);
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

        buildTerminalLayout();
        rootLayout.addView(terminalLayout, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1
        ));

        setContentView(rootLayout);

        refreshTokenStatus();
        if (!hasServerUrl()) {
            headerDetailsVisible = true;
        }
        updateHeaderDetailsView();
        updateAuthUiVisibility();
        showServerListView();

        if (hasBearerToken() && hasServerUrl()) {
            fetchMobileOperations();
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
            instanceFilterInput.setHint("Search instances across all servers");
            instanceFilterInput.setVisibility(View.VISIBLE);
        }

        if (fixedFilterRowLayout != null) {
            fixedFilterRowLayout.setVisibility(View.VISIBLE);
        }
        updateClearFilterButtonVisibility();

        if (fixedFilterRowLayout != null) {
            fixedFilterRowLayout.setVisibility(View.VISIBLE);
        }
        updateClearFilterButtonVisibility();

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
        updateFixedActionBarVisibility();
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
            instanceFilterInput.setHint("Search instances on this server");
            instanceFilterInput.setVisibility(View.VISIBLE);
        }

        if (fixedFilterRowLayout != null) {
            fixedFilterRowLayout.setVisibility(View.VISIBLE);
        }
        updateClearFilterButtonVisibility();

        if (fixedFilterRowLayout != null) {
            fixedFilterRowLayout.setVisibility(View.VISIBLE);
        }
        updateClearFilterButtonVisibility();

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
        updateFixedActionBarVisibility();
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

        if (fixedFilterRowLayout != null) {
            fixedFilterRowLayout.setVisibility(View.GONE);
        }

        if (fixedFilterRowLayout != null) {
            fixedFilterRowLayout.setVisibility(View.GONE);
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

    private void updateFixedActionBarVisibility() {
        if (fixedActionBarLayout == null) {
            return;
        }

        boolean visible = false;

        if (summaryButton != null && summaryButton.getVisibility() == View.VISIBLE) {
            visible = true;
        }

        if (backToServersButton != null && backToServersButton.getVisibility() == View.VISIBLE) {
            visible = true;
        }

        if (backToInstancesButton != null && backToInstancesButton.getVisibility() == View.VISIBLE) {
            visible = true;
        }

        fixedActionBarLayout.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void updateAuthUiVisibility() {
        boolean authorized = hasBearerToken();

        if (requestPairingButton != null) {
            requestPairingButton.setVisibility(View.GONE);
        }

        if (checkApprovalButton != null) {
            checkApprovalButton.setVisibility(View.GONE);
        }

        if (summaryButton != null) {
            summaryButton.setVisibility(authorized ? View.VISIBLE : View.GONE);
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

        updateFixedActionBarVisibility();
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
        fetchMobileOperations();
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

        /*
         * Do not hide serverFilterInput or instanceFilterInput here.
         * The active screen methods control filter visibility.
         */

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
        if (!hasServerUrl()) {
            setConnectionStatus("Enter and save a Server URL first.");
            headerDetailsVisible = true;
            updateHeaderDetailsView();
            return;
        }

        saveDeviceName();

        String deviceName = getDeviceName();

        if (deviceName.trim().isEmpty()) {
            setConnectionStatus("Device name is required.");
            return;
        }

        setConnectionStatus("Requesting pairing approval...");

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

                    runOnUiThread(() -> {
                        updateClientIdentityFromResponse(json);

                        if ("pending".equals(status)) {
                            setConnectionStatus("Pairing request sent. Waiting for admin approval...");
                            startPairingPolling();
                            return;
                        }

                        if ("approved".equals(status)) {
                            setConnectionStatus("Device approved. Claiming token...");
                            checkPairingStatus(true);
                            return;
                        }

                        if ("revoked".equals(status)) {
                            stopPairingPolling();
                            setConnectionStatus("Pairing was revoked on the server.");
                            return;
                        }

                        setConnectionStatus(result.toDisplayString());
                    });

                    return;
                }

                runOnUiThread(() -> setConnectionStatus(result.toDisplayString()));
            } catch (Exception e) {
                runOnUiThread(() -> showOperationMessage(errorText(e)));
            }
        }).start();
    }

    private void checkPairingStatus() {
        checkPairingStatus(false);
    }

    private void checkPairingStatus(boolean automatic) {
        if (!automatic) {
            setConnectionStatus("Checking pairing status...");
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
                    String status = json.optString("status", "");

                    String token = json.optString("token", "");
                    if (token.trim().isEmpty()) {
                        JSONObject client = json.optJSONObject("client");
                        if (client != null) {
                            token = client.optString("token", "");
                        }
                    }
                    if (token.trim().isEmpty()) {
                        JSONObject mobileClient = json.optJSONObject("mobile_client");
                        if (mobileClient != null) {
                            token = mobileClient.optString("token", "");
                        }
                    }

                    final String finalToken = token;

                    runOnUiThread(() -> {
                        updateClientIdentityFromResponse(json);

                        if (!finalToken.trim().isEmpty()) {
                            saveBearerToken(finalToken);
                            stopPairingPolling();
                            refreshTokenStatus();
                            updateAuthUiVisibility();
                            setConnectionStatus("Approved. Token stored locally. Loading servers...");
                            loadAuthorizedHome();
                            collapseConnectionDetailsSoon();
                            return;
                        }

                        if ("pending".equals(status)) {
                            setConnectionStatus("Pairing request sent. Waiting for admin approval...");
                            if (!pairingPollingActive) {
                                startPairingPolling();
                            }
                            return;
                        }

                        if ("approved".equals(status)) {
                            if (hasBearerToken()) {
                                stopPairingPolling();
                                refreshTokenStatus();
                                updateAuthUiVisibility();
                                String role = json.optString("role", "");
                                if (role == null || role.trim().isEmpty()) {
                                    setConnectionStatus("Paired. Token stored locally.");
                                } else {
                                    setConnectionStatus("Paired as " + role + ". Token stored locally.");
                                }
                                return;
                            }

                            setConnectionStatus("Approved. Waiting for server token...");
                            if (!pairingPollingActive) {
                                startPairingPolling();
                            }
                            return;
                        }

                        if ("revoked".equals(status)) {
                            stopPairingPolling();
                            setConnectionStatus("Pairing was revoked on the server.");
                            return;
                        }

                        if (!automatic) {
                            setConnectionStatus(result.toDisplayString());
                        }
                    });

                    return;
                }

                runOnUiThread(() -> {
                    if (!automatic) {
                        setConnectionStatus(result.toDisplayString());
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> showOperationMessage(errorText(e)));
            }
        }).start();
    }


    private void refreshMobileData() {
        showOperationMessage("Refreshing server data...");
        fetchMobileOperations();

        /*
         * If the user is looking at a selected instance, avoid full home/server
         * repaint. Refresh only instance data so the selected card can update
         * in place.
         */
        if (selectedInstanceKey != null && !selectedInstanceKey.trim().isEmpty()) {
            loadInstances();
            return;
        }

        loadAuthorizedHome();
        loadInstances();
    }


    private void loadAuthorizedHome() {
        String token = getBearerToken();

        if (token == null || token.trim().isEmpty()) {
            dashboardView.setText("\nNot paired yet.");
            setStatus("No bearer token stored. Request pairing and wait for admin approval.");
            return;
        }

        fetchMobileOperations();
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
                    updateClientIdentityFromResponse(json);
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
                    updateClientIdentityFromResponse(json);
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
        updateClientIdentityFromResponse(json);
        lastInstances = json.optJSONArray("instances");

        new Handler(Looper.getMainLooper()).post(() -> {
            String serverFilter = serverFilterInput != null
                ? serverFilterInput.getText().toString().trim()
                : "";

            renderHeaderStats();

            if (serverFilter.isEmpty()) {
                showServerListView();
                renderRemoteSummary();

                String instanceFilter = instanceFilterInput != null
                    ? instanceFilterInput.getText().toString().trim()
                    : "";

                if (!instanceFilter.isEmpty()) {
                    if (serverCardsContainer != null) {
                        serverCardsContainer.setVisibility(View.GONE);
                    }
                    if (instanceCardsContainer != null) {
                        instanceCardsContainer.setVisibility(View.VISIBLE);
                    }
                    renderInstancesList();
                }

                setConnectionStatus("");
                setStatus("Servers updated.");
        collapseConnectionDetailsSoon();
            } else {
                renderRemoteSummary();
                renderInstancesList();

                JSONObject selectedAfterRefresh = findSelectedInstanceObject();

                if (selectedInstanceKey != null && !selectedInstanceKey.trim().isEmpty() && selectedAfterRefresh != null) {
                    showInstanceDetailView();
                    setInstanceDetail(selectedAfterRefresh);
                } else if (selectedInstanceKey != null && !selectedInstanceKey.trim().isEmpty()) {
                    showInstanceDetailView();

                    if (selectedInstanceCardContainer != null) {
                        selectedInstanceCardContainer.removeAllViews();

                        TextView missing = new TextView(this);
                        missing.setText("Selected instance was not found after refresh.");
                        missing.setTextSize(14);
                        missing.setTextColor(0xFFD1D5DB);
                        missing.setPadding(20, 18, 20, 18);

                        selectedInstanceCardContainer.addView(missing);
                        selectedInstanceCardContainer.setVisibility(View.VISIBLE);
                    }
                } else {
                    showServerDrilldownView();
                }

                setConnectionStatus("");
                setStatus("Instances updated.");
        collapseConnectionDetailsSoon();
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

    private void styleFilterInput(EditText input) {
        if (input == null) {
            return;
        }

        input.setSingleLine(true);
        input.setTextColor(0xFFFFFFFF);
        input.setHintTextColor(0xFF94A3B8);
        input.setTextSize(15);
        input.setPadding(22, 13, 22, 13);
        input.setBackground(makeGlassBackground(0xCC07111F, 0xAA07111F, 0x7738BDF8, 1, 22));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1
        );
        params.setMargins(0, 0, 8, 0);
        input.setLayoutParams(params);
    }


    private void updateClearFilterButtonVisibility() {
        if (clearInstanceFilterButton == null || instanceFilterInput == null) {
            return;
        }

        String value = instanceFilterInput.getText() == null
            ? ""
            : instanceFilterInput.getText().toString().trim();

        clearInstanceFilterButton.setVisibility(value.isEmpty() ? View.GONE : View.VISIBLE);
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
    private void collapseConnectionDetailsSoon() {
        headerCollapseHandler.removeCallbacks(headerCollapseRunnable);

        if (headerDetailsManuallyOpened) {
            return;
        }

        headerCollapseHandler.postDelayed(headerCollapseRunnable, 2500);
    }

    private String normalizeServerUrl(String value) {
        if (value == null) {
            return "";
        }

        String normalized = value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        if (!normalized.isEmpty() && !normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "https://" + normalized;
        }

        return normalized;
    }

    private String getStoredServerUrl() {
        if (prefs == null) {
            return DEFAULT_API_BASE_URL;
        }
        return normalizeServerUrl(prefs.getString(PREF_SERVER_URL, DEFAULT_API_BASE_URL));
    }

    private String getApiBaseUrl() {
        return apiBaseUrl == null ? "" : apiBaseUrl;
    }

    private boolean hasServerUrl() {
        return !getApiBaseUrl().trim().isEmpty();
    }

    private String getClientRoleDisplay() {
        if (mobileClientRole == null || mobileClientRole.trim().isEmpty()) {
            return "unknown";
        }
        return mobileClientRole;
    }

    private void updateServerConfigVisibility() {
        if (serverConfigPanel == null) {
            return;
        }

        if (!hasServerUrl() || headerDetailsVisible) {
            serverConfigPanel.setVisibility(View.VISIBLE);
        } else {
            serverConfigPanel.setVisibility(View.GONE);
        }

        if (serverUrlInput != null && !serverUrlInput.hasFocus()) {
            serverUrlInput.setText(getApiBaseUrl());
        }
        updateSaveServerUrlButtonVisibility();
    }

    private void setConnectionStatus(String message) {
        setStatus(message);

        if (connectionStatusView == null) {
            return;
        }

        if (message == null || message.trim().isEmpty()) {
            connectionStatusView.setText("");
            connectionStatusView.setVisibility(View.GONE);
            return;
        }

        connectionStatusView.setText(message);
        connectionStatusView.setVisibility(View.VISIBLE);
    }
    private void updateSaveServerUrlButtonVisibility() {
        if (saveServerUrlButton == null || serverUrlInput == null) {
            return;
        }

        String typedUrl = normalizeServerUrl(serverUrlInput.getText().toString());
        boolean shouldShow = typedUrl.isEmpty() || !typedUrl.equals(getApiBaseUrl());
        saveServerUrlButton.setVisibility(shouldShow ? View.VISIBLE : View.GONE);
    }
    private void hideKeyboard() {
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null && serverUrlInput != null) {
                imm.hideSoftInputFromWindow(serverUrlInput.getWindowToken(), 0);
                serverUrlInput.clearFocus();
            }
        } catch (Exception ignored) {
        }
    }
    private void saveServerUrlFromInput() {
        if (serverUrlInput == null || prefs == null) {
            return;
        }

        String oldUrl = getApiBaseUrl();
        String newUrl = normalizeServerUrl(serverUrlInput.getText().toString());

        if (newUrl.isEmpty()) {
            setConnectionStatus("Enter a server URL first.");
            updateServerConfigVisibility();
            return;
        }

        boolean changed = !newUrl.equals(oldUrl);
        headerDetailsManuallyOpened = false;
        apiBaseUrl = newUrl;

        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(PREF_SERVER_URL, newUrl);

        if (changed) {
            editor.remove(PREF_BEARER_TOKEN);
            editor.putString(PREF_CLIENT_ROLE, "unknown");
            mobileClientRole = "unknown";
        }

        editor.apply();
        hideKeyboard();
        updateSaveServerUrlButtonVisibility();
        refreshTokenStatus();
        updateHeaderDetailsView();
        updateAuthUiVisibility();

        setConnectionStatus("Checking server reachability...");

        new Thread(() -> {
            try {
                HttpResult result = httpRequest("GET", "/api/mobile/health", null, null);

                runOnUiThread(() -> {
                    if (result.code >= 200 && result.code < 300) {
                        setConnectionStatus("Server reachable. Requesting pairing...");
                        requestPairing();
                    } else {
                        setConnectionStatus("Server URL saved, but server health check failed: HTTP " + result.code);
                        headerDetailsVisible = true;
                        updateHeaderDetailsView();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setConnectionStatus("Server URL saved, but server is unreachable: " + e.getMessage());
                    headerDetailsVisible = true;
                    updateHeaderDetailsView();
                });
            }
        }).start();
    }

    private void updateClientIdentityFromResponse(JSONObject json) {
        if (json == null || prefs == null) {
            return;
        }

        String role = "";

        JSONObject client = json.optJSONObject("client");
        if (client != null) {
            role = client.optString("role", "").trim();
        }

        if (role.isEmpty()) {
            JSONObject mobileClient = json.optJSONObject("mobile_client");
            if (mobileClient != null) {
                role = mobileClient.optString("role", "").trim();
            }
        }

        if (role.isEmpty()) {
            role = json.optString("role", "").trim();
        }

        if (!role.isEmpty()) {
            mobileClientRole = role;
            prefs.edit().putString(PREF_CLIENT_ROLE, role).apply();
            updateHeaderDetailsView();
        }
    }

    private void updateHeaderDetailsView() {
        if (headerDetailsButton == null || headerDetailsView == null) {
            return;
        }

        updateServerConfigVisibility();

        if (!headerDetailsVisible) {
            headerDetailsButton.setText("Connection details ▸");
            headerDetailsView.setVisibility(View.GONE);
            if (headerVersionButton != null) {
                headerVersionButton.setVisibility(View.GONE);
            }
            return;
        }

        headerDetailsButton.setText("Connection details ▾");

        String deviceName = getDeviceName();
        if (deviceName == null || deviceName.trim().isEmpty()) {
            deviceName = "Unnamed device";
        }

        String serverText = hasServerUrl() ? getApiBaseUrl() : "Not configured";

        headerDetailsView.setText(
            "Server\n" + serverText +
            "\n\nClient\n" + deviceName +
            "\n\nRole\n" + getClientRoleDisplay()
        );
        headerDetailsView.setVisibility(View.VISIBLE);
        if (headerVersionButton != null) {
            updateAndroidVersionButton();
            headerVersionButton.setVisibility(View.VISIBLE);
        }
        fetchAndroidVersionInfo();
        updateServerConfigVisibility();
    }
    private int compareVersionStrings(String left, String right) {
        String[] a = left == null ? new String[0] : left.split("\\.");
        String[] b = right == null ? new String[0] : right.split("\\.");
        int max = Math.max(a.length, b.length);

        for (int i = 0; i < max; i++) {
            int av = 0;
            int bv = 0;

            try {
                if (i < a.length) {
                    av = Integer.parseInt(a[i].replaceAll("[^0-9].*$", ""));
                }
            } catch (Exception ignored) {
            }

            try {
                if (i < b.length) {
                    bv = Integer.parseInt(b[i].replaceAll("[^0-9].*$", ""));
                }
            } catch (Exception ignored) {
            }

            if (av != bv) {
                return av - bv;
            }
        }

        return 0;
    }

    private void updateAndroidVersionButton() {
        if (headerVersionButton == null) {
            return;
        }

        String label = "Android App " + APP_VERSION;

        if (latestAndroidVersion != null && !latestAndroidVersion.trim().isEmpty()) {
            if (androidUpdateAvailable) {
                label = "Update Android App to " + latestAndroidVersion + " ▸";
            } else {
                label += " • Current";
            }
        } else {
            label += " • Check update ▸";
        }

        headerVersionButton.setText(label);
    }

    private void openAndroidUpdateLink() {
        String url = "";

        if (androidUpdateAvailable && latestAndroidApkUrl != null && !latestAndroidApkUrl.trim().isEmpty()) {
            url = latestAndroidApkUrl.trim();
        } else {
            showOperationMessage("Android app is current: " + APP_VERSION);
            return;
        }

        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        } catch (Exception e) {
            showOperationMessage("Unable to open Android update link: " + e.getMessage());
        }
    }

    private void fetchAndroidVersionInfo() {
        if (!hasServerUrl()) {
            return;
        }

        new Thread(() -> {
            try {
                HttpResult result = httpRequest(
                    "GET",
                    "/api/mobile/android-version",
                    null,
                    null
                );

                if (result.code < 200 || result.code >= 300) {
                    return;
                }

                JSONObject json = new JSONObject(result.body);
                if (!json.optBoolean("ok")) {
                    return;
                }

                String nextVersion = json.optString("android_version", "").trim();
                String apkUrl = json.optString("apk_url", "").trim();
                String releaseUrl = json.optString("release_url", "").trim();

                runOnUiThread(() -> {
                    latestAndroidVersion = nextVersion;
                    latestAndroidApkUrl = apkUrl;
                    latestAndroidReleaseUrl = releaseUrl;
                    androidUpdateAvailable = !latestAndroidVersion.isEmpty()
                        && compareVersionStrings(latestAndroidVersion, APP_VERSION) > 0;
                    updateAndroidVersionButton();
                });
            } catch (Exception ignored) {
            }
        }).start();
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

                boolean globalSearchMode = serverFilter.isEmpty() && !filter.isEmpty();

                if (globalSearchMode) {
                    if (serverCardsContainer != null) {
                        serverCardsContainer.setVisibility(View.GONE);
                    }
                    if (instanceCardsContainer != null) {
                        instanceCardsContainer.setVisibility(View.VISIBLE);
                    }
                }

                /*
                 * Do not clear the selected instance while refreshing the list.
                 * Operation refresh and manual Refresh both call renderInstancesList().
                 * If a container is selected, the selected-detail refresh path will
                 * re-bind that card. Clearing here causes the "No instance selected"
                 * screen after Start/Stop/Restart.
                 */
                if (selectedInstanceKey == null || selectedInstanceKey.trim().isEmpty()) {
                    setInstanceDetail(null);
                }
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

        String remote = item.optString("remote", "").trim();
        String project = item.optString("project", "default").trim();
        if (project.isEmpty()) {
            project = "default";
        }

        String name = item.optString("name",
            item.optString("instance",
                item.optString("id", "")
            )
        ).trim();

        /*
         * Stable across Start / Stop / Restart.
         * Never include status, type, location, or display text here.
         */
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
                lastSelectedInstance = item;
                setInstanceDetail(item);
                showInstanceDetailView();
            });

            card.addView(title);
            card.addView(meta);

            instanceCardsContainer.addView(card);
        }
    }

    private JSONObject findSelectedInstanceObject() {
        if (lastInstances == null || selectedInstanceKey == null || selectedInstanceKey.trim().isEmpty()) {
            return null;
        }

        try {
            String selected = selectedInstanceKey.trim();

            String[] selectedParts = selected.split(":", 3);
            String selectedRemote = selectedParts.length > 0 ? selectedParts[0] : "";
            String selectedProject = selectedParts.length > 1 ? selectedParts[1] : "default";
            String selectedName = selectedParts.length > 2 ? selectedParts[2] : "";

            if (selectedProject == null || selectedProject.trim().isEmpty()) {
                selectedProject = "default";
            }

            for (int i = 0; i < lastInstances.length(); i++) {
                JSONObject item = lastInstances.optJSONObject(i);
                if (item == null) {
                    continue;
                }

                String key = getInstanceKey(item);
                if (selected.equals(key)) {
                    return item;
                }

                String remote = item.optString("remote", "").trim();
                String project = item.optString("project", "default").trim();
                if (project.isEmpty()) {
                    project = "default";
                }

                String name = item.optString("name",
                    item.optString("instance",
                        item.optString("id", "")
                    )
                ).trim();

                // Fallback: remote + name is stable even if project was missing earlier.
                if (remote.equals(selectedRemote) && name.equals(selectedName)) {
                    return item;
                }
            }
        } catch (Exception ignored) {
        }

        return null;
    }


    private void refreshSelectedInstanceCard() {
        JSONObject selected = findSelectedInstanceObject();

        if (selected != null) {
            lastSelectedInstance = selected;
            selectedInstanceKey = getInstanceKey(selected);
            renderSelectedInstanceCard(selected);
            return;
        }

        /*
         * Do not blank the selected instance screen just because a refresh has
         * not re-bound the object yet. Keep the last known selected card visible.
         */
        if (lastSelectedInstance != null) {
            renderSelectedInstanceCard(lastSelectedInstance);
        }
    }


    private boolean updateMobileActionsStatusFromResponse(JSONObject json) {
        boolean previous = mobileActionsEffectiveEnabled;

        JSONObject mobileActions = json.optJSONObject("mobile_actions");
        if (mobileActions != null) {
            mobileActionsEffectiveEnabled = mobileActions.optBoolean("effective_enabled", true);
        } else if (json.has("actions_enabled")) {
            mobileActionsEffectiveEnabled = json.optBoolean("actions_enabled", true);
        }

        if (!mobileActionsEffectiveEnabled) {
            allowedOperations.clear();
        }

        return previous != mobileActionsEffectiveEnabled;
    }

    private boolean hasAllowedOperation(String operation) {
        return mobileActionsEffectiveEnabled && allowedOperations.contains(operation);
    }

    private void fetchMobileOperations() {
        if (!hasBearerToken() || !hasServerUrl()) {
            allowedOperations.clear();
            return;
        }

        String token = getBearerToken();

        new Thread(() -> {
            try {
                HttpResult result = httpRequest(
                    "GET",
                    "/api/mobile/operations",
                    null,
                    token
                );

                if (result.code >= 200 && result.code < 300) {
                    JSONObject json = new JSONObject(result.body);
                    JSONArray ops = json.optJSONArray("operations");

                    HashSet<String> nextAllowed = new HashSet<>();

                    if (ops != null) {
                        for (int i = 0; i < ops.length(); i++) {
                            JSONObject op = ops.optJSONObject(i);
                            if (op == null) {
                                continue;
                            }

                            String key = op.optString("operation", "");
                            if (!key.trim().isEmpty()) {
                                nextAllowed.add(key);
                            }
                        }
                    }

                    runOnUiThread(() -> {
                        updateClientIdentityFromResponse(json);
                        updateMobileActionsStatusFromResponse(json);

                        allowedOperations.clear();
                        if (mobileActionsEffectiveEnabled) {
                            allowedOperations.addAll(nextAllowed);
                        }
                        setConnectionStatus(mobileActionsEffectiveEnabled
                            ? "Loaded " + allowedOperations.size() + " mobile operations."
                            : "Mobile actions are disabled. Read-only mode.");

                        if (lastInstances != null) {
                            renderInstancesList();
                            refreshSelectedInstanceCard();
                        } else {
                            refreshSelectedInstanceCard();
                        }
                    });
                } else {
                    runOnUiThread(() -> {
                        mobileActionsEffectiveEnabled = false;
                        allowedOperations.clear();
                        refreshSelectedInstanceCard();
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    allowedOperations.clear();
                });
            }
        }).start();
    }

    private void buildTerminalLayout() {
        terminalLayout = new LinearLayout(this);
        terminalLayout.setOrientation(LinearLayout.VERTICAL);
        terminalLayout.setBackgroundColor(0xFF020617);
        terminalLayout.setPadding(8, 8, 8, 8);
        terminalLayout.setVisibility(View.GONE);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        terminalTitleView = new TextView(this);
        terminalTitleView.setText("Shell");
        terminalTitleView.setTextSize(18);
        terminalTitleView.setTypeface(Typeface.DEFAULT_BOLD);
        terminalTitleView.setTextColor(0xFFFFFFFF);

        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1
        );
        terminalTitleView.setLayoutParams(titleParams);

        terminalExitButton = new Button(this);
        terminalExitButton.setText("Exit");
        terminalExitButton.setAllCaps(false);
        terminalExitButton.setTextColor(0xFFFFFFFF);
        terminalExitButton.setTextSize(14);
        terminalExitButton.setPadding(24, 10, 24, 10);
        terminalExitButton.setBackground(makeGlassBackground(0xCC7F1D1D, 0xAA450A0A, 0xFFF87171, 1, 18));
        terminalExitButton.setOnClickListener(v -> closeTerminalSession("Terminal closed."));

        header.addView(terminalTitleView);
        header.addView(terminalExitButton);

        terminalWebView = new WebView(this);
        terminalWebView.setBackgroundColor(0xFF020617);
        terminalWebView.setFocusable(true);
        terminalWebView.setFocusableInTouchMode(true);

        WebSettings settings = terminalWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setLoadWithOverviewMode(false);
        settings.setUseWideViewPort(false);

        terminalWebView.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void close() {
                runOnUiThread(() -> closeTerminalSession("Terminal closed."));
            }
        }, "AndroidTerminal");

        LinearLayout.LayoutParams webParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1
        );
        webParams.setMargins(0, 8, 0, 0);
        terminalWebView.setLayoutParams(webParams);

        // The WebView terminal page owns its own title, Exit button, keybar, and terminal.
        // Keeping a native Android header here causes WebView/xterm keyboard resize errors.
        terminalLayout.addView(terminalWebView);
    }


    private boolean isAdminRole() {
        return "admin".equalsIgnoreCase(getClientRoleDisplay());
    }

    private boolean isContainer(JSONObject item) {
        return item != null && "container".equalsIgnoreCase(item.optString("type", ""));
    }

    private boolean isRunning(JSONObject item) {
        return item != null && "Running".equalsIgnoreCase(item.optString("status", ""));
    }

    private String buildTargetId(JSONObject item) {
        if (item == null) {
            return "";
        }

        String remote = item.optString("remote", "").trim();
        String project = item.optString("project", "default").trim();
        if (project.isEmpty()) {
            project = "default";
        }
        String name = item.optString("name", item.optString("instance", item.optString("id", ""))).trim();

        if (remote.isEmpty() || project.isEmpty() || name.isEmpty()) {
            return "";
        }

        return remote + ":" + project + ":" + name;
    }

    private String terminalWebSocketUrl(String targetId, String token) throws Exception {
        String base = getApiBaseUrl();

        if (base.startsWith("https://")) {
            base = "wss://" + base.substring("https://".length());
        } else if (base.startsWith("http://")) {
            base = "ws://" + base.substring("http://".length());
        } else {
            throw new IllegalStateException("Server URL must start with http:// or https://");
        }

        return base + "/api/mobile/terminal?target=" +
            URLEncoder.encode(targetId, "UTF-8") +
            "&token=" +
            URLEncoder.encode(token, "UTF-8");
    }

    private String cleanTerminalOutput(String text) {
        if (text == null) {
            return "";
        }

        String cleaned = text;

        /*
         * The Android screen is currently a command-console TextView, not a full
         * terminal emulator. Strip common ANSI/OSC/control sequences so shell
         * output stays readable.
         */
        cleaned = cleaned.replaceAll("\\u001B\\][^\\u0007]*(\\u0007|\\u001B\\\\)", "");
        cleaned = cleaned.replaceAll("\\u001B\\[[0-?]*[ -/]*[@-~]", "");
        cleaned = cleaned.replaceAll("\\u001B[=>]", "");
        cleaned = cleaned.replace("\\r\\n", "\\n");
        cleaned = cleaned.replace("\\r", "\\n");

        return cleaned;
    }

    private void appendTerminalOutput(String text) {
        if (terminalOutputView == null) {
            return;
        }

        String current = terminalOutputView.getText() == null
            ? ""
            : terminalOutputView.getText().toString();

        String next = current + cleanTerminalOutput(text);

        if (next.length() > 60000) {
            next = next.substring(next.length() - 60000);
        }

        terminalOutputView.setText(next);

        if (terminalScrollView != null) {
            terminalScrollView.post(() -> terminalScrollView.fullScroll(View.FOCUS_DOWN));
        }
    }

    private String terminalPageUrl(String targetId, String token) throws Exception {
        String base = getApiBaseUrl();

        if (!base.startsWith("http://") && !base.startsWith("https://")) {
            throw new IllegalStateException("Server URL must start with http:// or https://");
        }

        return base + "/mobile-terminal.html?target=" +
            URLEncoder.encode(targetId, "UTF-8") +
            "&token=" +
            URLEncoder.encode(token, "UTF-8");
    }

    private void openTerminalSession(JSONObject item) {
        if (!isAdminRole()) {
            showOperationMessage("Admin role required for shell access.");
            return;
        }

        if (!isContainer(item)) {
            showOperationMessage("Shell is only available for containers.");
            return;
        }

        if (!isRunning(item)) {
            showOperationMessage("Shell is only available for running containers.");
            return;
        }

        String token = getBearerToken();
        if (token == null || token.trim().isEmpty()) {
            showOperationMessage("Not paired. Pair this device first.");
            return;
        }

        String targetId = buildTargetId(item);
        if (targetId.isEmpty()) {
            showOperationMessage("Selected instance is missing remote, project, or name.");
            return;
        }

        terminalTargetId = targetId;

        if (terminalTitleView != null) {
            terminalTitleView.setText("Shell: " + targetId);
        }

        if (fixedHeaderLayout != null) {
            fixedHeaderLayout.setVisibility(View.GONE);
        }

        if (mainScrollView != null) {
            mainScrollView.setVisibility(View.GONE);
        }

        if (terminalLayout != null) {
            terminalLayout.setVisibility(View.VISIBLE);
        }

        try {
            String url = terminalPageUrl(targetId, token);
            terminalWebView.loadUrl(url);
            terminalWebView.requestFocus();
        } catch (Exception e) {
            showOperationMessage("Terminal error: " + e.getMessage());
        }
    }


    private void sendTerminalInput() {
        if (terminalWebSocket == null) {
            appendTerminalOutput("\nNot connected.\n");
            return;
        }

        String command = terminalInputView == null || terminalInputView.getText() == null
            ? ""
            : terminalInputView.getText().toString();

        if (command.trim().isEmpty()) {
            return;
        }

        try {
            JSONObject msg = new JSONObject();
            msg.put("type", "input");
            msg.put("data", command + "\n");
            terminalWebSocket.send(msg.toString());

            if (terminalInputView != null) {
                terminalInputView.setText("");
            }
        } catch (Exception e) {
            appendTerminalOutput("\nERROR: " + e.getMessage() + "\n");
        }
    }

    private void closeTerminalSession(String message) {
        try {
            if (terminalWebView != null) {
                terminalWebView.evaluateJavascript(
                    "if (window.closeTerminalFromAndroid) { window.closeTerminalFromAndroid(); }",
                    null
                );
                terminalWebView.loadUrl("about:blank");
            }
        } catch (Exception ignored) {
        }

        terminalTargetId = "";

        if (terminalLayout != null) {
            terminalLayout.setVisibility(View.GONE);
        }

        if (fixedHeaderLayout != null) {
            fixedHeaderLayout.setVisibility(View.VISIBLE);
        }

        if (mainScrollView != null) {
            mainScrollView.setVisibility(View.VISIBLE);
        }

        if (message != null && !message.trim().isEmpty()) {
            showOperationMessage(message);
        }
    }


    private Button makeInstanceOperationButton(String label, String operation, JSONObject item) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(13);
        button.setTextColor(0xFFFFFFFF);
        button.setPadding(12, 8, 12, 8);
        button.setBackground(makeGlassBackground(0xCC1F2937, 0xAA111827, 0x7738BDF8, 1, 18));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1
        );
        params.setMargins(0, 12, 10, 0);
        button.setLayoutParams(params);

        button.setOnClickListener(v -> {
            if ("instance.shell".equals(operation)) {
                openTerminalSession(item);
                return;
            }

            executeInstanceOperation(operation, item);
        });

        return button;
    }

    private Button makeInstanceActionButton(String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(13);
        button.setTextColor(0xFFFFFFFF);
        button.setPadding(12, 8, 12, 8);
        button.setBackground(makeGlassBackground(0xCC1F2937, 0xAA111827, 0x7738BDF8, 1, 18));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1
        );
        params.setMargins(0, 12, 10, 0);
        button.setLayoutParams(params);
        button.setOnClickListener(listener);

        return button;
    }

    private void addInstanceOperationButtons(LinearLayout card, JSONObject item) {
        if (!mobileActionsEffectiveEnabled) {
            return;
        }

        if (card == null || item == null) {
            return;
        }

        String role = getClientRoleDisplay();
        boolean operator = "operator".equalsIgnoreCase(role);
        boolean admin = "admin".equalsIgnoreCase(role);

        if (!operator && !admin) {
            return;
        }

        boolean running = isRunning(item);
        boolean container = isContainer(item);

        boolean showStart = !running && hasAllowedOperation("instance.start");
        boolean showStop = running && hasAllowedOperation("instance.stop");
        boolean showRestart = running && hasAllowedOperation("instance.restart");
        boolean showShell = admin &&
            running &&
            container &&
            hasAllowedOperation("instance.shell");
        boolean showSnapshots = admin;

        if (!showStart && !showStop && !showRestart && !showShell && !showSnapshots) {
            return;
        }

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        if (showStart) {
            row.addView(makeInstanceOperationButton("Start", "instance.start", item));
        }

        if (showStop) {
            row.addView(makeInstanceOperationButton("Stop", "instance.stop", item));
        }

        if (showRestart) {
            row.addView(makeInstanceOperationButton("Restart", "instance.restart", item));
        }

        if (showShell) {
            row.addView(makeInstanceOperationButton("Shell", "instance.shell", item));
        }

        if (showStart || showStop || showRestart || showShell) {
            card.addView(row);
        }

        if (showSnapshots) {
            LinearLayout snapshotRow = new LinearLayout(this);
            snapshotRow.setOrientation(LinearLayout.HORIZONTAL);
            snapshotRow.addView(makeInstanceActionButton("Create Snapshot", v -> createInstanceSnapshot(item)));
            snapshotRow.addView(makeInstanceActionButton("View Snapshots", v -> showInstanceSnapshots(item)));
            card.addView(snapshotRow);
        }
    }


    private String snapshotPathForInstance(JSONObject item) throws Exception {
        String targetId = buildTargetId(item);

        if (targetId.isEmpty()) {
            throw new IllegalStateException("Selected instance is missing remote, project, or name.");
        }

        return "/api/mobile/instances/" + URLEncoder.encode(targetId, "UTF-8") + "/snapshots";
    }

    private void createInstanceSnapshot(JSONObject item) {
        if (!isAdminRole()) {
            showOperationMessage("Admin role required for snapshot management.");
            return;
        }

        String token = getBearerToken();
        if (token == null || token.trim().isEmpty()) {
            showOperationMessage("Not paired. Pair this device first.");
            return;
        }

        String name = item == null ? "instance" : item.optString("name", "instance");
        showOperationMessage("Creating snapshot for " + name + "...");

        new Thread(() -> {
            try {
                HttpResult result = httpRequest(
                    "POST",
                    snapshotPathForInstance(item),
                    "{}",
                    token
                );

                JSONObject json = null;
                try {
                    json = new JSONObject(result.body);
                } catch (Exception ignored) {
                }

                final JSONObject finalJson = json;

                runOnUiThread(() -> {
                    if (result.code >= 200 && result.code < 300 && finalJson != null && finalJson.optBoolean("ok")) {
                        String snapshot = finalJson.optString("snapshot", "");
                        showOperationMessage(snapshot.trim().isEmpty()
                            ? "Snapshot created."
                            : "Snapshot created: " + snapshot);
                        loadInstances();
                        return;
                    }

                    String error = finalJson == null ? "" : finalJson.optString("error", "");
                    if (error.trim().isEmpty()) {
                        error = result.toDisplayString();
                    }

                    showOperationMessage(error);
                });
            } catch (Exception e) {
                runOnUiThread(() -> showOperationMessage(errorText(e)));
            }
        }).start();
    }

    private void showInstanceSnapshots(JSONObject item) {
        if (!isAdminRole()) {
            showOperationMessage("Admin role required for snapshot management.");
            return;
        }

        String token = getBearerToken();
        if (token == null || token.trim().isEmpty()) {
            showOperationMessage("Not paired. Pair this device first.");
            return;
        }

        String name = item == null ? "instance" : item.optString("name", "instance");
        showOperationMessage("Loading snapshots for " + name + "...");

        new Thread(() -> {
            try {
                HttpResult result = httpRequest(
                    "GET",
                    snapshotPathForInstance(item),
                    null,
                    token
                );

                JSONObject json = null;
                try {
                    json = new JSONObject(result.body);
                } catch (Exception ignored) {
                }

                final JSONObject finalJson = json;

                runOnUiThread(() -> {
                    if (result.code >= 200 && result.code < 300 && finalJson != null && finalJson.optBoolean("ok")) {
                        JSONArray snapshots = finalJson.optJSONArray("snapshots");
                        StringBuilder message = new StringBuilder();

                        if (snapshots == null || snapshots.length() == 0) {
                            message.append("No snapshots found.");
                        } else {
                            for (int i = 0; i < snapshots.length(); i++) {
                                JSONObject snapshot = snapshots.optJSONObject(i);
                                if (snapshot == null) {
                                    continue;
                                }

                                String snapshotName = snapshot.optString("name", "");
                                if (!snapshotName.trim().isEmpty()) {
                                    message.append(snapshotName).append("\n");
                                }
                            }

                            if (message.length() == 0) {
                                message.append("No snapshots found.");
                            }
                        }

                        showSnapshotListDialog(name, snapshots);

                        setConnectionStatus("Loaded snapshots for " + name + ".");
                        return;
                    }

                    String error = finalJson == null ? "" : finalJson.optString("error", "");
                    if (error.trim().isEmpty()) {
                        error = result.toDisplayString();
                    }

                    showOperationMessage(error);
                });
            } catch (Exception e) {
                runOnUiThread(() -> showOperationMessage(errorText(e)));
            }
        }).start();
    }


    private void showSnapshotListDialog(String instanceName, JSONArray snapshots) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setPadding(28, 24, 28, 24);
        outer.setBackground(makeGlassBackground(0xEE10233F, 0xDD08111F, 0xFF38BDF8, 2, 24));

        TextView title = new TextView(this);
        title.setText("Snapshots: " + instanceName);
        title.setTextSize(18);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(0xFFFFFFFF);
        title.setPadding(0, 0, 0, 18);
        outer.addView(title);

        ScrollView scroll = new ScrollView(this);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            (int) (getResources().getDisplayMetrics().heightPixels * 0.35f)
        );
        scroll.setLayoutParams(scrollParams);

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);

        if (snapshots == null || snapshots.length() == 0) {
            TextView empty = new TextView(this);
            empty.setText("No snapshots found.");
            empty.setTextSize(15);
            empty.setTextColor(0xFFFFFFFF);
            empty.setPadding(12, 8, 12, 8);
            list.addView(empty);
        } else {
            for (int i = 0; i < snapshots.length(); i++) {
                JSONObject snapshot = snapshots.optJSONObject(i);
                if (snapshot == null) {
                    continue;
                }

                String snapshotName = snapshot.optString("name", "").trim();
                if (snapshotName.isEmpty()) {
                    continue;
                }

                TextView item = new TextView(this);
                item.setText(snapshotName);
                item.setTextSize(16);
                item.setTextColor(0xFFFFFFFF);
                item.setPadding(18, 16, 18, 16);
                item.setBackground(makeGlassBackground(0xCC1F2937, 0xAA111827, 0x5538BDF8, 1, 18));

                LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                );
                itemParams.setMargins(0, 0, 0, 12);
                item.setLayoutParams(itemParams);

                list.addView(item);
            }
        }

        scroll.addView(list);
        outer.addView(scroll);

        Button close = new Button(this);
        close.setText("Close");
        close.setAllCaps(false);
        close.setTextSize(14);
        close.setTextColor(0xFFFFFFFF);
        close.setPadding(18, 12, 18, 12);
        close.setBackground(makeGlassBackground(0xEE123254, 0xCC08111F, 0xFF38BDF8, 2, 24));

        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        closeParams.setMargins(0, 18, 0, 0);
        close.setLayoutParams(closeParams);
        close.setOnClickListener(v -> dialog.dismiss());

        outer.addView(close);

        dialog.setContentView(outer);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(0x00000000));
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.90f);
            dialog.getWindow().setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        dialog.show();
    }

    private void showOperationMessage(String message) {
        setConnectionStatus(message);

        try {
            android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show();
        } catch (Exception ignored) {
        }
    }

    private void applyOptimisticInstanceStatus(String operation, JSONObject item) {
        if (item == null) {
            return;
        }

        try {
            if ("instance.stop".equals(operation)) {
                item.put("status", "Stopped");
            } else if ("instance.start".equals(operation) || "instance.restart".equals(operation)) {
                item.put("status", "Running");
            }

            lastSelectedInstance = item;
            selectedInstanceKey = getInstanceKey(item);
            showInstanceDetailView();
            setInstanceDetail(item);
        } catch (Exception ignored) {
        }
    }

    private void refreshAfterInstanceOperation(String message) {
        /*
         * Refresh silently after Start/Stop/Restart.
         * The operation result toast has already been shown, and the selected
         * card is updated optimistically before this method runs.
         */
        setStatus(message + ". Refreshing instance state...");
        fetchMobileOperations();
        loadInstances();
    }


    private void executeInstanceOperation(String operation, JSONObject item) {
        if (item == null) {
            showOperationMessage("No instance selected.");
            return;
        }

        String token = getBearerToken();
        if (token == null || token.trim().isEmpty()) {
            showOperationMessage("Not paired. Pair this device first.");
            return;
        }

        String remote = item.optString("remote", "").trim();
        String project = item.optString("project", "default").trim();
        String name = item.optString("name", item.optString("instance", item.optString("id", ""))).trim();

        if (remote.isEmpty() || project.isEmpty() || name.isEmpty()) {
            showOperationMessage("Selected instance is missing remote, project, or name.");
            return;
        }

        String targetId = remote + ":" + project + ":" + name;
        showOperationMessage("Requesting " + operation + " for " + name + "...");

        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("operation", operation);
                body.put("target_type", "instance");
                body.put("target_id", targetId);

                HttpResult result = httpRequest(
                    "POST",
                    "/api/mobile/operations",
                    body.toString(),
                    token
                );

                JSONObject json = null;
                try {
                    json = new JSONObject(result.body);
                } catch (Exception ignored) {
                }

                final JSONObject finalJson = json;

                runOnUiThread(() -> {
                    if (result.code >= 200 && result.code < 300 && finalJson != null && finalJson.optBoolean("ok")) {
                        applyOptimisticInstanceStatus(operation, item);
                        refreshAfterInstanceOperation("Operation completed: " + operation);
                        return;
                    }

                    String error = "";
                    if (finalJson != null) {
                        error = finalJson.optString("error", "");
                    }

                    if (error.trim().isEmpty()) {
                        error = result.toDisplayString();
                    }

                    showOperationMessage(error);
                });
            } catch (Exception e) {
                runOnUiThread(() -> showOperationMessage(errorText(e)));
            }
        }).start();
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
        addInstanceOperationButtons(card, item);

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
        if (!hasServerUrl()) {
            throw new IllegalStateException("Server URL is not configured");
        }
        URL url = new URL(getApiBaseUrl() + path);
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
        closeTerminalSession("");
        stopPairingPolling();
        super.onDestroy();
    }

}
