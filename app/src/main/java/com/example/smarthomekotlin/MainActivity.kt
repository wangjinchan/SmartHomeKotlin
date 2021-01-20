package com.example.smarthomekotlin

import android.annotation.SuppressLint
import android.os.*
import androidx.appcompat.app.AppCompatActivity
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket

/**
 * 建立socket连接实现tcp/ip协议的方式：
 * 1.创建Socket（安卓作为客户端，所以是client，单片机作为server端）
 *
 * 2.打开连接到Socket的输入/输出流
 *
 * 3.按照协议对Socket进行读/写操作
 *
 * 4.关闭输入输出流、关闭Socket
 *
 */
class MainActivity : AppCompatActivity() {
    private var startButton: Button? = null//连接按钮
    private var ipText: EditText? = null//ip地址输入
    private var isConnecting = false//判断是否连接
    private var mThreadClient: Thread? = null//子线程
    private var mSocketClient: Socket? = null//socket实现tcp、ip协议，实现tcp server和tcp client的连接
    private var res = ""//接收的数据
    private var warningShow: TextView? = null
    private var temp: TextView? = null
    private var mq: TextView? = null//警告语  温湿度  气体浓度
    private val sendOrder = arrayOf("1\n", "2\n", "3\n", "4\n")//发送的指令 1开启通风  2 关闭通风 3开启抽湿 4关闭抽湿

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)//该activity绑定的xml界面是activity_main.xml
        setContentView(R.layout.activity_main)//该activity绑定的xml界面是activity_main.xml
        strictMode()//严苛模式
        initView()//初始化显示的功能组件
    }

    //开启子线程
    private val mRunnable = Runnable {
        val msgText = ipText!!.text.toString()
        if (msgText.isEmpty())
        //IP和端口号不能为空
        {
            val msg = Message()
            msg.what = 5
            mHandler.sendMessage(msg)
            return@Runnable
        }
        val start = msgText.indexOf(":")//IP和端口号格式不正确
        if (start == -1 || start + 1 >= msgText.length) {
            val msg = Message()
            msg.what = 6
            mHandler.sendMessage(msg)
            return@Runnable
        }
        val sIP = msgText.substring(0, start)
        val sPort = msgText.substring(start + 1)
        val port = Integer.parseInt(sPort)

        val mBufferedReaderClient: BufferedReader//从字符输入流中读取文本并缓冲字符，以便有效地读取字符，数组和行
        try {
            //连接服务器
            mSocketClient = Socket()//创建Socket
            val socAddress = InetSocketAddress(sIP, port)//设置ip地址和端口号
            mSocketClient!!.connect(socAddress, 2000)//设置超时时间为2秒

            //取得输入、输出流
            mBufferedReaderClient = BufferedReader(InputStreamReader(mSocketClient!!.getInputStream()))
            mPrintWriterClient = PrintWriter(mSocketClient!!.getOutputStream(), true)

            //连接成功,把这个好消息告诉主线程，配合主线程进行更新UI。
            val msg = Message()
            msg.what = 1
            mHandler.sendMessage(msg)

        } catch (e: Exception) {
            //如果连接不成功，也要把这个消息告诉主线程，配合主线程进行更新UI。
            val msg = Message()
            msg.what = 2
            mHandler.sendMessage(msg)
            return@Runnable
        }

        val buffer = CharArray(256)
        var count: Int

        while (true) {
            try {
                count=( mBufferedReaderClient.read(buffer))
                if (count> 0)
                //当读取服务器发来的数据时
                {

                    res = getInfoBuff(buffer, count) + "\n"//接收到的内容格式转换成字符串
                    //当读取服务器发来的数据时，也把这个消息告诉主线程，配合主线程进行更新UI。
                    val msg = Message()
                    msg.what = 4
                    mHandler.sendMessage(msg)
                }
            } catch (e: Exception) {
                // TODO: handle exception
                //当读取服务器发来的数据错误时，也把这个消息告诉主线程，配合主线程进行更新UI。
                val msg = Message()
                msg.what = 3
                mHandler.sendMessage(msg)
            }

        }
    }

    /**
     * 在安卓里面，涉及到网络连接等耗时操作时，不能将其放在UI主线程中，
     * 需要添加子线程，在子线程进行网络连接，这就涉及到安卓线程间的通信了，
     * 用Handle来实现。这里的子线程也就是 mThreadClient
     *
     * handle的定义： 主要接受子线程发送的数据, 并用此数据配合主线程更新UI.
     * 解释: 当应用程序启动时，Android首先会开启一个主线程 (也就是UI线程) ,
     * 主线程为管理界面中的UI控件，进行事件分发, 比如说, 你要是点击一个 Button,
     * Android会分发事件到Button上，来响应你的操作。  如果此时需要一个耗时的操作，
     * 例如: 联网读取数据，或者读取本地较大的一个文件的时候，你不能把这些操作放在主线程中，
     * 如果你放在主线程中的话，界面会出现假死现象, 如果5秒钟还没有完成的话，
     * 会收到Android系统的一个错误提示  "强制关闭".  这个时候我们需要把这些耗时的操作，
     * 放在一个子线程中,更新UI只能在主线程中更新，子线程中操作是危险的. 这个时候，
     * Handler就出现了来解决这个复杂的问题，由于Handler运行在主线程中(UI线程中)，
     * 它与子线程可以通过Message对象来传递数据，这个时候，
     * Handler就承担着接受子线程传过来的(子线程用sedMessage()方法传弟)Message对象，
     * 里面包含数据, 把这些消息放入主线程队列中，配合主线程进行更新UI。
     */
    @SuppressLint("HandlerLeak")
    internal var mHandler: Handler = object : Handler() {
        @SuppressLint("SetTextI18n")
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            if (msg.what == 4)
            //当读取到服务器发来的数据时，收到了子线程的消息，而且接收到的字符串我们给它定义的是res
            {
                val aars: CharArray = res.toCharArray()
                //接收来自服务器的字符串，把字符串转成字符数组
                if (aars.size >= 3) {
                    if (aars[0] == 'T') {//如果字符数组的首位是T，说明接收到的信息是温湿度 T25

                        temp!!.text = "温湿度：" + aars[1] + aars[2] + "℃"
                    } else if (aars[0] == 'M') {//如果字符数组的首位是T，说明接收到的信息是气体浓度M66

                        mq!!.text = "气体浓度：" + aars[1] + aars[2] + "%"
                    }
                } else {
                    showDialog("收到格式错误的数据:$res")
                }
            } else if (msg.what == 2) {
                showDialog("连接失败，服务器走丢了")
                startButton!!.text = "开始连接"


            } else if (msg.what == 1) {
                showDialog("连接成功！")
                warningShow!!.text = "已连接智能衣柜\n"
                ipText!!.isEnabled = false//锁定ip地址和端口号
                isConnecting = true
                startButton!!.text = "停止连接"
            } else if (msg.what == 3) {
                warningShow!!.text = "已断开连接\n"


            } else if (msg.what == 5) {
                warningShow!!.text = "IP和端口号不能为空\n"
            } else if (msg.what == 6) {
                warningShow!!.text = "IP地址不合法\n"
            }
        }
    }



    /**
     * 严苛模式
     * StrictMode类是Android 2.3 （API 9）引入的一个工具类，可以用来帮助开发者发现代码中的一些不规范的问题，
     * 以达到提升应用响应能力的目的。举个例子来说，如果开发者在UI线程中进行了网络操作或者文件系统的操作，
     * 而这些缓慢的操作会严重影响应用的响应能力，甚至出现ANR对话框。为了在开发中发现这些容易忽略的问题，
     * 我们使用StrictMode，系统检测出主线程违例的情况并做出相应的反应，最终帮助开发者优化和改善代码逻辑。
     */
    private fun strictMode() {
        StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .penaltyLog()
                .build()
        )
        StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .penaltyLog()
                .penaltyDeath()
                .build())
    }

    /**
     * layout组件初始化
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun initView() {
        warningShow = findViewById(R.id.tv1)//警告语显示
        temp = findViewById(R.id.temp_text)//温湿度显示
        mq = findViewById(R.id.mq_text)//气体浓度显示

        ipText = findViewById(R.id.IPText)//ip地址和端口号
        val ipPort = "192.168.1.120:8080"
        ipText!!.setText(ipPort)//把ip地址和端口号设一个默认值，这个要改成你自己设置的

        startButton = findViewById(R.id.StartConnect)//连接按钮
        //连接事件  其实就是建立socket连接

        startButton!!.setOnClickListener {
            if (isConnecting) {
                isConnecting = false
                if (mSocketClient != null) {
                    try {
                        mSocketClient!!.close()
                        mSocketClient = null
                        if (mPrintWriterClient != null) {
                            mPrintWriterClient!!.close()
                            mPrintWriterClient = null
                        }
                        mThreadClient!!.interrupt()
                        startButton!!.text = "开始连接"
                        ipText!!.isEnabled = true//可以输入ip和端口号
                        warningShow!!.text = "断开连接"

                    } catch (e: IOException) {
                        e.printStackTrace()
                    }

                }
            } else {
                mThreadClient = Thread(mRunnable)
                mThreadClient!!.start()
            }
        }

        //通风开关按钮初始化
        val switchC = findViewById<Switch>(R.id.switch_c)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            switchC.showText = true//按钮上默认显示文字
        }
        switchC.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked)
            //当isChecked为true时，按钮就打开，并发送开的指令，当isChecked为false时，按钮就关闭，并发送关的指令
            {
                switchC.setSwitchTextAppearance(this@MainActivity, R.style.s_true)//开关样式
                switchC.showText = true//显示开关为on
                if (send(sendOrder[0], -1)) {
                    showDialog("开启通风")
                } else {
                    switchC.isChecked = false//当APP没有连接到单片机时，默认此按钮点击无效
                }
            } else {

                switchC.setSwitchTextAppearance(this@MainActivity, R.style.s_false)//开关样式
                switchC.showText = true//显示文字on
                if (send(sendOrder[1], -1)) {
                    showDialog("关闭通风")
                } else {
                    switchC.isChecked = false//当APP没有连接到单片机时，默认此按钮点击无效
                }
            }
        }

        //抽湿开关按钮
        val switchT = findViewById<Switch>(R.id.switch_t)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            switchT.showText = true//按钮上默认显示文字
        }
        switchT.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                switchT.setSwitchTextAppearance(this@MainActivity, R.style.s_true)//开关样式
                switchT.showText = true//显示文字on
                if (send(sendOrder[2], -1)) {
                    showDialog("开启抽湿")
                } else {
                    switchT.isChecked = false//当APP没有连接到单片机时，默认此按钮点击无效
                }
            } else {
                switchT.setSwitchTextAppearance(this@MainActivity, R.style.s_false)//开关样式
                switchT.showText = true
                if (send(sendOrder[3], -1)) {
                    showDialog("关闭抽湿")
                } else {
                    switchT.isChecked = false//当APP没有连接到单片机时，默认此按钮点击无效
                }
            }
        }
    }

    /**
     * 窗口提示
     * @param msg
     */
    private fun showDialog(msg: String) {
        val builder = AlertDialog.Builder(this@MainActivity)
        builder.setIcon(android.R.drawable.ic_dialog_info)
        builder.setTitle(msg)
        builder.setCancelable(false)
        builder.setPositiveButton("确定") { _, _ -> }
        builder.create().show()
    }

    /**
     * 字符数组转字符串
     * @param buff
     * @param count
     * @return
     */
    private fun getInfoBuff(buff: CharArray, count: Int): String {
        val temp = CharArray(count)
        System.arraycopy(buff, 0, temp, 0, count)
        return String(temp)
    }

    /**
     * 发送函数
     * @param msg
     * @param position
     * @return
     */
    private fun send(msg: String, position: Int): Boolean {
        if (isConnecting && mSocketClient != null) {
            if (position == -1) {
                try {
                    mPrintWriterClient!!.print(msg)
                    mPrintWriterClient!!.flush()
                    return true
                } catch (e: Exception) {
                    // TODO: handle exception
                    Toast.makeText(this@MainActivity, "发送异常" + e.message, Toast.LENGTH_SHORT).show()
                }

            }

        } else {
            showDialog("您还没有连接衣柜呢！")
        }
        return false
    }

    companion object {
        private var mPrintWriterClient: PrintWriter? = null//PrintWriter是java中很常见的一个类，该类可用来创建一个文件并向文本文件写入数据
    }

}
