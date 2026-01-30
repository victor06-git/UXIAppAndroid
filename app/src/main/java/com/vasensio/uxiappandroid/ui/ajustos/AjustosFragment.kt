package com.vasensio.uxiappandroid.ui.ajustos

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.vasensio.uxiappandroid.databinding.FragmentAjustosBinding

class AjustosFragment : Fragment() {

    private var _binding: FragmentAjustosBinding? = null
    private val binding get() = _binding!!

    private lateinit var customAdapter: CustomAdapter
    private lateinit var ajustosViewModel: AjustosViewModel

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value == true }
        if (allGranted) {
            try {
                ajustosViewModel.updatePairedDevices()
            } catch (e: SecurityException) {
                Log.e("AjustosFragment", "SecurityException after granting permissions: ${e.message}")
            }
        } else {
            AlertDialog.Builder(requireContext())
                .setTitle("Permisos necesarios")
                .setMessage("Se requieren permisos Bluetooth para listar dispositivos emparejados.")
                .setPositiveButton("Aceptar", null)
                .show()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        ajustosViewModel = ViewModelProvider(this).get(AjustosViewModel::class.java)
        _binding = FragmentAjustosBinding.inflate(inflater, container, false)

        setupRecyclerView()
        setupObservers()

        // Cargar la lista inicial al abrir el fragment — verificar permisos antes
        ensurePermissionsThenUpdate()

        // Botón para actualizar la lista de dispositivos vinculados
        binding.button.setOnClickListener {
            ensurePermissionsThenUpdate()
        }

        return binding.root
    }

    @SuppressLint("MissingPermission")
    private fun setupRecyclerView() {
        // CORRECCIÓN: Especificamos <BluetoothDevice> para evitar errores de tipos
        customAdapter = CustomAdapter(mutableListOf<BluetoothDevice>()) { dispositivo ->
            // Guardamos la MAC en el ViewModel (settings.xml)
            ajustosViewModel.guardarDispositivoSeleccionado(dispositivo.address, dispositivo.name)

            // Feedback visual para el usuario
            AlertDialog.Builder(requireContext())
                .setTitle("Configuración guardada")
                .setMessage("Vinculado: ${dispositivo.name ?: "Desconocido"}\nMAC: ${dispositivo.address}")
                .setPositiveButton("Aceptar", null)
                .show()
        }

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = customAdapter
        }
    }

    private fun setupObservers() {
        // Escuchamos los cambios en el ViewModel para actualizar la lista visual
        ajustosViewModel.listaDispositivos.observe(viewLifecycleOwner) { lista ->
            customAdapter.updateList(lista)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun ensurePermissionsThenUpdate() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) {
            try {
                ajustosViewModel.updatePairedDevices()
            } catch (e: SecurityException) {
                Log.e("AjustosFragment", "SecurityException when listing devices: ${e.message}")
                AlertDialog.Builder(requireContext())
                    .setTitle("Permiso denegado")
                    .setMessage("No se puede listar dispositivos emparejados: falta permiso en tiempo de ejecución.")
                    .setPositiveButton("Aceptar", null)
                    .show()
            }
        } else {
            permissionsLauncher.launch(missing.toTypedArray())
        }
    }
}