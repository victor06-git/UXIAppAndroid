package com.vasensio.bluetooth_list_recyclerview

import android.app.Dialog
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import com.vasensio.uxiappandroid.R

class BLEconnDialog(
    context: Context,
    private val deviceName: String,
    private val deviceAddress: String
) : Dialog(context) {

    lateinit var tvStatus: TextView
    lateinit var tvImage: ImageView
    lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_ble_conn)

        tvStatus = findViewById(R.id.tvStatus)
        tvImage = findViewById(R.id.imageView)
        progressBar = findViewById(R.id.progressBar)

        findViewById<TextView>(R.id.tvDeviceName).text = deviceName
        findViewById<TextView>(R.id.tvDeviceAddress).text = deviceAddress
        findViewById<Button>(R.id.btnCancel).setOnClickListener { dismiss() }
        findViewById<Button>(R.id.btnConnect).setOnClickListener { dismiss() }
    }

    fun updateProgress(current: Int, total: Int) {
        progressBar.max = if (total > 0) total else 100
        progressBar.progress = current
        tvStatus.text = "Descarregant: $current bytes"
    }

    fun showImage(uri: Uri) {
        tvImage.setImageURI(null)
        tvImage.setImageURI(uri)
        tvStatus.text = "📸 Foto rebuda i guardada!"
    }
}