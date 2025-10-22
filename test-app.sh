#!/bin/bash
# Quick test script - Verify application running status

echo "=========================================="
echo "OpenVPN TAP Bridge - Quick Test"
echo "=========================================="
echo ""

# Check device connection
echo "1. Checking device connection..."
adb devices | grep "device$" > /dev/null
if [ $? -ne 0 ]; then
    echo "❌ No device detected"
    exit 1
fi
echo "✅ Device connected"
echo ""

# Check if app is installed
echo "2. Checking if app is installed..."
adb shell pm list packages | grep "com.chinawrj.openvpntapbridge" > /dev/null
if [ $? -ne 0 ]; then
    echo "❌ App not installed"
    echo "   Run: adb install app/build/outputs/apk/debug/app-debug.apk"
    exit 1
fi
echo "✅ App installed"
echo ""

# Check Root permission
echo "3. Checking device Root status..."
ROOT_CHECK=$(adb shell "su -c 'echo rooted' 2>/dev/null")
if [ "$ROOT_CHECK" = "rooted" ]; then
    echo "✅ Device is rooted"
else
    echo "❌ Device not rooted or permission not granted"
    exit 1
fi
echo ""

# Check network interfaces
echo "4. Checking available network interfaces..."
echo "   Detected interfaces:"
adb shell su -c "ls /sys/class/net/" | grep -E "tap|tun|wlan" | head -5
echo ""

# Check tap0 interface (if exists)
TAP0_EXISTS=$(adb shell su -c "[ -e /sys/class/net/tap0 ] && echo 'yes' || echo 'no'")
if [ "$TAP0_EXISTS" = "yes" ]; then
    echo "   ✅ tap0 interface exists"
    echo "   Status information:"
    OPERSTATE=$(adb shell su -c "cat /sys/class/net/tap0/operstate 2>/dev/null")
    CARRIER=$(adb shell su -c "cat /sys/class/net/tap0/carrier 2>/dev/null")
    RX_BYTES=$(adb shell su -c "cat /sys/class/net/tap0/statistics/rx_bytes 2>/dev/null")
    TX_BYTES=$(adb shell su -c "cat /sys/class/net/tap0/statistics/tx_bytes 2>/dev/null")
    
    echo "      operstate: $OPERSTATE"
    echo "      carrier: $CARRIER"
    echo "      rx_bytes: $RX_BYTES"
    echo "      tx_bytes: $TX_BYTES"
else
    echo "   ℹ️  tap0 interface does not exist (normal, can configure other interfaces in app)"
fi
echo ""

# Start application
echo "5. Starting application..."
adb shell am start -n com.chinawrj.openvpntapbridge/.ui.MainActivity > /dev/null 2>&1
echo "✅ Application started"
echo ""

# View real-time logs (5 seconds)
echo "6. Viewing application logs (5 seconds)..."
echo "=========================================="
timeout 5 adb logcat -s IfaceReader:D IfaceMonitor:D 2>/dev/null || gtimeout 5 adb logcat -s IfaceReader:D IfaceMonitor:D 2>/dev/null || (adb logcat -s IfaceReader:D IfaceMonitor:D & PID=$!; sleep 5; kill $PID 2>/dev/null)
echo "=========================================="
echo ""

echo "✅ Test complete!"
echo ""
echo "Tips:"
echo "  - If you see 'exists=true', the app is working properly"
echo "  - If you need to monitor other interfaces, please modify in app settings"
echo "  - View complete log: adb logcat -s IfaceReader:D"
