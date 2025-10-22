package com.chinawrj.openvpntapbridge.ui

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.chinawrj.openvpntapbridge.R
import com.chinawrj.openvpntapbridge.data.AppPreferences

/**
 * Settings activity
 * Allows user to configure monitored network interface name
 */
class SettingsActivity : AppCompatActivity() {
    private lateinit var prefs: AppPreferences
    private lateinit var etInterfaceName: EditText
    private lateinit var btnSave: Button
    private lateinit var btnCancel: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Enable back button
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        prefs = AppPreferences.getInstance(this)

        // Bind views
        etInterfaceName = findViewById(R.id.etInterfaceName)
        btnSave = findViewById(R.id.btnSave)
        btnCancel = findViewById(R.id.btnCancel)

        // Load current configuration
        etInterfaceName.setText(prefs.interfaceName)

        // Set listeners
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
            Toast.makeText(this, "Interface name cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        // Save configuration
        prefs.interfaceName = interfaceName

        Toast.makeText(this, getString(R.string.saved_successfully), Toast.LENGTH_SHORT).show()
        finish()
    }
}
