package com.rentflow.scanner.data.hardware

import android.content.Context
import android.device.ScanManager
import android.device.scanner.configuration.PropertyID
import android.util.Log
import com.rfid.trans.ReadTag
import com.rfid.trans.ReaderHelp
import com.rfid.trans.TagCallback
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real hardware scanner implementation for the CF-H906 UHF PDA.
 *
 * Barcode: Uses android.device.ScanManager (built-in barcode engine).
 *          Receives results via BroadcastReceiver in intent output mode.
 *
 * UHF RFID: Uses com.rfid.trans.ReaderHelp (rfiddrive-release.aar).
 *           Connects via serial port /dev/ttyHSL0 at 115200 baud.
 *           Tags arrive via TagCallback on background thread.
 */
class CfH906HardwareScanner(
    private val context: Context,
) : HardwareScanner {

    companion object {
        private const val TAG = "CfH906Scanner"
        private const val RFID_SERIAL_PORT = "/dev/ttyHSL0"
        private const val RFID_BAUD_RATE = 115200
        private const val RFID_DEFAULT_PASSWORD = "00000000"
    }

    // --- Barcode ---
    private var scanManager: ScanManager? = null
    private val broadcastReceiver = BroadcastScannerReceiver()
    private var isBarcodeOpen = false

    override val barcodeScanEvents: Flow<BarcodeScanEvent> = broadcastReceiver.scanEvents

    // --- UHF RFID ---
    private val rfidReader by lazy { ReaderHelp() }
    private var isRfidConnected = false

    private val _rfidReadEvents = MutableSharedFlow<RfidReadEvent>(extraBufferCapacity = 64)
    override val rfidReadEvents: Flow<RfidReadEvent> = _rfidReadEvents

    private val rfidCallback = object : TagCallback {
        override fun tagCallback(tag: ReadTag?) {
            tag ?: return
            val epc = tag.epcId?.uppercase() ?: return
            val rssi = tag.rssi
            _rfidReadEvents.tryEmit(RfidReadEvent(epc, rssi))
        }

        override fun StopReadCallBack() {
            Log.d(TAG, "RFID continuous read stopped")
        }
    }

    // ==================== Barcode Scanner ====================

    override fun startBarcodeScan() {
        try {
            if (scanManager == null) {
                scanManager = ScanManager()
            }
            if (!isBarcodeOpen) {
                val opened = scanManager?.openScanner() ?: false
                if (opened) {
                    isBarcodeOpen = true
                    // Switch to intent output mode (broadcasts result)
                    scanManager?.switchOutputMode(0)
                    // Disable keyboard wedge so we get broadcast instead
                    scanManager?.setParameterInts(
                        intArrayOf(PropertyID.WEDGE_KEYBOARD_ENABLE),
                        intArrayOf(0)
                    )
                    // Register receiver
                    context.registerReceiver(broadcastReceiver, broadcastReceiver.getIntentFilter())
                    Log.d(TAG, "Barcode scanner opened successfully")
                } else {
                    Log.e(TAG, "Failed to open barcode scanner")
                }
            }
            scanManager?.startDecode()
        } catch (e: Exception) {
            Log.e(TAG, "Error starting barcode scan", e)
        }
    }

    override fun stopBarcodeScan() {
        try {
            scanManager?.stopDecode()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping barcode scan", e)
        }
    }

    // ==================== UHF RFID ====================

    private fun connectRfid(): Boolean {
        if (isRfidConnected) return true
        try {
            // Power on POGO pin for RFID module
            OtgUtils.setPOGOPINEnable(true)
            Thread.sleep(300) // Allow module to power up

            var result = rfidReader.Connect(RFID_SERIAL_PORT, RFID_BAUD_RATE, 1)
            if (result != 0) {
                // Fallback to 57600 baud
                result = rfidReader.Connect(RFID_SERIAL_PORT, 57600, 1)
            }
            if (result == 0) {
                isRfidConnected = true
                rfidReader.SetCallBack(rfidCallback)

                // Configure inventory session
                val param = rfidReader.GetInventoryPatameter()
                param.Session = 1
                rfidReader.SetInventoryPatameter(param)

                Log.d(TAG, "RFID connected, reader type: ${rfidReader.GetReaderType()}")
                return true
            } else {
                Log.e(TAG, "RFID connect failed with code: $result")
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting RFID", e)
            return false
        }
    }

    override fun startRfidRead() {
        if (!connectRfid()) return
        try {
            rfidReader.ScanRfid()
        } catch (e: Exception) {
            Log.e(TAG, "Error starting single RFID read", e)
        }
    }

    override fun startRfidBulkRead() {
        if (!connectRfid()) return
        try {
            rfidReader.StartRead()
            Log.d(TAG, "RFID bulk read started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting bulk RFID read", e)
        }
    }

    override fun stopRfid() {
        try {
            if (isRfidConnected) {
                rfidReader.StopRead()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping RFID", e)
        }
    }

    override suspend fun writeRfidTag(epc: String): RfidWriteResult {
        if (!connectRfid()) {
            return RfidWriteResult(false, "RFID not connected")
        }
        return try {
            val result = rfidReader.WriteEPC_G2(epc.uppercase(), RFID_DEFAULT_PASSWORD)
            if (result == 0) {
                RfidWriteResult(true)
            } else {
                RfidWriteResult(false, "Write failed with code: $result")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error writing RFID tag", e)
            RfidWriteResult(false, e.message)
        }
    }

    override fun isRfidAvailable(): Boolean {
        return try {
            connectRfid()
        } catch (e: Exception) {
            false
        }
    }

    // ==================== Lifecycle ====================

    override fun destroy() {
        try {
            // Barcode
            if (isBarcodeOpen) {
                scanManager?.stopDecode()
                scanManager?.closeScanner()
                try {
                    context.unregisterReceiver(broadcastReceiver)
                } catch (_: Exception) {}
                isBarcodeOpen = false
            }
            // RFID
            if (isRfidConnected) {
                rfidReader.StopRead()
                rfidReader.DisConnect()
                isRfidConnected = false
            }
            OtgUtils.setPOGOPINEnable(false)
            Log.d(TAG, "Scanner destroyed, resources released")
        } catch (e: Exception) {
            Log.e(TAG, "Error during destroy", e)
        }
    }
}
