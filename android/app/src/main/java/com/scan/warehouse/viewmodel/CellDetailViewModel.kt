package com.scan.warehouse.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scan.warehouse.model.MapCell
import com.scan.warehouse.model.MapLayout
import com.scan.warehouse.model.MapLevel
import com.scan.warehouse.model.ScanResponse
import com.scan.warehouse.repository.ProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import okhttp3.MultipartBody
import javax.inject.Inject

@HiltViewModel
class CellDetailViewModel @Inject constructor(
    private val repository: ProductRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    val cellKey: String get() = savedStateHandle["cell_key"] ?: ""
    val floor: Int get() = savedStateHandle["floor"] ?: 5
    val zone: String get() = savedStateHandle["zone"] ?: ""

    private val _cellData = MutableLiveData<MapCell?>()
    val cellData: LiveData<MapCell?> = _cellData

    private val _isEditMode = MutableLiveData(false)
    val isEditMode: LiveData<Boolean> = _isEditMode

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _allCellKeys = MutableLiveData<ArrayList<String>?>()
    val allCellKeys: LiveData<ArrayList<String>?> = _allCellKeys

    private val _currentCellIndex = MutableLiveData(-1)
    val currentCellIndex: LiveData<Int> = _currentCellIndex

    var zoneColCount = 4
        private set

    private val _loadError = MutableLiveData<String?>()
    val loadError: LiveData<String?> = _loadError

    fun setEditMode(edit: Boolean) {
        _isEditMode.value = edit
    }

    fun loadCellData() {
        _isLoading.value = true
        viewModelScope.launch {
            repository.getMapLayout().onSuccess { layout ->
                val cell = layout.cells[cellKey]
                _cellData.value = cell
                layout.zones.find { it.code == zone }?.let { zoneColCount = it.cols }

                val keys = ArrayList<String>()
                for (z in layout.zones) {
                    for (r in 1..z.rows) {
                        for (c in 1..z.cols) {
                            keys.add("${z.code}-$r-$c")
                        }
                    }
                }
                _allCellKeys.value = keys
                _currentCellIndex.value = keys.indexOf(cellKey)

                val isEmpty = cell?.levels?.all { level ->
                    level.itemLabel.isNullOrEmpty() && level.sku.isNullOrEmpty() && level.photo.isNullOrEmpty()
                } ?: true
                if (isEmpty && _isEditMode.value != true) {
                    if (cell == null) _cellData.value = MapCell(levels = DEFAULT_LEVELS)
                    _isEditMode.value = true
                }
                _loadError.value = null
            }.onFailure {
                _loadError.value = "셀 정보를 불러오지 못했습니다"
            }
            _isLoading.value = false
        }
    }

    fun saveLevels(levels: List<MapLevel>, onError: ((String) -> Unit)? = null) {
        viewModelScope.launch {
            repository.updateMapCell(cellKey, mapOf("levels" to levels.toApiPayload()))
                .onSuccess { loadCellData() }
                .onFailure { onError?.invoke("저장 실패") }
        }
    }

    fun saveLevelChanges(
        levelIndex: Int,
        productName: String,
        sku: String,
        matchedPhotoUrl: String?,
        matchedSkuId: String?
    ) {
        val cell = _cellData.value ?: MapCell(levels = listOf(MapLevel(index = 0, label = "하단 (1층)")))
        if (_cellData.value == null) _cellData.value = cell
        val levels = cell.levels?.toMutableList() ?: DEFAULT_LEVELS.toMutableList()

        if (levelIndex >= levels.size) {
            _loadError.value = "층 구성이 변경되었습니다. 화면을 새로고침해주세요"
            return
        }

        val updated = levels[levelIndex].copy(
            itemLabel = productName.ifEmpty { null },
            sku = sku.ifEmpty { null },
            photo = matchedPhotoUrl ?: levels[levelIndex].photo
        )
        levels[levelIndex] = updated

        saveLevels(levels)

        if (matchedSkuId != null) {
            val parts = cellKey.split("-")
            val row = parts.getOrElse(1) { "1" }.toIntOrNull() ?: 1
            val col = parts.getOrElse(2) { "1" }.toIntOrNull() ?: 1
            val seqNum = (row - 1) * zoneColCount + col
            val location = "${floor}층-$zone-$seqNum"
            viewModelScope.launch {
                repository.updateProductLocation(matchedSkuId, location)
            }
        }
    }

    fun deleteProduct(levelIndex: Int) {
        val cell = _cellData.value ?: return
        val levels = cell.levels?.toMutableList() ?: return
        if (levelIndex >= levels.size) return
        val cleared = levels[levelIndex].copy(itemLabel = null, sku = null, photo = null)
        levels[levelIndex] = cleared
        saveLevels(levels) { }
    }

    fun addLevel() {
        val cell = _cellData.value ?: return
        val levels = cell.levels?.toMutableList() ?: mutableListOf()
        val newIndex = levels.size
        val label = when (newIndex) {
            0 -> "하단 (1층)"
            1 -> "중단 (2층)"
            2 -> "상단 (3층)"
            else -> "${newIndex + 1}층"
        }
        levels.add(MapLevel(index = newIndex, label = label))
        saveLevels(levels) { }
    }

    fun removeLevel() {
        val cell = _cellData.value ?: return
        val levels = cell.levels?.toMutableList() ?: return
        if (levels.isEmpty()) return
        levels.removeAt(levels.size - 1)
        saveLevels(levels) { }
    }

    fun uploadLevelPhoto(levelIndex: Int, part: MultipartBody.Part, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        viewModelScope.launch {
            repository.uploadLevelPhoto(cellKey, levelIndex, part)
                .onSuccess {
                    repository.getMapLayout().onSuccess { layout ->
                        _cellData.value = layout.cells[cellKey]
                        layout.zones.find { it.code == zone }?.let { zoneColCount = it.cols }
                        onSuccess()
                    }
                }
                .onFailure { e -> onFailure(e.message ?: "업로드 실패") }
        }
    }

    fun deleteLevelPhoto(levelIndex: Int, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        viewModelScope.launch {
            repository.deleteLevelPhoto(cellKey, levelIndex)
                .onSuccess {
                    loadCellData()
                    onSuccess()
                }
                .onFailure { onFailure("사진 삭제 실패") }
        }
    }

    fun scanBarcode(barcode: String, onResult: (Result<ScanResponse>) -> Unit) {
        viewModelScope.launch {
            val (result, _) = repository.scanBarcode(barcode)
            onResult(result)
        }
    }

    fun searchProducts(query: String, onResult: (Result<com.scan.warehouse.model.SearchResponse>) -> Unit) {
        viewModelScope.launch {
            val (result, _) = repository.searchProducts(query)
            onResult(result)
        }
    }

    fun getImageUrl(filePath: String): String = repository.getImageUrl(filePath)

    private fun List<MapLevel>.toApiPayload(): List<Map<String, Any>> = map { lv ->
        mapOf(
            "index" to lv.index,
            "label" to lv.label,
            "itemLabel" to (lv.itemLabel ?: ""),
            "sku" to (lv.sku ?: ""),
            "photo" to (lv.photo ?: "")
        )
    }

    companion object {
        val DEFAULT_LEVELS = listOf(
            MapLevel(index = 0, label = "하단 (1층)"),
            MapLevel(index = 1, label = "중단 (2층)"),
            MapLevel(index = 2, label = "상단 (3층)")
        )
    }
}
