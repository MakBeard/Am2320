package com.makbeard.am2320

import android.util.Log
import com.google.android.things.pio.I2cDevice
import com.google.android.things.pio.PeripheralManagerService
import java.io.IOException

// Sensor constants from the datasheet.
// https://akizukidenshi.com/download/ds/aosong/AM2320.pdf
/**
 * Mininum temperature in Celsius the sensor can measure.
 */
const val MIN_TEMP_C = -40f

/**
 * Maximum temperature in Celsius the sensor can measure.
 */
const val MAX_TEMP_C = 80f

/**
 * Mininum humidity in RH the sensor can measure.
 */
const val MIN_HUM_RH = 0f

/**
 * Maximum temperature in RH the sensor can measure.
 */
const val MAX_HUM_RH = 99.9f

/**
 * Maximum power consumption in micro-amperes when measuring.
 */
const val MAX_POWER_CONSUMPTION_MEASURE_UA = 950f

/**
 * Average power consumption in micro-amperes when work.
 */
const val AVERAGE_POWER_CONSUMPTION_MEASURE_UA = 350f

/**
 * Driver for the AM2320 temperature sensor.
 *
 * Created by makbeard on 13.01.2018.
 */
class Am2320 : AutoCloseable {

    /** Registers from datasheet
    0x00	High humidity           0x01	Low humidity
    0x02	High temperature        0x03	Low temperature
    0x04	Retention               0x05	Retention
    0x06	Retention               0x07	Retention
    0x08	Model High              0x09	Model Low
    0x0A	The version number      0x0B	Device ID (24-31) Bit
    0x0C	Device ID (16-23) Bit   0x0D	Device ID (8-15) Bit
    0x0E	Device ID (0-7) Bit     0x0F	Status Register
    0x10	Users register a high   0x11	Users register a low
    0x12	Users register 2 high   0x13	Users register 2 low
    0x14	Retention               0x15	Retention
    0x16	Retention               0x17	Retention
    0x18	Retention               0x19	Retention
    0x1A	Retention               0x1B	Retention
    0x1C	Retention               0x1D	Retention
    0x1E	Retention               0x1F	Retention
    */

    private val TAG = "AM2320"

    /**
     * Default I2C address for the sensor.
     */
    val DEFAULT_I2C_ADDRESS = 0xB8 shr 1 //0x5C

    /**
     * Reading Register Data
     * Read one or more data registers
     */
    private val AM2320_FUN_CODE_READ_REG_DATA : Byte = 0x03

    /**
     * Write Multiple Registers
     * Multiple sets of binary data to write multiple registers
     */
    private val AM2320_FUN_CODE_WRITE_MULT_REG : Byte = 0x10

    private val AM2320_REG_HIGH_HUMIDITY : Byte = 0x00
    private val AM2320_REG_LOW_HUMIDITY : Byte = 0x01
    private val AM2320_REG_HIGH_TEMPERATURE : Byte = 0x02
    private val AM2320_REG_LOW_TEMPERATURE : Byte = 0x03
    private val AM2320_REG_SENSEOR_MODEL_HIGH : Byte = 0x08

    var device: I2cDevice? = null
    var address: Int = 0

    /**
     * Create a new AM2320 sensor driver connected on the given bus and address.
     * @param bus I2C bus the sensor is connected to.
     * @throws IOException
     */
    constructor(bus: String) {
        val manager = PeripheralManagerService()
        if (address == 0) {
            address = DEFAULT_I2C_ADDRESS
        }
        val device = manager.openI2cDevice(bus, address)
        if (manager.i2cBusList.size > 0) {
            try {
                connect(device)
            } catch (e: IOException) {
                Log.e(TAG, "init exception ", e)
                try {
                    close()
                } catch (e: IOException) {
                    Log.e(TAG, "close exception", e)
                }
            }
        } else {
            Log.e(TAG, "i2c bus list is empty!")
        }
    }

    constructor(bus: String, address : Int) : this(bus) {
        this.address = address
    }

    constructor(i2cDevice: I2cDevice) {
        connect(i2cDevice)
    }

    private fun connect(device: I2cDevice) {
        this.device = device
        wakeUp()
    }

    /**
     * Close the driver and the underlying device.
     */
    override fun close() {
        if (device != null) {
            try {
                device!!.close()
            } finally {
                device = null
            }
        }
    }

    /**
     * The sensor in a non-working state, dormant, so to read the sensor must wake sensor
     * to transmit commands to read and write, otherwise the sensor will not respond
     */
    private fun wakeUp() {
        try {
            device?.read(ByteArray(1), 1)
        } catch (e: IOException) {
            Log.w(TAG, "wake up exception (expected): ${e.message}")
        }
    }


    /**
     * Method return temperature (cesium) and humidity (%) in array. First temperature, second humidity
     */
    fun temperatureHumidity() : FloatArray {
        if (device == null)
            throw IllegalStateException("cannot register closed driver")

        //temperature and humidity (SLA + W) + 0x03 + 0x00 + 0x04
        val writeBytes = ByteArray(3)
        //Function code
        writeBytes[0] = AM2320_FUN_CODE_READ_REG_DATA
        //Start address
        writeBytes[1] = AM2320_REG_HIGH_HUMIDITY
        //Number of registers
        writeBytes[2] = 0x04

        device?.write(writeBytes, writeBytes.size)

        //Array size = number of registers + function code + crc low + crc high + count of data(number of registers)
        val readBytes = ByteArray(8)
        //Return：0x03 +0 x04 + humidity + high + low temperature and humidity high temperature low + CRC
        device?.read(readBytes, readBytes.size)

        val crcShort = crc16(readBytes.copyOf(6))
        val crcArray  = shortToByteArray(crcShort)

        //Function code and number of registers must be equals, crc code calculated on client side
        //must be the same as from sensor
        if (readBytes[0] == writeBytes[0] && readBytes[1] == writeBytes[2]
                && readBytes[readBytes.size - 2] == crcArray[1] && readBytes[readBytes.size - 1] == crcArray[0]) {

            val resultArray = FloatArray(2)
            //temperature
            resultArray[0] = bytesToDecLong(readBytes[4], readBytes[5]).toFloat() / 10
            //humidity
            resultArray[1] = bytesToDecLong(readBytes[2], readBytes[3]).toFloat() / 10

            return resultArray
        } else {
            throw IOException("Incorrect sensor answer")
        }
    }

    /**
     * Method return temperature in cesium
     */
    fun temperature() : Float {
        if (device == null)
            throw IllegalStateException("cannot register closed driver")

        //Temperature (SLA + W) + 0x03 + 0x02 + 0x02
        val writeBytes = ByteArray(3)
        //Function code
        writeBytes[0] = AM2320_FUN_CODE_READ_REG_DATA
        //Start address
        writeBytes[1] = AM2320_REG_HIGH_TEMPERATURE
        //Number of registers
        writeBytes[2] = 0x02

        device?.write(writeBytes, writeBytes.size)

        //Array size = number of registers + function code + crc low + crc high
        //+ count of data(number of registers)
        val readBytes = ByteArray(6)
        //Return：0x03 + 0x02 + High temperature + Low temperature + CRC
        device?.read(readBytes, readBytes.size)

        val crcShort = crc16(readBytes.copyOf(4))
        val crcArray = shortToByteArray(crcShort)

        //Function code and number of registers must be equals, crc code calculated on client side
        //must be the same as from sensor
        if (readBytes[0] == writeBytes[0] && readBytes[1] == writeBytes[2]
                && readBytes[readBytes.size - 2] == crcArray[1] && readBytes[readBytes.size - 1] == crcArray[0]) {
            return bytesToDecLong(readBytes[2], readBytes[3]).toFloat() / 10
        } else {
            throw IOException("Incorrect sensor answer")
        }
    }

    /**
     * Method return humidity in %
     */
    fun humidity() : Float {
        if (device == null)
            throw IllegalStateException("cannot register closed driver")

        //Humidity (SLA + W) + 0x03 + 0x00 + 0x02
        val writeBytes = ByteArray(3)
        //Function code
        writeBytes[0] = AM2320_FUN_CODE_READ_REG_DATA
        //Start address
        writeBytes[1] = AM2320_REG_HIGH_HUMIDITY
        //Number of registers
        writeBytes[2] = 0x02

        device?.write(writeBytes, writeBytes.size)

        //Array size = number of registers + function code + crc low + crc high
        //+ count of data(number of registers)
        val readBytes = ByteArray(6)
        //Return：0x03 + 0x02 + High humidity + Low humidity + CRC
        device?.read(readBytes, readBytes.size)

        val crcShort = crc16(readBytes.copyOf(4))
        val crcArray = shortToByteArray(crcShort)

        //Function code and number of registers must be equals, crc code calculated on client side
        //must be the same as from sensor
        if (readBytes[0] == writeBytes[0] && readBytes[1] == writeBytes[2]
                && readBytes[readBytes.size - 2] == crcArray[1] && readBytes[readBytes.size - 1] == crcArray[0]) {
            return bytesToDecLong(readBytes[2], readBytes[3]).toFloat() / 10
        } else {
            throw IOException("Incorrect sensor answer")
        }
    }

    /**
     * Method return device info
     */
    fun deviceInfo() {
        //Device Information (SLA + W) + 0x03 + 0x08 + 0x07
        val writeBytes = ByteArray(3)
        //Function code
        writeBytes[0] = AM2320_FUN_CODE_READ_REG_DATA
        //Start address
        writeBytes[1] = AM2320_REG_SENSEOR_MODEL_HIGH
        //Number of registers
        writeBytes[2] = 0x07

        device?.write(writeBytes, writeBytes.size)

        //Array size = number of registers + function code + crc low + crc high
        //+ count of data(number of registers)
        val readBytes = ByteArray(11)
        //Return：0x03 + 0x07 + Model (16) + version number (8) + ID (32-bit) + CRC
        device?.read(readBytes, readBytes.size)

        val crcShort = crc16(readBytes.copyOf(9))
        val crcArray = shortToByteArray(crcShort)

        //Function code and number of registers must be equals, crc code calculated on client side
        //must be the same as from sensor
        if (readBytes[0] == writeBytes[0] && readBytes[1] == writeBytes[2]
                && readBytes[readBytes.size - 2] == crcArray[1] && readBytes[readBytes.size - 1] == crcArray[0]) {
            val stringBuilder = StringBuilder()

            readBytes
                    .copyOfRange(2, 9)
                    .forEach { byte -> stringBuilder.append(formatByte(byte)) }

            //TODO Add return statement and find out why device info always 00000000000000
            Log.d(TAG, "device info = $stringBuilder")
        } else {
            throw IOException("Incorrect sensor answer")
        }
    }


    /**
     * Method for calculate crc16/modbus from byte array
     */
    fun crc16(byteArray: ByteArray): Short {
        var crc = 0xFFFF

        byteArray.forEach { byte ->
            run {
                val convertedByte = byte.toInt() and 0xFF
                crc = crc xor convertedByte
                for (i in 0..7) {
                    if (crc and 0x01 == 0x01) {
                        crc = crc shr 1
                        crc = crc xor 0xA001
                    } else {
                        crc = crc shr 1
                    }
                }
            }
        }

        return crc.toShort()
    }

    // ---------- Utils functions ----------
    fun shortToByteArray(short: Short): ByteArray {
        val byteArray = ByteArray(2)
        byteArray[0] = (short.toPositiveInt() shr 8).toByte()
        byteArray[1] = short.toPositiveInt().toByte()

        return byteArray
    }

    private fun bytesToDecLong(highByte: Byte, lowByte: Byte): Long {
        return formatByte(highByte).plus(formatByte(lowByte)).toLong(16)
    }

    fun Byte.toPositiveInt() = toInt() and 0xFF

    fun Short.toPositiveInt() = toInt() and 0xFFFF

    fun formatByte(byte: Byte): String {
        return String.format("%02X", byte)
    }
}