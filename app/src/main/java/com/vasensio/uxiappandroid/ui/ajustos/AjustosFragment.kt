package com.vasensio.uxiappandroid.ui.ajustos

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.vasensio.uxiappandroid.databinding.FragmentAjustosBinding

class AjustosFragment : Fragment() {

    private var _binding: FragmentAjustosBinding? = null
    private val binding get() = _binding!!

    private lateinit var customAdapter: CustomAdapter
    private lateinit var ajustosViewModel: AjustosViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        ajustosViewModel = ViewModelProvider(this).get(AjustosViewModel::class.java)
        _binding = FragmentAjustosBinding.inflate(inflater, container, false)

        setupRecyclerView()
        setupObservers()

        // Botón para actualizar la lista de dispositivos vinculados
        binding.button.setOnClickListener {
            ajustosViewModel.updatePairedDevices()
        }

        return binding.root
    }

    @SuppressLint("MissingPermission")
    private fun setupRecyclerView() {
        // CORRECCIÓN: Especificamos <BluetoothDevice> para evitar errores de tipos
        customAdapter = CustomAdapter(mutableListOf<BluetoothDevice>()) { dispositivo ->
            // Guardamos la MAC en el ViewModel (settings.xml)
            ajustosViewModel.guardarDispositivoSeleccionado(dispositivo.address)

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
}