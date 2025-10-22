# OpenVPN TAP Bridge Monitor

A lightweight Android application for real-time monitoring of VPN/TAP/TUN network interface status and traffic rate.

## Features

### Core Functions
- ✅ **Real-time Monitoring**: Second-level refresh of network interface status
- ✅ **Interface Status Detection**: Display whether interface is UP/DOWN and has carrier
- ✅ **Bridge Detection**: Detect if interface is added to bridge (e.g. br0)
- ✅ **Route Detection**: Detect if interface carries default route
- ✅ **Rate Statistics**: Real-time display of uplink/downlink rate (bps) with automatic unit conversion (Kbps/Mbps/Gbps)
- ✅ **Interface Switching**: Support custom monitoring of interface names (tap0/tun0/wlan0, etc.)
- ✅ **Foreground Service**: Keep app resident with brief status bar information

### Technical Features
- 🔒 **Root Permission**: Requires root permission to read protected files in `/sys` and `/proc`
- 📊 **Pure Filesystem**: Read status through `/sys` and `/proc` for excellent performance
- 🔄 **Auto Recovery**: Automatically resume monitoring when interface disappears/rebuilds
- 🔧 **Auto Fallback**: Prioritize direct reading, automatically use root permission on failure
- 🧪 **Unit Tests**: Core logic covered by unit tests

## Technical Architecture

### Data Sources
- `/sys/class/net/<ifname>/operstate` - Interface operational state
- `/sys/class/net/<ifname>/carrier` - Carrier status
- `/sys/class/net/<ifname>/statistics/rx_bytes` - Received bytes
- `/sys/class/net/<ifname>/statistics/tx_bytes` - Transmitted bytes
- `/sys/class/net/<ifname>/brport/bridge` - Bridge status
- `/proc/net/route` - Routing table

### Core Modules
```
core/
  ├── FileReaders.kt      # Safe file reading
  ├── RouteParser.kt      # Route table parsing
  ├── BridgeDetector.kt   # Bridge detection
  ├── RateMeter.kt        # Rate calculation
  ├── IfaceReader.kt      # Interface status reading
  └── IfaceMonitor.kt     # Monitor (coroutine polling)
```

## Build Project

### Using Android Studio JDK (Recommended)
```bash
./build-with-as.sh assembleDebug
```

### Run Tests
```bash
./build-with-as.sh test
```

### Manual Build (Requires JDK 11+)
```bash
export JAVA_HOME="/path/to/jdk11"
./gradlew assembleDebug
```

## Installation and Usage

### Prerequisites
- ⚠️ **Root Permission Required**: App needs root permission to read protected files under `/sys/class/net`
- Android 8.0+ (API 26+)
- Rooted Android device

### Installation Steps

1. **Compile APK**:
   ```bash
   ./build-with-as.sh assembleDebug
   ```
   APK location: `app/build/outputs/apk/debug/app-debug.apk`

2. **Install to device**:
   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

3. **Grant Root permission**:
   - When opening the app for the first time, it will request root permission
   - Please allow in root management apps like SuperSU/Magisk

4. **Configure interface**:
   - Open the app
   - Click the "Settings" icon in the upper right corner
   - Enter the interface name to monitor (e.g. `tap0`)
   - Save settings

5. **Start monitoring**:
   - Return to main interface
   - Click menu → "Start Service"
   - App will continuously monitor in background with brief status bar information

## Use Cases

### Typical Scenarios
1. **OpenVPN TAP Monitoring**: Monitor if TAP interface is working properly
2. **Bridge Debugging**: Check if interface successfully joined bridge
3. **Route Switching**: Observe default route switching
4. **Traffic Statistics**: Real-time network traffic rate viewing

### Acceptance Testing
- [x] Interface appear/disappear: UI switches status within ≤2s when plugging/unplugging VPN
- [x] Bridge detection: Correctly shows bridge status during `brctl addif/delif`
- [x] Default route switching: Updates within ≤2s when route changes
- [x] Rate calculation: Rate responds monotonically under continuous traffic, fluctuation <10%

## Permission Description

```xml
<!-- Foreground service permission (Android 9+) -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />

<!-- Notification permission (Android 13+, optional) -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

⚠️ **Root Permission Explanation**:
- App requires root permission to read protected files under `/sys/class/net` and `/proc/net`
- On some Android devices, SELinux policies prevent normal apps from accessing these system files
- App will first try to read directly, automatically using `su -c` command on failure
- App **only reads** system files, does not modify any configuration

## Project Configuration

- **minSdk**: 26 (Android 8.0)
- **targetSdk**: 34 (Android 14)
- **Language**: Kotlin
- **Coroutines**: kotlinx-coroutines
- **Architecture**: MVVM (LiveData)

## Testing

### Unit Tests
```bash
./build-with-as.sh test
```

Test Coverage:
- `RateMeterTest` - Rate calculation logic
- `FormatUtilsTest` - Formatting utilities

### Manual Test Checklist
1. ✅ Interface existence detection
2. ✅ UP/DOWN status switching
3. ✅ Carrier status detection
4. ✅ Bridge status detection (requires root permission to execute brctl)
5. ✅ Default route detection
6. ✅ Rate calculation accuracy
7. ✅ Resume monitoring after interface rebuild

## Roadmap

- [x] Basic functionality implementation
- [x] Foreground service
- [x] Unit tests
- [ ] Multiple interface monitoring simultaneously
- [ ] Historical data recording
- [ ] Data export functionality
- [ ] Widget support

## License

MIT License

## Contributing

Issues and Pull Requests are welcome!

---

**Note**: This app is for monitoring only and does not modify any system configuration or network settings.
