package com.vasensio.uxiappandroid.ui.ajustos

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.vasensio.uxiappandroid.R

class CustomAdapter(
    private var dataSet: MutableList<BluetoothDevice>,
    private val onItemClick: (BluetoothDevice) -> Unit
) : RecyclerView.Adapter<CustomAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitulo: TextView = view.findViewById(R.id.tvTitulo)
        val tvMac: TextView = view.findViewById(R.id.tvMac)
    }

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(viewGroup.context)
            .inflate(R.layout.item_layout, viewGroup, false)
        return ViewHolder(view)
    }

    @SuppressLint("MissingPermission")
    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val item = dataSet[position]
        // Usamos item.name (puede ser nulo) e item.address
        viewHolder.tvTitulo.text = item.name ?: "Dispositivo desconocido"
        viewHolder.tvMac.text = item.address

        viewHolder.itemView.setOnClickListener {
            onItemClick(item)
        }
    }

    override fun getItemCount() = dataSet.size

    fun updateList(newList: List<BluetoothDevice>) {
        dataSet.clear()
        dataSet.addAll(newList)
        notifyDataSetChanged()
    }
}