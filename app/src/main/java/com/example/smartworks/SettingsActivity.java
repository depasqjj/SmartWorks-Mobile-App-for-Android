package com.example.smartworks;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {
    private EditText highThresholdEdit;
    private EditText lowThresholdEdit;
    private Button saveButton;

    private SharedPreferences preferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Temperature Settings");
        }

        preferences = getSharedPreferences("SmartWorksApp", MODE_PRIVATE);

        initializeViews();
        loadCurrentSettings();
    }

    private void initializeViews() {
        highThresholdEdit = findViewById(R.id.highThresholdEdit);
        lowThresholdEdit = findViewById(R.id.lowThresholdEdit);
        saveButton = findViewById(R.id.saveButton);

        saveButton.setOnClickListener(v -> saveSettings());
    }

    private void loadCurrentSettings() {
        float highThreshold = preferences.getFloat("high_threshold", 85.0f);
        float lowThreshold = preferences.getFloat("low_threshold", 60.0f);

        highThresholdEdit.setText(String.valueOf(highThreshold));
        lowThresholdEdit.setText(String.valueOf(lowThreshold));
    }

    private void saveSettings() {
        try {
            float highThreshold = Float.parseFloat(highThresholdEdit.getText().toString());
            float lowThreshold = Float.parseFloat(lowThresholdEdit.getText().toString());

            if (lowThreshold >= highThreshold) {
                Toast.makeText(this, "Low threshold must be less than high threshold", Toast.LENGTH_SHORT).show();
                return;
            }

            preferences.edit()
                    .putFloat("high_threshold", highThreshold)
                    .putFloat("low_threshold", lowThreshold)
                    .apply();

            Toast.makeText(this, "Settings saved successfully", Toast.LENGTH_SHORT).show();
            finish();

        } catch (NumberFormatException e) {
            Toast.makeText(this, "Please enter valid temperature values", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}