package com.makbeard.am2320

import android.hardware.Sensor
import com.google.android.things.userdriver.UserDriverManager
import com.google.android.things.userdriver.UserSensor
import com.google.android.things.userdriver.UserSensorDriver
import com.google.android.things.userdriver.UserSensorReading
import java.util.*

/**
 * Sensor driver
 *
 * Created by makbeard on 15.01.2018.
 */
class Am2320SensorDriver(bus: String) : AutoCloseable {

    private val DRIVER_VENDOR = "Aosong"
    private val DRIVER_NAME = "AM2320"
    private val DRIVER_VERSION = 1
    private var device: Am2320?
    private var temperatureUserDriver: TemperatureUserDriver? = null
    private var humidityUserDriver: HumidityUserDriver? = null

    /**
     * Create a new framework sensor driver connected on the given bus.
     * The driver emits {@link android.hardware.Sensor} with humidity and temperature data when
     * registered.
     * @param bus I2C bus the sensor is connected to.
     */
    init {
        device = Am2320(bus)
    }

    /**
     * Create a new framework sensor driver connected on the given bus and address.
     * The driver emits {@link android.hardware.Sensor} with humidity and temperature data when
     * registered.
     * @param bus I2C bus the sensor is connected to.
     * @param address I2C address of the sensor.
     */
    constructor(bus: String, address: Int) : this(bus) {
        device = Am2320(bus, address)
    }

    /**
     * Register a [UserSensor] that pipes temperature readings into the Android SensorManager.
     */
    fun registerTemperatureSensor() {
        if (device == null) {
            throw IllegalStateException("cannot register closed driver")
        }

        if (temperatureUserDriver == null) {
            temperatureUserDriver = TemperatureUserDriver()
            UserDriverManager.getManager().registerSensor(temperatureUserDriver?.getUserSensor())
        }
    }

    /**
     * Register a [UserSensor] that pipes humidity readings into the Android SensorManager.
     * @see .unregisterHumiditySensor
     */
    fun registerHumiditySensor() {
        if (device == null) {
            throw IllegalStateException("cannot register closed driver")
        }

        if (humidityUserDriver == null) {
            humidityUserDriver = HumidityUserDriver()
            UserDriverManager.getManager().registerSensor(humidityUserDriver?.getUserSensor())
        }
    }

    /**
     * Unregister the temperature [UserSensor].
     */
    fun unregisterTemperatureSensor() {
        if (temperatureUserDriver != null) {
            UserDriverManager.getManager().unregisterSensor(temperatureUserDriver?.getUserSensor())
            temperatureUserDriver = null
        }
    }

    /**
     * Unregister the humidity [UserSensor].
     */
    fun unregisterHumiditySensor() {
        if (humidityUserDriver != null) {
            UserDriverManager.getManager().unregisterSensor(humidityUserDriver?.getUserSensor())
            humidityUserDriver = null
        }
    }

    /**
     * Close the driver and the underlying device.
     */
    override fun close() {
        unregisterTemperatureSensor()
        unregisterHumiditySensor()
        if (device != null) {
            try {
                device?.close()
            } finally {
                device = null
            }
        }
    }

    private inner class TemperatureUserDriver : UserSensorDriver() {
        // DRIVER parameters
        private val DRIVER_RESOLUTION = 0.1f
        private val DRIVER_MAX_RANGE = MAX_TEMP_C
        private val DRIVER_POWER = MAX_POWER_CONSUMPTION_MEASURE_UA / 1000f

        private lateinit var userSensor: UserSensor

        fun getUserSensor(): UserSensor {
            userSensor = UserSensor.Builder()
                    .setType(Sensor.TYPE_AMBIENT_TEMPERATURE)
                    .setName(DRIVER_NAME)
                    .setVendor(DRIVER_VENDOR)
                    .setVersion(DRIVER_VERSION)
                    .setResolution(DRIVER_RESOLUTION)
                    .setMaxRange(DRIVER_MAX_RANGE)
                    .setPower(DRIVER_POWER)
                    .setUuid(UUID.randomUUID())
                    .setDriver(this)
                    .build()

            return userSensor
        }

        override fun read(): UserSensorReading {
            val floatArray = FloatArray(1)
            floatArray[0] = device!!.temperature()
            return UserSensorReading(floatArray)
        }
    }

    private inner class HumidityUserDriver : UserSensorDriver() {
        // DRIVER parameters
        private val DRIVER_RESOLUTION = 0.1f
        private val DRIVER_MAX_RANGE = MAX_HUM_RH
        private val DRIVER_POWER = MAX_POWER_CONSUMPTION_MEASURE_UA / 1000f

        private lateinit var userSensor: UserSensor

        fun getUserSensor(): UserSensor {
            userSensor = UserSensor.Builder()
                    .setType(Sensor.TYPE_RELATIVE_HUMIDITY)
                    .setName(DRIVER_NAME)
                    .setVendor(DRIVER_VENDOR)
                    .setVersion(DRIVER_VERSION)
                    .setResolution(DRIVER_RESOLUTION)
                    .setMaxRange(DRIVER_MAX_RANGE)
                    .setPower(DRIVER_POWER)
                    .setUuid(UUID.randomUUID())
                    .setDriver(this)
                    .build()

            return userSensor
        }

        override fun read(): UserSensorReading {
            val floatArray = FloatArray(1)
            floatArray[0] = device!!.humidity()
            return UserSensorReading(floatArray)
        }

    }
}