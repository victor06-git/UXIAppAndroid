package com.vasensio.bluetooth_list_recyclerview

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.RequiresPermission
import androidx.core.net.toUri
import com.vasensio.uxiappandroid.R
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class BLEconnDialog(
    context: Context,
    private val device: BluetoothDevice,
    private val connectionCallback: BLEConnectionCallback
) : Dialog(context) {

    interface BLEConnectionCallback {
        fun onConnectionSuccess(gatt: BluetoothGatt)
        fun onConnectionFailed(error: String)
        fun onConnectionCancelled()
        fun onReceivedImage(file: File)
    }

    // Views
    private lateinit var tvDeviceName: TextView
    private lateinit var tvDeviceAddress: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvImage: ImageView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnConnect: Button
    private lateinit var btnCancel: Button

    // BLE
    // UUIDs
    private val SERVICE_UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
    private val CHARACTERISTIC_UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")
    private var bluetoothGatt: BluetoothGatt? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isConnecting = false
    private val CONNECTION_TIMEOUT = 10000L // 10 segons
    private val SCAN_PERIOD: Long = 10000
    private val RECEIVE_TIMEOUT: Long = 30000  // 30 segons timeout


    // Variables per foto
    private val receivedData = ByteArrayOutputStream()
    lateinit private var receivedFile : File
    private var totalSize = 0
    private var isReceiving = false
    private var received = false
    private var lastPacketTime = 0L
    private var packetCount = 0

    // Callback de timeout
    @SuppressLint("MissingPermission")
    private val connectionTimeoutRunnable = Runnable {
        if (isConnecting) {
            disconnect()
            connectionCallback.onConnectionFailed("Timeout de connexió")
            dismiss()
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_ble_conn)

        // Inicialitzar views
        tvDeviceName = findViewById(R.id.tvDeviceName)
        tvDeviceAddress = findViewById(R.id.tvDeviceAddress)
        tvStatus = findViewById(R.id.tvStatus)
        tvImage = findViewById(R.id.imageView)
        progressBar = findViewById(R.id.progressBar)
        btnConnect = findViewById(R.id.btnConnect)
        btnCancel = findViewById(R.id.btnCancel)

        // Configurar dades del dispositiu
        val deviceName = device.name ?: "Dispositiu desconegut"
        tvDeviceName.text = deviceName
        tvDeviceAddress.text = device.address

        // Configurar botons
        btnConnect.setOnClickListener {
            if( received ) {
                connectionCallback.onReceivedImage(receivedFile)
                disconnect()
                dismiss()
            }
            if (!isConnecting) {
                connectToDevice()
            }
        }

        btnCancel.setOnClickListener {
            cancelConnection()
        }

        // Connectar automàticament al mostrar el dialog
        connectToDevice()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun connectToDevice() {
        if (isConnecting) return

        isConnecting = true
        updateUIForConnecting()

        // Iniciar timeout
        handler.postDelayed(connectionTimeoutRunnable, CONNECTION_TIMEOUT)

        // Connectar al dispositiu BLE
        // Nota: Necessites tenir el context de l'activitat o un context vàlid
        val context = context.applicationContext ?: context
        bluetoothGatt = device.connectGatt(context, false, gattCallback)

        // En alguns dispositius, cal fer un connect explícit
        // bluetoothGatt?.connect()
    }

    private fun updateUIForConnecting() {
        tvStatus.text = "Connectant..."
        btnConnect.isEnabled = false
        btnConnect.text = "Connectant..."
        progressBar.isIndeterminate = true
    }

    private fun updateUIForConnected() {
        tvStatus.text = "Connectat"
        btnConnect.isEnabled = false
        btnConnect.text = "Connectat"
        progressBar.isIndeterminate = false
        progressBar.progress = 100
    }

    private fun updateUIForDisconnected() {
        tvStatus.text = "Desconnectat"
        btnConnect.isEnabled = true
        btnConnect.text = "Connectar"
        progressBar.isIndeterminate = false
        progressBar.progress = 0
    }

    private fun updateUIForReceived() {
        //tvStatus.text = "Desconnectat"
        btnConnect.isEnabled = true
        btnConnect.text = "Fet!"
        //progressBar.isIndeterminate = false
        //progressBar.progress = 0
    }

    // TÉCNICA AVANZADA: Limpiar caché interna del Bluetooth usando Reflexión
    // Esto soluciona muchos problemas de Status 133 y Status 5 tras actualizaciones
    private fun refreshDeviceCache(gatt: BluetoothGatt): Boolean {
        try {
            val localMethod = gatt.javaClass.getMethod("refresh")
            return localMethod.invoke(gatt) as Boolean
        } catch (localException: Exception) {
            Log.e(TAG, "No se pudo refrescar la caché GATT: ${localException.message}")
        }
        return false
    }


    @SuppressLint("MissingPermission")
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            handler.post {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        handler.removeCallbacks(connectionTimeoutRunnable)
                        isConnecting = false
                        updateUIForConnected()

                        // CAMBIO 1: Lógica secuencial (No usar postDelayed para servicios)
                        Log.d("BLE", "Solicitando MTU...")
                        // Si falla la petición de MTU, saltamos directo a servicios
                        if (!gatt.requestMtu(517)) {
                            gatt.discoverServices()
                        }
                    }

                    BluetoothProfile.STATE_DISCONNECTED -> {
                        handler.removeCallbacks(connectionTimeoutRunnable)
                        isConnecting = false

                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            // Desconexión normal
                            updateUIForDisconnected()
                            gatt.close()
                        } else {
                            // --- MANEJO DE ERRORES ---

                            if (status == 5 || status == 133) {
                                // 1. Limpieza
                                refreshDeviceCache(gatt)
                                gatt.close() // Cerramos la conexión vieja
                                bluetoothGatt = null

                                if (status == 5) {
                                    // 2. Si es error de claves (5), borramos el vínculo corrupto
                                    Log.w("BLE", "Error de keys (Status 5). Borrando vínculo y reintentando...")
                                    removeBond(device)

                                    tvStatus.text = "Reparando vínculo..."

                                    // 3. TRUCO: Reintentar conexión automáticamente en 1 segundo
                                    // Esto hará que salte la ventana de "Vincular" de nuevo sin que toques nada
                                    handler.postDelayed({
                                        Log.d("BLE", "Reintentando conexión automática...")
                                        connectToDevice()
                                    }, 1000)
                                    return@post // Salimos para no mostrar error en pantalla
                                }
                            } else {
                                // Otros errores, solo cerramos
                                gatt.close()
                                bluetoothGatt = null
                            }

                            // Mostrar error solo si NO estamos reintentando automáticamente
                            val errorMsg = "Error: $status"
                            tvStatus.text = errorMsg
                            connectionCallback.onConnectionFailed(errorMsg)
                        }
                    }
                }
            }
        }

        // Método para forzar desvinculación (Unpair) programáticamente
        private fun removeBond(device: BluetoothDevice) {
            try {
                Log.d("BLE", "Intentando borrar vinculación (unpair) por reflejo...")
                val method = device.javaClass.getMethod("removeBond")
                method.invoke(device)
                Log.d("BLE", "Vinculación borrada. Intenta conectar de nuevo.")
            } catch (e: Exception) {
                Log.e("BLE", "Falló el removeBond: ${e.message}")
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            super.onMtuChanged(gatt, mtu, status)
            handler.post {
                Log.d("BLE", "MTU cambiado a: $mtu. Iniciando descubrimiento de servicios...")
                tvStatus.text = "MTU: $mtu"

                // CAMBIO 3: Aquí descubrimos servicios de forma segura
                gatt.discoverServices()
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            super.onServicesDiscovered(gatt, status)

            if (status == BluetoothGatt.GATT_SUCCESS) {
                handler.post {
                    //binding.statusText.text = "Serveis descoberts"
                }

                val service = gatt.getService(SERVICE_UUID)
                service?.let {
                    val photoCharacteristic = it.getCharacteristic(CHARACTERISTIC_UUID)
                    photoCharacteristic?.let { characteristic ->
                        // Habilitar notificacions
                        gatt.setCharacteristicNotification(characteristic, true)

                        val descriptor = characteristic.getDescriptor(
                            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
                        )
                        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        gatt.writeDescriptor(descriptor)

                        /*handler.post {
                            binding.statusText.text = "Llest per rebre fotos!"
                            binding.photoStatus.text = "Esperant foto..."
                            Toast.makeText(this@MainActivity,
                                "Connectat! Prem BOOT a l'ESP32",
                                Toast.LENGTH_SHORT).show()
                        }*/
                    } ?: run {
                        handler.post {
                            Log.d("BT","Error: Característica no trobada")
                            //binding.statusText.text = "Error: Característica no trobada"
                        }
                    }
                } ?: run {
                    handler.post {
                        Log.d("BT","Error: Servei no trobat")
                        //binding.statusText.text = "Error: Servei no trobat"
                    }
                }
            } else {
                handler.post {
                    Log.d("BT","Error descobrint serveis: $status")
                    //binding.statusText.text = "Error descobrint serveis: $status"
                }
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt,
                                             characteristic: BluetoothGattCharacteristic
        ) {
            super.onCharacteristicChanged(gatt, characteristic)

            if (characteristic.uuid == CHARACTERISTIC_UUID) {
                handleIncomingData(characteristic.value)
            }
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt,
                                          characteristic: BluetoothGattCharacteristic,
                                          status: Int) {
            super.onCharacteristicRead(gatt, characteristic, status)
            Log.d("BLE", "Característica llegida: ${characteristic.uuid}")
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt,
                                           characteristic: BluetoothGattCharacteristic,
                                           status: Int) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            Log.d("BLE", "Característica escrita: $status")
        }



    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun disconnect() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        isConnecting = false
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun cancelConnection() {
        handler.removeCallbacks(connectionTimeoutRunnable)
        disconnect()
        connectionCallback.onConnectionCancelled()
        dismiss()
    }

    override fun dismiss() {
        handler.removeCallbacks(connectionTimeoutRunnable)
        super.dismiss()
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(connectionTimeoutRunnable)
        super.onDetachedFromWindow()
    }

    companion object {
        const val TAG = "BLEconnDialog"
    }


    // BLE data receive
    ///////////////////////
    private fun handleIncomingData(data: ByteArray) {
        handler.post {
            packetCount++
            lastPacketTime = System.currentTimeMillis()

            Log.d("BLE", "Paquet $packetCount rebut: ${data.size} bytes")
            Log.d("BLE", "Primers bytes: ${data.take(4).joinToString("") { "%02X".format(it) }}")

            // Verificar si és paquet de finalització
            if (data.size == 4 && data.contentEquals(byteArrayOf(0xFF.toByte(),
                    0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()
                ))) {
                if (isReceiving && receivedData.size() > 0) {
                    completePhotoTransfer()
                } else {
                    tvStatus.text = "Finalització rebuda sense dades"
                }
                return@post
            }

            // Primer paquet (pot ser la mida)
            if (!isReceiving && data.size == 4) {
                // Intentar interpretar com a mida de 32 bits
                try {
                    totalSize = (data[0].toInt() and 0xFF) +
                            ((data[1].toInt() and 0xFF) shl 8) +
                            ((data[2].toInt() and 0xFF) shl 16) +
                            ((data[3].toInt() and 0xFF) shl 24)

                    isReceiving = true
                    receivedData.reset()

                    tvStatus.text = "Rebent foto ($totalSize bytes)..."
                    progressBar.max = totalSize
                    progressBar.progress = 0

                    Log.d("BLE", "Mida anunciada: $totalSize bytes")

                    // Iniciar timeout
                    startReceiveTimeout()

                } catch (e: Exception) {
                    Log.e("BLE", "Error interpretant mida: ${e.message}")
                }
                return@post
            }

            // Si estem rebent, afegir dades
            if (isReceiving) {
                receivedData.write(data)

                val currentSize = receivedData.size()
                progressBar.progress = currentSize

                // Actualitzar estat cada certs paquets
                if (packetCount % 10 == 0 || currentSize == totalSize) {
                    val percent = if (totalSize > 0)
                        (currentSize * 100) / totalSize else 0

                    tvStatus.text =
                        "Rebent: $currentSize/$totalSize bytes ($percent%)"

                    Log.d("BLE", "Progrés: $currentSize/$totalSize ($percent%)")
                }

                // Reiniciar timeout amb cada paquet
                resetReceiveTimeout()

                // Si hem arribat a la mida esperada, completar
                if (totalSize > 0 && currentSize >= totalSize) {
                    completePhotoTransfer()
                }
            }
        }
    }

    private fun startReceiveTimeout() {
        handler.removeCallbacks(receiveTimeoutRunnable)
        handler.postDelayed(receiveTimeoutRunnable, RECEIVE_TIMEOUT)
    }

    private fun resetReceiveTimeout() {
        handler.removeCallbacks(receiveTimeoutRunnable)
        handler.postDelayed(receiveTimeoutRunnable, RECEIVE_TIMEOUT)
    }

    private val receiveTimeoutRunnable = Runnable {
        handler.post {
            if (isReceiving) {
                tvStatus.text = "⏰ Timeout! Transferència incompleta"
                Log.e("BLE", "Timeout en recepció de foto")
                resetPhotoTransfer()
            }
        }
    }

    private fun completePhotoTransfer() {
        val finalSize = receivedData.size()

        handler.post {
            tvStatus.text = "✅ Foto rebuda: $finalSize bytes"
            progressBar.progress = finalSize

            val dataStr = receivedData.toString().trim()
            Log.v("FOTO",dataStr)
            // Guardar foto
            try {
                val decodedData = Base64.decode(dataStr,Base64.DEFAULT)
                savePhoto(decodedData)
                // Notificació
                //Toast.makeText(this, "Foto rebuda: $finalSize bytes",
                //    Toast.LENGTH_LONG).show()
                Log.v("BT","Foto rebuda: $finalSize bytes")
            } catch (e : Exception) {
                Log.v("ERROR","Error en descodificació base64")
                e.printStackTrace()
            }

            // Reset
            resetPhotoTransfer()
        }
    }

    private fun resetPhotoTransfer() {
        isReceiving = false
        totalSize = 0
        receivedData.reset()
        packetCount = 0
        handler.removeCallbacks(receiveTimeoutRunnable)
    }

    private fun savePhoto(imageData: ByteArray) {
        try {
            val timestamp = System.currentTimeMillis()
            val filename = "ESP32_${timestamp}.jpg"

            // Guardar al directori de Pictures
            val picturesDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES)
            val fri3dDir = File(picturesDir, "ESP32_Camera")

            if (!fri3dDir.exists()) {
                fri3dDir.mkdirs()
            }

            receivedFile = File(fri3dDir, filename)
            FileOutputStream(receivedFile).use { fos ->
                fos.write(imageData)
                fos.flush()
            }

            Log.d("Photo", "Foto guardada: ${receivedFile.absolutePath}")

            // preview image a l'app
            tvImage.setImageURI(receivedFile.toUri())

            // Notificar galeria
            val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
            mediaScanIntent.data = android.net.Uri.fromFile(receivedFile)
            context.sendBroadcast(mediaScanIntent)

            tvStatus.text = "💾 Guardat: $filename"
            received = true
            updateUIForReceived()
            //dismiss()

        } catch (e: Exception) {
            Log.e("Photo", "Error guardant foto: ${e.message}")
            tvStatus.text = "❌ Error guardant foto"
        }
    }

}