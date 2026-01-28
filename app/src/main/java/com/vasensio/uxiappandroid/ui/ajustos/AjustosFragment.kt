package com.vasensio.uxiappandroid.ui.ajustos

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.vasensio.uxiappandroid.databinding.FragmentAjustosBinding

class AjustosFragment : Fragment() {

    private var _binding: FragmentAjustosBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val ajustosViewModel =
            ViewModelProvider(this).get(AjustosViewModel::class.java)

        _binding = FragmentAjustosBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val textView: TextView = binding.textAjustos
        ajustosViewModel.text.observe(viewLifecycleOwner) {
            textView.text = it
        }
        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}