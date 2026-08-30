package com.rezerv.upload.ui

import android.content.Context
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import com.rezerv.upload.R
import com.rezerv.upload.utils.Validators

class ConnectionFormHandler(
    private val context: Context,
    private val etServer: EditText,
    private val etUser: EditText,
    private val etPass: EditText,
    private val spAuth: Spinner
) {
    init {
        // ✅ Spinner настраивается один раз при создании handler
        setupAuthSpinner()
    }

    private fun setupAuthSpinner() {
        val authTypes = context.resources.getStringArray(R.array.auth_types)
        val spinnerAdapter = ArrayAdapter(context, R.layout.spinner_item, authTypes).apply {
            setDropDownViewResource(R.layout.spinner_dropdown_item)
        }
        spAuth.adapter = spinnerAdapter
        spAuth.background = null
    }

    fun getCredentials(): Triple<String, String, String> {
        return Triple(
            etServer.text.toString().trim(),
            etUser.text.toString().trim(),
            etPass.text.toString()
        )
    }

    fun getAuthType(): Int = spAuth.selectedItemPosition

    fun setCredentials(server: String, user: String, pass: String, authType: Int) {
        etServer.setText(server)
        etUser.setText(user)
        etPass.setText(pass)
        if (authType in 0 until spAuth.adapter.count) {
            spAuth.setSelection(authType)
        }
    }

    fun validate(): Boolean {
        var isValid = true

        validateField(etServer) { Validators.validateServerUrl(it) }?.let {
            isValid = false
        }
        validateField(etUser) { Validators.validateUsername(it) }?.let {
            isValid = false
        }
        validateField(etPass) { Validators.validatePassword(it) }?.let {
            isValid = false
        }

        return isValid
    }

    private fun validateField(
        field: EditText,
        validator: (String) -> String?
    ): String? {
        val error = validator(field.text.toString())
        field.error = error
        if (error != null) field.requestFocus()
        return error
    }

    fun clearErrors() {
        etServer.error = null
        etUser.error = null
        etPass.error = null
    }
}