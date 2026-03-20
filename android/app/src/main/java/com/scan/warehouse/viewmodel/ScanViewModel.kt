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
                _error.value = when {
                    e.message?.contains("404") == true -> "등록되지 않은 바코드입니다"
                    e.message?.contains("timeout", true) == true -> "서버 응답이 느립니다. 다시 시도해주세요"
                    e.message?.contains("connect", true) == true -> "서버에 연결할 수 없습니다"
                    else -> "일시적인 오류가 발생했습니다"
                }
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
                if (response.items.isEmpty()) {
                    _error.value = "\"${query}\" 검색 결과가 없습니다"
                }
                _searchResults.value = response
                savedStateHandle[KEY_SCAN_RESULT] = null
            }.onFailure { e ->
                _error.value = when {
                    e.message?.contains("timeout", true) == true -> "서버 응답이 느립니다. 다시 시도해주세요"
                    e.message?.contains("connect", true) == true -> "서버에 연결할 수 없습니다"
                    else -> "일시적인 오류가 발생했습니다"
                }
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
