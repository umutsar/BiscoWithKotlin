package com.zeugma.bisco

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.content.Context
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
// import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.IOException
// import java.nio.charset.Charset
// import kotlinx.coroutines.*


fun openUsbConnection(
    context: Context,
    hizTextValue: TextView,
    sarjTextValue: TextView,
    alinanMesafeTextValue: TextView,
    voltageTextValue: TextView,
    sicaklikTextValue: TextView,
    maxPilVoltajiTextValue: TextView,
    harcananGucTextValue: TextView,
    menzilTextValue: TextView
) {
    val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager)
    if (availableDrivers.isEmpty()) return

    val driver = availableDrivers[0]
    val connection: UsbDeviceConnection? = manager.openDevice(driver.device)
    if (connection == null) return

    val port: UsbSerialPort = driver.ports[0]
    port.open(connection)

    // cdc gibi bir protokol kullanıyorsanız (baud rate hızı olmadan) baud rate, databits gibi değerlerin önemi yok.
    //
    port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)

    var lastDataReceivedTime = System.currentTimeMillis()
    var reconnectAttempts = 0
    val maxReconnectAttempts = 5
    val reconnectDelay = 1500L

    val readThread = Thread {
        try {
            val buffer = ByteArray(120)
            while (true) {
                val numBytes = port.read(buffer, 1000)
                if (numBytes > 0) {
                    lastDataReceivedTime = System.currentTimeMillis()
                    val hexString = buffer.take(numBytes).joinToString("") { String.format("%02X", it) }

                    val isSecondPacket = hexString.lastOrNull() == 'F' && hexString[hexString.length - 2] == 'F'

                    if (!isSecondPacket) {
                        val speed = ((hexString.substring(2, 4).toInt(16) shl 8) or hexString.substring(0, 2).toInt(16)) / 200.0
                        val temp = hexString.substring(4, 6).toInt(16) - 40
                        val sumVoltage = ((hexString.substring(6, 8).toInt(16) shl 8) or hexString.substring(8, 10).toInt(16)) / 10.0
                        val distanceCovered = (hexString.substring(10, 12).toInt(16) shl 8) or hexString.substring(12, 14).toInt(16)

                        (context as AppCompatActivity).runOnUiThread {
                            hizTextValue.text = "%.2f".format(speed) + " KPH"
                            sicaklikTextValue.text = temp.toString() + "°C"
                            voltageTextValue.text = "%.1f".format(sumVoltage) + "V"
                            alinanMesafeTextValue.text = distanceCovered.toString() + "m"
                            menzilTextValue.text = "%.1f".format(200.0 - distanceCovered / 100.0) + "m"
                        }
                    } else {
                        val powerWatt = (hexString.substring(0, 2).toInt(16) shl 8) or hexString.substring(2, 4).toInt(16)
                        val maxVoltage = ((hexString.substring(4, 6).toInt(16) shl 8) or hexString.substring(6, 8).toInt(16)) / 1000.0
                        val soc = ((hexString.substring(8, 10).toInt(16) shl 8) or hexString.substring(10, 12).toInt(16)) / 10.0

                        (context as AppCompatActivity).runOnUiThread {
                            harcananGucTextValue.text = powerWatt.toString() + "W"
                            maxPilVoltajiTextValue.text = maxVoltage.toString() + "V"
                            sarjTextValue.text = "%.1f".format(soc) + "%"
                        }
                    }
                } else {
                    if (System.currentTimeMillis() - lastDataReceivedTime > 2000) {
                        port.close()
                        if (reconnectAttempts < maxReconnectAttempts) {
                            reconnectAttempts++
                            Thread.sleep(reconnectDelay)
                            openUsbConnection(
                                context,
                                hizTextValue,
                                sarjTextValue,
                                alinanMesafeTextValue,
                                voltageTextValue,
                                sicaklikTextValue,
                                maxPilVoltajiTextValue,
                                harcananGucTextValue,
                                menzilTextValue
                            )
                        } else {
                            break
                        }
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
    readThread.start()
}



class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val hizTextValue = findViewById<TextView>(R.id.hizTextValue)
        val sarjTextValue = findViewById<TextView>(R.id.sarjTextValue)
        val alinanMesafeTextValue = findViewById<TextView>(R.id.alinanMesafeTextValue)
        val voltageTextValue = findViewById<TextView>(R.id.voltajTextValue)
        val sicaklikTextValue = findViewById<TextView>(R.id.sicaklikTextValue)
        val maxPilVoltajiTextValue = findViewById<TextView>(R.id.maxPilVoltajiTextValue)
        val harcananGucTextValue = findViewById<TextView>(R.id.harcananGucTextValue)
        val menzilTextValue = findViewById<TextView>(R.id.menzilTextValue)


        openUsbConnection(
            this,
            hizTextValue,
            sarjTextValue,
            alinanMesafeTextValue,
            voltageTextValue,
            sicaklikTextValue,
            maxPilVoltajiTextValue,
            harcananGucTextValue,
            menzilTextValue
        )
    }
}
