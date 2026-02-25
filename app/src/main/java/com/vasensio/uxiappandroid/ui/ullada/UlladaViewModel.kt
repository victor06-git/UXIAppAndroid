package com.vasensio.uxiappandroid.ui.ullada

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class UlladaViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "This is Ullada Fragment"
    }
    val text: LiveData<String> = _text
}