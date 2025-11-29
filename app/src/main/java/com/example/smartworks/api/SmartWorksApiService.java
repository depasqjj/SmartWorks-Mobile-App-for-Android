package com.example.smartworks.api;

import android.util.Log;
import com.example.smartworks.auth.AuthenticationManager;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import okhttp3.*;

/**
 * API Service for SmartWorks device management
 * Integrates with your existing PHP backend
 */
public class SmartWorksApiService {
    private static final String TAG = "ApiService";
    private static final String BASE_URL = "https://smartworkstech.com/server/"; // Updated to match new API structure
    
    private static SmartWorksApiService instance;
    private final OkHttpClient httpClient;
    private final Gson gson;
    private final ExecutorService executorService;
    private final AuthenticationManager authManager;
    
    private SmartWorksApiService(AuthenticationManager authManager) {
        this.authManager = authManager;
        this.gson = new Gson();
        this.executorService = Executors.newCachedThreadPool();
        
        // Initialize OkHttp with timeouts and interceptors
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .cache(null)  // Disable caching completely
                .addInterceptor(new AuthInterceptor(authManager))
                .addInterceptor(new LoggingInterceptor())
                .build();
    }
    
    public static synchronized SmartWorksApiService getInstance(AuthenticationManager authManager) {
        if (instance == null) {
            instance = new SmartWorksApiService(authManager);
        }
        return instance;
    }
    
    /**
     * Register a new device for the authenticated user
     */
    public CompletableFuture<ApiResult<Device>> registerDevice(String deviceMac, String type, String friendlyName) {
        CompletableFuture<ApiResult<Device>> future = new CompletableFuture<>();
        
        executorService.execute(() -> {
            try {
                RegisterDeviceRequest request = new RegisterDeviceRequest(deviceMac, type, friendlyName);
                RequestBody body = RequestBody.create(
                        gson.toJson(request),
                        MediaType.get("application/json")
                );
                
                Request httpRequest = new Request.Builder()
                        .url(BASE_URL + "register_device.php")
                        .post(body)
                        .header("Content-Type", "application/json")
                        .build();
                
                Response response = httpClient.newCall(httpRequest).execute();
                String responseBody = response.body().string();
                
                if (response.isSuccessful()) {
                    RegisterDeviceResponse deviceResponse = gson.fromJson(responseBody, RegisterDeviceResponse.class);
                    
                    if ("ok".equals(deviceResponse.status)) {
                        future.complete(ApiResult.success("Device registered successfully", deviceResponse.device));
                    } else {
                        future.complete(ApiResult.error(deviceResponse.message));
                    }
                } else {
                    ErrorResponse errorResponse = gson.fromJson(responseBody, ErrorResponse.class);
                    String errorMessage = errorResponse != null ? errorResponse.message : "Device registration failed";
                    future.complete(ApiResult.error(errorMessage));
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Device registration error", e);
                future.complete(ApiResult.error("Network error: " + e.getMessage()));
            }
        });
        
        return future;
    }
    
    /**
     * Get all devices for the authenticated user
     */
    public CompletableFuture<ApiResult<List<Device>>> getUserDevices() {
        CompletableFuture<ApiResult<List<Device>>> future = new CompletableFuture<>();
        
        executorService.execute(() -> {
            try {
                // Add timestamp to force fresh data (cache busting)
                String url = BASE_URL + "api/get_user_devices.php?_t=" + System.currentTimeMillis();
                
                Request httpRequest = new Request.Builder()
                        .url(url)
                        .get()
                        .build();
                
                Response response = httpClient.newCall(httpRequest).execute();
                String responseBody = response.body().string();
                
                if (response.isSuccessful()) {
                    DeviceListResponse deviceResponse = gson.fromJson(responseBody, DeviceListResponse.class);
                    
                    if ("ok".equals(deviceResponse.status)) {
                        future.complete(ApiResult.success("Devices retrieved successfully", deviceResponse.devices));
                    } else {
                        future.complete(ApiResult.error(deviceResponse.message));
                    }
                } else {
                    // Try to parse error message from response body
                    try {
                        ErrorResponse errorResponse = gson.fromJson(responseBody, ErrorResponse.class);
                        String errorMessage = errorResponse != null && errorResponse.message != null ? 
                                errorResponse.message : "Server error: " + response.code();
                        future.complete(ApiResult.error(errorMessage));
                    } catch (Exception e) {
                        future.complete(ApiResult.error("Failed to retrieve devices: " + response.code()));
                    }
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Get devices error", e);
                future.complete(ApiResult.error("Network error: " + e.getMessage()));
            }
        });
        
        return future;
    }
    
    /**
     * Update device settings
     */
    public CompletableFuture<ApiResult<Device>> updateDevice(int deviceId, DeviceUpdateRequest updateRequest) {
        CompletableFuture<ApiResult<Device>> future = new CompletableFuture<>();
        
        executorService.execute(() -> {
            try {
                RequestBody body = RequestBody.create(
                        gson.toJson(updateRequest),
                        MediaType.get("application/json")
                );
                
                Request httpRequest = new Request.Builder()
                        .url(BASE_URL + "api/update_device.php?device_id=" + deviceId)
                        .put(body)
                        .header("Content-Type", "application/json")
                        .build();
                
                Response response = httpClient.newCall(httpRequest).execute();
                String responseBody = response.body().string();
                
                if (response.isSuccessful()) {
                    UpdateDeviceResponse deviceResponse = gson.fromJson(responseBody, UpdateDeviceResponse.class);
                    
                    if ("ok".equals(deviceResponse.status)) {
                        future.complete(ApiResult.success("Device updated successfully", deviceResponse.device));
                    } else {
                        future.complete(ApiResult.error(deviceResponse.message));
                    }
                } else {
                    future.complete(ApiResult.error("Failed to update device"));
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Update device error", e);
                future.complete(ApiResult.error("Network error: " + e.getMessage()));
            }
        });
        
        return future;
    }
    
    /**
     * Delete a device
     */
    /**
     * Delete a device
     */
    public CompletableFuture<ApiResult<Void>> deleteDevice(String deviceId) {
        CompletableFuture<ApiResult<Void>> future = new CompletableFuture<>();
        
        executorService.execute(() -> {
            try {
                Request httpRequest = new Request.Builder()
                        .url(BASE_URL + "api/devices/register.php?device_id=" + deviceId)
                        .delete()
                        .build();
                
                Response response = httpClient.newCall(httpRequest).execute();
                String responseBody = response.body().string();
                
                if (response.isSuccessful()) {
                    BasicResponse basicResponse = gson.fromJson(responseBody, BasicResponse.class);
                    
                    if ("ok".equals(basicResponse.status)) {
                        future.complete(ApiResult.success("Device deleted successfully"));
                    } else {
                        future.complete(ApiResult.error(basicResponse.message));
                    }
                } else {
                    future.complete(ApiResult.error("Failed to delete device"));
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Delete device error", e);
                future.complete(ApiResult.error("Network error: " + e.getMessage()));
            }
        });
        
        return future;
    }
    
    /**
     * Send command to device
     */
    public CompletableFuture<ApiResult<Void>> sendDeviceCommand(String deviceId, String command) {
        CompletableFuture<ApiResult<Void>> future = new CompletableFuture<>();
        
        executorService.execute(() -> {
            try {
                DeviceCommandRequest request = new DeviceCommandRequest(deviceId, command);
                RequestBody body = RequestBody.create(
                        gson.toJson(request),
                        MediaType.get("application/json")
                );
                
                Request httpRequest = new Request.Builder()
                        .url(BASE_URL + "api/send_command.php")
                        .post(body)
                        .header("Content-Type", "application/json")
                        .build();
                
                Response response = httpClient.newCall(httpRequest).execute();
                String responseBody = response.body().string();
                
                if (response.isSuccessful()) {
                    BasicResponse basicResponse = gson.fromJson(responseBody, BasicResponse.class);
                    
                    if ("ok".equals(basicResponse.status)) {
                        future.complete(ApiResult.success("Command sent successfully"));
                    } else {
                        future.complete(ApiResult.error(basicResponse.message));
                    }
                } else {
                    future.complete(ApiResult.error("Failed to send command"));
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Send command error", e);
                future.complete(ApiResult.error("Network error: " + e.getMessage()));
            }
        });
        
        return future;
    }
    
    /**
     * Get device readings/data
     */
    public CompletableFuture<ApiResult<List<DeviceReading>>> getDeviceReadings(String deviceId, int limit) {
        CompletableFuture<ApiResult<List<DeviceReading>>> future = new CompletableFuture<>();
        
        executorService.execute(() -> {
            try {
                String url = BASE_URL + "api/get_readings.php?device_id=" + deviceId;
                if (limit > 0) {
                    url += "&limit=" + limit;
                }
                
                Request httpRequest = new Request.Builder()
                        .url(url)
                        .get()
                        .build();
                
                Response response = httpClient.newCall(httpRequest).execute();
                String responseBody = response.body().string();
                
                if (response.isSuccessful()) {
                    ReadingsResponse readingsResponse = gson.fromJson(responseBody, ReadingsResponse.class);
                    
                    if ("ok".equals(readingsResponse.status)) {
                        future.complete(ApiResult.success("Readings retrieved successfully", readingsResponse.readings));
                    } else {
                        future.complete(ApiResult.error(readingsResponse.message));
                    }
                } else {
                    future.complete(ApiResult.error("Failed to retrieve readings"));
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Get readings error", e);
                future.complete(ApiResult.error("Network error: " + e.getMessage()));
            }
        });
        
        return future;
    }
    
    /**
     * Check for firmware updates
     */
    public CompletableFuture<ApiResult<FirmwareUpdateResponse>> checkFirmwareUpdate(String deviceType, String currentVersion) {
        CompletableFuture<ApiResult<FirmwareUpdateResponse>> future = new CompletableFuture<>();
        
        executorService.execute(() -> {
            try {
                String url = BASE_URL + "api/firmware/check.php?device_type=" + deviceType + "&current_version=" + currentVersion;
                
                Request httpRequest = new Request.Builder()
                        .url(url)
                        .get()
                        .build();
                
                Response response = httpClient.newCall(httpRequest).execute();
                String responseBody = response.body().string();
                
                if (response.isSuccessful()) {
                    FirmwareUpdateResponse updateResponse = gson.fromJson(responseBody, FirmwareUpdateResponse.class);
                    
                    if (updateResponse != null) {
                        future.complete(ApiResult.success("Check complete", updateResponse));
                    } else {
                        future.complete(ApiResult.error("Invalid response from server"));
                    }
                } else {
                    future.complete(ApiResult.error("Failed to check for updates"));
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Check firmware error", e);
                future.complete(ApiResult.error("Network error: " + e.getMessage()));
            }
        });
        
        return future;
    }
    
    // Data classes for API communication
    public static class ApiResult<T> {
        public final boolean success;
        public final String message;
        public final T data;
        
        private ApiResult(boolean success, String message, T data) {
            this.success = success;
            this.message = message;
            this.data = data;
        }
        
        public static <T> ApiResult<T> success(String message) {
            return new ApiResult<>(true, message, null);
        }
        
        public static <T> ApiResult<T> success(String message, T data) {
            return new ApiResult<>(true, message, data);
        }
        
        public static <T> ApiResult<T> error(String message) {
            return new ApiResult<>(false, message, null);
        }
    }
    
    // Device model class
    public static class Device {
        public int id;
        @SerializedName("device_id")
        public String deviceId;
        @SerializedName("user_id")
        public int userId;
        @SerializedName("api_key")
        public String apiKey;
        @SerializedName("low_threshold")
        public float lowThreshold;
        @SerializedName("high_threshold")
        public float highThreshold;
        @SerializedName("webhook_url")
        public String webhookUrl;
        @SerializedName("last_temp")
        public Float lastTemp;
        public String type;
        @SerializedName("friendly_name")
        public String friendlyName;
        public boolean enabled;
        @SerializedName("updated_at")
        public String updatedAt;
        
        // Additional fields from new API
        @SerializedName("ip_address")
        public String ipAddress;
        public Float temperature;
        @SerializedName("temperature_time")
        public String temperatureTime;
        public String status;  // online, idle, offline
        @SerializedName("status_message")
        public String statusMessage;
        @SerializedName("last_seen")
        public String lastSeen;
        @SerializedName("wifi_ssid")
        public String wifiSsid;
        public Integer rssi;
        @SerializedName("firmware_version")
        public String firmwareVersion;
    }
    
    // Device reading model
    public static class DeviceReading {
        public int id;
        @SerializedName("device_id")
        public String deviceId;
        @SerializedName("user_id")
        public int userId;
        public float temperature;
        public String timestamp;
    }
    
    // Request/Response classes
    private static class RegisterDeviceRequest {
        @SerializedName("device_mac")
        final String deviceMac;
        final String type;
        @SerializedName("friendly_name")
        final String friendlyName;
        
        RegisterDeviceRequest(String deviceMac, String type, String friendlyName) {
            this.deviceMac = deviceMac;
            this.type = type;
            this.friendlyName = friendlyName;
        }
    }
    
    private static class RegisterDeviceResponse {
        String status;
        String message;
        Device device;
    }
    
    private static class DeviceListResponse {
        String status;
        String message;
        List<Device> devices;
    }
    
    public static class DeviceUpdateRequest {
        @SerializedName("friendly_name")
        public String friendlyName;
        @SerializedName("low_threshold")
        public Float lowThreshold;
        @SerializedName("high_threshold")
        public Float highThreshold;
        @SerializedName("webhook_url")
        public String webhookUrl;
        public Boolean enabled;
        
        public DeviceUpdateRequest() {}
        
        public DeviceUpdateRequest setFriendlyName(String friendlyName) {
            this.friendlyName = friendlyName;
            return this;
        }
        
        public DeviceUpdateRequest setLowThreshold(float lowThreshold) {
            this.lowThreshold = lowThreshold;
            return this;
        }
        
        public DeviceUpdateRequest setHighThreshold(float highThreshold) {
            this.highThreshold = highThreshold;
            return this;
        }
        
        public DeviceUpdateRequest setWebhookUrl(String webhookUrl) {
            this.webhookUrl = webhookUrl;
            return this;
        }
        
        public DeviceUpdateRequest setEnabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }
    }
    
    private static class UpdateDeviceResponse {
        String status;
        String message;
        Device device;
    }
    
    private static class DeviceCommandRequest {
        @SerializedName("device_id")
        final String deviceId;
        final String command;
        
        DeviceCommandRequest(String deviceId, String command) {
            this.deviceId = deviceId;
            this.command = command;
        }
    }
    
    private static class ReadingsResponse {
        String status;
        String message;
        List<DeviceReading> readings;
    }
    
    private static class BasicResponse {
        String status;
        String message;
    }
    
    private static class ErrorResponse {
        String status;
        String message;
    }
    
    public static class FirmwareUpdateResponse {
        public boolean available;
        public String version;
        public String url;
        public String notes;
        public String message;
    }
    
    // HTTP Interceptors
    private static class AuthInterceptor implements Interceptor {
        private final AuthenticationManager authManager;
        
        AuthInterceptor(AuthenticationManager authManager) {
            this.authManager = authManager;
        }
        
        @Override
        public Response intercept(Chain chain) throws IOException {
            Request originalRequest = chain.request();
            
            // Add authentication headers if user is logged in
            if (authManager.isLoggedIn()) {
                Request.Builder builder = originalRequest.newBuilder()
                        .header("X-API-KEY", authManager.getApiKey())
                        .header("X-USER-ID", String.valueOf(authManager.getUserId()));
                
                Request newRequest = builder.build();
                return chain.proceed(newRequest);
            }
            
            return chain.proceed(originalRequest);
        }
    }
    
    private static class LoggingInterceptor implements Interceptor {
        @Override
        public Response intercept(Chain chain) throws IOException {
            Request request = chain.request();
            
            long startTime = System.nanoTime();
            Log.d(TAG, String.format("Sending request %s on %s%n%s",
                    request.url(), chain.connection(), request.headers()));
            
            Response response = chain.proceed(request);
            
            long endTime = System.nanoTime();
            Log.d(TAG, String.format("Received response for %s in %.1fms%n%s",
                    response.request().url(), (endTime - startTime) / 1e6d, response.headers()));
            
            return response;
        }
    }
}