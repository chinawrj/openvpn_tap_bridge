# OpenVPN PID File Support

## 问题
之前的 Stop Script 按钮只能停止 `vpn-hotspot-bridge.sh` 监控脚本本身，但无法正确停止 OpenVPN 进程。这导致：
1. 修改配置后无法重启 OpenVPN
2. OpenVPN 进程会成为孤儿进程继续运行
3. 资源泄漏

## 解决方案

### 1. OpenVPN Native PID File Support
OpenVPN 原生支持 `--writepid` 参数，可以自动创建 PID 文件。

**修改的文件**: `app/src/main/assets/vpn-hotspot-bridge.sh`

```bash
# 添加 PID 文件路径常量
OPENVPN_PID=/data/local/tmp/openvpn.pid

# 启动 OpenVPN 时添加 --writepid 参数
openvpn --config "$CFG" --daemon --log /data/local/tmp/openvpn.log --writepid "$OPENVPN_PID"
```

### 2. 更新 ScriptManager.stop() 方法
**修改的文件**: `app/src/main/java/com/chinawrj/openvpntapbridge/core/ScriptManager.kt`

新的停止逻辑：
1. 首先读取 PID 文件获取 OpenVPN 进程 ID
2. 使用 `kill` 命令优雅地停止 OpenVPN 进程
3. 删除 PID 文件
4. 如果 PID 文件不存在，使用 `pkill -f` 作为后备方案
5. 最后停止脚本进程

```kotlin
fun stop(): InstallResult {
    // 1. 尝试从 PID 文件停止 OpenVPN
    val pidResult = FileReaders.executeInRootShell("cat '/data/local/tmp/openvpn.pid' 2>/dev/null")
    if (pidResult != null && pidResult.isNotBlank()) {
        val pid = pidResult.trim()
        FileReaders.executeInRootShell("kill '$pid' 2>/dev/null || true")
        FileReaders.executeInRootShell("rm -f '/data/local/tmp/openvpn.pid'")
    } else {
        // 2. 后备方案：按进程名停止
        FileReaders.executeInRootShell("pkill -f 'openvpn.*--config' || true")
    }
    
    // 3. 停止脚本进程
    FileReaders.executeInRootShell("pkill -f 'vpn-hotspot-bridge.sh' || true")
}
```

### 3. 添加 Restart 功能
为了方便处理配置修改后需要重启的场景，新增 `restart()` 方法：

```kotlin
fun restart(scriptPath: String? = null): InstallResult {
    // 停止当前实例
    stop()
    Thread.sleep(1000)
    
    // 重新启动
    execute(scriptPath, background = true)
}
```

## 使用场景

### 停止服务
点击 "Stop Script" 按钮会：
1. 优雅地停止 OpenVPN 进程
2. 停止监控脚本
3. 清理 PID 文件

### 配置更改后重启
当用户修改了以下配置时，可以使用 restart 功能：
- AP 接口列表
- NCM 接口
- OpenVPN 配置文件路径

未来可以在 SettingsActivity 中添加"Restart Script"按钮调用 `ScriptManager.restart()`。

## 技术细节

### PID 文件位置
`/data/local/tmp/openvpn.pid`

选择这个位置的原因：
- `/data/local/tmp` 目录在启动早期就可用
- 不需要等待 sdcard 挂载
- Root 权限可以访问

### 错误处理
- 使用 `|| true` 避免命令失败导致脚本中断
- 提供详细的停止消息（包括 PID 信息）
- 后备方案确保即使 PID 文件丢失也能停止进程

## 测试建议
1. 安装并运行脚本
2. 检查 PID 文件是否创建：`adb shell su -c "cat /data/local/tmp/openvpn.pid"`
3. 点击 Stop Script 按钮
4. 验证 OpenVPN 进程已停止：`adb shell su -c "pgrep -f openvpn"`
5. 验证 PID 文件已删除
