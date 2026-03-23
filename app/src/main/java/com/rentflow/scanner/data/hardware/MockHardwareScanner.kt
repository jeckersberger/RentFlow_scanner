package com.rentflow.scanner.data.hardware

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import javax.inject.Inject

class MockHardwareScanner @Inject constructor() : HardwareScanner {
    private val _barcodeScanEvents = MutableSharedFlow<BarcodeScanEvent>()
    override val barcodeScanEvents: Flow<BarcodeScanEvent> = _barcodeScanEvents

    private val _rfidReadEvents = MutableSharedFlow<RfidReadEvent>()
    override val rfidReadEvents: Flow<RfidReadEvent> = _rfidReadEvents

    private val mockBarcodes = listOf("EQ-001", "EQ-002", "EQ-003", "EQ-004", "EQ-005")
    private val mockRfidTags = listOf("E200001", "E200002", "E200003")
    private var scanIndex = 0

    override fun startBarcodeScan() {}
    override fun stopBarcodeScan() {}
    override fun startRfidRead() {}
    override fun startRfidBulkRead() {}
    override fun stopRfid() {}

    override suspend fun writeRfidTag(epc: String): RfidWriteResult {
        delay(500)
        return RfidWriteResult(success = true)
    }

    override fun isRfidAvailable(): Boolean = true
    override fun destroy() {}

    suspend fun simulateBarcodeScan(barcode: String? = null) {
        val code = barcode ?: mockBarcodes[scanIndex++ % mockBarcodes.size]
        _barcodeScanEvents.emit(BarcodeScanEvent(code, "QR_CODE"))
    }

    suspend fun simulateRfidRead(epc: String? = null, rssi: Int = -45) {
        val tag = epc ?: mockRfidTags.random()
        _rfidReadEvents.emit(RfidReadEvent(tag, rssi))
    }
}
