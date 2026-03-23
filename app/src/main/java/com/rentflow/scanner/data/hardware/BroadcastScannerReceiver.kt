package com.rentflow.scanner.data.hardware

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

class BroadcastScannerReceiver : BroadcastReceiver() {
    private val _scanEvents = MutableSharedFlow<BarcodeScanEvent>(extraBufferCapacity = 10)
    val scanEvents: Flow<BarcodeScanEvent> = _scanEvents

    override fun onReceive(context: Context?, intent: Intent?) {
        // TODO: Update action/extra keys based on CF-H906 SDK documentation
        val barcode = intent?.getStringExtra("SCAN_BARCODE_DATA") ?: return
        val format = intent.getStringExtra("SCAN_BARCODE_TYPE") ?: "UNKNOWN"
        _scanEvents.tryEmit(BarcodeScanEvent(barcode, format))
    }

    fun getIntentFilter(): IntentFilter {
        // TODO: Update with actual CF-H906 broadcast action
        return IntentFilter("com.cfh906.scanner.BARCODE_SCAN")
    }
}
