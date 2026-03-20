package com.scan.warehouse.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.scan.warehouse.model.ScanResponse
import com.scan.warehouse.model.SearchResponse
import com.scan.warehouse.repository.ProductRepository
import kotlinx.coroutines.launch

class ScanViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val repository = ProductRepository(application)

    val scanResult: LiveData<ScanResponse?> = savedStateHandle.getLiveData(KEY_SCAN_RESULT)

    private val _searchResults = MutableLiveData<SearchResponse?>()
    val searchResults: LiveData<SearchResponse?> = _searchResults

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _isOffline = MutableLiveData(false)
    val isOffline: LiveData<Boolean> = _isOffline

    private var lastScanTime = 0L

    fun scanBarcode(barcode: String) {
        val now = System.currentTimeMillis()
        if (now - lastScanTime < 300) return
        lastScanTime = now

        _isLoading.value = true
        _error.value = null
        viewModelScope.launch {
            val result = repository.scanBarcode(barcode)
            _isOffline.value = repository.isOffline
            result.onSuccess { response ->
                savedStateHandle[KEY_SCAN_RESULT] = response
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
            _isOffline.value = repository.isOffline
            result.onSuccess { response ->
                _searchResults.value = response
                savedStateHandle[KEY_SCAN_RESULT] = null
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

    companion object {
        private const val KEY_SCAN_RESULT = "scan_result"
    }
}
