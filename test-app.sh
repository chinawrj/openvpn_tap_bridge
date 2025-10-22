#!/bin/bash
# 快速测试脚本 - 验证应用运行状态

echo "=========================================="
echo "OpenVPN TAP Bridge - 快速测试"
echo "=========================================="
echo ""

# 检查设备连接
echo "1. 检查设备连接..."
adb devices | grep "device$" > /dev/null
if [ $? -ne 0 ]; then
    echo "❌ 没有检测到设备"
    exit 1
fi
echo "✅ 设备已连接"
echo ""

# 检查应用是否安装
echo "2. 检查应用是否安装..."
adb shell pm list packages | grep "com.chinawrj.openvpntapbridge" > /dev/null
if [ $? -ne 0 ]; then
    echo "❌ 应用未安装"
    echo "   运行: adb install app/build/outputs/apk/debug/app-debug.apk"
    exit 1
fi
echo "✅ 应用已安装"
echo ""

# 检查 Root 权限
echo "3. 检查设备 Root 状态..."
ROOT_CHECK=$(adb shell "su -c 'echo rooted' 2>/dev/null")
if [ "$ROOT_CHECK" = "rooted" ]; then
    echo "✅ 设备已 Root"
else
    echo "❌ 设备未 Root 或未授予权限"
    exit 1
fi
echo ""

# 检查网络接口
echo "4. 检查可用的网络接口..."
echo "   检测到以下接口："
adb shell su -c "ls /sys/class/net/" | grep -E "tap|tun|wlan" | head -5
echo ""

# 检查 tap0 接口（如果存在）
TAP0_EXISTS=$(adb shell su -c "[ -e /sys/class/net/tap0 ] && echo 'yes' || echo 'no'")
if [ "$TAP0_EXISTS" = "yes" ]; then
    echo "   ✅ tap0 接口存在"
    echo "   状态信息："
    OPERSTATE=$(adb shell su -c "cat /sys/class/net/tap0/operstate 2>/dev/null")
    CARRIER=$(adb shell su -c "cat /sys/class/net/tap0/carrier 2>/dev/null")
    RX_BYTES=$(adb shell su -c "cat /sys/class/net/tap0/statistics/rx_bytes 2>/dev/null")
    TX_BYTES=$(adb shell su -c "cat /sys/class/net/tap0/statistics/tx_bytes 2>/dev/null")
    
    echo "      operstate: $OPERSTATE"
    echo "      carrier: $CARRIER"
    echo "      rx_bytes: $RX_BYTES"
    echo "      tx_bytes: $TX_BYTES"
else
    echo "   ℹ️  tap0 接口不存在（正常，可在应用中配置其他接口）"
fi
echo ""

# 启动应用
echo "5. 启动应用..."
adb shell am start -n com.chinawrj.openvpntapbridge/.ui.MainActivity > /dev/null 2>&1
echo "✅ 应用已启动"
echo ""

# 查看实时日志（5秒）
echo "6. 查看应用日志（5秒）..."
echo "=========================================="
timeout 5 adb logcat -s IfaceReader:D IfaceMonitor:D 2>/dev/null || gtimeout 5 adb logcat -s IfaceReader:D IfaceMonitor:D 2>/dev/null || (adb logcat -s IfaceReader:D IfaceMonitor:D & PID=$!; sleep 5; kill $PID 2>/dev/null)
echo "=========================================="
echo ""

echo "✅ 测试完成！"
echo ""
echo "提示："
echo "  - 如果看到 'exists=true' 说明应用正常工作"
echo "  - 如果需要监控其他接口，请在应用设置中修改"
echo "  - 查看完整日志: adb logcat -s IfaceReader:D"
