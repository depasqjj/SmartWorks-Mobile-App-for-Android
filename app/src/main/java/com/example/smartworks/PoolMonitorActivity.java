// PoolMonitorActivity.java - Dedicated Pool Temperature Monitoring
package com.example.smartworks;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PoolMonitorActivity extends AppCompatActivity {
    private static final String TAG = "PoolMonitorActivity";
    private static final long AUTO_REFRESH_INTERVAL = 30000; // 30 seconds
    private static final int CONNECTION_TIMEOUT = 15000; // 15 seconds - increased for temperature sensor
    
    // UI Elements
    private TextView temperatureCelsius;
    private TextView temperatureFahrenheit;
    private TextView statusText;
    private TextView lastUpdateText;
    private TextView deviceNameText;
    private TextView wifiStatusText;
    private TextView signalStrengthText;
    private TextView uptimeText;
    private ProgressBar temperatureProgress;
    private LinearLayout temperatureCard;
    private LinearLayout statusCard;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ImageButton settingsFab;
    
    // Device Info
    private String deviceName = "Pool Monitor";
    private String deviceIP = "192.168.0.132"; // Your ESP32 IP
    private boolean isMonitoring = false;
    
    // Background Services
    private ExecutorService executorService;
    private Handler mainHandler;
    private Runnable autoRefreshRunnable;
    
    // Temperature Data
    private double currentTempCelsius = Double.NaN;
    private double currentTempFahrenheit = Double.NaN;
    private String deviceWifiSSID = "";
    private int deviceRSSI = 0;
    private long deviceUptime = 0;
    private boolean sensorFound = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pool_monitor);
        
        Log.d(TAG, "PoolMonitorActivity onCreate");
        
        // Setup action bar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("🏊‍♂️ Pool Monitor");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        
        // Initialize background services
        executorService = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
        
        // Load device settings
        loadDeviceSettings();
        
        // Initialize UI
        initializeViews();
        
        // Start monitoring
        startMonitoring();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopMonitoring();
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Stop auto-refresh when not visible
        if (autoRefreshRunnable != null) {
            mainHandler.removeCallbacks(autoRefreshRunnable);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Resume auto-refresh when visible
        scheduleAutoRefresh();
        // Refresh immediately when returning to activity
        refreshTemperatureData();
    }

    private void loadDeviceSettings() {
        SharedPreferences prefs = getSharedPreferences("SmartWorks", Context.MODE_PRIVATE);
        
        // Try to get device IP from intent first (if launched from device list)
        Intent intent = getIntent();
        if (intent != null) {
            String intentIP = intent.getStringExtra("device_ip");
            String intentName = intent.getStringExtra("device_name");
            
            if (intentIP != null && !intentIP.isEmpty()) {
                deviceIP = intentIP;
            }
            if (intentName != null && !intentName.isEmpty()) {
                deviceName = intentName;
            }
        }
        
        // Fallback to saved preferences
        if (deviceIP.equals("192.168.0.132")) {
            deviceIP = prefs.getString("pool_monitor_ip", "192.168.0.132");
        }
        deviceName = prefs.getString("pool_monitor_name", deviceName);
        
        Log.d(TAG, "Using device: " + deviceName + " at " + deviceIP);
    }

    private void saveDeviceSettings() {
        SharedPreferences prefs = getSharedPreferences("SmartWorks", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("pool_monitor_ip", deviceIP);
        editor.putString("pool_monitor_name", deviceName);
        editor.apply();
    }

    private void initializeViews() {
        // Find all views
        temperatureCelsius = findViewById(R.id.temperatureCelsius);
        temperatureFahrenheit = findViewById(R.id.temperatureFahrenheit);
        statusText = findViewById(R.id.statusText);
        lastUpdateText = findViewById(R.id.lastUpdateText);
        deviceNameText = findViewById(R.id.deviceNameText);
        wifiStatusText = findViewById(R.id.wifiStatusText);
        signalStrengthText = findViewById(R.id.signalStrengthText);
        uptimeText = findViewById(R.id.uptimeText);
        temperatureProgress = findViewById(R.id.temperatureProgress);
        temperatureCard = findViewById(R.id.temperatureCard);
        statusCard = findViewById(R.id.statusCard);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        settingsFab = findViewById(R.id.settingsFab);
        
        // Setup device name
        deviceNameText.setText(deviceName);
        
        // Setup swipe refresh
        swipeRefreshLayout.setColorSchemeResources(
            android.R.color.holo_blue_bright,
            android.R.color.holo_green_light,
            android.R.color.holo_orange_light,
            android.R.color.holo_red_light
        );
        swipeRefreshLayout.setOnRefreshListener(this::refreshTemperatureData);
        
        // Setup settings FAB
        settingsFab.setOnClickListener(v -> showSettingsDialog());
        
        // Setup temperature card click for manual refresh
        temperatureCard.setOnClickListener(v -> refreshTemperatureData());
        
        // Initialize with loading state
        showLoadingState();
    }

    private void showLoadingState() {
        temperatureCelsius.setText("--");
        temperatureFahrenheit.setText("--°F");
        statusText.setText("Connecting...");
        temperatureProgress.setVisibility(ProgressBar.VISIBLE);
        lastUpdateText.setText("Loading...");
    }

    private void startMonitoring() {
        if (!isMonitoring) {
            isMonitoring = true;
            Log.d(TAG, "Starting pool monitoring");
            refreshTemperatureData();
            scheduleAutoRefresh();
        }
    }

    private void stopMonitoring() {
        isMonitoring = false;
        if (autoRefreshRunnable != null) {
            mainHandler.removeCallbacks(autoRefreshRunnable);
        }
        Log.d(TAG, "Stopped pool monitoring");
    }

    private void scheduleAutoRefresh() {
        // Remove any existing callback
        if (autoRefreshRunnable != null) {
            mainHandler.removeCallbacks(autoRefreshRunnable);
        }
        
        // Create new runnable
        autoRefreshRunnable = new Runnable() {
            @Override
            public void run() {
                if (isMonitoring) {
                    refreshTemperatureData();
                    mainHandler.postDelayed(this, AUTO_REFRESH_INTERVAL);
                }
            }
        };
        
        // Schedule first run
        mainHandler.postDelayed(autoRefreshRunnable, AUTO_REFRESH_INTERVAL);
    }

    private void refreshTemperatureData() {
        Log.d(TAG, "Refreshing temperature data from " + deviceIP);
        
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setRefreshing(true);
        }
        
        executorService.execute(() -> {
            boolean success = false;
            String errorMessage = "";
            
            HttpURLConnection connection = null;
            try {
                // Connect to ESP32 JSON API
                URL url = new URL("http://" + deviceIP + "/data");
                Log.d(TAG, "Connecting to: " + url.toString());
                
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(CONNECTION_TIMEOUT);
                connection.setReadTimeout(25000); // 25 seconds for temperature sensor reading
                connection.setRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate");
                connection.setRequestProperty("Pragma", "no-cache");
                connection.setRequestProperty("Connection", "close");
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("User-Agent", "SmartWorks-Pool-Monitor/1.0");
                connection.setUseCaches(false);
                
                int responseCode = connection.getResponseCode();
                Log.d(TAG, "Response code: " + responseCode);
                
                if (responseCode == 200) {
                    // Read response
                    BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();
                    
                    String jsonResponse = response.toString();
                    Log.d(TAG, "Response: " + jsonResponse);
                    
                    // Parse JSON
                    if (parseTemperatureData(jsonResponse)) {
                        success = true;
                    } else {
                        errorMessage = "Failed to parse temperature data";
                    }
                } else {
                    errorMessage = "HTTP " + responseCode;
                }
                
            } catch (java.net.SocketTimeoutException e) {
                errorMessage = "Temperature sensor timeout - This is normal for temperature readings. Device may be slow to respond. Wait a moment and try refreshing.";
                Log.e(TAG, "Temperature timeout: " + e.getMessage());
            } catch (java.net.ConnectException e) {
                errorMessage = "Cannot connect - check IP address and WiFi";
                Log.e(TAG, "Connection refused: " + e.getMessage());
            } catch (Exception e) {
                errorMessage = "Error: " + e.getMessage();
                Log.e(TAG, "Error fetching data: " + e.getMessage(), e);
            } finally {
                if (connection != null) {
                    try {
                        connection.disconnect();
                    } catch (Exception ignored) {}
                }
            }
            
            // Update UI on main thread
            final boolean finalSuccess = success;
            final String finalErrorMessage = errorMessage;
            
            mainHandler.post(() -> {
                if (swipeRefreshLayout != null) {
                    swipeRefreshLayout.setRefreshing(false);
                }
                
                if (finalSuccess) {
                    updateTemperatureUI();
                } else {
                    showErrorState(finalErrorMessage);
                }
            });
        });
    }

    private boolean parseTemperatureData(String jsonResponse) {
        try {
            JSONObject json = new JSONObject(jsonResponse);
            
            // Parse temperature
            if (json.has("temperature_celsius") && !json.isNull("temperature_celsius")) {
                currentTempCelsius = json.getDouble("temperature_celsius");
                
                if (json.has("temperature_fahrenheit") && !json.isNull("temperature_fahrenheit")) {
                    currentTempFahrenheit = json.getDouble("temperature_fahrenheit");
                } else {
                    // Calculate Fahrenheit if not provided
                    currentTempFahrenheit = (currentTempCelsius * 9.0 / 5.0) + 32.0;
                }
            } else {
                Log.w(TAG, "No valid temperature data in response");
                return false;
            }
            
            // Parse sensor status
            if (json.has("sensor_found")) {
                sensorFound = json.getBoolean("sensor_found");
            }
            
            // Parse WiFi info
            if (json.has("wifi_ssid")) {
                deviceWifiSSID = json.getString("wifi_ssid");
            }
            
            if (json.has("rssi")) {
                deviceRSSI = json.getInt("rssi");
            }
            
            // Parse uptime
            if (json.has("uptime_seconds")) {
                deviceUptime = json.getLong("uptime_seconds");
            }
            
            Log.d(TAG, String.format("Parsed: %.1f°C (%.1f°F), WiFi: %s, RSSI: %d", 
                currentTempCelsius, currentTempFahrenheit, deviceWifiSSID, deviceRSSI));
            
            return true;
            
        } catch (JSONException e) {
            Log.e(TAG, "JSON parsing error: " + e.getMessage());
            return false;
        }
    }

    private void updateTemperatureUI() {
        // Update temperature display
        temperatureCelsius.setText(String.format("%.1f°C", currentTempCelsius));
        temperatureFahrenheit.setText(String.format("%.1f°F", currentTempFahrenheit));
        
        // Update temperature progress (0-40°C range for pool)
        float progress = Math.max(0, Math.min(100, (float)(currentTempCelsius / 40.0 * 100)));
        temperatureProgress.setProgress((int)progress);
        temperatureProgress.setVisibility(ProgressBar.INVISIBLE);
        
        // Update status based on temperature
        String status = getTemperatureStatus(currentTempFahrenheit);
        statusText.setText(status);
        
        // Set temperature card color based on status
        updateTemperatureCardColor(currentTempFahrenheit);
        
        // Update WiFi status
        if (!deviceWifiSSID.isEmpty()) {
            wifiStatusText.setText("Connected to: " + deviceWifiSSID);
        } else {
            wifiStatusText.setText("WiFi: Connected");
        }
        
        // Update signal strength
        String signalText = getSignalStrengthText(deviceRSSI);
        signalStrengthText.setText("Signal: " + signalText);
        
        // Update uptime
        uptimeText.setText("Uptime: " + formatUptime(deviceUptime));
        
        // Update last refresh time
        String timestamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        lastUpdateText.setText("Last update: " + timestamp);
        
        Log.d(TAG, "UI updated successfully");
    }

    private String getTemperatureStatus(double tempF) {
        if (tempF >= 78 && tempF <= 82) {
            return "🏊‍♂️ Perfect for swimming!";
        } else if (tempF >= 75 && tempF <= 85) {
            return "🌊 Good swimming temperature";
        } else if (tempF < 70) {
            return "🥶 Too cold for swimming";
        } else if (tempF > 85) {
            return "🔥 Getting quite warm!";
        } else {
            return "🌡️ Temperature recorded";
        }
    }

    private void updateTemperatureCardColor(double tempF) {
        int colorResId;
        if (tempF >= 78 && tempF <= 82) {
            colorResId = android.R.color.holo_green_light;
        } else if (tempF >= 75 && tempF <= 85) {
            colorResId = android.R.color.holo_blue_light;
        } else if (tempF < 70) {
            colorResId = android.R.color.holo_blue_dark;
        } else {
            colorResId = android.R.color.holo_orange_light;
        }
        
        temperatureCard.setBackgroundColor(getResources().getColor(colorResId, null));
    }

    private String getSignalStrengthText(int rssi) {
        if (rssi == 0) return "Unknown";
        if (rssi >= -50) return "Excellent (" + rssi + " dBm)";
        if (rssi >= -60) return "Good (" + rssi + " dBm)";
        if (rssi >= -70) return "Fair (" + rssi + " dBm)";
        return "Weak (" + rssi + " dBm)";
    }

    private String formatUptime(long seconds) {
        if (seconds < 60) {
            return seconds + " seconds";
        } else if (seconds < 3600) {
            return (seconds / 60) + " minutes";
        } else if (seconds < 86400) {
            long hours = seconds / 3600;
            long minutes = (seconds % 3600) / 60;
            return hours + "h " + minutes + "m";
        } else {
            long days = seconds / 86400;
            long hours = (seconds % 86400) / 3600;
            return days + "d " + hours + "h";
        }
    }

    private void showErrorState(String errorMessage) {
        temperatureCelsius.setText("--");
        temperatureFahrenheit.setText("--°F");
        statusText.setText("❌ " + errorMessage);
        temperatureProgress.setVisibility(ProgressBar.INVISIBLE);
        
        // Reset card to default color
        temperatureCard.setBackgroundColor(getResources().getColor(android.R.color.darker_gray, null));
        
        // Update status info
        wifiStatusText.setText("Connection: Failed");
        signalStrengthText.setText("Signal: --");
        uptimeText.setText("Uptime: --");
        
        String timestamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        lastUpdateText.setText("Last attempt: " + timestamp);
        
        Log.w(TAG, "Showing error state: " + errorMessage);
    }

    private void showSettingsDialog() {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("Pool Monitor Settings");
        
        // Create input field for IP address
        final android.widget.EditText ipInput = new android.widget.EditText(this);
        ipInput.setHint("192.168.0.132");
        ipInput.setText(deviceIP);
        ipInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        
        // Create input field for device name
        final android.widget.EditText nameInput = new android.widget.EditText(this);
        nameInput.setHint("Pool Monitor");
        nameInput.setText(deviceName);
        
        // Create layout
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);
        
        android.widget.TextView ipLabel = new android.widget.TextView(this);
        ipLabel.setText("ESP32 IP Address:");
        ipLabel.setPadding(0, 0, 0, 10);
        
        android.widget.TextView nameLabel = new android.widget.TextView(this);
        nameLabel.setText("Device Name:");
        nameLabel.setPadding(0, 20, 0, 10);
        
        layout.addView(ipLabel);
        layout.addView(ipInput);
        layout.addView(nameLabel);
        layout.addView(nameInput);
        
        builder.setView(layout);
        
        builder.setPositiveButton("Save & Test", (dialog, which) -> {
            String newIP = ipInput.getText().toString().trim();
            String newName = nameInput.getText().toString().trim();
            
            if (!newIP.isEmpty()) {
                deviceIP = newIP;
            }
            if (!newName.isEmpty()) {
                deviceName = newName;
                deviceNameText.setText(deviceName);
                if (getSupportActionBar() != null) {
                    getSupportActionBar().setTitle("🏊‍♂️ " + deviceName);
                }
            }
            
            saveDeviceSettings();
            
            // Test new connection
            Toast.makeText(this, "Testing connection to " + deviceIP + "...", Toast.LENGTH_SHORT).show();
            refreshTemperatureData();
        });
        
        builder.setNegativeButton("Cancel", null);
        
        builder.setNeutralButton("Web Interface", (dialog, which) -> {
            // Open web browser to device
            Intent browserIntent = new Intent(Intent.ACTION_VIEW);
            browserIntent.setData(android.net.Uri.parse("http://" + deviceIP));
            startActivity(browserIntent);
        });
        
        builder.show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, "Refresh")
            .setIcon(android.R.drawable.ic_popup_sync)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
            
        menu.add(0, 2, 1, "Settings")
            .setIcon(android.R.drawable.ic_menu_preferences)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
            
        menu.add(0, 3, 2, "Web Interface")
            .setIcon(android.R.drawable.ic_menu_view)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
            
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            case 1:
                refreshTemperatureData();
                return true;
            case 2:
                showSettingsDialog();
                return true;
            case 3:
                Intent browserIntent = new Intent(Intent.ACTION_VIEW);
                browserIntent.setData(android.net.Uri.parse("http://" + deviceIP));
                startActivity(browserIntent);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
}