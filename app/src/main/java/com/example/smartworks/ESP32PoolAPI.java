// ESP32PoolAPI.java - Helper class for ESP32 Pool Monitor communication
package com.example.smartworks;

import android.util.Log;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CompletableFuture;

public class ESP32PoolAPI {
    private static final String TAG = "ESP32PoolAPI";
    private static final int CONNECTION_TIMEOUT = 15000; // 15 seconds - increased for temperature sensor
    private static final int READ_TIMEOUT = 20000; // 20 seconds - longer read timeout for sensor data
    
    public static class PoolData {
        public double temperatureCelsius;
        public double temperatureFahrenheit;
        public boolean sensorFound;
        public String wifiSSID;
        public int rssi;
        public long uptimeSeconds;
        public int freeHeap;
        public String deviceName;
        public boolean isValid;
        public String errorMessage;
        
        public PoolData() {
            this.isValid = false;
        }
        
        public String getTemperatureStatus() {
            if (!isValid || !sensorFound) return "❌ Sensor Error";
            
            if (temperatureFahrenheit >= 78 && temperatureFahrenheit <= 82) {
                return "🏊‍♂️ Perfect for swimming!";
            } else if (temperatureFahrenheit >= 75 && temperatureFahrenheit <= 85) {
                return "🌊 Good swimming temperature";
            } else if (temperatureFahrenheit < 70) {
                return "🥶 Too cold for swimming";
            } else if (temperatureFahrenheit > 85) {
                return "🔥 Getting quite warm!";
            } else {
                return "🌡️ Temperature recorded";
            }
        }
        
        public String getSignalStrengthText() {
            if (rssi == 0) return "Unknown";
            if (rssi >= -50) return "Excellent (" + rssi + " dBm)";
            if (rssi >= -60) return "Good (" + rssi + " dBm)";
            if (rssi >= -70) return "Fair (" + rssi + " dBm)";
            return "Weak (" + rssi + " dBm)";
        }
    }
    
    public static class WiFiConfig {
        public String ssid;
        public String password;
        
        public WiFiConfig(String ssid, String password) {
            this.ssid = ssid;
            this.password = password;
        }
        
        public String toPostData() {
            return "ssid=" + urlEncode(ssid) + "&password=" + urlEncode(password);
        }
        
        private String urlEncode(String value) {
            try {
                return java.net.URLEncoder.encode(value, "UTF-8");
            } catch (Exception e) {
                return value;
            }
        }
    }
    
    /**
     * Fetch temperature and system data from ESP32
     */
    public static CompletableFuture<PoolData> fetchPoolData(String ipAddress) {
        return CompletableFuture.supplyAsync(() -> {
            PoolData data = new PoolData();
            
            try {
                URL url = new URL("http://" + ipAddress + "/data");
                Log.d(TAG, "Fetching data from: " + url.toString());
                
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(CONNECTION_TIMEOUT);
                connection.setReadTimeout(READ_TIMEOUT);
                connection.setRequestProperty("Cache-Control", "no-cache");
                connection.setRequestProperty("Connection", "close"); // Force close to avoid keep-alive issues
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("User-Agent", "SmartWorks-Pool-Monitor/1.0");
                connection.setUseCaches(false);
                
                int responseCode = connection.getResponseCode();
                
                if (responseCode == 200) {
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
                    
                    if (parsePoolData(jsonResponse, data)) {
                        data.isValid = true;
                    } else {
                        data.errorMessage = "Failed to parse response";
                    }
                } else {
                    data.errorMessage = "HTTP " + responseCode;
                }
                
                connection.disconnect();
                
            } catch (java.net.SocketTimeoutException e) {
                data.errorMessage = "Temperature sensor timeout - ESP32 taking longer than expected. This is common with temperature readings. Try refreshing in a few seconds.";
                Log.e(TAG, "Timeout connecting to " + ipAddress + ": " + e.getMessage());
            } catch (java.net.ConnectException e) {
                data.errorMessage = "Cannot connect - check IP and ESP32 power";
                Log.e(TAG, "Connection refused to " + ipAddress + ": " + e.getMessage());
            } catch (java.net.UnknownHostException e) {
                data.errorMessage = "Invalid IP address";
                Log.e(TAG, "Unknown host " + ipAddress + ": " + e.getMessage());
            } catch (java.net.NoRouteToHostException e) {
                data.errorMessage = "No route to device - check WiFi network";
                Log.e(TAG, "No route to " + ipAddress + ": " + e.getMessage());
            } catch (Exception e) {
                data.errorMessage = "Error: " + e.getMessage();
                Log.e(TAG, "Error fetching data: " + e.getMessage(), e);
            }
            
            return data;
        });
    }
    
    /**
     * Send WiFi configuration to ESP32
     */
    public static CompletableFuture<Boolean> configureWiFi(String ipAddress, WiFiConfig config) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                URL url = new URL("http://" + ipAddress + "/wifi");
                Log.d(TAG, "Configuring WiFi at: " + url.toString());
                
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(CONNECTION_TIMEOUT);
                connection.setReadTimeout(READ_TIMEOUT);
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                connection.setDoOutput(true);
                
                // Send POST data
                String postData = config.toPostData();
                connection.getOutputStream().write(postData.getBytes());
                
                int responseCode = connection.getResponseCode();
                connection.disconnect();
                
                Log.d(TAG, "WiFi config response: " + responseCode);
                return responseCode == 200;
                
            } catch (Exception e) {
                Log.e(TAG, "Error configuring WiFi: " + e.getMessage(), e);
                return false;
            }
        });
    }
    
    /**
     * Test basic connectivity to ESP32
     */
    public static CompletableFuture<Boolean> testConnection(String ipAddress) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                URL url = new URL("http://" + ipAddress + "/data");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                
                int responseCode = connection.getResponseCode();
                connection.disconnect();
                
                return responseCode == 200;
                
            } catch (Exception e) {
                Log.e(TAG, "Connection test failed: " + e.getMessage());
                return false;
            }
        });
    }
    
    /**
     * Get system status from ESP32
     */
    public static CompletableFuture<PoolData> getSystemStatus(String ipAddress) {
        return CompletableFuture.supplyAsync(() -> {
            PoolData data = new PoolData();
            
            try {
                URL url = new URL("http://" + ipAddress + "/status");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(CONNECTION_TIMEOUT);
                connection.setReadTimeout(READ_TIMEOUT);
                connection.setRequestProperty("Accept", "application/json");
                
                int responseCode = connection.getResponseCode();
                
                if (responseCode == 200) {
                    BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();
                    
                    // Parse system status JSON
                    JSONObject json = new JSONObject(response.toString());
                    if (json.has("free_heap")) {
                        data.freeHeap = json.getInt("free_heap");
                    }
                    if (json.has("uptime_seconds")) {
                        data.uptimeSeconds = json.getLong("uptime_seconds");
                    }
                    
                    data.isValid = true;
                }
                
                connection.disconnect();
                
            } catch (Exception e) {
                Log.e(TAG, "Error getting system status: " + e.getMessage(), e);
                data.errorMessage = e.getMessage();
            }
            
            return data;
        });
    }
    
    private static boolean parsePoolData(String jsonResponse, PoolData data) {
        try {
            JSONObject json = new JSONObject(jsonResponse);
            
            // Parse temperature
            if (json.has("temperature_celsius") && !json.isNull("temperature_celsius")) {
                data.temperatureCelsius = json.getDouble("temperature_celsius");
                
                if (json.has("temperature_fahrenheit") && !json.isNull("temperature_fahrenheit")) {
                    data.temperatureFahrenheit = json.getDouble("temperature_fahrenheit");
                } else {
                    // Calculate Fahrenheit if not provided
                    data.temperatureFahrenheit = (data.temperatureCelsius * 9.0 / 5.0) + 32.0;
                }
            } else {
                Log.w(TAG, "No valid temperature data in response");
                return false;
            }
            
            // Parse sensor status
            if (json.has("sensor_found")) {
                data.sensorFound = json.getBoolean("sensor_found");
            } else {
                data.sensorFound = true; // Assume sensor is found if not specified
            }
            
            // Parse WiFi info
            if (json.has("wifi_ssid")) {
                data.wifiSSID = json.getString("wifi_ssid");
            }
            
            if (json.has("rssi")) {
                data.rssi = json.getInt("rssi");
            }
            
            // Parse uptime
            if (json.has("uptime_seconds")) {
                data.uptimeSeconds = json.getLong("uptime_seconds");
            }
            
            // Parse free heap
            if (json.has("free_heap")) {
                data.freeHeap = json.getInt("free_heap");
            }
            
            // Parse device name
            if (json.has("device_name")) {
                data.deviceName = json.getString("device_name");
            }
            
            Log.d(TAG, String.format("Parsed: %.1f°C (%.1f°F), WiFi: %s, RSSI: %d", 
                data.temperatureCelsius, data.temperatureFahrenheit, data.wifiSSID, data.rssi));
            
            return true;
            
        } catch (JSONException e) {
            Log.e(TAG, "JSON parsing error: " + e.getMessage());
            return false;
        }
    }
}