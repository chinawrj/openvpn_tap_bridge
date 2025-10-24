package com.chinawrj.openvpntapbridge.ui

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.chinawrj.openvpntapbridge.R
import com.chinawrj.openvpntapbridge.core.ScriptManager
import com.chinawrj.openvpntapbridge.data.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings activity
 * Allows user to configure monitored network interface name and manage VPN bridge script
 */
class SettingsActivity : AppCompatActivity() {
    private lateinit var prefs: AppPreferences
    
    // Interface settings views
    private lateinit var etInterfaceName: EditText
    private lateinit var btnSave: Button
    private lateinit var btnCancel: Button
    
    // Script management views
    private lateinit var tvScriptStatus: TextView
    private lateinit var tvScriptPath: TextView
    private lateinit var layoutScriptPath: LinearLayout
    private lateinit var btnInstallScript: Button
    private lateinit var btnUninstallScript: Button
    private lateinit var btnRunScript: Button
    private lateinit var btnStopScript: Button
    private lateinit var btnViewLog: Button
    private lateinit var btnClearLog: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Enable back button
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        prefs = AppPreferences.getInstance(this)

        // Bind interface settings views
        etInterfaceName = findViewById(R.id.etInterfaceName)
        btnSave = findViewById(R.id.btnSave)
        btnCancel = findViewById(R.id.btnCancel)

        // Bind script management views
        tvScriptStatus = findViewById(R.id.tvScriptStatus)
        tvScriptPath = findViewById(R.id.tvScriptPath)
        layoutScriptPath = findViewById(R.id.layoutScriptPath)
        btnInstallScript = findViewById(R.id.btnInstallScript)
        btnUninstallScript = findViewById(R.id.btnUninstallScript)
        btnRunScript = findViewById(R.id.btnRunScript)
        btnStopScript = findViewById(R.id.btnStopScript)
        btnViewLog = findViewById(R.id.btnViewLog)
        btnClearLog = findViewById(R.id.btnClearLog)

        // Load current configuration
        etInterfaceName.setText(prefs.interfaceName)

        // Set interface settings listeners
        btnSave.setOnClickListener {
            saveSettings()
        }

        btnCancel.setOnClickListener {
            finish()
        }
        
        // Set script management listeners
        btnInstallScript.setOnClickListener {
            installScript()
        }
        
        btnUninstallScript.setOnClickListener {
            uninstallScript()
        }
        
        btnRunScript.setOnClickListener {
            runScript()
        }
        
        btnStopScript.setOnClickListener {
            stopScript()
        }
        
        btnViewLog.setOnClickListener {
            viewLog()
        }
        
        btnClearLog.setOnClickListener {
            clearLog()
        }
        
        // Update script status
        updateScriptStatus()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
    
    override fun onResume() {
        super.onResume()
        updateScriptStatus()
    }

    private fun saveSettings() {
        val interfaceName = etInterfaceName.text.toString().trim()

        if (interfaceName.isEmpty()) {
            Toast.makeText(this, "Interface name cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        // Save configuration
        prefs.interfaceName = interfaceName

        Toast.makeText(this, getString(R.string.saved_successfully), Toast.LENGTH_SHORT).show()
        finish()
    }
    
    private fun updateScriptStatus() {
        lifecycleScope.launch(Dispatchers.IO) {
            val status = ScriptManager.checkStatus()
            
            withContext(Dispatchers.Main) {
                if (status.installed) {
                    val statusText = if (status.running) {
                        getString(R.string.script_running)
                    } else {
                        getString(R.string.script_installed)
                    }
                    
                    tvScriptStatus.text = statusText
                    tvScriptStatus.setTextColor(if (status.running) {
                        Color.parseColor("#4CAF50") // Green
                    } else {
                        Color.parseColor("#2196F3") // Blue
                    })
                    
                    tvScriptPath.text = status.installedPath
                    layoutScriptPath.visibility = View.VISIBLE
                    
                    btnInstallScript.visibility = View.GONE
                    btnUninstallScript.visibility = View.VISIBLE
                    btnRunScript.visibility = if (status.running) View.GONE else View.VISIBLE
                    btnStopScript.visibility = if (status.running) View.VISIBLE else View.GONE
                    
                } else {
                    tvScriptStatus.text = getString(R.string.script_not_installed)
                    tvScriptStatus.setTextColor(Color.parseColor("#9E9E9E")) // Gray
                    layoutScriptPath.visibility = View.GONE
                    
                    btnInstallScript.visibility = View.VISIBLE
                    btnUninstallScript.visibility = View.GONE
                    btnRunScript.visibility = View.GONE
                    btnStopScript.visibility = View.GONE
                }
            }
        }
    }
    
    private fun installScript() {
        btnInstallScript.isEnabled = false
        btnInstallScript.text = getString(R.string.script_installing)
        
        lifecycleScope.launch(Dispatchers.IO) {
            val result = ScriptManager.install(this@SettingsActivity)
            
            withContext(Dispatchers.Main) {
                Toast.makeText(this@SettingsActivity, result.message, Toast.LENGTH_LONG).show()
                btnInstallScript.isEnabled = true
                btnInstallScript.text = getString(R.string.script_install)
                
                if (result.success) {
                    updateScriptStatus()
                }
            }
        }
    }
    
    private fun uninstallScript() {
        AlertDialog.Builder(this)
            .setTitle("Uninstall Script")
            .setMessage("Are you sure you want to uninstall the VPN bridge script?")
            .setPositiveButton("Uninstall") { _, _ ->
                performUninstall()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun performUninstall() {
        lifecycleScope.launch(Dispatchers.IO) {
            val result = ScriptManager.uninstall()
            
            withContext(Dispatchers.Main) {
                Toast.makeText(this@SettingsActivity, result.message, Toast.LENGTH_SHORT).show()
                
                if (result.success) {
                    updateScriptStatus()
                }
            }
        }
    }
    
    private fun runScript() {
        btnRunScript.isEnabled = false
        btnRunScript.text = getString(R.string.script_running_action)
        
        lifecycleScope.launch(Dispatchers.IO) {
            val result = ScriptManager.execute()
            
            withContext(Dispatchers.Main) {
                Toast.makeText(this@SettingsActivity, result.message, Toast.LENGTH_SHORT).show()
                btnRunScript.isEnabled = true
                btnRunScript.text = getString(R.string.script_run)
                
                if (result.success) {
                    updateScriptStatus()
                }
            }
        }
    }
    
    private fun stopScript() {
        lifecycleScope.launch(Dispatchers.IO) {
            val result = ScriptManager.stop()
            
            withContext(Dispatchers.Main) {
                Toast.makeText(this@SettingsActivity, result.message, Toast.LENGTH_SHORT).show()
                
                if (result.success) {
                    updateScriptStatus()
                }
            }
        }
    }
    
    private fun viewLog() {
        lifecycleScope.launch(Dispatchers.IO) {
            val logContent = ScriptManager.readLog(100)
            
            withContext(Dispatchers.Main) {
                val dialogView = ScrollView(this@SettingsActivity).apply {
                    val textView = TextView(this@SettingsActivity).apply {
                        text = logContent
                        textSize = 12f
                        setTextIsSelectable(true)
                        typeface = android.graphics.Typeface.MONOSPACE
                        setPadding(16, 16, 16, 16)
                    }
                    addView(textView)
                }
                
                AlertDialog.Builder(this@SettingsActivity)
                    .setTitle("Script Log")
                    .setView(dialogView)
                    .setPositiveButton("Close", null)
                    .setNeutralButton("Refresh") { _, _ ->
                        viewLog()
                    }
                    .show()
            }
        }
    }
    
    private fun clearLog() {
        lifecycleScope.launch(Dispatchers.IO) {
            val result = ScriptManager.clearLog()
            
            withContext(Dispatchers.Main) {
                Toast.makeText(this@SettingsActivity, result.message, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
