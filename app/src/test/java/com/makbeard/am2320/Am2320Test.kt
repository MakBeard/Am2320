package com.makbeard.am2320

import com.google.android.things.pio.I2cDevice
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.*

/**
 * Test class for am2320
 *
 * Created by makbeard on 29.01.2018.
 */
class Am2320Test {

    @Mock
    private val i2cDevice : I2cDevice = mock(I2cDevice::class.java)

    @Rule
    @JvmField
    val expectedException : ExpectedException = ExpectedException.none()

    @Test
    fun close() {
        val am2320 = Am2320(i2cDevice)
        am2320.close()
        Mockito.verify<I2cDevice>(i2cDevice).close()
    }

    @Test
    fun readAfterClose() {
        val am2320 = Am2320(i2cDevice)
        am2320.close()
        expectedException.expect(IllegalStateException::class.java)
        am2320.temperature()
    }

    @Test
    fun checkCrc() {
        val am2320 = Am2320(i2cDevice)
        val inputByteArray = ByteArray(6)
        inputByteArray[0] = 0x03
        inputByteArray[1] = 0x04
        inputByteArray[2] = 0x03
        inputByteArray[3] = 0x39
        inputByteArray[4] = 0x01
        inputByteArray[5] = 0x15

        val crcShort = am2320.crc16(inputByteArray)
        val crcByteArray = am2320.shortToByteArray(crcShort)
        Assert.assertEquals(0xFE.toByte(), crcByteArray[0])
        Assert.assertEquals(0xE1.toByte(), crcByteArray[1])
    }
}