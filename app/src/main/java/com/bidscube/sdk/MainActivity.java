package com.bidscube.sdk;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.media3.common.util.UnstableApi;

import com.bidscube.sdk.activities.ConsentTestActivity;
import com.bidscube.sdk.activities.SDKTestActivity;
import com.bidscube.sdk.activities.WindowedAdTestActivity;

@UnstableApi
public class MainActivity extends Activity {

    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        createLauncherLayout();
    }

    private void createLauncherLayout() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);
        layout.setGravity(android.view.Gravity.CENTER);

        TextView titleText = new TextView(this);
        titleText.setText("Bidscube SDK Test Launcher");
        titleText.setTextSize(24);
        titleText.setTextColor(Color.BLACK);
        titleText.setGravity(android.view.Gravity.CENTER);
        titleText.setPadding(0, 0, 0, 32);
        layout.addView(titleText);

        TextView descText = new TextView(this);
        descText.setText("Select a test activity to launch:");
        descText.setTextSize(16);
        descText.setTextColor(Color.GRAY);
        descText.setGravity(android.view.Gravity.CENTER);
        descText.setPadding(0, 0, 0, 32);
        layout.addView(descText);

        Button sdkTestBtn = new Button(this);
        sdkTestBtn.setText("SDK Test Activity");
        sdkTestBtn.setBackgroundColor(0xFF4CAF50);
        sdkTestBtn.setTextColor(Color.WHITE);
        sdkTestBtn.setPadding(32, 16, 32, 16);
        sdkTestBtn.setOnClickListener(v -> {
            Intent intent = new Intent(this, SDKTestActivity.class);
            startActivity(intent);
        });
        layout.addView(sdkTestBtn);

        TextView space1 = new TextView(this);
        space1.setPadding(0, 16, 0, 16);
        layout.addView(space1);

        Button consentTestBtn = new Button(this);
        consentTestBtn.setText("Consent Test Activity");
        consentTestBtn.setBackgroundColor(0xFFFF9800);
        consentTestBtn.setTextColor(Color.WHITE);
        consentTestBtn.setPadding(32, 16, 32, 16);
        consentTestBtn.setOnClickListener(v -> {
            Intent intent = new Intent(this, ConsentTestActivity.class);
            startActivity(intent);
        });
        layout.addView(consentTestBtn);

        TextView space2 = new TextView(this);
        space2.setPadding(0, 16, 0, 16);
        layout.addView(space2);

        Button windowedAdTestBtn = new Button(this);
        windowedAdTestBtn.setText("Windowed Ad Test Activity");
        windowedAdTestBtn.setBackgroundColor(0xFF2196F3);
        windowedAdTestBtn.setTextColor(Color.WHITE);
        windowedAdTestBtn.setPadding(32, 16, 32, 16);
        windowedAdTestBtn.setOnClickListener(v -> {
            Intent intent = new Intent(this, WindowedAdTestActivity.class);
            startActivity(intent);
        });
        layout.addView(windowedAdTestBtn);

        TextView space3 = new TextView(this);
        space3.setPadding(0, 16, 0, 16);
        layout.addView(space3);

        setContentView(layout);
    }
}