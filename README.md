# SmartWorks Thermostat System

A comprehensive IoT temperature management system with real-time monitoring, web dashboard, mobile app integration, and secure device management capabilities.

## 🌟 Features

### Core Functionality
- **Real-time Temperature Monitoring** - Live temperature readings from ESP32/IoT devices
- **Remote Device Control** - Turn devices on/off, set thresholds via web interface
- **Multi-device Support** - Manage multiple thermostats from a single account
- **Responsive Web Dashboard** - Access from desktop, tablet, or mobile
- **Android Mobile App** - Native Android application for on-the-go control

### Security Features
- **Account Lockout Protection** - 5 failed attempts = 15-minute lockout
- **Secure Password Reset** - AES-256 encrypted tokens with expiration
- **API Key Authentication** - Secure device-to-server communication
- **SQL Injection Prevention** - Prepared statements throughout
- **Input Validation** - Comprehensive sanitization and validation
- **Admin Audit Logging** - Track user actions and system events

### Advanced Features
- **Webhook Integration** - Custom notifications for temperature alerts
- **Threshold Management** - Automated heating/cooling based on temperature ranges
- **Historical Data** - Temperature readings with timestamps
- **Command Queuing** - Reliable command delivery to devices
- **Admin Dashboard** - System management and user administration
- **Device Registration** - Easy setup for new IoT devices

## 📋 System Requirements

### Server Requirements
- **PHP 7.4+** with extensions:
  - mysqli
  - openssl
  - json
  - mbstring
- **MySQL/MariaDB 5.7+**
- **Web Server** (Apache/Nginx) with HTTPS support
- **SSL Certificate** (recommended for production)

### Device Requirements
- **ESP32/Arduino** with WiFi capability
- **Temperature sensor** (DS18B20, DHT22, etc.)
- **Stable internet connection**
- **Arduino IDE** or **PlatformIO** for firmware development

## 🚀 Installation

### 1. Server Setup

1. **Clone/Download** the project files to your web server
2. **Import Database** schema using the provided SQL files:
   ```sql
   -- Import these files in order:
   users.sql
   devices.sql
   readings.sql
   commands.sql
   password_resets.sql
   login_attempts.sql
   user_logs.sql
   user_update_logs.sql
   ```

3. **Configure Database** - Update `server/config.php`:
   ```php
   $host = "localhost";
   $db   = "your_database_name";
   $user = "your_db_username";
   $pass = "your_db_password";
   ```

4. **Set Encryption Key** - Generate a secure 256-bit key:
   ```php
   define('ENCRYPTION_KEY', hex2bin('your_64_character_hex_key'));
   ```

5. **Configure Email** - Set up mail settings in config.php:
   ```php
   define('ADMIN_EMAIL', 'admin@yourdomain.com');
   define('MAIL_API_URL', 'your_mail_api_endpoint');
   define('MAIL_API_SECRET', 'your_mail_api_secret');
   ```

### 2. System Verification

Run the system check script to verify everything is configured correctly:
```
https://yourdomain.com/server/system_check.php
```

### 3. Create Admin User

Register your first admin user at:
```
https://yourdomain.com/server/register_user.php
```

### 4. Device Setup

1. **Register Device** in the web dashboard
2. **Note the API key** generated for your device  
3. **Flash ESP32** with thermostat firmware
4. **Configure device** with:
   - WiFi credentials
   - Server URL: `https://yourdomain.com/server/api/devices/`
   - Device ID and API key from registration

## 📱 Usage

### Web Dashboard
1. **Login** at `https://yourdomain.com/server/login.php`
2. **Register devices** via "Add Device" button
3. **Monitor temperatures** on dashboard
4. **Send commands** (turn on/off) to devices
5. **Set thresholds** for automatic control

### Admin Panel
- **Access:** `https://yourdomain.com/server/admin/admin_dashboard.php`
- **Manage users** and device registrations
- **View system statistics** and recent activity
- **Monitor security** events and locked accounts

### Mobile App
- **Install APK** from the Android project build
- **Login** with same credentials as web dashboard
- **Control devices** from anywhere
- **Receive push notifications** for alerts

## 🔧 API Usage

### Authentication
Include API key in headers or request body:
```bash
# Header method
curl -H "X-API-KEY: your_api_key" \
     -H "X-USER-ID: your_user_id" \
     https://yourdomain.com/api/devices/temperature.php
```

### Temperature Reporting
```bash
curl -X POST https://yourdomain.com/api/devices/temperature.php \
     -H "Content-Type: application/json" \
     -d '{
       "device_id": "pool_thermo_001",
       "user_id": 1,
       "temperature": 78.5
     }'
```

### Command Polling (for devices)
```bash
curl "https://yourdomain.com/api/devices/commands.php?device_id=pool_thermo_001"
```

### Send Commands (from dashboard)
```bash
curl -X POST https://yourdomain.com/api/devices/commands.php \
     -H "Content-Type: application/json" \
     -d '{
       "device_id": "pool_thermo_001",
       "user_id": 1,
       "command": "turn_on"
     }'
```

## 📊 Database Schema

### Core Tables
- **users** - User accounts and authentication
- **devices** - Registered IoT devices
- **readings** - Temperature measurements
- **commands** - Device command queue
- **password_resets** - Secure password reset tokens
- **login_attempts** - Security audit trail
- **user_logs** - User activity logging
- **user_update_logs** - User modification tracking

### Key Relationships
- Users → Devices (1:many)
- Devices → Readings (1:many)
- Users → Commands → Devices
- Users → Password Resets (1:many)

## 🔐 Security Features

### Authentication & Authorization
- **Secure password hashing** using PHP's password_hash()
- **API key-based** device authentication
- **Role-based access** (admin/user)
- **Session management** with secure cookies

### Protection Mechanisms
- **Account lockout** after failed login attempts
- **SQL injection prevention** via prepared statements
- **XSS protection** through input sanitization
- **CSRF protection** on sensitive operations
- **Rate limiting** on API endpoints

### Data Protection
- **AES-256 encryption** for sensitive tokens
- **Secure password reset** workflow
- **Input validation** on all user data
- **Database connection** encryption

## 📈 Monitoring & Maintenance

### System Health Checks
- Run `system_check.php` regularly
- Monitor database connections
- Check file permissions
- Verify SSL certificate status

### Database Maintenance
```sql
-- Clean expired reset tokens
DELETE FROM password_resets WHERE expires_at < NOW() - INTERVAL 24 HOUR;

-- Archive old readings (optional)
-- Move readings older than 1 year to archive table

-- Monitor table sizes
SELECT table_name, table_rows FROM information_schema.tables 
WHERE table_schema = 'your_database_name';
```

### Log Monitoring
- Monitor login attempts for suspicious activity
- Check command execution success rates
- Review user activity logs
- Monitor device connectivity patterns

## 🛠️ Troubleshooting

### Common Issues

#### Database Connection Errors
```php
// Check config.php settings
// Verify database user permissions
// Test connection manually
```

#### API Authentication Failures
```bash
# Verify API key is correct
# Check user_id matches device owner
# Ensure device is registered and enabled
```

#### Device Not Receiving Commands
1. Check device is polling `/commands.php` regularly
2. Verify device authentication
3. Check command queue in database
4. Test network connectivity

#### Temperature Readings Not Appearing
1. Verify POST data format
2. Check device_id matches registration
3. Confirm user_id is correct
4. Test API endpoint manually

### Debug Mode
Enable debug mode in development:
```php
// Add to config.php
ini_set('display_errors', 1);
error_reporting(E_ALL);
```

## 🤝 Contributing

### Development Setup
1. Clone repository
2. Set up local LAMP/WAMP stack
3. Import database schema
4. Configure development environment
5. Test with system_check.php

### Code Standards
- Follow PSR-12 coding standards
- Use prepared statements for all database queries
- Implement proper error handling
- Add comments for complex logic
- Test security features thoroughly

## 📄 License

This project is licensed under the MIT License - see the LICENSE file for details.

## 📞 Support

### Documentation
- **API Documentation:** `server/API_DOCUMENTATION.md`
- **System Check:** `server/system_check.php`
- **Database Updates:** `server/database_updates.sql`

### Contact
- **Email:** support@smartworkstech.com
- **GitHub Issues:** Create issue for bugs/features
- **Security Issues:** security@smartworkstech.com

## 🏗️ Architecture Overview

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   Web Browser   │    │   Mobile App     │    │   ESP32 Device  │
│   Dashboard     │    │   (Android)      │    │   Thermostat    │
└─────────┬───────┘    └─────────┬────────┘    └─────────┬───────┘
          │                      │                       │
          ├──────────────────────┼───────────────────────┤
          │                      │                       │
          ▼                      ▼                       ▼
┌─────────────────────────────────────────────────────────────────┐
│                     Web Server (PHP)                           │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────┐ │
│  │   Login/    │  │    API      │  │     Admin Dashboard     │ │
│  │  Dashboard  │  │ Endpoints   │  │    User Management      │ │
│  └─────────────┘  └─────────────┘  └─────────────────────────┘ │
└─────────────────────┬───────────────────────────────────────────┘
                      │
                      ▼
            ┌─────────────────────┐
            │   MySQL Database    │
            │  ┌─────────────────┐│
            │  │ users, devices, ││
            │  │ readings, cmds, ││
            │  │ logs, resets    ││
            │  └─────────────────┘│
            └─────────────────────┘
```

## 🔄 Data Flow

### Temperature Reporting
1. **ESP32** reads temperature sensor
2. **HTTP POST** to `/api/devices/temperature.php`
3. **Server** validates and stores reading
4. **Dashboard** displays updated temperature
5. **Alerts** triggered if thresholds crossed

### Command Execution
1. **User** clicks "Turn On" in dashboard
2. **AJAX POST** to `/api/devices/commands.php`
3. **Command** queued in database
4. **ESP32** polls for pending commands
5. **ESP32** executes and confirms completion

This comprehensive system provides enterprise-grade IoT temperature management with security, scalability, and ease of use.
