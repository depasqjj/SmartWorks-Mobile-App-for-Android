// NetworkTestActivity.java - Simple network connectivity test
package com.example.smartworks;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NetworkTestActivity extends AppCompatActivity {
    private static final String TAG = "NetworkTest";
    
    private EditText ipInput;
    private Button testButton;
    private TextView resultText;
    private ExecutorService executor;
    private Handler mainHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Create simple layout programmatically
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(20, 20, 20, 20);
        
        TextView title = new TextView(this);
        title.setText("Network Connectivity Test");
        title.setTextSize(20);
        layout.addView(title);
        
        ipInput = new EditText(this);
        ipInput.setText("192.168.0.132");
        ipInput.setHint("Enter IP address");
        layout.addView(ipInput);
        
        testButton = new Button(this);
        testButton.setText("Test Connection");
        layout.addView(testButton);
        
        resultText = new TextView(this);
        resultText.setText("Results will appear here...");
        resultText.setPadding(0, 20, 0, 0);
        layout.addView(resultText);
        
        setContentView(layout);
        
        executor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
        
        testButton.setOnClickListener(v -> testConnection());
    }
    
    private void testConnection() {
        String ip = ipInput.getText().toString().trim();
        if (ip.isEmpty()) {
            resultText.setText("Please enter an IP address");
            return;
        }
        
        resultText.setText("Testing connection to " + ip + "...\n\n");
        testButton.setEnabled(false);
        
        executor.execute(() -> {
            StringBuilder results = new StringBuilder();
            
            // Test 1: Check if IP is reachable
            try {
                results.append("1. Testing IP reachability:\n");
                InetAddress inet = InetAddress.getByName(ip);
                boolean reachable = inet.isReachable(5000);
                results.append("   isReachable: ").append(reachable).append("\n\n");
            } catch (Exception e) {
                results.append("   Error: ").append(e.getMessage()).append("\n\n");
            }
            
            // Test 2: Try ping
            try {
                results.append("2. Testing ping:\n");
                Process process = Runtime.getRuntime().exec("/system/bin/ping -c 1 -W 2 " + ip);
                int returnVal = process.waitFor();
                results.append("   Ping result: ").append(returnVal == 0 ? "SUCCESS" : "FAILED").append("\n\n");
            } catch (Exception e) {
                results.append("   Ping error: ").append(e.getMessage()).append("\n\n");
            }
            
            // Test 3: Try HTTP connection
            try {
                results.append("3. Testing HTTP connection:\n");
                URL url = new URL("http://" + ip + "/");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestMethod("GET");
                
                int responseCode = conn.getResponseCode();
                results.append("   HTTP Response Code: ").append(responseCode).append("\n");
                
                if (responseCode == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    String firstLine = reader.readLine();
                    results.append("   First line of response: ").append(firstLine != null ? firstLine.substring(0, Math.min(50, firstLine.length())) : "empty").append("\n");
                    reader.close();
                }
                conn.disconnect();
                results.append("\n");
            } catch (Exception e) {
                results.append("   HTTP error: ").append(e.getClass().getSimpleName()).append(" - ").append(e.getMessage()).append("\n\n");
            }
            
            // Test 4: Try /data endpoint
            try {
                results.append("4. Testing /data endpoint:\n");
                URL url = new URL("http://" + ip + "/data");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/json");
                
                int responseCode = conn.getResponseCode();
                results.append("   Response Code: ").append(responseCode).append("\n");
                
                if (responseCode == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();
                    results.append("   Response: ").append(response.toString()).append("\n");
                }
                conn.disconnect();
            } catch (Exception e) {
                results.append("   Error: ").append(e.getClass().getSimpleName()).append(" - ").append(e.getMessage()).append("\n");
            }
            
            // Update UI
            mainHandler.post(() -> {
                resultText.setText(results.toString());
                testButton.setEnabled(true);
            });
        });
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null) {
            executor.shutdown();
        }
    }
}