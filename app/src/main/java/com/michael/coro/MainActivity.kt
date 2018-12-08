package com.michael.coro

import android.annotation.SuppressLint
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    val executor: ExecutorService = Executors.newSingleThreadExecutor()
    var kick = false
    val event = SuspendingEvent(false,true)

    val firstButton: Button by lazy {
        findViewById<Button>(R.id.first_button)
    }
    val outputView: TextView by lazy {
        findViewById<TextView>(R.id.outputView)
    }
    val clearButton:Button by lazy {
        findViewById<Button>(R.id.clear)
    }

    var suspendFun : (suspend (v:String)->Boolean)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        firstButton.setOnClickListener {
            onClickFirst()
        }
        clearButton.setOnClickListener {
            clearOutput()
        }
        executeSubThread(this::myThirdSuspendFunction)
        executeSuspendTask(this::myThirdSuspendFunction)
    }

    fun ok1_onClickFirst() = runBlocking {
        launch { // launch new coroutine in the scope of runBlocking
            delay(1000L)
            println("World!")
        }
        println("Hello,")
    }

    fun ok2_onClickFirst() = runBlocking {
        coroutineScope { // launch new coroutine in the scope of runBlocking
            delay(1000L)
            println("World!")
        }
        println("Hello,")
    }

    fun ok6_onClickFirst() = runBlocking {
        CoroutineScope(Dispatchers.Main).launch { // launch new coroutine in the scope of runBlocking
            delay(1000L)
            println("World!")
        }
        println("Hello,")
    }

    fun ng1_onClickFirst() = runBlocking {
        launch(Dispatchers.Main) { // NG
            delay(1000L)
            println("World!")
        }
        println("Hello,")
    }

    fun ok4_onClickFirst() = runBlocking {
        GlobalScope.launch(Dispatchers.Main) { // OK
            delay(1000L)
            println("World!")
        }
        println("Hello,")
    }

    fun ok3_onClickFirst() {
        GlobalScope.launch(Dispatchers.Main) { // OK
            delay(1000L)
            println("World!")
        }
        println("Hello,")
    }

    @SuppressLint("SetTextI18n")
    fun addOutput(text:String) = runBlocking {
        GlobalScope.launch(Dispatchers.Main) {
            outputView.text = "${outputView.text}\n${text}"
        }
    }
    fun clearOutput() = runBlocking {
        GlobalScope.launch(Dispatchers.Main) {
            outputView.text = ""
        }
    }

    fun onClickFirst() {

        addOutput("enter onClickFirst")
        GlobalScope.launch {
//        runBlocking {
            myFirstSuspendFunction()
        }
        addOutput("exit onClickFirst")
    }

    suspend fun myFirstSuspendFunction() {
        addOutput("1) enter myFirstSuspendFunction")
        suspendFun = this::myThirdSuspendFunction

        coroutineScope {
            addOutput("2) enter coroutineScope")
            mySecondSuspendFunction()
            addOutput("2) exit coroutineScope")
        }
        suspendFun  = null;
        addOutput("1) exit myFirstSuspendFunction")
    }

    suspend fun mySecondSuspendFunction() {
        addOutput("3) enter mySecondSuspendFunction")
        kick = true

        coroutineScope {
            addOutput("4) enter second coroutineScope")
            launch {
                addOutput("5) enter launched scope in second coroutineScope")
                delay(3000)
                addOutput("5) exit launched scope in second coroutineScope")
            }
            addOutput("4) exit second coroutineScope")
        }
        //myThirdSuspendFunction()
        val b = suspendFun!!("Hoge")
        assert(b)
        event.set()
        addOutput("3) exit mySecondSuspendFunction")
    }
    suspend fun myThirdSuspendFunction(v:String) = coroutineScope<Boolean> {
        addOutput("6) enter myThirdFunction($v")

        launch {
            addOutput("7) enter launched scope in second coroutineScope")
            delay(3000)
            addOutput("7) exit launched scope in second coroutineScope")
        }
        addOutput("6) exit myThirdFunction")
        true
    }


    fun executeSubThread(action:suspend (v:String)->Boolean) {
        executor.submit {
            runBlocking {
                while(true) {
                    if(kick) {
                        kick = false
                        addOutput("...sub-thread kicked")
                        action("Fuga")
                    }
                    delay(1000)
                }
            }
        }
    }

    fun executeSuspendTask(action:suspend (v:String)->Boolean) {
        addOutput("8) enter executeSuspendTask")
        val ch = Channel<Boolean>(capacity = 1)

        GlobalScope.launch {
            while(true) {
                event.waitOne()
                addOutput("8) action call")
                action("Moge")
            }
        }
        addOutput("8) exit executeSuspendTask")
    }
}


class SuspendingEvent(var signal:(Boolean), val autoReset:(Boolean)) {
    private val channel = Channel<Unit>(capacity = 1)
    private val mutex = Mutex()

    init {
        if(!signal) {
            runBlocking {
                channel.send(Unit)
                signal = false
            }
        }
    }

    suspend fun set() {
        if(mutex.withLock {signal}) {
            return
        }
        channel.receive()
        mutex.withLock {signal = true}
    }

    suspend fun reset() {
        if(mutex.withLock {!signal}) {
            return
        }
        channel.send(Unit)
        mutex.withLock {signal = false}
    }

    suspend fun waitOne() {
        if(mutex.withLock {signal}) {
            return
        }
        channel.send(Unit)
        mutex.withLock {
            if(autoReset) {
                signal = false
            } else {
                channel.receive()
                signal = true
            }
        }
    }
}