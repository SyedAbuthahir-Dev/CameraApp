package com.example.cameraapp.utils

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat

fun Context.hasCameraPermission(): Boolean {
    return ContextCompat.checkSelfPermission(
        this,
        android.Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED
}

fun Context.showDialogPermission(permissionName: String, isPermanentlyDenied: Boolean,requestCamearaPermission:()->Unit) {
    val alertDialog = AlertDialog
        .Builder(this)
        .setTitle("Request Permission For : $permissionName")
        .setMessage("Please give the permission To Take Picture")
        .setPositiveButton(if (isPermanentlyDenied) "Go To Settings" else "OK") { _, _ ->
            if(isPermanentlyDenied)
                goToAppSettings()
            else
                requestCamearaPermission()
        }
    alertDialog.create()
    alertDialog.show()
}

fun Context.goToAppSettings() {
    Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null)
    ).also(::startActivity)
}