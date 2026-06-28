package com.scottibyte.incusmobile;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {
    private static final String API_BASE_URL = "https://incusmobile.scottibyte.com";
    private TextView statusView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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
        apiUrl.setTextSize(16);

        Button healthButton = new Button(this);
        healthButton.setText("Test Server Health");

        statusView = new TextView(this);
        statusView.setText("\nStatus: Not tested yet");
        statusView.setTextSize(16);

        healthButton.setOnClickListener(v -> testHealth());

        layout.addView(title);
        layout.addView(apiUrl);
        layout.addView(healthButton);
        layout.addView(statusView);

        scroll.addView(layout);
        setContentView(scroll);
    }

    private void testHealth() {
        statusView.setText("\nStatus: Testing...");

        new Thread(() -> {
            String result;

            try {
                URL url = new URL(API_BASE_URL + "/api/mobile/health");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);

                int code = conn.getResponseCode();

                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(
                        code >= 200 && code < 400
                            ? conn.getInputStream()
                            : conn.getErrorStream()
                    )
                );

                StringBuilder body = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    body.append(line).append("\n");
                }

                reader.close();
                conn.disconnect();

                result = "HTTP " + code + "\n\n" + body.toString().trim();
            } catch (Exception e) {
                result = "ERROR\n\n" + e.getClass().getSimpleName() + ": " + e.getMessage();
            }

            String finalResult = result;
            new Handler(Looper.getMainLooper()).post(() ->
                statusView.setText("\nStatus:\n" + finalResult)
            );
        }).start();
    }
}
