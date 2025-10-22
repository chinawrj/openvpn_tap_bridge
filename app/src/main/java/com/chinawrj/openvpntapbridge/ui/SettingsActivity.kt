package com.chinawrj.openvpntapbridge.ui

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.chinawrj.openvpntapbridge.R
import com.chinawrj.openvpntapbridge.data.AppPreferences

/**
 * 设置界面
 * 允许用户配置监控的网络接口名称
 */
class SettingsActivity : AppCompatActivity() {
    private lateinit var prefs: AppPreferences
    private lateinit var etInterfaceName: EditText
    private lateinit var btnSave: Button
    private lateinit var btnCancel: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // 启用返回按钮
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        prefs = AppPreferences.getInstance(this)

        // 绑定视图
        etInterfaceName = findViewById(R.id.etInterfaceName)
        btnSave = findViewById(R.id.btnSave)
        btnCancel = findViewById(R.id.btnCancel)

        // 加载当前配置
        etInterfaceName.setText(prefs.interfaceName)

        // 设置监听器
        btnSave.setOnClickListener {
            saveSettings()
        }

        btnCancel.setOnClickListener {
            finish()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun saveSettings() {
        val interfaceName = etInterfaceName.text.toString().trim()

        if (interfaceName.isEmpty()) {
            Toast.makeText(this, "接口名称不能为空", Toast.LENGTH_SHORT).show()
            return
        }

        // 保存配置
        prefs.interfaceName = interfaceName

        Toast.makeText(this, getString(R.string.saved_successfully), Toast.LENGTH_SHORT).show()
        finish()
    }
}
