# OpenVPN TAP Bridge Monitor

一个轻量级的 Android 应用，用于实时监控 VPN/TAP/TUN 网络接口的状态和流量速率。

## 功能特性

### 核心功能
- ✅ **实时监控**：秒级刷新网络接口状态
- ✅ **接口状态检测**：显示接口是否 UP/DOWN、是否有载波
- ✅ **桥接检测**：检测接口是否加入网桥（如 br0）
- ✅ **路由检测**：检测接口是否承载默认路由
- ✅ **速率统计**：实时显示上行/下行速率（bps），自动单位换算（Kbps/Mbps/Gbps）
- ✅ **接口切换**：支持自定义监控的接口名称（tap0/tun0/wlan0 等）
- ✅ **常驻服务**：前台服务保持应用常驻，状态栏显示简要信息

### 技术特点
- 🔒 **Root 权限**：需要 root 权限读取 `/sys` 和 `/proc` 受保护的文件
- 📊 **纯文件系统**：通过 `/sys` 和 `/proc` 读取状态，性能优异
- 🔄 **自动恢复**：接口消失/重建时自动恢复监控
- 🔧 **自动降级**：优先直接读取，失败时自动使用 root 权限
- 🧪 **单元测试**：核心逻辑覆盖单元测试

## 技术架构

### 数据源
- `/sys/class/net/<ifname>/operstate` - 接口运行状态
- `/sys/class/net/<ifname>/carrier` - 载波状态
- `/sys/class/net/<ifname>/statistics/rx_bytes` - 接收字节数
- `/sys/class/net/<ifname>/statistics/tx_bytes` - 发送字节数
- `/sys/class/net/<ifname>/brport/bridge` - 桥接状态
- `/proc/net/route` - 路由表

### 核心模块
```
core/
  ├── FileReaders.kt      # 安全文件读取
  ├── RouteParser.kt      # 路由表解析
  ├── BridgeDetector.kt   # 桥接检测
  ├── RateMeter.kt        # 速率计算
  ├── IfaceReader.kt      # 接口状态读取
  └── IfaceMonitor.kt     # 监控器（协程轮询）
```

## 构建项目

### 使用 Android Studio JDK（推荐）
```bash
./build-with-as.sh assembleDebug
```

### 运行测试
```bash
./build-with-as.sh test
```

### 手动构建（需要 JDK 11+）
```bash
export JAVA_HOME="/path/to/jdk11"
./gradlew assembleDebug
```

## 安装使用

### 前提条件
- ⚠️ **需要 Root 权限**：应用需要 root 权限才能读取 `/sys/class/net` 下的受保护文件
- Android 8.0+ (API 26+)
- 已 root 的 Android 设备

### 安装步骤

1. **编译 APK**：
   ```bash
   ./build-with-as.sh assembleDebug
   ```
   APK 位置：`app/build/outputs/apk/debug/app-debug.apk`

2. **安装到设备**：
   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

3. **授予 Root 权限**：
   - 首次打开应用时，会请求 root 权限
   - 请在 SuperSU/Magisk 等 root 管理应用中允许

4. **配置接口**：
   - 打开应用
   - 点击右上角"设置"图标
   - 输入要监控的接口名称（如 `tap0`）
   - 保存设置

5. **启动监控**：
   - 返回主界面
   - 点击菜单 → "启动服务"
   - 应用将在后台持续监控，状态栏显示简要信息

## 使用场景

### 典型场景
1. **OpenVPN TAP 监控**：监控 TAP 接口是否正常工作
2. **网桥调试**：检查接口是否成功加入网桥
3. **路由切换**：观察默认路由的切换情况
4. **流量统计**：实时查看网络流量速率

### 验收测试
- [x] 接口出现/消失：拔/插 VPN，UI 在 ≤2s 内切换状态
- [x] 桥接检测：`brctl addif/delif` 时正确显示桥接状态
- [x] 默认路由切换：路由变化时 ≤2s 内更新
- [x] 速率计算：持续流量下速率单调响应，抖动 <10%

## 权限说明

```xml
<!-- 前台服务权限（Android 9+） -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />

<!-- 通知权限（Android 13+，可选） -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

⚠️ **Root 权限说明**：
- 应用需要 root 权限才能读取 `/sys/class/net` 和 `/proc/net` 下的受保护文件
- 在某些 Android 设备上，SELinux 策略会阻止普通应用访问这些系统文件
- 应用会优先尝试直接读取，失败时自动使用 `su -c` 命令
- 应用**仅读取**系统文件，不会修改任何配置

## 项目配置

- **minSdk**: 26 (Android 8.0)
- **targetSdk**: 34 (Android 14)
- **语言**: Kotlin
- **协程**: kotlinx-coroutines
- **架构**: MVVM (LiveData)

## 测试

### 单元测试
```bash
./build-with-as.sh test
```

测试覆盖：
- `RateMeterTest` - 速率计算逻辑
- `FormatUtilsTest` - 格式化工具

### 手工测试清单
1. ✅ 接口存在性检测
2. ✅ UP/DOWN 状态切换
3. ✅ 载波状态检测
4. ✅ 桥接状态检测（需要 root 权限执行 brctl）
5. ✅ 默认路由检测
6. ✅ 速率计算准确性
7. ✅ 接口重建后恢复监控

## 开发路线图

- [x] 基础功能实现
- [x] 前台服务
- [x] 单元测试
- [ ] 多接口同时监控
- [ ] 历史数据记录
- [ ] 数据导出功能
- [ ] Widget 支持

## 许可证

MIT License

## 贡献

欢迎提交 Issue 和 Pull Request！

---

**注意**：本应用仅用于监控，不会修改任何系统配置或网络设置。
