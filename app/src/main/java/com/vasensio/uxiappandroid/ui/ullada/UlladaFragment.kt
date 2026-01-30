package com.vasensio.uxiappandroid.ui.ullada

import android.bluetooth.BluetoothGattDescriptor
import kotlin.collections.filter
import kotlin.collections.toTypedArray
import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.vasensio.uxiappandroid.R // Assegura't d'importar el teu R
import com.vasensio.uxiappandroid.databinding.FragmentUlladaBinding
import java.util.UUID

class UlladaFragment : Fragment() {

    private var _binding: FragmentUlladaBinding? = null
    private val binding get() = _binding!!

    // Codis per a sol·licituds de permisos
    private val REQUEST_CODE_BLUETOOTH_PERMISSIONS = 101
    private val REQUEST_CODE_WRITE_STORAGE = 102

    // Variables de Bluetooth
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var bluetoothGatt: BluetoothGatt? = null

    // UUIDs del servei i característiques (AJUSTA'LS ALS DEL TEU ESP32)
    private val SERVICE_UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
    private val IMAGE_REQUEST_CHARACTERISTIC_UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")
    private val IMAGE_DATA_CHARACTERISTIC_UUID = UUID.fromString("a3dd5150-4828-4b06-8b30-1b2be6c22c10")
    private val imageBuffer = mutableListOf<Byte>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUlladaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Inicialització del Bluetooth Adapter
        val bluetoothManager = requireActivity().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        setupUI()
        checkPermissionsAndConnect()
    }

    private fun setupUI() {
        // Lògica per actualitzar el text amb la MAC guardada
        val prefs = requireContext().getSharedPreferences("settings", Context.MODE_PRIVATE)
        val mac = prefs.getString("mac_configurada", null)
        val name = prefs.getString("nombre_configurado", null)
        binding.textUllada.text = if (mac.isNullOrEmpty()) {
            "Cap dispositiu configurat"
        } else {
            "Dispositiu: $name ($mac)"
        }

    }

    // --- LÒGICA DE CONNEXIÓ BLUETOOTH ---

    private fun checkPermissionsAndConnect() {
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        } else {
            listOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.ACCESS_FINE_LOCATION)
        }

        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isEmpty()) {
            startAutoConnection()
        } else {
            ActivityCompat.requestPermissions(requireActivity(), permissionsToRequest.toTypedArray(), REQUEST_CODE_BLUETOOTH_PERMISSIONS)
        }
    }

    private fun startAutoConnection() {
        val prefs = requireContext().getSharedPreferences("settings", Context.MODE_PRIVATE)
        val macAddress = prefs.getString("mac_configurada", null)

        if (!bluetoothAdapter.isEnabled) {
            Toast.makeText(context, "Activa el Bluetooth", Toast.LENGTH_SHORT).show()
            return
        }

        val device: BluetoothDevice? = try {
            bluetoothAdapter.getRemoteDevice(macAddress)
        } catch (e: IllegalArgumentException) {
            null
        }

        if (device == null) {
            Toast.makeText(context, "L'adreça MAC guardada no és vàlida", Toast.LENGTH_LONG).show()
            return
        }

        Log.d("UlladaFragment", "Intentant connectar amb ${device.name} (${device.address})")
        // El 'false' en el tercer paràmetre indica que no és una connexió automàtica post-desconnexió.
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            bluetoothGatt = device.connectGatt(context, false, gattCallback)
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val deviceName = gatt.device.name ?: gatt.device.address
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i("GattCallback", "Connectat a $deviceName")
                    activity?.runOnUiThread { Toast.makeText(context, "Connectat a $deviceName", Toast.LENGTH_SHORT).show() }
                    bluetoothGatt = gatt
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.i("GattCallback", "Desconnectat de $deviceName")
                    activity?.runOnUiThread { Toast.makeText(context, "Desconnectat", Toast.LENGTH_SHORT).show() }
                    bluetoothGatt?.close()
                    bluetoothGatt = null
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i("GattCallback", "Serveis descoberts amb èxit")
                // Activa les notificacions per a la característica de dades d'imatge
                enableImageDataNotifications(gatt)
            } else {
                Log.w("GattCallback", "Error en descobrir serveis: $status")
            }
        }
    }

    // --- LÒGICA DE RECEPCIÓ D'IMATGE ---

    @SuppressLint("MissingPermission")
    private fun enableImageDataNotifications(gatt: BluetoothGatt) {
        val service = gatt.getService(SERVICE_UUID)
        val characteristic = service?.getCharacteristic(IMAGE_DATA_CHARACTERISTIC_UUID)

        if (characteristic == null) {
            Log.e("Notifications", "La característica de dades d'imatge no s'ha trobat!")
            return
        }

        gatt.setCharacteristicNotification(characteristic, true)
        // Descriptor estàndard per a notificacions (Client Characteristic Configuration)
        val descriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        gatt.writeDescriptor(descriptor)
        Log.i("Notifications", "Notificacions activades per a la imatge.")
    }

    @SuppressLint("MissingPermission")
    private fun requestImageFromESP32() {
        val gatt = bluetoothGatt ?: run {
            Toast.makeText(context, "No estàs connectat a cap dispositiu", Toast.LENGTH_SHORT).show()
            return
        }

        val service = gatt.getService(SERVICE_UUID)
        val characteristic = service?.getCharacteristic(IMAGE_REQUEST_CHARACTERISTIC_UUID)

        if (characteristic == null) {
            Toast.makeText(context, "La característica per demanar la imatge no existeix!", Toast.LENGTH_LONG).show()
            return
        }

        imageBuffer.clear() // Neteja el buffer abans de cada nova petició
        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        characteristic.value = "GET_IMAGE".toByteArray() // Envia una comanda

        if (gatt.writeCharacteristic(characteristic)) {
            Log.i("ImageRequest", "Petició d'imatge enviada correctament.")
            activity?.runOnUiThread {
                Toast.makeText(context, "Demanant imatge...", Toast.LENGTH_SHORT).show()
            }
        } else {
            Log.e("ImageRequest", "Error en enviar la petició d'imatge.")
            activity?.runOnUiThread {
                Toast.makeText(context, "Error al demanar la imatge", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Quan tornem a aquesta pestanya, intenta connectar de nou si no estàs connectat.
        if (bluetoothGatt == null) {
            checkPermissionsAndConnect()
        }
    }

    @SuppressLint("MissingPermission")
    override fun onPause() {
        super.onPause()
        // Desconnecta per estalviar bateria quan l'usuari surt de la pestanya
        bluetoothGatt?.close()
        bluetoothGatt = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
