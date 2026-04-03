package com.scan.warehouse.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scan.warehouse.model.BoxResponse
import com.scan.warehouse.model.ScanResponse
import com.scan.warehouse.model.SearchResponse
import com.scan.warehouse.repository.ProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val repository: ProductRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    val scanResult: LiveData<ScanResponse?> = savedStateHandle.getLiveData(KEY_SCAN_RESULT)

    private val _searchResults = MutableLiveData<SearchResponse?>()
    val searchResults: LiveData<SearchResponse?> = _searchResults

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _isOffline = MutableLiveData(false)
    val isOffline: LiveData<Boolean> = _isOffline

    private val _boxResult = MutableLiveData<BoxResponse?>()
    val boxResult: LiveData<BoxResponse?> = _boxResult

    private val _boxNotFound = MutableLiveData<String?>()
    val boxNotFound: LiveData<String?> = _boxNotFound

    private var lastScanTime = 0L

    fun scanBarcode(barcode: String) {
        val now = System.currentTimeMillis()
        if (now - lastScanTime < 300) return
        lastScanTime = now

        _isLoading.value = true
        _error.value = null
        savedStateHandle[KEY_SCAN_RESULT] = null
        _searchResults.value = null
        viewModelScope.launch {
            val (result, offline) = repository.scanBarcode(barcode)
            _isOffline.value = offline
            result.onSuccess { response ->
                savedStateHandle[KEY_SCAN_RESULT] = response
                _searchResults.value = null
            }.onFailure { e ->
                _error.value = when (e) {
                    is retrofit2.HttpException -> when (e.code()) {
                        404 -> "등록되지 않은 바코드입니다"
                        in 500..599 -> "서버 오류가 발생했습니다"
                        else -> "일시적인 오류가 발생했습니다"
                    }
                    is java.net.SocketTimeoutException -> "서버 응답이 느립니다"
                    is java.net.ConnectException -> "서버에 연결할 수 없습니다"
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
        savedStateHandle[KEY_SCAN_RESULT] = null
        _searchResults.value = null
        viewModelScope.launch {
            val (result, offline) = repository.searchProducts(query)
            _isOffline.value = offline
            result.onSuccess { response ->
                if (response.items.isEmpty()) {
                    _error.value = "\"${query}\" 검색 결과가 없습니다"
                }
                _searchResults.value = response
                savedStateHandle[KEY_SCAN_RESULT] = null
            }.onFailure { e ->
                _error.value = when (e) {
                    is retrofit2.HttpException -> when (e.code()) {
                        in 500..599 -> "서버 오류가 발생했습니다"
                        else -> "일시적인 오류가 발생했습니다"
                    }
                    is java.net.SocketTimeoutException -> "서버 응답이 느립니다"
                    is java.net.ConnectException -> "서버에 연결할 수 없습니다"
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

    fun scanBox(qrCode: String) {
        _isLoading.value = true
        _error.value = null
        viewModelScope.launch {
            repository.scanBox(qrCode)
                .onSuccess { _boxResult.value = it }
                .onFailure { e ->
                    if (e is retrofit2.HttpException && e.code() == 404) {
                        _boxNotFound.value = qrCode
                    } else {
                        _error.value = "박스 조회 실패: ${e.message}"
                    }
                }
            _isLoading.value = false
        }
    }

    fun clearBoxResult() {
        _boxResult.value = null
    }

    fun clearBoxNotFound() {
        _boxNotFound.value = null
    }

    companion object {
        private const val KEY_SCAN_RESULT = "scan_result"
    }
}
