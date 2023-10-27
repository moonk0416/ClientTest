package com.example.clienttest

import androidx.appcompat.app.AppCompatActivity
import android.view.View
import android.widget.Button
import android.widget.GridLayout
import android.widget.TextView
import java.net.Socket
import android.os.Handler
import android.os.Looper
import android.os.Bundle as AndroidBundle
import android.content.Context
import android.net.wifi.WifiManager
import android.widget.EditText
import android.text.Editable
import android.text.TextWatcher
import android.widget.LinearLayout
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import androidx.lifecycle.Observer
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.*

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.OutputStream


class DAQHeadPacket {
    val sof: Byte
    val uniqueId: ByteArray
    var cmd: Byte

    constructor() {
        sof = SOF_PACKET
        uniqueId = UNIQUE_ID.toByteArray(Charsets.UTF_8).copyOf(4)
        cmd = TCP_CMD_IDX.CMD_DISCONNECTION.value.toByte()
    }

    companion object {
        private const val SOF_PACKET: Byte = 0xFF.toByte()
        private const val UNIQUE_ID: String = "DAQ"
    }
}
enum class TCP_CMD_IDX(var value: Int) {
    CMD_CONNECTION(1),
    CMD_DISCONNECTION(2),
    CMD_VEHICLE_INFO_REQUEST(3),
    CMD_TURRET_INFO_REQUEST(4)
}

fun sendPacket(outputStream: OutputStream, packet: DAQHeadPacket) {
    try {
        val dataOutputStream = DataOutputStream(outputStream)
        dataOutputStream.write(packet.sof.toInt())
        dataOutputStream.write(packet.uniqueId)
        dataOutputStream.write(packet.cmd.toInt())
        dataOutputStream.flush()
        println("Data sent successfully.")
    } catch (e: IOException) {
        println("Error sending data: ${e.message}")
    }
}



class MainActivity : AppCompatActivity() {

    private lateinit var textView: TextView
    private lateinit var gridLayout: GridLayout //수신된 데이터 출력
    private var clientThread: ClientThread? = null
    private lateinit var currentSSID: TextView

    private lateinit var ipAddress: EditText
    private lateinit var portNum: EditText

    private lateinit var btnConnect: Button
    private lateinit var btnDisconnect: Button

    //넘어오는 데이터 이름(?)
    private var stringArrayBody = arrayOf<String>("시스템전압","엔진회전수","변속기어","차량속도","연료량","냉각수온도","가속폐달","제동폐달","주차상태","조종수핸들","현수모드","궤도장력","시스템압력")
    private var stringArrayTurret = arrayOf<String>("포탑전원","포탑방위각","포탑고저각","포탑선회속도","AP탄 재고","HEAT(지상) 재고","HEAT(대공) 재고","전차주행방향","주포고정","폐쇄기 상태","사격준비","차장(표적사거리)","포수(표적사거리)")

    override fun onCreate(savedInstanceState: AndroidBundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textView = findViewById(R.id.text_connection)
        gridLayout = findViewById(R.id.gridLayout)
        currentSSID = findViewById(R.id.currentSSID)

        //IP, PORT
        ipAddress = findViewById(R.id.editIP)
        portNum = findViewById(R.id.editPORT)

        //Button
        btnConnect = findViewById<Button>(R.id.btn_connect)
        btnDisconnect = findViewById<Button>(R.id.btn_disconnect)

        //IP, PORT 입력 전 버튼 비활성화
        btnConnect.isEnabled = false
        btnDisconnect.isEnabled = false

        //Wifi SSID
        val networkConnection = NetworkConnection(applicationContext)
        networkConnection.observe(this, Observer { isConnected ->
            val wifiSSID = getWifiSSID()
            if (isConnected) {currentSSID.text = wifiSSID}
            else {currentSSID.text = wifiSSID}
        })

        //데이터 수신 전 목록 UI
        setUI()
        
        //입력 시 확인 버튼 활성화
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                // ipAddress와 portNum 모두 텍스트가 있을 때 버튼 활성화
                btnConnect.isEnabled = ipAddress.text.isNotEmpty() && portNum.text.isNotEmpty()
                btnDisconnect.isEnabled = ipAddress.text.isNotEmpty() && portNum.text.isNotEmpty()
            }
        }
        ipAddress.addTextChangedListener(textWatcher)
        portNum.addTextChangedListener(textWatcher)

        // btnConnect 클릭시 IP와 Port 번호로 연결 시도
        btnConnect.setOnClickListener {
            textView.text = "Connecting"
            ipAddress.isEnabled = false
            portNum.isEnabled = false
            try {
                val ip = ipAddress.text.toString()
                val port = portNum.text.toString().toInt()
                clientThread = ClientThread(ip, port)
            } catch (e: NumberFormatException) { }
            clientThread!!.start()
            btnConnect.isEnabled = false
            btnDisconnect.isEnabled = true
            textView.visibility = View.VISIBLE

        }
        btnDisconnect.setOnClickListener {
            textView.text = "Disconnected"
            clientThread?.disconnect()
            textView.visibility = View.VISIBLE
            ipAddress.isEnabled = true
            portNum.isEnabled = true
            btnConnect.isEnabled = true
        }
    }

    // 생명주기 설정
    override fun onPause() {
        super.onPause()
        val isRunning = clientThread?.running ?: false
        //백그라운드 넘어가기 전 running상태 저장
        saveState(ipAddress.text.toString(), portNum.text.toString(), isRunning)
        clientThread?.disconnect()
    }

    override fun onRestart() {
        super.onRestart()
        restoreState()
    }

    private fun saveState(ip: String, port: String, isRunning: Boolean) {
        val sharedPref = getSharedPreferences("AppPrefs", MODE_PRIVATE)
        val editor = sharedPref.edit()
        //포그라운드로 돌아올 때 IP, PORT 돌려놓기 위해 저장
        editor.putString("IP_ADDRESS", ip)
        editor.putString("PORT_NUMBER", port)
        editor.putBoolean("IS_RUNNING", isRunning)
        editor.apply()
    }

    private fun restoreState() {
        val sharedPref = getSharedPreferences("AppPrefs", MODE_PRIVATE)
        val savedIpAddress = sharedPref.getString("IP_ADDRESS", "")
        val savedPortNum = sharedPref.getString("PORT_NUMBER", "")
        val isRunning = sharedPref.getBoolean("IS_RUNNING", false)
        //포그라운드로 돌아왔을 때 IP, PORT 불러오기
        ipAddress.setText(savedIpAddress)
        portNum.setText(savedPortNum)
        //백그라운드로 가기 전 running 상태에 따라 버튼 상태 설정 및 running == true 라면 서버 재연결
        if (isRunning) {
            btnConnect.isEnabled = false
            btnDisconnect.isEnabled = true
            clientThread = ClientThread(ipAddress.text.toString(), portNum.text.toString().toInt())
            clientThread!!.start()
        } else {
            btnConnect.isEnabled = true
            btnDisconnect.isEnabled = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        val sharedPref = getSharedPreferences("AppPrefs", MODE_PRIVATE)
        val editor = sharedPref.edit()
        //앱 종료시 스레드 종료 및 저장된 IP, PORT 파기
        clientThread?.disconnect()
        editor.remove("IP_ADDRESS")
        editor.remove("PORT_NUMBER")
        editor.apply()
    }

    inner class ClientThread(ip: String, port: Int) : Thread() {
        private val serverIp: String = ip
        private val serverPort: Int = port
        private var socket: Socket? = null
        private val func = func()
        @Volatile
        var running = true

        private var packet = DAQHeadPacket()
        private lateinit var outputStream: DataOutputStream

        override fun run() {
            try {
                socket = Socket()
                socket!!.connect(InetSocketAddress(serverIp, serverPort), 5000)

                val handler = Handler(Looper.getMainLooper())
                val inputStream = DataInputStream(socket!!.getInputStream())
                outputStream = DataOutputStream(socket!!.getOutputStream())

                // 연결, 데이터 요청
//                var packet = DAQHeadPacket()
                packet.cmd = TCP_CMD_IDX.CMD_CONNECTION.value.toByte()
                sendPacket(outputStream, packet)
                GlobalScope.launch { delay(3000) }
                packet.cmd = TCP_CMD_IDX.CMD_VEHICLE_INFO_REQUEST.value.toByte()
                sendPacket(outputStream, packet)
                GlobalScope.launch { delay(2000) }
                packet.cmd = TCP_CMD_IDX.CMD_TURRET_INFO_REQUEST.value.toByte()
                sendPacket(outputStream, packet)

                handler.post {
                    textView.text = "Connected"
                }

                //버퍼 크기 설정, 데이터 출력 ARRAY
                val buffer = ByteArray(4 * 13)
                var bodyDataArray: Array<String>? = null
                var turretDataArray: Array<String>? = null

                while (running) {
                    // 데이터 담기
                    val header = inputStream.read().toByte()
                    //LITTLE_ENDIAN으로 설정 (획득장치 LITTLE_ENDIAN?)
                    val byteBuffer = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN)

                    var body: bodyData? = null
                    var turret: turretData? = null

                    // header로 body, turret 판별
                    when (header) {
                        1.toByte() -> {
                            inputStream.read(buffer)
                            body = bodyData(
                                a = byteBuffer.int,
                                b = byteBuffer.int,
                                c = byteBuffer.int.toUInt(),
                                d = byteBuffer.int,
                                e = byteBuffer.int,
                                f = byteBuffer.int,
                                g = byteBuffer.int,
                                h = byteBuffer.int,
                                i = byteBuffer.int,
                                j = byteBuffer.int,
                                k = byteBuffer.int.toUInt(),
                                l = byteBuffer.int.toUInt(),
                                m = byteBuffer.int,
                            )
//                            println("body.c data : ${body.c}")
//                            println("body.k data : ${body.k}")

                            var a = body.a.toFloat() / 1000
                            var b = body.b.toFloat() / 1000
                            var c = func.calC(body)
                            var d = body.d.toFloat() / 1000
                            var e = body.e.toFloat() / 1000
                            var f = body.f.toFloat() / 1000
                            var g = body.g.toFloat() / 1000
                            var h = body.h.toFloat() / 1000
                            var i = func.calI(body)
                            var j = body.j.toFloat() / 1000
                            var k = func.calK(body)
                            println("k data : ${body.k.toString(16)}")

                            var l = func.calL(body)
                            var m = body.m.toFloat() / 1000

                            bodyDataArray = arrayOf(
                                a.toString(),
                                b.toString(),
                                c.toString(),
                                d.toString(),
                                e.toString(),
                                f.toString(),
                                g.toString(),
                                h.toString(),
                                i.toString(),
                                j.toString(),
                                k.toString(),
                                l.toString(),
                                m.toString())
                            println("Array for Data1: ${bodyDataArray!!.joinToString(", ")}")
                        }

                        2.toByte() -> {
                            inputStream.read(buffer)
                            turret = turretData(
                                n = byteBuffer.int,
                                o = byteBuffer.int,
                                p = byteBuffer.int,
                                q = byteBuffer.int,
                                r = byteBuffer.int,
                                s = byteBuffer.int,
                                t = byteBuffer.int,
                                u = byteBuffer.int,
                                v = byteBuffer.int,
                                w = byteBuffer.int,
                                x = byteBuffer.int,
                                y = byteBuffer.int,
                                z = byteBuffer.int,
                            )

                            var n = func.calN(turret)
                            var o = turret.o.toFloat() / 1000
                            var p = turret.p.toFloat() / 1000
                            var q = turret.q.toFloat() / 1000
                            var r = turret.r.toFloat() / 1000
                            var s = turret.s.toFloat() / 1000
                            var v = func.calV(turret)
                            var w = func.calW(turret)
                            var x = func.calX(turret)


                            turretDataArray = arrayOf(
                                n.toString(),
                                o.toString(),
                                p.toString(),
                                q.toString(),
                                r.toString(),
                                s.toString(),
                                turret.t.toString(),
                                turret.u.toString(),
                                v.toString(),
                                w.toString(),
                                x.toString(),
                                turret.y.toString(),
                                turret.z.toString())
                            println("Array for Data2: ${turretDataArray.joinToString(", ")}")
                        }
                    }

                    // body, turret 둘 다 채워졌을 때 출력
                    if(bodyDataArray != null && turretDataArray != null) {
                        handler.post {
                            gridLayout.removeAllViews()
                            val rowCount = 13
                            val columnCount = 4

                            gridLayout.rowCount = rowCount
                            gridLayout.columnCount = columnCount

                            for (i in 0 until gridLayout.rowCount) {
                                for (j in 0 until gridLayout.columnCount) {
                                    val dataPrint = TextView(this@MainActivity)
                                    setGridLayout(i, j, dataPrint)
                                    if (j == 1 || j == 3) { dataPrint.textAlignment = TextView.TEXT_ALIGNMENT_VIEW_END }
                                    else { dataPrint.textAlignment = TextView.TEXT_ALIGNMENT_CENTER}
                                    when (j) {
                                        0 -> if (i < stringArrayBody.size) dataPrint.text = stringArrayBody[i]
                                        1 -> if (i < bodyDataArray!!.size) dataPrint.text = bodyDataArray[i]
                                        2 -> if (i < stringArrayTurret.size) dataPrint.text = stringArrayTurret[i]
                                        3 -> if (i < turretDataArray!!.size) dataPrint.text = turretDataArray[i]
                                    }
                                    gridLayout.addView(dataPrint)
                                }
                            }
                        }
                    }
                }
            } catch (e: SocketTimeoutException) {
                runOnUiThread {
                    textView.text = "Connection Failed"
                    btnException()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    textView.text = "Disconnected"
                    disconnect()
                    btnException()
                }
            } finally {
                disconnect()
            }
        }
        fun disconnect() {
            running = false
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    packet.cmd = TCP_CMD_IDX.CMD_DISCONNECTION.value.toByte()
                    sendPacket(outputStream, packet)
                } catch (e: IOException) {
                    e.printStackTrace()
                } finally {
                    socket?.close()
                }
            }
        }
    }

    private fun btnException() {
        val ipAddress = findViewById<EditText>(R.id.editIP)
        val portNum = findViewById<EditText>(R.id.editPORT)
        val btnDisconnect = findViewById<Button>(R.id.btn_disconnect)
        val btnConnect = findViewById<Button>(R.id.btn_connect)
        ipAddress.isEnabled = true
        portNum.isEnabled = true
        btnDisconnect.isEnabled = false
        btnConnect.isEnabled = true
    }

    private fun clearGridLayout() {
        gridLayout.removeAllViews()
    }

    private fun getWifiSSID(): String? {
        val wifiManager = getSystemService(Context.WIFI_SERVICE) as WifiManager
        val wifiInfo = wifiManager.connectionInfo
        return wifiInfo.ssid.replace("\"", "")
    }

    //초기화면
    private fun setUI() {
        clearGridLayout()
        val rowCount = 13
        val columnCount = 4

        gridLayout.rowCount = rowCount
        gridLayout.columnCount = columnCount

        for (i in 0 until gridLayout.rowCount) {
            for (j in 0 until gridLayout.columnCount) {
                val dataPrint = TextView(this)
                setGridLayout(i, j, dataPrint)
                if (j == 1 || j == 3) {
                    dataPrint.textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                }
                else {
                    dataPrint.textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                }
                when (j) {
                    0 -> if (i < stringArrayBody.size) dataPrint.text = stringArrayBody[i]
                    1 -> dataPrint.text = "-"
                    2 -> if (i < stringArrayTurret.size) dataPrint.text = stringArrayTurret[i]
                    3 -> dataPrint.text = "-"
                }
                gridLayout.addView(dataPrint)
            }
        }
    }
    private fun setGridLayout(i: Int, j: Int, dataPrint: TextView) {
        val params = GridLayout.LayoutParams(GridLayout.spec(i), GridLayout.spec(j))

        val columnWidthInDp = when (j) {
            0 -> 70
            1 -> 80
            2 -> 100
            3 -> 65
            else -> 0
        }
        //마진
        val columnWidthInPixels = (columnWidthInDp * resources.displayMetrics.density + 0.5f).toInt()
        params.width = columnWidthInPixels

        val marginInDp = 4
        val marginInPixels = (marginInDp * resources.displayMetrics.density + 0.5f).toInt()
        params.setMargins(marginInPixels, marginInPixels, marginInPixels, marginInPixels)

        dataPrint.layoutParams = params
        dataPrint.textSize = 15f
    }

}
