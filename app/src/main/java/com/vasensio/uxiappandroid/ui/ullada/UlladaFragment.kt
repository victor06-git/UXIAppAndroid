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
import android.speech.tts.TextToSpeech
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.vasensio.uxiappandroid.databinding.FragmentUlladaBinding
import com.vasensio.bluetooth_list_recyclerview.BLEconnDialog
import com.vasensio.uxiappandroid.R
import java.io.ByteArrayOutputStream
import java.util.UUID
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Locale

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
    private var lastReceivedBytes: ByteArray? = null
    private val client = okhttp3.OkHttpClient()

    private var tts: TextToSpeech? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentUlladaBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // En tu onCreateView o onViewCreated, inicialízalo:
        tts = TextToSpeech(requireContext()) { status ->
            if (status != TextToSpeech.ERROR) {
                tts?.language = Locale("ca", "ES") // O Locale.getDefault() para el idioma del sistema
            }
        }

        setupUI()

        binding.btnRequestImage.setOnClickListener {
            val prefs = requireContext().getSharedPreferences("settings", Context.MODE_PRIVATE)
            val mac = prefs.getString("mac_configurada", null) ?: return@setOnClickListener
            val manager = requireActivity().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val device = manager.adapter.getRemoteDevice(mac)

            // --- RESET DE DATOS AQUÍ ---
            lastReceivedBytes = null   // Borra la referencia a la foto anterior
            receivedData.reset()       // Limpia el flujo de bytes
            isReceiving = false        // Resetea el estado de recepción
            // ----------------------------

            activeDialog = BLEconnDialog(requireContext(), device.name ?: "ESP32", device.address) {
                enviarImatgeAlServidor()
            }

            activeDialog?.show()

            if (bluetoothGatt == null) {
                activeDialog?.tvStatus?.text = "Connectant..."
                bluetoothGatt = device.connectGatt(requireContext(), false, gattCallback)
            } else {
                requestImage()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Al volver de Ajustes, comprobamos de nuevo el estado
        setupUI()
    }

    private fun setupUI() {
        val prefs = requireContext().getSharedPreferences("settings", Context.MODE_PRIVATE)
        val macConfigurada = prefs.getString("mac_configurada", null)

        val bluetoothManager = requireActivity().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter

        // 1. SIN CONFIGURACIÓN EN LA APP
        if (macConfigurada.isNullOrEmpty()) {
            binding.btnRequestImage.text = "CONFIGURAR APP"
            binding.textUllada.text = "Falta configurar la MAC"
            binding.btnRequestImage.setOnClickListener {
                // Aquí navegas a tu fragment de ajustes interno
                Toast.makeText(context, "Ves a Ajustos de l'aplicació", Toast.LENGTH_SHORT).show()
            }
            return
        }

        // 2. BLUETOOTH APAGADO
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            binding.btnRequestImage.isEnabled = false
            binding.textUllada.text = "Activa el Bluetooth"
            return
        }

        // 3. COMPROBAR VINCULACIÓN (BONDED)
        @SuppressLint("MissingPermission")
        val dispositivosVinculados = bluetoothAdapter.bondedDevices
        val estaVinculado = dispositivosVinculados.any { it.address.equals(macConfigurada, ignoreCase = true) }

        if (estaVinculado) {
            // CASO OK: VINCULADO
            binding.btnRequestImage.isEnabled = true
            binding.btnRequestImage.text = "REBRE IMATGE"
            binding.textUllada.text = "Dispositiu: $macConfigurada"

            // El click aquí SÍ abre el proceso de conexión y el diálogo
            binding.btnRequestImage.setOnClickListener {
                iniciarConexionBLE(macConfigurada)
            }
        } else {
            // CASO ERROR: NO VINCULADO
            binding.btnRequestImage.isEnabled = true
            binding.btnRequestImage.text = "ANAR A AJUSTOS" // Texto claro
            binding.textUllada.text = "L'ESP32 no està vinculat al telèfon"

            // El click aquí NO abre el diálogo, abre los AJUSTES DEL SISTEMA

            /*binding.btnRequestImage.setOnClickListener {
                val intent = android.content.Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
                startActivity(intent)
            }*/

            binding.btnRequestImage.setOnClickListener {
                findNavController().navigate(R.id.navigation_ajustos)
            }
        }
    }

    // Mueve la lógica de conexión aquí para que no se mezcle
    @SuppressLint("Missingpermission")
    private fun iniciarConexionBLE(mac: String) {
        val manager = requireActivity().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val device = manager.adapter.getRemoteDevice(mac)

        activeDialog = BLEconnDialog(requireContext(), device.name ?: "ESP32", device.address) {
            enviarImatgeAlServidor()
        }
        activeDialog?.show()

        if (bluetoothGatt == null) {
            activeDialog?.tvStatus?.text = "Connectant..."
            bluetoothGatt = device.connectGatt(requireContext(), false, gattCallback)
        } else {
            requestImage()
        }
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
                    if (!isReceiving && data.size == 4) {
                        totalSize = (data[0].toInt() and 0xFF) or ((data[1].toInt() and 0xFF) shl 8) or
                                ((data[2].toInt() and 0xFF) shl 16) or ((data[3].toInt() and 0xFF) shl 24)

                        isReceiving = true
                        receivedData.reset()
                        lastReceivedBytes = null // Borramos la foto anterior para no repetir


                        activeDialog?.tvStatus?.text = "Rebent dades..."
                    }
                    // SEÑAL DE INICIO (TAMAÑO)
                    else if (isReceiving) {
                        if (data.size == 4 && data.all { it == (-1).toByte() }) {
                            processFinalImage()
                        } else {
                            receivedData.write(data)
                            activeDialog?.updateProgress(receivedData.size(), totalSize)
                            if (receivedData.size() >= totalSize) processFinalImage()
                        }
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

            this.lastReceivedBytes = bytes // Última foto rebuda

            handler.post {
                // Creamos un Bitmap temporal solo para el preview del diálogo
                val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                activeDialog?.tvImage?.setImageBitmap(bitmap)
                activeDialog?.tvStatus?.text = "📸 Foto rebuda. Prem Enviar per desar i analitzar."
            }

        } catch (e: Exception) {
            Log.e("BLE", "Error en Base64")
        }
    }

    private fun saveToUxiaAlbum(bytes: ByteArray): Uri? {
        val filename = "UXIA_${System.currentTimeMillis()}.jpg"
        val resolver = requireContext().contentResolver
        var imageUri: Uri? = null

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/UXIA")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)

        uri?.let { targetUri ->
            resolver.openOutputStream(targetUri).use { it?.write(bytes) }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(targetUri, values, null, null)
            } else {
                val intent = android.content.Intent(android.content.Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                intent.data = targetUri
                requireContext().sendBroadcast(intent)
            }
            imageUri = targetUri
        }
        return imageUri
    }

    private fun enviarImatgeAlServidor() {
        val bytesToSend = lastReceivedBytes
        if (bytesToSend == null) {
            Toast.makeText(context, "Error: No s'ha rebut cap imagen nova", Toast.LENGTH_SHORT).show()
            return
        }

        val savedUri : Uri? = saveToUxiaAlbum(bytesToSend)

        handler.post {
            if (savedUri != null) {
                binding.imageView2.setImageURI(null)
                binding.imageView2.setImageURI(savedUri)
            } else {
                Log.e("BLE", "La URI guardada és nul·la")
            }
        }

        // Fil per enviar post al servidor a través de OkHttp
        Thread {
            try {
                val urlEndpoint = "https://uxia5.ieti.site/api/analitzar-imatge"
                val base64Image = Base64.encodeToString(bytesToSend, Base64.NO_WRAP)

                val json = org.json.JSONObject().apply {
                    put("images", org.json.JSONArray().put(base64Image))
                    put("prompt", "")
                    put("stream", false)
                }
                val jsonString = json.toString()
                val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                val requestBody = jsonString.toRequestBody(mediaType)
                val request = okhttp3.Request.Builder()
                    .url(urlEndpoint)
                    .post(requestBody)
                    .build()
                client.newCall(request).execute().use { response ->
                    val responseData = response.body?.string()

                    activity?.runOnUiThread {
                        if (isAdded && response.isSuccessful && responseData != null) {
                            try {
                                val jsonRespuesta = org.json.JSONObject(responseData)

                                val msg = jsonRespuesta.optString("message", "Processat")

                                val dataObj = jsonRespuesta.getJSONObject("data")
                                val descripcio = dataObj.optString("description", "Sense descripció")

                                val tagsArray = dataObj.getJSONArray("tags")
                                val tagsList = mutableListOf<String>()
                                for (i in 0 until tagsArray.length()) {
                                    tagsList.add(tagsArray.getString(i))
                                }

                                val textoParaLeer = "Descripció: $descripcio. Tags: ${tagsList.joinToString(", ")}"

                                tts?.speak(textoParaLeer, TextToSpeech.QUEUE_FLUSH, null, null)

                                val resumen = "Resposta: $msg\n\n" +
                                        "Descripció: $descripcio\n\n" +
                                        "Tags: ${tagsList.joinToString(", ")}"

                                androidx.appcompat.app.AlertDialog.Builder(requireContext())
                                    .setTitle("Anàlisi Finalitzat")
                                    .setMessage(resumen)
                                    .setIcon(R.drawable.ullada_icon) // Si tienes un icono
                                    .setPositiveButton("D'acord") { dialog, _ -> dialog.dismiss() }
                                    .show()

                                Log.d("API_RES", "Tot correcte: $responseData")

                            } catch (e: Exception) {
                                Log.e("API_RES", "Error parsejant JSON", e)
                                Toast.makeText(context, "Error en el format de la resposta", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Log.e("API_RES", "Error: ${response.code}")
                            Toast.makeText(context, "Error servidor: ${response.code}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("HTTP_ERROR", "Error: ", e)
                activity?.runOnUiThread {
                    // Esto nos dirá si es un error de permisos, de red o de protocolo
                    Log.e("DETALL", "Error de xarxa", e)
                    Toast.makeText(context, "Error de xarxa", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    @SuppressLint("MissingPermission")
    private fun requestImage() {
        val char = bluetoothGatt?.getService(SERVICE_UUID)?.getCharacteristic(CHAR_UUID)
        char?.let {
            it.value = "GET_IMAGE".toByteArray()
            bluetoothGatt?.writeCharacteristic(it)
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}