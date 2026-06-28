package com.scottibyte.incusmobile;

import android.app.Activity;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ScrollView;
import android.graphics.Typeface;
import android.view.Gravity;

public class MainActivity extends Activity {
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

        TextView status = new TextView(this);
        status.setText(
            "\nAndroid client shell is installed.\n\n" +
            "Next milestone:\n" +
            "• Configure API base URL\n" +
            "• Pair device\n" +
            "• Store bearer token\n" +
            "• Read server health\n" +
            "• Display Incus summary"
        );
        status.setTextSize(16);

        layout.addView(title);
        layout.addView(status);

        scroll.addView(layout);
        setContentView(scroll);
    }
}
