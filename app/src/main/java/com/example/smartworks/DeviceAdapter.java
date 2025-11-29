package com.example.smartworks;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.LinkedBlockingQueue;

public class DeviceAdapter extends BaseAdapter {
    private static final String TAG = "DeviceAdapter";
    private Context context;
    private List<DeviceInfo> devices;
    private LayoutInflater inflater;
    private ExecutorService executorService;
    private Handler mainHandler;
    private OnDeviceConfigListener configListener;

    // Interface for device configuration callback
    public interface OnDeviceConfigListener {
        void onOpenDeviceConfig(DeviceInfo device, int position);
    }

    // Track which devices are already being fetched to prevent duplicates
    private final Set<Integer> fetchingDevices = new HashSet<>();

    // Single shared executor for all adapters to prevent thread explosion
    private static ExecutorService sharedExecutor = null;

    public DeviceAdapter(Context context, List<DeviceInfo> devices) {
        this.context = context;
        this.devices = devices;
        this.inflater = LayoutInflater.from(context);

        // Use shared single thread executor across all adapter instances
        if (sharedExecutor == null || sharedExecutor.isShutdown()) {
            sharedExecutor = new ThreadPoolExecutor(
                    1,  // Core pool size - only 1 thread
                    1,  // Maximum pool size - only 1 thread
                    60L, // Keep alive time
                    TimeUnit.SECONDS,
                    new LinkedBlockingQueue<Runnable>(5), // Very small queue
                    new ThreadPoolExecutor.DiscardOldestPolicy() // Discard old tasks if queue full
            );
        }
        this.executorService = sharedExecutor;

        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public void setOnDeviceConfigListener(OnDeviceConfigListener listener) {
        this.configListener = listener;
    }

    @Override
    public int getCount() {
        return devices.size();
    }

    @Override
    public Object getItem(int position) {
        return devices.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;

        if (convertView == null) {
            convertView = inflater.inflate(R.layout.device_card, parent, false);
            holder = new ViewHolder();

            holder.deviceName = convertView.findViewById(R.id.deviceName);
            holder.routerInfo = convertView.findViewById(R.id.routerInfo);
            holder.temperature = convertView.findViewById(R.id.temperature);
            holder.statusText = convertView.findViewById(R.id.statusText);
            holder.temperatureStatus = convertView.findViewById(R.id.temperatureStatus);
            holder.ipAddress = convertView.findViewById(R.id.ipAddress);
            holder.signalBars = convertView.findViewById(R.id.signalBars);
            holder.signalBar1 = convertView.findViewById(R.id.signalBar1);
            holder.signalBar2 = convertView.findViewById(R.id.signalBar2);
            holder.signalBar3 = convertView.findViewById(R.id.signalBar3);
            holder.signalBar4 = convertView.findViewById(R.id.signalBar4);
            holder.signalBar5 = convertView.findViewById(R.id.signalBar5);
            holder.configButton = convertView.findViewById(R.id.configButton);

            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        DeviceInfo device = devices.get(position);

        // Set device name
        holder.deviceName.setText(device.name);

        // Set router info
        if (device.wifiSSID != null && !device.wifiSSID.isEmpty()) {
            holder.routerInfo.setText("Router: " + device.wifiSSID);
        } else {
            holder.routerInfo.setText("Router: Unknown");
        }

        // FIXED: Set temperature with proper conversion handling
        if (device.temperature != null && !device.temperature.isEmpty() && !device.temperature.equals("Loading...")) {
            String tempDisplay = convertTemperatureBasedOnPrefs(device.temperature, device.name);
            holder.temperature.setText("Temperature: " + tempDisplay);
        } else {
            holder.temperature.setText("Temperature: Loading...");
        }

        // Set status text
        String status = device.status != null ? device.status : "Online";
        holder.statusText.setText(status);

        // Set temperature/sensor status
        String sensorStatus = (device.temperature != null &&
                !device.temperature.equals("Not Found") &&
                !device.temperature.equals("Loading...")) ? "OK" : "Checking...";
        holder.temperatureStatus.setText("Sensor: " + sensorStatus);

        // IMPROVED: Dynamic IP discovery instead of hardcoded IPs
        if (device.ipAddress == null || device.ipAddress.isEmpty() || device.ipAddress.equals("Discovering...")) {
            holder.ipAddress.setText("IP: Discovering...");
            // SERVER-SIDE INTEGRATION: Disabled local discovery
            // discoverDeviceIP(device, position);
        } else {
            holder.ipAddress.setText("IP: " + device.ipAddress);
        }

        // Set signal strength bars
        updateSignalBars(holder, device);

        // Set up config button
        if (holder.configButton != null) {
            holder.configButton.setOnClickListener(v -> {
                Log.d(TAG, "Config button clicked for: " + device.name);
                openDeviceConfiguration(position);
            });
            holder.configButton.setVisibility(android.view.View.VISIBLE);
            Log.d(TAG, "Config button set up for device: " + device.name);
        } else {
            Log.w(TAG, "Config button not found in ViewHolder for position " + position);
        }

        // FIXED: Only fetch data ONCE per device, and only if IP is known
        // SERVER-SIDE INTEGRATION:
        // We now fetch data from the server in MainActivity.
        // Disabled local fetching to prevent overwriting server data.
        /*
        synchronized (fetchingDevices) {
            boolean needsFetch = !fetchingDevices.contains(position) &&
                    (device.temperature == null || device.temperature.equals("Loading...")) &&
                    device.ipAddress != null && !device.ipAddress.isEmpty() &&
                    !device.ipAddress.equals("Discovering...");

            if (needsFetch) {
                fetchingDevices.add(position);
                fetchTemperatureDataSimple(device, position);
            }
        }
        */

        return convertView;
    }

    /**
     * IMPROVED: Discover device IP by scanning current network subnet
     */
    private void discoverDeviceIP(DeviceInfo device, int position) {
        Log.d(TAG, "Starting intelligent IP discovery for device: " + device.name);

        executorService.execute(() -> {
            String deviceIP = findDeviceOnCurrentNetwork(device.name);

            if (deviceIP != null) {
                Log.d(TAG, "Found device " + device.name + " at IP: " + deviceIP);

                // Update device IP
                device.ipAddress = deviceIP;
                device.status = "Found at " + deviceIP;

                // Save the discovered IP
                saveDeviceIP(device, deviceIP);

                // Update UI and start temperature monitoring
                mainHandler.post(() -> {
                    notifyDataSetChanged();
                    Toast.makeText(context, "Found " + device.name + " at " + deviceIP,
                            Toast.LENGTH_SHORT).show();
                });

                // Start fetching temperature data
                synchronized (fetchingDevices) {
                    if (!fetchingDevices.contains(position)) {
                        fetchingDevices.add(position);
                        fetchTemperatureDataSimple(device, position);
                    }
                }
            } else {
                // Device not found on network
                Log.w(TAG, "Could not find device " + device.name + " on current network");
                device.ipAddress = "Not Found";
                device.status = "Device Offline";
                device.temperature = "Device Not Found";

                mainHandler.post(() -> {
                    notifyDataSetChanged();
                    Toast.makeText(context, device.name + " not found on network",
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    /**
     * Scan current network subnet for ESP32 devices
     */
    private String findDeviceOnCurrentNetwork(String deviceName) {
        try {
            // Get current WiFi network info
            WifiManager wifiManager = (WifiManager) context.getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            int ipAddress = wifiInfo.getIpAddress();

            // Convert to IP string and get network prefix
            String phoneIP = String.format("%d.%d.%d.%d",
                    (ipAddress & 0xff),
                    (ipAddress >> 8 & 0xff),
                    (ipAddress >> 16 & 0xff),
                    (ipAddress >> 24 & 0xff));

            Log.d(TAG, "Phone IP: " + phoneIP);

            // Extract network prefix (e.g., "192.168.0")
            String networkPrefix = phoneIP.substring(0, phoneIP.lastIndexOf('.'));
            Log.d(TAG, "Scanning network: " + networkPrefix + ".xxx");

            // Try known device IPs first
            String[] knownIPs = {
                networkPrefix + ".147",  // Original device IP
                networkPrefix + ".131",  // New device IP
                networkPrefix + ".140"   // Another possible IP
            };
            
            for (String knownIP : knownIPs) {
                if (testESP32DeviceAtIP(knownIP)) {
                    Log.d(TAG, "ESP32 device found at known IP: " + knownIP);
                    return knownIP;
                }
            }

            // Scan common device IP ranges (skip .1 router, start from .2)
            int[] commonIPs = {100, 101, 102, 110, 120, 150, 200};
            for (int ip : commonIPs) {
                String candidateIP = networkPrefix + "." + ip;

                // Skip phone's own IP
                if (candidateIP.equals(phoneIP)) continue;

                Log.d(TAG, "Testing IP: " + candidateIP);

                if (testESP32DeviceAtIP(candidateIP)) {
                    Log.d(TAG, "ESP32 device found at: " + candidateIP);
                    return candidateIP;
                }
            }

            return null; // Device not found

        } catch (Exception e) {
            Log.e(TAG, "Error during network discovery", e);
            return null;
        }
    }

    /**
     * Test if an ESP32 device is at the given IP
     */
    private boolean testESP32DeviceAtIP(String ipAddress) {
        try {
            // Test HTTP connection to ESP32's /data endpoint
            URL url = new URL("http://" + ipAddress + "/data");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(2000); // 2 second timeout for faster scanning
            connection.setReadTimeout(2000);
            connection.setRequestProperty("User-Agent", "SmartWorks-Discovery/1.0");

            int responseCode = connection.getResponseCode();

            if (responseCode == 200) {
                // Read response to verify it's an ESP32 device
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                String jsonResponse = response.toString();

                // Check if response contains ESP32 device indicators
                return jsonResponse.contains("temperature_celsius") ||
                        jsonResponse.contains("ESP32") ||
                        jsonResponse.contains("device_name");
            }

            connection.disconnect();
            return false;

        } catch (Exception e) {
            // Connection failed - device not at this IP
            return false;
        }
    }

    /**
     * Save discovered IP address to preferences
     */
    private void saveDeviceIP(DeviceInfo device, String ipAddress) {
        try {
            android.content.SharedPreferences prefs = context.getSharedPreferences("SmartWorks", Context.MODE_PRIVATE);
            android.content.SharedPreferences.Editor editor = prefs.edit();

            // Save IP for quick lookup
            editor.putString("device_ip_" + device.name, ipAddress);

            // Update main device storage
            Set<String> deviceSet = new HashSet<>(prefs.getStringSet("provisioned_devices", new HashSet<>()));

            // Remove old entry and add updated one
            deviceSet.removeIf(s -> s.startsWith(device.name + "\n"));
            deviceSet.add(device.toStoredString());

            editor.putStringSet("provisioned_devices", deviceSet);
            editor.apply();

            Log.d(TAG, "Saved IP for " + device.name + ": " + ipAddress);
        } catch (Exception e) {
            Log.e(TAG, "Error saving device IP", e);
        }
    }

    /**
     * FIXED: Proper temperature conversion - assumes input is Fahrenheit (from server)
     */
    private String convertTemperatureBasedOnPrefs(String temperatureValue, String deviceName) {
        try {
            // Get user preference for temperature units
            android.content.SharedPreferences prefs =
                    context.getSharedPreferences("SmartWorks_" + deviceName, Context.MODE_PRIVATE);
            String tempUnits = prefs.getString("temp_units", "Fahrenheit");

            // Extract numeric value (Server sends Fahrenheit)
            String numericPart = temperatureValue.replaceAll("[^0-9.-]", "");
            if (!numericPart.isEmpty()) {
                double fahrenheit = Double.parseDouble(numericPart);

                if ("Celsius".equals(tempUnits)) {
                    // Convert Fahrenheit to Celsius
                    double celsius = (fahrenheit - 32) * 5.0 / 9.0;
                    return String.format("%.1f°C", celsius);
                } else {
                    // Display as Fahrenheit (default)
                    return String.format("%.1f°F", fahrenheit);
                }
            }
        } catch (NumberFormatException e) {
            Log.w(TAG, "Could not convert temperature: " + temperatureValue);
        }
        return temperatureValue;
    }

    private void openDeviceConfiguration(int position) {
        if (position < devices.size()) {
            DeviceInfo device = devices.get(position);

            // Use callback if available, otherwise fall back to direct intent
            if (configListener != null) {
                configListener.onOpenDeviceConfig(device, position);
                return;
            }

            // Fallback to direct intent (for backwards compatibility)
            // Check if this is a Pool Monitor device
            if (device.name.toLowerCase().contains("pool") ||
                    device.name.toLowerCase().contains("temperature") ||
                    device.name.toLowerCase().contains("monitor")) {

                // Show options for pool devices
                android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(context);
                builder.setTitle("Open " + device.name);
                builder.setMessage("How would you like to access this device?");

                builder.setPositiveButton("Pool Monitor", (dialog, which) -> {
                    // Open dedicated Pool Monitor activity
                    android.content.Intent poolIntent = new android.content.Intent(context, PoolMonitorActivity.class);
                    poolIntent.putExtra("device_name", device.name);
                    poolIntent.putExtra("device_ip", device.ipAddress);
                    poolIntent.putExtra("wifi_ssid", device.wifiSSID);
                    context.startActivity(poolIntent);
                });

                builder.setNegativeButton("Device Settings", (dialog, which) -> {
                    // Open device config for settings/delete
                    android.content.Intent intent = new android.content.Intent(context, DeviceConfigActivity.class);
                    intent.putExtra("device_name", device.name);
                    intent.putExtra("device_address", device.address);
                    intent.putExtra("device_ip", device.ipAddress);
                    intent.putExtra("wifi_ssid", device.wifiSSID);
                    intent.putExtra("device_position", position);
                    context.startActivity(intent);
                });

                builder.setNeutralButton("Cancel", null);
                builder.show();

            } else {
                // Open generic device config
                android.content.Intent intent = new android.content.Intent(context, DeviceConfigActivity.class);
                intent.putExtra("device_name", device.name);
                intent.putExtra("device_address", device.address);
                intent.putExtra("device_ip", device.ipAddress);
                intent.putExtra("wifi_ssid", device.wifiSSID);
                intent.putExtra("device_position", position);
                context.startActivity(intent);
            }
        }
    }

    // Fetch temperature from device using its known IP
    private void fetchTemperatureDataSimple(DeviceInfo device, int position) {
        if (device.ipAddress == null || device.ipAddress.isEmpty() || device.ipAddress.equals("Discovering...")) {
            Log.w(TAG, "Cannot fetch temperature - no IP address for " + device.name);
            return;
        }

        // Submit task to executor
        try {
            executorService.execute(() -> {
                Log.d(TAG, "Fetching temperature for: " + device.name + " at " + device.ipAddress);

                // First check if we're on WiFi
                if (!isWifiConnected()) {
                    Log.e(TAG, "WiFi not connected");
                    device.temperature = "No WiFi";
                    device.status = "WiFi Disconnected";
                    mainHandler.post(() -> notifyDataSetChanged());
                    synchronized (fetchingDevices) {
                        fetchingDevices.remove(position);
                    }
                    return;
                }

                HttpURLConnection connection = null;
                try {
                    // Try the URL with explicit parameters
                    URL url = new URL("http://" + device.ipAddress + "/data");
                    Log.d(TAG, "Connecting to: " + url.toString());

                    connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(10000); // 10 seconds
                    connection.setReadTimeout(10000);
                    connection.setRequestProperty("Accept", "application/json, text/plain, */*");
                    connection.setRequestProperty("User-Agent", "SmartWorks-Android/1.0");
                    connection.setRequestProperty("Connection", "close");
                    connection.setUseCaches(false);
                    connection.setDoInput(true);

                    int responseCode = connection.getResponseCode();
                    Log.d(TAG, "Response code: " + responseCode);

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

                        if (parseESP32Response(jsonResponse, device)) {
                            Log.d(TAG, "Successfully parsed temperature: " + device.temperature);
                            device.status = "Online";
                        } else {
                            device.temperature = "Parse Error";
                            device.status = "Data Error";
                        }
                    } else {
                        Log.e(TAG, "HTTP error code: " + responseCode);
                        device.temperature = "HTTP " + responseCode;
                        device.status = "Server Error";
                    }

                } catch (java.net.SocketTimeoutException e) {
                    Log.e(TAG, "Socket timeout: " + e.getMessage());
                    device.temperature = "Timeout";
                    device.status = "Connection Timeout";
                } catch (java.net.ConnectException e) {
                    Log.e(TAG, "Connection refused: " + e.getMessage());
                    device.temperature = "Cannot Reach Device";
                    device.status = "Device Unreachable";

                    // IP might have changed - trigger rediscovery
                    mainHandler.post(() -> {
                        Toast.makeText(context,
                                device.name + " not responding at " + device.ipAddress + ". Searching for new IP...",
                                Toast.LENGTH_SHORT).show();
                    });

                    // Clear IP and rediscover
                    device.ipAddress = "Discovering...";
                    discoverDeviceIP(device, position);
                } catch (java.net.UnknownHostException e) {
                    Log.e(TAG, "Unknown host: " + e.getMessage());
                    device.temperature = "DNS Error";
                    device.status = "Cannot Resolve IP";
                } catch (Exception e) {
                    Log.e(TAG, "Error fetching temperature: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                    device.temperature = "Error";
                    device.status = "Connection Failed";
                } finally {
                    if (connection != null) {
                        try {
                            connection.disconnect();
                        } catch (Exception ignored) {}
                    }

                    // Remove from fetching set
                    synchronized (fetchingDevices) {
                        fetchingDevices.remove(position);
                    }
                }

                // Update UI
                mainHandler.post(() -> notifyDataSetChanged());
            });
        } catch (Exception e) {
            Log.e(TAG, "Failed to submit fetch task", e);
            synchronized (fetchingDevices) {
                fetchingDevices.remove(position);
            }
        }
    }

    private boolean isWifiConnected() {
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager)
                    context.getSystemService(Context.CONNECTIVITY_SERVICE);
            android.net.NetworkInfo networkInfo = cm.getActiveNetworkInfo();
            return networkInfo != null && networkInfo.isConnected() &&
                    networkInfo.getType() == android.net.ConnectivityManager.TYPE_WIFI;
        } catch (Exception e) {
            Log.e(TAG, "Error checking WiFi status", e);
            return false;
        }
    }

    public void fetchDeviceData(DeviceInfo device, int position) {
        synchronized (fetchingDevices) {
            fetchingDevices.remove(position);
        }
        fetchTemperatureDataSimple(device, position);
    }

    /**
     * FIXED: Parse ESP32 response and store temperature as Celsius number only
     */
    private boolean parseESP32Response(String response, DeviceInfo device) {
        if (response == null || response.trim().isEmpty()) {
            return false;
        }

        try {
            JSONObject json = new JSONObject(response);

            // FIXED: Store temperature as plain Celsius number (no units)
            // The convertTemperatureBasedOnPrefs method will handle unit conversion
            if (json.has("temperature_celsius")) {
                double tempCelsius = json.getDouble("temperature_celsius");
                device.temperature = String.format("%.1f", tempCelsius); // Store as plain number
                Log.d(TAG, "Parsed temperature: " + tempCelsius + "°C");
            } else if (json.has("temperature_fahrenheit")) {
                // If ESP32 sends Fahrenheit, convert to Celsius for storage
                double tempFahrenheit = json.getDouble("temperature_fahrenheit");
                double tempCelsius = (tempFahrenheit - 32) * 5.0 / 9.0;
                device.temperature = String.format("%.1f", tempCelsius);
                Log.d(TAG, "Converted temperature: " + tempFahrenheit + "°F -> " + tempCelsius + "°C");
            } else if (json.has("temperature")) {
                // Assume this is in Celsius
                double temp = json.getDouble("temperature");
                device.temperature = String.format("%.1f", temp);
            } else {
                Log.w(TAG, "No temperature field in JSON");
                return false;
            }

            if (json.has("sensor_found")) {
                boolean sensorFound = json.getBoolean("sensor_found");
                if (!sensorFound) {
                    device.temperature = "Sensor Error";
                    device.status = "Sensor Not Found";
                    return true;
                }
            }

            if (json.has("wifi_ssid")) {
                device.wifiSSID = json.getString("wifi_ssid");
            }

            if (json.has("rssi")) {
                device.rssi = json.getInt("rssi");
                updateSignalStatus(device);
            } else {
                device.status = "Online";
            }

            return true;

        } catch (JSONException e) {
            Log.e(TAG, "JSON parsing error: " + e.getMessage());
            return false;
        }
    }

    private void updateSignalStatus(DeviceInfo device) {
        if (device.rssi != null) {
            String signalStrength;
            if (device.rssi >= -50) signalStrength = "Excellent";
            else if (device.rssi >= -60) signalStrength = "Good";
            else if (device.rssi >= -70) signalStrength = "Fair";
            else signalStrength = "Weak";

            device.status = "Online (" + signalStrength + ")";
        }
    }

    private void updateSignalBars(ViewHolder holder, DeviceInfo device) {
        int inactiveColor = 0xFF999999;
        holder.signalBar1.setBackgroundColor(inactiveColor);
        holder.signalBar2.setBackgroundColor(inactiveColor);
        holder.signalBar3.setBackgroundColor(inactiveColor);
        holder.signalBar4.setBackgroundColor(inactiveColor);
        holder.signalBar5.setBackgroundColor(inactiveColor);

        int signalStrength = getSignalStrengthFromDevice(device);

        int color = 0xFF4CAF50;
        if (signalStrength <= 2) color = 0xFFFF9800;
        if (signalStrength == 0) color = 0xFFF44336;

        if (signalStrength >= 1) holder.signalBar1.setBackgroundColor(color);
        if (signalStrength >= 2) holder.signalBar2.setBackgroundColor(color);
        if (signalStrength >= 3) holder.signalBar3.setBackgroundColor(color);
        if (signalStrength >= 4) holder.signalBar4.setBackgroundColor(color);
        if (signalStrength >= 5) holder.signalBar5.setBackgroundColor(color);
    }

    private int getSignalStrengthFromDevice(DeviceInfo device) {
        if (device.rssi != null) {
            if (device.rssi >= -50) return 5;
            if (device.rssi >= -60) return 4;
            if (device.rssi >= -70) return 3;
            if (device.rssi >= -80) return 2;
            return 1;
        }

        if (device.status != null) {
            if (device.status.contains("Excellent")) return 5;
            if (device.status.contains("Good")) return 4;
            if (device.status.contains("Fair")) return 3;
            if (device.status.contains("Weak")) return 2;
            if (device.status.contains("Online")) return 4;
            if (device.status.contains("Offline")) return 0;
        }

        return 0;
    }

    public void refreshDevice(int position) {
        if (position >= 0 && position < devices.size()) {
            DeviceInfo device = devices.get(position);
            Log.d(TAG, "Refreshing device: " + device.name);

            device.temperature = "Loading...";
            device.status = "Refreshing...";

            synchronized (fetchingDevices) {
                fetchingDevices.remove(position);
            }

            fetchTemperatureDataSimple(device, position);
            notifyDataSetChanged();
        }
    }

    public void refreshAllDevices() {
        Log.d(TAG, "Refreshing all devices");

        synchronized (fetchingDevices) {
            fetchingDevices.clear();
        }

        for (int i = 0; i < devices.size(); i++) {
            DeviceInfo device = devices.get(i);
            device.temperature = "Loading...";
            device.status = "Refreshing...";
        }

        notifyDataSetChanged();
    }

    public void forceIPRediscovery() {
        Log.d(TAG, "Force IP rediscovery for all devices");

        synchronized (fetchingDevices) {
            fetchingDevices.clear();
        }

        for (int i = 0; i < devices.size(); i++) {
            DeviceInfo device = devices.get(i);
            device.ipAddress = "Discovering...";
            device.temperature = "Loading...";
            device.status = "Searching...";
            discoverDeviceIP(device, i);
        }

        notifyDataSetChanged();
    }

    public void updateStoredDeviceIP(DeviceInfo device, String newIP) {
        device.ipAddress = newIP;
        saveDeviceIP(device, newIP);
    }

    public String getDeviceIP(int position) {
        if (position < devices.size()) {
            return devices.get(position).ipAddress;
        }
        return null;
    }

    public void cleanup() {
        synchronized (fetchingDevices) {
            fetchingDevices.clear();
        }
    }

    private static class ViewHolder {
        TextView deviceName;
        TextView routerInfo;
        TextView temperature;
        TextView statusText;
        TextView temperatureStatus;
        TextView ipAddress;
        LinearLayout signalBars;
        View signalBar1, signalBar2, signalBar3, signalBar4, signalBar5;
        android.widget.ImageButton configButton;
    }

    public static class DeviceInfo {
        public String name;
        public String address;
        public String wifiSSID;
        public String ipAddress;
        public String temperature;
        public String status;
        public Integer rssi;

        public DeviceInfo(String name, String address, String wifiSSID) {
            this.name = name;
            this.address = address;
            this.wifiSSID = wifiSSID;
            this.temperature = "Loading...";
            this.status = "Connecting...";
            this.rssi = null;
        }

        public static DeviceInfo fromStoredString(String deviceString) {
            String[] lines = deviceString.split("\n");
            String name = lines.length > 0 ? lines[0] : "Unknown Device";
            String address = "";
            String wifiSSID = "";
            String temperature = "Loading...";
            String status = "Online";
            String ipAddress = "";

            for (int i = 1; i < lines.length; i++) {
                String line = lines[i];
                if (line.startsWith("Router: ")) {
                    wifiSSID = line.substring(8);
                } else if (line.startsWith("Temperature: ")) {
                    temperature = line.substring(13);
                } else if (line.startsWith("Status: ")) {
                    status = line.substring(8);
                } else if (line.startsWith("IP: ")) {
                    ipAddress = line.substring(4);
                } else if (i == 1 && !line.contains(":")) {
                    address = line;
                }
            }

            DeviceInfo device = new DeviceInfo(name, address, wifiSSID);
            device.temperature = temperature;
            device.status = status;
            device.ipAddress = ipAddress;
            return device;
        }

        public static DeviceInfo fromStoredString(String deviceString, Context context) {
            DeviceInfo device = fromStoredString(deviceString);

            if (context != null) {
                android.content.SharedPreferences prefs =
                        context.getSharedPreferences("SmartWorks_" + device.name, Context.MODE_PRIVATE);
                String customName = prefs.getString("device_name", null);
                if (customName != null && !customName.isEmpty()) {
                    device.name = customName;
                }
                
                // IMPROVED: Load stored IP address if not already set
                if ((device.ipAddress == null || device.ipAddress.isEmpty()) && context != null) {
                    android.content.SharedPreferences mainPrefs = 
                        context.getSharedPreferences("SmartWorks", Context.MODE_PRIVATE);
                    String storedIP = mainPrefs.getString("device_ip_" + device.name, "");
                    if (!storedIP.isEmpty()) {
                        device.ipAddress = storedIP;
                        Log.d("DeviceInfo", "Loaded stored IP for " + device.name + ": " + storedIP);
                    }
                }
            }

            return device;
        }

        public String toStoredString() {
            StringBuilder sb = new StringBuilder();
            sb.append(name);
            if (address != null && !address.isEmpty()) {
                sb.append("\n").append(address);
            }
            if (wifiSSID != null && !wifiSSID.isEmpty()) {
                sb.append("\nRouter: ").append(wifiSSID);
            }
            if (ipAddress != null && !ipAddress.isEmpty()) {
                sb.append("\nIP: ").append(ipAddress);
            }
            if (temperature != null && !temperature.isEmpty()) {
                sb.append("\nTemperature: ").append(temperature);
            }
            if (status != null && !status.isEmpty()) {
                sb.append("\nStatus: ").append(status);
            }
            return sb.toString();
        }
    }
}