package com.example.smartworks.config;

/**
 * Configuration class for SmartWorks API settings
 * Update these values for your production deployment
 */
public class ApiConfig {
    
    // Production API Configuration
    // TODO: Update this URL for your production server
    public static final String PRODUCTION_BASE_URL = "https://smartworkstech.com/server/";
    
    // Development/Testing Configuration - FIXED: Updated for proper server
    // Update this to match your local development server
    public static final String DEVELOPMENT_BASE_URL = "https://smartworkstech.com/server/";
    
    // Current environment settings - FIXED: Set to production
    public static final boolean USE_PRODUCTION = true; // FIXED: Changed to true for production builds
    
    /**
     * Get the appropriate base URL based on current environment
     */
    public static String getBaseUrl() {
        return USE_PRODUCTION ? PRODUCTION_BASE_URL : DEVELOPMENT_BASE_URL;
    }
    
    // Web Interface Endpoints - Updated to match your PHP backend
    public static final String LOGIN_ENDPOINT = "login.php";
    public static final String REGISTER_ENDPOINT = "register_user.php";
    public static final String FORGOT_PASSWORD_ENDPOINT = "forgot_password.php";
    public static final String RESET_PASSWORD_ENDPOINT = "reset_password.php";
    public static final String LOGOUT_ENDPOINT = "logout.php";
    public static final String DASHBOARD_ENDPOINT = "dashboard.php";
    public static final String REGISTER_DEVICE_ENDPOINT = "register_device.php";
    public static final String VALIDATE_SESSION_ENDPOINT = "api/validate.php";
    
    // Device API endpoints
    public static final String TEMPERATURE_ENDPOINT = "api/devices/temperature.php";
    public static final String COMMANDS_ENDPOINT = "api/devices/commands.php";
    public static final String DEVICE_TEST_ENDPOINT = "api/devices/test.php";
    
    // Timeout configurations (in milliseconds)
    public static final int CONNECT_TIMEOUT = 10000;    // 10 seconds
    public static final int READ_TIMEOUT = 15000;       // 15 seconds  
    public static final int WRITE_TIMEOUT = 15000;      // 15 seconds
    
    /**
     * Build full URL for a web interface endpoint
     */
    public static String buildUrl(String endpoint) {
        return getBaseUrl() + endpoint;
    }
    
    /**
     * Build API URL for device endpoints  
     */
    public static String buildApiUrl(String endpoint) {
        return getBaseUrl() + endpoint;
    }
    
    /**
     * Get login URL for JSON authentication
     */
    public static String getLoginUrl() {
        return buildUrl(LOGIN_ENDPOINT);
    }
    
    /**
     * Get registration URL
     */
    public static String getRegisterUrl() {
        return buildUrl(REGISTER_ENDPOINT);
    }
    
    /**
     * Get forgot password URL
     */
    public static String getForgotPasswordUrl() {
        return buildUrl(FORGOT_PASSWORD_ENDPOINT);
    }
    
    /**
     * Get temperature reporting URL
     */
    public static String getTemperatureUrl() {
        return buildApiUrl(TEMPERATURE_ENDPOINT);
    }
    
    /**
     * Get commands URL
     */
    public static String getCommandsUrl() {
        return buildApiUrl(COMMANDS_ENDPOINT);
    }
    
    /**
     * Get device test URL
     */
    public static String getTestUrl() {
        return buildApiUrl(DEVICE_TEST_ENDPOINT);
    }
    
    // API Response Field Names
    public static final String STATUS_FIELD = "status";
    public static final String MESSAGE_FIELD = "message";
    public static final String DATA_FIELD = "data";
    public static final String API_KEY_FIELD = "api_key";
    public static final String USER_ID_FIELD = "user_id";
    
    // HTTP Headers
    public static final String CONTENT_TYPE_JSON = "application/json";
    public static final String CONTENT_TYPE_FORM = "application/x-www-form-urlencoded";
    public static final String HEADER_API_KEY = "X-API-KEY";
    public static final String HEADER_USER_ID = "X-USER-ID";
    
    // Status Values
    public static final String STATUS_OK = "ok";
    public static final String STATUS_ERROR = "error";
}
