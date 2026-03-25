package com.rentflow.scanner.data.hardware

import kotlinx.coroutines.flow.Flow

data class BarcodeScanEvent(val barcode: String, val format: String)
data class RfidReadEvent(val epc: String, val rssi: Int)
data class RfidWriteResult(val success: Boolean, val error: String? = null)

interface HardwareScanner {
    val barcodeScanEvents: Flow<BarcodeScanEvent>
    val rfidReadEvents: Flow<RfidReadEvent>

    fun initBarcodeScan()
    fun startBarcodeScan()
    fun stopBarcodeScan()
    fun closeBarcodeScan()
    fun startRfidRead()
    fun startRfidBulkRead()
    fun stopRfid()
    fun closeRfid()
    suspend fun writeRfidTag(epc: String): RfidWriteResult
    fun isRfidAvailable(): Boolean
    fun destroy()
}
