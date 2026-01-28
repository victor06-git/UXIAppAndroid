package com.vasensio.uxiappandroid.ui.historial

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class HistorialViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "This is Historial Fragment"
    }
    val text: LiveData<String> = _text
}