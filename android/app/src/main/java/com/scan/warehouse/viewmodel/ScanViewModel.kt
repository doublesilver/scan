package com.scan.warehouse.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.scan.warehouse.model.ScanResponse
import com.scan.warehouse.model.SearchResponse
import com.scan.warehouse.repository.ProductRepository
import kotlinx.coroutines.launch

class ScanViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ProductRepository(application)

    private val _scanResult = MutableLiveData<ScanResponse?>()
    val scanResult: LiveData<ScanResponse?> = _scanResult

    private val _searchResults = MutableLiveData<SearchResponse?>()
    val searchResults: LiveData<SearchResponse?> = _searchResults

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private var lastScanTime = 0L

    fun scanBarcode(barcode: String) {
        val now = System.currentTimeMillis()
        if (now - lastScanTime < 300) return
        lastScanTime = now

        _isLoading.value = true
        _error.value = null
        viewModelScope.launch {
            val result = repository.scanBarcode(barcode)
            result.onSuccess { response ->
                _scanResult.value = response
                _searchResults.value = null
            }.onFailure { e ->
                _error.value = "스캔 실패: ${e.message}"
            }
            _isLoading.value = false
        }
    }

    fun searchProducts(query: String) {
        if (query.isBlank()) return
        _isLoading.value = true
        _error.value = null
        viewModelScope.launch {
            val result = repository.searchProducts(query)
            result.onSuccess { response ->
                _searchResults.value = response
                _scanResult.value = null
            }.onFailure { e ->
                _error.value = "검색 실패: ${e.message}"
            }
            _isLoading.value = false
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun getImageUrl(filePath: String): String {
        return repository.getImageUrl(filePath)
    }
}
