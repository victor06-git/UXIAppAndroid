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
        findViewById<Button>(R.id.btnSend).setOnClickListener { dismiss() }

        progressBar.isIndeterminate = false
    }

    fun updateProgress(current: Int, total: Int) {
        if (total <= 0) return

        val percentage = (current.toFloat() / total.toFloat() * 100).toInt()

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            progressBar.setProgress(percentage, true)
        } else {
            // Fallback para versiones antiguas
            progressBar.progress = percentage
        }

        //progressBar.max = 100
        //progressBar.progress = (current / total * 100).toInt()
        tvStatus.text = "Descarregant: $current bytes"
    }

    fun showImage(uri: Uri) {
        tvImage.setImageURI(null)
        tvImage.setImageURI(uri)
        tvStatus.text = "📸 Foto rebuda i guardada!"
    }
}