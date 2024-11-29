package com.example.obdiireader

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.amazonaws.mobileconnectors.iot.AWSIotMqttClientStatusCallback.AWSIotMqttClientStatus
import com.amazonaws.mobileconnectors.iot.AWSIotMqttManager
import com.amazonaws.mobileconnectors.iot.AWSIotMqttQos
import com.google.android.material.appbar.MaterialToolbar
import com.google.gson.Gson
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore


class MainActivity : AppCompatActivity() {

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var rpmTextView: TextView
    private lateinit var speedTextView: TextView
    private lateinit var temperatureTextView: TextView
    private lateinit var fuelLevelTextView: TextView
    private lateinit var mapTextView: TextView
    private lateinit var mafTextView: TextView
    private lateinit var throttleTextView: TextView
    private lateinit var voltageTextView: TextView
    private lateinit var engineLoadTextView: TextView
    private lateinit var fuelPressureTextView: TextView
    private lateinit var fuelTrimTextView: TextView
    private lateinit var airFuelRatioTextView: TextView
    private lateinit var egrTextView: TextView
    private lateinit var ignitionTimingTextView: TextView
    private lateinit var intakeAirTempTextView: TextView


    private var isMqttConnected = false

    // Intervalo de atualização das métricas (em milissegundos)
    private val updateInterval: Long = 2000

    private val permissionRequestCode = 100

    private val permissions: Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    } else {
        arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    // Variáveis do AWS IoT
    private val awsIotEndpoint = "" // Substitua pelo seu endpoint
    private val clientId = "TestClient" //
    private lateinit var mqttManager: AWSIotMqttManager

    // Handler para atualização das métricas
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var updateTask: Runnable

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Referenciando os TextViews
        val toolbar = findViewById<MaterialToolbar>(R.id.topAppBar)
        setSupportActionBar(toolbar);
        rpmTextView = findViewById(R.id.rpmTextView)
        speedTextView = findViewById(R.id.speedTextView)
        temperatureTextView = findViewById(R.id.temperatureTextView)
        fuelLevelTextView = findViewById(R.id.fuelLevelTextView)
        mapTextView = findViewById(R.id.mapTextView)
        mafTextView = findViewById(R.id.mafTextView)
        throttleTextView = findViewById(R.id.throttleTextView)
        voltageTextView = findViewById(R.id.voltageTextView)
        engineLoadTextView = findViewById(R.id.engineLoadTextView)
        fuelPressureTextView = findViewById(R.id.fuelPressureTextView)
        fuelTrimTextView = findViewById(R.id.fuelTrimTextView)
        airFuelRatioTextView = findViewById(R.id.airFuelRatioTextView)
        egrTextView = findViewById(R.id.egrTextView)
        ignitionTimingTextView = findViewById(R.id.ignitionTimingTextView)
        intakeAirTempTextView = findViewById(R.id.intakeAirTempTextView)

        checkPermissions()
    }

    private fun checkPermissions() {
        val permissionsNeeded = mutableListOf<String>()
        for (permission in permissions) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    permission
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsNeeded.add(permission)
            }
        }

        if (permissionsNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsNeeded.toTypedArray(),
                permissionRequestCode
            )
        } else {
            setupBluetooth()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == permissionRequestCode) {
            var allGranted = true
            for (result in grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false
                    break
                }
            }

            if (allGranted) {
                setupBluetooth()
            } else {
                Toast.makeText(this, "Permissões necessárias não concedidas", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    private fun setupBluetooth() {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothLauncher.launch(enableBtIntent)
        } else {
            listPairedDevices()
        }
    }

    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            listPairedDevices()
        } else {
            Toast.makeText(this, "Bluetooth não ativado", Toast.LENGTH_SHORT).show()
        }
    }

    private fun listPairedDevices() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "Permissão Bluetooth necessária", Toast.LENGTH_SHORT).show()
            return
        }

        val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter.bondedDevices
        val deviceList = ArrayList<String>()
        val deviceMap = HashMap<String, String>()

        pairedDevices?.forEach { device ->
            val deviceName = device.name
            val deviceAddress = device.address
            deviceList.add("$deviceName - $deviceAddress")
            deviceMap[deviceName] = deviceAddress
        }

        if (deviceList.isEmpty()) {
            Toast.makeText(
                this,
                "Nenhum dispositivo Bluetooth pareado encontrado",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val builder = AlertDialog.Builder(this)
        builder.setTitle("Selecione o dispositivo OBD-II")
        builder.setItems(deviceList.toTypedArray()) { _, which ->
            val deviceInfo = deviceList[which]
            val deviceName = deviceInfo.split(" - ")[0]
            val deviceAddress = deviceMap[deviceName]

            connectToDevice(deviceAddress!!)
        }
        builder.show()
    }

    private fun connectToDevice(deviceAddress: String) {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(
                this,
                "Permissão para conectar ao Bluetooth necessária",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val device: BluetoothDevice = bluetoothAdapter.getRemoteDevice(deviceAddress)
        Thread {
            try {
                val uuid = device.uuids[0].uuid
                val socket: BluetoothSocket = device.createRfcommSocketToServiceRecord(uuid)
                bluetoothAdapter.cancelDiscovery()
                socket.connect()

                runOnUiThread {
                    Toast.makeText(this, "Conectado ao dispositivo OBD-II", Toast.LENGTH_SHORT)
                        .show()
                }

                if (socket.isConnected) {
                    manageOBDConnection(socket)
                } else {
                    Log.d("OBD", "Erro: Falha ao estabelecer conexão com o OBD-II.")
                }

            } catch (e: IOException) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this, "Falha ao conectar: ${e.message}", Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }.start()
    }

    private fun manageOBDConnection(socket: BluetoothSocket) {
        val inputStream = socket.inputStream
        val outputStream = socket.outputStream

        // Inicializar o MQTT
        initializeMQTTClient()

        // Inicializar o dispositivo OBD-II
        initializeOBD(outputStream, inputStream)

        updateTask = object : Runnable {
            override fun run() {
                try {
                    // Coletar todas as métricas
                    collectAllSupportedMetrics(outputStream, inputStream)
                } catch (e: IOException) {
                    e.printStackTrace()
                    runOnUiThread {
                        Toast.makeText(
                            this@MainActivity,
                            "Erro na comunicação OBD: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                // Agendar a próxima execução
                handler.postDelayed(this, updateInterval)
            }
        }

        // Iniciar a coleta de métricas
        handler.post(updateTask)
    }

    private fun initializeOBD(outputStream: OutputStream, inputStream: InputStream) {
        sendOBDCommand("ATZ", outputStream, inputStream)   // Resetar o dispositivo
        Thread.sleep(1000)
        sendOBDCommand("ATE0", outputStream, inputStream)  // Desativar eco
        sendOBDCommand("ATL0", outputStream, inputStream)  // Desativar feeds de linha
        sendOBDCommand("ATS0", outputStream, inputStream)  // Desativar espaços
        sendOBDCommand("ATH0", outputStream, inputStream)  // Desativar cabeçalhos
        sendOBDCommand("ATSP0", outputStream, inputStream) // Definir protocolo para automático
    }

    private fun initializeMQTTClient() {
        mqttManager = AWSIotMqttManager(clientId, awsIotEndpoint)
        mqttManager.isAutoReconnect = true
        mqttManager.keepAlive = 10
        mqttManager.setOfflinePublishQueueEnabled(false) // Desativa a fila offline
        mqttManager.setAutoReconnect(false) // Desativa a reconexão automática

        try {
            val keyStorePassword = "obdnickolas".toCharArray() // Sua senha do KeyStore

            // Carregar o KeyStore do recurso raw
            val keyStore = KeyStore.getInstance("BKS")
            resources.openRawResource(R.raw.client_keystore).use { inputStream ->
                keyStore.load(inputStream, keyStorePassword)
            }

            Log.d("MQTT", "KeyStore carregado com sucesso")

            // Conectar ao AWS IoT
            mqttManager.connect(keyStore) { status, throwable ->
                runOnUiThread {
                    if (throwable != null) {
                        Log.e("MQTT", "Erro de conexão: ", throwable)
                    }
                    when (status) {
                        AWSIotMqttClientStatus.Connecting -> {
                            isMqttConnected = false
                            Toast.makeText(
                                this@MainActivity,
                                "Conectando ao AWS IoT",
                                Toast.LENGTH_SHORT
                            ).show()
                            Log.d("MQTT", "Conectando ao AWS IoT")
                        }

                        AWSIotMqttClientStatus.Connected -> {
                            isMqttConnected = true
                            Toast.makeText(
                                this@MainActivity,
                                "Conectado ao AWS IoT",
                                Toast.LENGTH_SHORT
                            ).show()
                            Log.d("MQTT", "Conectado ao AWS IoT")
                        }

                        AWSIotMqttClientStatus.Reconnecting -> {
                            isMqttConnected = false
                            Toast.makeText(
                                this@MainActivity,
                                "Reconectando ao AWS IoT",
                                Toast.LENGTH_SHORT
                            ).show()
                            Log.d("MQTT", "Reconectando ao AWS IoT")
                        }

                        AWSIotMqttClientStatus.ConnectionLost -> {
                            isMqttConnected = false
                            Toast.makeText(
                                this@MainActivity,
                                "Conexão perdida com o AWS IoT",
                                Toast.LENGTH_SHORT
                            ).show()
                            Log.e("MQTT", "Conexão perdida com o AWS IoT")
                        }

                        else -> {
                            isMqttConnected = false
                            Toast.makeText(
                                this@MainActivity,
                                "Status desconhecido: $status",
                                Toast.LENGTH_SHORT
                            ).show()
                            Log.e("MQTT", "Status desconhecido: $status")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("MQTT", "Erro ao inicializar o cliente MQTT", e)
            runOnUiThread {
                Toast.makeText(
                    this,
                    "Erro ao inicializar o cliente MQTT: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun collectAllSupportedMetrics(outputStream: OutputStream, inputStream: InputStream) {
        val rpm = readRPM(outputStream, inputStream)
        val speed = readSpeed(outputStream, inputStream)
        val temperature = readEngineTemp(outputStream, inputStream)
        val fuelLevel = readFuelLevel(outputStream, inputStream)
        val map = readMap(outputStream, inputStream)
        val maf = readMaf(outputStream, inputStream)
        val throttle = readThrottlePosition(outputStream, inputStream)
        val voltage = readVoltage(outputStream, inputStream)
        val engineLoad = readEngineLoad(outputStream, inputStream)
        val fuelPressure = readFuelPressure(outputStream, inputStream)
        val fuelTrim = readFuelTrim(outputStream, inputStream)
        val airFuelRatio = readAirFuelRatio(outputStream, inputStream)
        val egr = readEGR(outputStream, inputStream)
        val ignitionTiming = readIgnitionTiming(outputStream, inputStream)
        val intakeAirTemp = readIntakeAirTemp(outputStream, inputStream)

        runOnUiThread {
            rpmTextView.text = "RPM: $rpm"
            speedTextView.text = "Velocidade: $speed km/h"
            temperatureTextView.text = "Temp. Motor: $temperature °C"
            fuelLevelTextView.text = "Nível Combustível: $fuelLevel%"
            mapTextView.text = "MAP: $map kPa"
            mafTextView.text = "MAF: $maf g/s"
            throttleTextView.text = "Posição Acelerador: $throttle%"
            voltageTextView.text = "Tensão: $voltage V"
            engineLoadTextView.text = "Carga Motor: $engineLoad%"
            fuelPressureTextView.text = "Pressão Combustível: $fuelPressure kPa"
            fuelTrimTextView.text = "Fuel Trim: $fuelTrim%"
            airFuelRatioTextView.text = "Relação A/F: $airFuelRatio"
            egrTextView.text = "EGR: $egr%"
            ignitionTimingTextView.text = "Avanço Ignição: $ignitionTiming°"
            intakeAirTempTextView.text = "Temp. Ar Admissão: $intakeAirTemp °C"
        }

        // Criar um objeto com as métricas
        val metrics = mapOf(
            "rpm" to rpm,
            "speed" to speed,
            "temperature" to temperature,
            "fuelLevel" to fuelLevel,
            "map" to map,
            "maf" to maf,
            "throttle" to throttle,
            "voltage" to voltage,
            "engineLoad" to engineLoad,
            "fuelPressure" to fuelPressure,
            "fuelTrim" to fuelTrim,
            "airFuelRatio" to airFuelRatio,
            "egr" to egr,
            "ignitionTiming" to ignitionTiming,
            "intakeAirTemp" to intakeAirTemp
        )

        // Converter o objeto para JSON
        val gson = Gson()
        val metricsJson = gson.toJson(metrics)

        // Publicar os dados no tópico MQTT
        publishMetrics(metricsJson)
    }

    private fun publishMetrics(metricsJson: String) {
        try {
            val topic = "obd/vehicle/metrics" // Substitua pelo seu tópico

            if (isMqttConnected) {
                mqttManager.publishString(metricsJson, topic, AWSIotMqttQos.QOS0)
                Log.d("MQTT", "Métricas publicadas no tópico $topic: $metricsJson")
            } else {
                Toast.makeText(this, "Cliente MQTT não está conectado", Toast.LENGTH_SHORT).show()
                Log.w("MQTT", "Tentativa de publicar sem conexão MQTT")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("MQTT", "Erro ao publicar métricas", e)
            runOnUiThread {
                Toast.makeText(
                    this,
                    "Erro ao publicar métricas: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // Funções de leitura de cada métrica
    private fun readRPM(outputStream: OutputStream, inputStream: InputStream): Int {
        val response = sendOBDCommand("01 0C", outputStream, inputStream)
        return parseRPM(response)
    }

    private fun parseRPM(response: String): Int {
        val lines = response.split("\r", "\n")
        for (line in lines) {
            val cleanLine = line.replace("\\s".toRegex(), "")
            if (cleanLine.startsWith("410C")) {
                val hexResult = cleanLine.substring(4, 8)
                val rpmValue = Integer.parseInt(hexResult, 16) / 4
                return rpmValue
            }
        }
        return -1
    }

    private fun readSpeed(outputStream: OutputStream, inputStream: InputStream): Int {
        val response = sendOBDCommand("01 0D", outputStream, inputStream)
        return parseSpeed(response)
    }

    private fun parseSpeed(response: String): Int {
        val lines = response.split("\r", "\n")
        for (line in lines) {
            val cleanLine = line.replace("\\s".toRegex(), "")
            if (cleanLine.startsWith("410D")) {
                val hexResult = cleanLine.substring(4, 6)
                return Integer.parseInt(hexResult, 16)
            }
        }
        return -1
    }

    private fun readEngineTemp(outputStream: OutputStream, inputStream: InputStream): Int {
        val response = sendOBDCommand("01 05", outputStream, inputStream)
        return parseEngineTemp(response)
    }

    private fun parseEngineTemp(response: String): Int {
        val lines = response.split("\r", "\n")
        for (line in lines) {
            val cleanLine = line.replace("\\s".toRegex(), "")
            if (cleanLine.startsWith("4105")) {
                val hexResult = cleanLine.substring(4, 6)
                return Integer.parseInt(hexResult, 16) - 40
            }
        }
        return -1000
    }

    private fun readFuelLevel(outputStream: OutputStream, inputStream: InputStream): Int {
        val response = sendOBDCommand("01 2F", outputStream, inputStream)
        return parseFuelLevel(response)
    }

    private fun parseFuelLevel(response: String): Int {
        val lines = response.split("\r", "\n")
        for (line in lines) {
            val cleanLine = line.replace("\\s".toRegex(), "")
            if (cleanLine.startsWith("412F")) {
                val hexResult = cleanLine.substring(4, 6)
                return Integer.parseInt(hexResult, 16) * 100 / 255
            }
        }
        return -1
    }

    // Funções para ler e interpretar as métricas avançadas

    // Ler Pressão Absoluta do Coletor de Admissão (MAP) - PID: 01 0B
    private fun readMap(outputStream: OutputStream, inputStream: InputStream): Int {
        val response = sendOBDCommand("01 0B", outputStream, inputStream)
        return parseMap(response)
    }

    private fun parseMap(response: String): Int {
        val lines = response.split("\r", "\n")
        for (line in lines) {
            val cleanLine = line.replace("\\s".toRegex(), "")
            if (cleanLine.startsWith("410B")) {
                val hexResult = cleanLine.substring(4, 6)
                return Integer.parseInt(hexResult, 16)
            }
        }
        return -1
    }

    // Ler Fluxo de Ar Massivo (MAF) - PID: 01 10
    private fun readMaf(outputStream: OutputStream, inputStream: InputStream): Double {
        val response = sendOBDCommand("01 10", outputStream, inputStream)
        return parseMaf(response)
    }

    private fun parseMaf(response: String): Double {
        val lines = response.split("\r", "\n")
        for (line in lines) {
            val cleanLine = line.replace("\\s".toRegex(), "")
            if (cleanLine.startsWith("4110")) {
                val hexResult = cleanLine.substring(4, 8)
                val value = Integer.parseInt(hexResult, 16)
                return value / 100.0
            }
        }
        return -1.0
    }

    // Ler Posição do Acelerador - PID: 01 11
    private fun readThrottlePosition(outputStream: OutputStream, inputStream: InputStream): Int {
        val response = sendOBDCommand("01 11", outputStream, inputStream)
        return parseThrottlePosition(response)
    }

    private fun parseThrottlePosition(response: String): Int {
        val lines = response.split("\r", "\n")
        for (line in lines) {
            val cleanLine = line.replace("\\s".toRegex(), "")
            if (cleanLine.startsWith("4111")) {
                val hexResult = cleanLine.substring(4, 6)
                return Integer.parseInt(hexResult, 16) * 100 / 255
            }
        }
        return -1
    }

    // Ler Tensão do Sistema de Carga - PID: 01 42
    private fun readVoltage(outputStream: OutputStream, inputStream: InputStream): Double {
        val response = sendOBDCommand("01 42", outputStream, inputStream)
        return parseVoltage(response)
    }

    private fun parseVoltage(response: String): Double {
        val lines = response.split("\r", "\n")
        for (line in lines) {
            val cleanLine = line.replace("\\s".toRegex(), "")
            if (cleanLine.startsWith("4142")) {
                val hexResult = cleanLine.substring(4, 8)
                val value = Integer.parseInt(hexResult, 16)
                return value / 1000.0
            }
        }
        return -1.0
    }

    // Funções para ler as novas métricas

    // Ler Carga do Motor - PID: 01 04
    private fun readEngineLoad(outputStream: OutputStream, inputStream: InputStream): Int {
        val response = sendOBDCommand("01 04", outputStream, inputStream)
        return parseEngineLoad(response)
    }

    private fun parseEngineLoad(response: String): Int {
        val lines = response.split("\r", "\n")
        for (line in lines) {
            val cleanLine = line.replace("\\s".toRegex(), "")
            if (cleanLine.startsWith("4104")) {
                val hexResult = cleanLine.substring(4, 6)
                return Integer.parseInt(hexResult, 16) * 100 / 255
            }
        }
        return -1
    }

    // Ler Fuel Trim (ajuste de combustível) - PID: 01 06
    private fun readFuelTrim(outputStream: OutputStream, inputStream: InputStream): Double {
        val response = sendOBDCommand("01 06", outputStream, inputStream)
        return parseFuelTrim(response)
    }

    private fun parseFuelTrim(response: String): Double {
        val lines = response.split("\r", "\n")
        for (line in lines) {
            val cleanLine = line.replace("\\s".toRegex(), "")
            if (cleanLine.startsWith("4106")) {
                val hexResult = cleanLine.substring(4, 6)
                val a = Integer.parseInt(hexResult, 16)
                return ((a - 128) * 100) / 128.0
            }
        }
        return -1.0
    }

    // Ler Pressão de Combustível - PID: 01 0A
    private fun readFuelPressure(outputStream: OutputStream, inputStream: InputStream): Int {
        val response = sendOBDCommand("01 0A", outputStream, inputStream)
        return parseFuelPressure(response)
    }

    private fun parseFuelPressure(response: String): Int {
        val lines = response.split("\r", "\n")
        for (line in lines) {
            val cleanLine = line.replace("\\s".toRegex(), "")
            if (cleanLine.startsWith("410A")) {
                val hexResult = cleanLine.substring(4, 6)
                return Integer.parseInt(hexResult, 16) * 3
            }
        }
        return -1
    }

    // Ler Temperatura do Ar de Admissão - PID: 01 0F
    private fun readIntakeAirTemp(outputStream: OutputStream, inputStream: InputStream): Int {
        val response = sendOBDCommand("01 0F", outputStream, inputStream)
        return parseIntakeAirTemp(response)
    }

    private fun parseIntakeAirTemp(response: String): Int {
        val lines = response.split("\r", "\n")
        for (line in lines) {
            val cleanLine = line.replace("\\s".toRegex(), "")
            if (cleanLine.startsWith("410F")) {
                val hexResult = cleanLine.substring(4, 6)
                return Integer.parseInt(hexResult, 16) - 40
            }
        }
        return -1000
    }

    // Ler Relação Ar-Combustível - PID: 01 44
    private fun readAirFuelRatio(outputStream: OutputStream, inputStream: InputStream): Double {
        val response = sendOBDCommand("01 44", outputStream, inputStream)
        return parseAirFuelRatio(response)
    }

    private fun parseAirFuelRatio(response: String): Double {
        val lines = response.split("\r", "\n")
        for (line in lines) {
            val cleanLine = line.replace("\\s".toRegex(), "")
            if (cleanLine.startsWith("4144")) {
                val hexResult = cleanLine.substring(4, 8)
                val value = Integer.parseInt(hexResult, 16)
                return value / 32768.0
            }
        }
        return -1.0
    }

    // Ler Sistema EGR - PID: 01 2E
    private fun readEGR(outputStream: OutputStream, inputStream: InputStream): Int {
        val response = sendOBDCommand("01 2E", outputStream, inputStream)
        return parseEGR(response)
    }

    private fun parseEGR(response: String): Int {
        val lines = response.split("\r", "\n")
        for (line in lines) {
            val cleanLine = line.replace("\\s".toRegex(), "")
            if (cleanLine.startsWith("412E")) {
                val hexResult = cleanLine.substring(4, 6)
                return Integer.parseInt(hexResult, 16) * 100 / 255
            }
        }
        return -1
    }

    // Ler Avanço de Ignição - PID: 01 0E
    private fun readIgnitionTiming(outputStream: OutputStream, inputStream: InputStream): Double {
        val response = sendOBDCommand("01 0E", outputStream, inputStream)
        return parseIgnitionTiming(response)
    }

    private fun parseIgnitionTiming(response: String): Double {
        val lines = response.split("\r", "\n")
        for (line in lines) {
            val cleanLine = line.replace("\\s".toRegex(), "")
            if (cleanLine.startsWith("410E")) {
                val hexResult = cleanLine.substring(4, 6)
                val a = Integer.parseInt(hexResult, 16)
                return (a / 2.0) - 64.0
            }
        }
        return -1.0
    }

    private fun sendOBDCommand(
        command: String,
        outputStream: OutputStream,
        inputStream: InputStream
    ): String {
        try {
            val cmdWithTerminator = "$command\r"
            outputStream.write(cmdWithTerminator.toByteArray())
            outputStream.flush()

            val buffer = StringBuilder()
            val byteBuffer = ByteArray(1024)
            var bytesRead: Int
            val startTime = System.currentTimeMillis()

            // Read until '>' character is found or timeout
            while (true) {
                if (inputStream.available() > 0) {
                    bytesRead = inputStream.read(byteBuffer)
                    if (bytesRead > 0) {
                        val response = String(byteBuffer, 0, bytesRead)
                        buffer.append(response)
                        if (response.contains('>')) {
                            break
                        }
                    }
                }
                // Timeout após 2 segundos
                if (System.currentTimeMillis() - startTime > 2000) {
                    break
                }
            }
            return buffer.toString().trim()
        } catch (e: IOException) {
            e.printStackTrace()
            return "Erro ao enviar comando"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cancelar tarefas pendentes
        handler.removeCallbacks(updateTask)
        // Desconectar do MQTT
        mqttManager.disconnect()
    }
}
