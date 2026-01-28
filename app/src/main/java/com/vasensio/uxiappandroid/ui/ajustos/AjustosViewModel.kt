package com.vasensio.uxiappandroid.ui.ajustos

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

class AjustosViewModel(application: Application) : AndroidViewModel(application) {

    // LiveData privado para manejar la lista internamente
    private val _listaDispositivos = MutableLiveData<List<BluetoothDevice>>()

    // LiveData público que el Fragment observará (solo lectura)
    val listaDispositivos: LiveData<List<BluetoothDevice>> = _listaDispositivos

    /**
     * Obtiene los dispositivos ya vinculados en el sistema Android
     */
    @SuppressLint("MissingPermission")
    fun updatePairedDevices() {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        // Filtramos para obtener solo dispositivos compatibles (LE o Dual)
        val pairedDevices = bluetoothAdapter?.bondedDevices?.filter { device ->
            device.type == BluetoothDevice.DEVICE_TYPE_LE ||
                    device.type == BluetoothDevice.DEVICE_TYPE_DUAL ||
                    device.type == BluetoothDevice.DEVICE_TYPE_UNKNOWN
        } ?: emptyList()

        // Notificamos al Fragment actualizando el valor del LiveData
        _listaDispositivos.value = pairedDevices.toList()
    }

    /**
     * Guarda la MAC en el archivo privado settings.xml
     */
    fun guardarDispositivoSeleccionado(mac: String) {
        // Accedemos a SharedPreferences (el archivo físico settings.xml)
        val sharedPrefs = getApplication<Application>().getSharedPreferences("settings", Context.MODE_PRIVATE)

        // Editamos y guardamos la dirección MAC con la clave "mac_configurada"
        sharedPrefs.edit().putString("mac_configurada", mac).apply()
    }
}