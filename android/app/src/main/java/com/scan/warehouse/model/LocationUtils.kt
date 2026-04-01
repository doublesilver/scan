package com.scan.warehouse.model

data class ParsedLocation(
    val floor: Int = 5,
    val zone: String = "",
    val shelf: String = ""
) {
    companion object {
        fun parse(location: String?): ParsedLocation {
            if (location == null) return ParsedLocation()
            val parts = location.replace("층", "").split("-").map { it.trim() }
            return ParsedLocation(
                floor = parts.getOrNull(0)?.toIntOrNull() ?: 5,
                zone = parts.getOrNull(1) ?: "",
                shelf = parts.getOrNull(2) ?: ""
            )
        }
    }
}
