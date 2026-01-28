package com.vasensio.uxiappandroid.ui.ullada

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.vasensio.uxiappandroid.databinding.FragmentUlladaBinding

class UlladaFragment : Fragment() {

    private var _binding: FragmentUlladaBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val ulladaViewModel =
            ViewModelProvider(this).get(UlladaViewModel::class.java)

        _binding = FragmentUlladaBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val textView: TextView = binding.textUllada
        ulladaViewModel.text.observe(viewLifecycleOwner) {
            textView.text = it
        }
        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}