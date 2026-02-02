package com.vasensio.uxiappandroid.ui.ullada

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.vasensio.uxiappandroid.databinding.FragmentUlladaBinding
import com.vasensio.bluetooth_list_recyclerview.BLEconnDialog
import java.io.ByteArrayOutputStream
import java.util.UUID

class UlladaFragment : Fragment() {
    private var _binding: FragmentUlladaBinding? = null
    private val binding get() = _binding!!

    private var bluetoothGatt: BluetoothGatt? = null
    private var activeDialog: BLEconnDialog? = null
    private val handler = Handler(Looper.getMainLooper())

    private val receivedData = ByteArrayOutputStream()
    private var isReceiving = false
    private var totalSize = 0

    private val SERVICE_UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
    private val CHAR_UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")
    private val DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentUlladaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()

        binding.btnRequestImage.setOnClickListener {
            val prefs = requireContext().getSharedPreferences("settings", Context.MODE_PRIVATE)
            val mac = prefs.getString("mac_configurada", null) ?: return@setOnClickListener
            val manager = requireActivity().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val device = manager.adapter.getRemoteDevice(mac)

            activeDialog = BLEconnDialog(requireContext(), device.name ?: "ESP32", device.address)
            activeDialog?.show()

            if (bluetoothGatt == null) {
                activeDialog?.tvStatus?.text = "Connectant..."
                bluetoothGatt = device.connectGatt(requireContext(), false, gattCallback)
            } else {
                requestImage()
            }
        }
    }

    private fun setupUI() {
        val prefs = requireContext().getSharedPreferences("settings", Context.MODE_PRIVATE)
        val mac = prefs.getString("mac_configurada", null)
        binding.btnRequestImage.isEnabled = !mac.isNullOrEmpty()
        binding.textUllada.text = if (mac.isNullOrEmpty()) "Sense dispositiu" else "Dispositiu: $mac"
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            handler.post {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    activeDialog?.tvStatus?.text = "Configurant MTU..."
                    gatt.requestMtu(517)
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    bluetoothGatt = null
                    activeDialog?.tvStatus?.text = "Desconnectat"
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            gatt.discoverServices()
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val service = gatt.getService(SERVICE_UUID)
            val characteristic = service?.getCharacteristic(CHAR_UUID)

            if (characteristic != null) {
                // ACTIVAR NOTIFICACIONES (CRUCIAL)
                gatt.setCharacteristicNotification(characteristic, true)
                val descriptor = characteristic.getDescriptor(DESCRIPTOR_UUID)
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(descriptor)

                handler.post { activeDialog?.tvStatus?.text = "Llest! Demanant foto..." }
                // No pedimos la foto aquí, esperamos a que el descriptor se escriba
            }
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            // Una vez que las notificaciones están activas, pedimos la foto
            requestImage()
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == CHAR_UUID) {
                val data = characteristic.value
                handler.post {
                    // SEÑAL DE FIN [255, 255, 255, 255]
                    if (data.size == 4 && data.all { it == (-1).toByte() }) {
                        if (isReceiving) processFinalImage()
                    }
                    // SEÑAL DE INICIO (TAMAÑO)
                    else if (!isReceiving && data.size == 4) {
                        totalSize = (data[0].toInt() and 0xFF) or ((data[1].toInt() and 0xFF) shl 8) or
                                ((data[2].toInt() and 0xFF) shl 16) or ((data[3].toInt() and 0xFF) shl 24)
                        isReceiving = true
                        receivedData.reset()
                        activeDialog?.tvStatus?.text = "Rebent dades..."
                    }
                    // DATOS (CHUNKS)
                    else if (isReceiving) {
                        receivedData.write(data)
                        activeDialog?.updateProgress(receivedData.size(), totalSize)
                        if (receivedData.size() >= totalSize) processFinalImage()
                    }
                }
            }
        }
    }

    private fun processFinalImage() {
        isReceiving = false
        try {
            val rawStr = receivedData.toString("UTF-8").trim()
            val cleanBase64 = rawStr.substringAfterLast(",").trim()
            val bytes = Base64.decode(cleanBase64, Base64.DEFAULT)
            saveToUxiaAlbum(bytes)
        } catch (e: Exception) {
            Log.e("BLE", "Error en Base64")
        }
    }

    private fun saveToUxiaAlbum(bytes: ByteArray) {
        val filename = "UXIA_${System.currentTimeMillis()}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/UXIA")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = requireContext().contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        uri?.let { targetUri ->
            requireContext().contentResolver.openOutputStream(targetUri).use { it?.write(bytes) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                requireContext().contentResolver.update(targetUri, values, null, null)
            }
            handler.post {
                activeDialog?.showImage(targetUri)
                binding.btnRequestImage.text = "FER UNA ALTRA FOTO"
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestImage() {
        val char = bluetoothGatt?.getService(SERVICE_UUID)?.getCharacteristic(CHAR_UUID)
        char?.let {
            it.value = "GET_IMAGE".toByteArray()
            bluetoothGatt?.writeCharacteristic(it)
        }
    }
}