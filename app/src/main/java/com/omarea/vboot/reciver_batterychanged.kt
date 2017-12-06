package com.omarea.vboot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.BatteryManager
import android.os.Handler
import android.os.Message
import android.widget.Toast
import com.omarea.shared.*
import java.io.DataOutputStream
import java.io.IOException
import java.util.*

class reciver_batterychanged : BroadcastReceiver() {
    private var p: Process? = null
    internal var out: DataOutputStream? = null
    private var bp: Boolean = false

    internal fun tryExit() {
        try {
            if (out != null)
                out!!.close()
        } catch (ex: Exception) {
        }

        try {
            p!!.destroy()
        } catch (ex: Exception) {
        }

    }

    @JvmOverloads internal fun DoCmd(cmd: String, isRedo: Boolean = false) {
        Thread(Runnable {
            try {
                tryExit()
                if (p == null || isRedo || out == null) {
                    tryExit()
                    p = Runtime.getRuntime().exec("su")
                    out = DataOutputStream(p!!.outputStream)
                }
                out!!.writeBytes(cmd)
                out!!.writeBytes("\n")
                out!!.flush()
                //out!!.close()
            } catch (e: IOException) {
                //重试一次
                if (!isRedo)
                    DoCmd(cmd, true)
                else
                    ShowMsg("Failed execution action!\nError message : " + e.message + "\n\n\ncommand : \r\n" + cmd, true)
            }
        }).start()
    }

    internal var context: Context? = null
    private var sharedPreferences: SharedPreferences? = null
    private var globalSharedPreferences: SharedPreferences? = null
    internal var listener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private val myHandler = object : Handler() {
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
        }
    }

    //显示文本消息
    private fun ShowMsg(msg: String, longMsg: Boolean) {
        if (context != null)
            myHandler.post { Toast.makeText(context, msg, if (longMsg) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show() }
    }

    //快速充电
    private fun FastCharger() {
        if (!sharedPreferences!!.getBoolean(SpfConfig.CHARGE_SPF_QC_BOOSTER, false))
            return

        if (globalSharedPreferences!!.getBoolean(SpfConfig.GLOBAL_SPF_DEBUG, false))
            ShowMsg("充电器已连接！", false)
        DoCmd(Consts.FastChanger)
    }

    internal var lastBatteryLeavel = -1
    internal var lastChangerState = false

    override fun onReceive(context: Context, intent: Intent) {
        if (sharedPreferences == null) {
            sharedPreferences = context.getSharedPreferences(SpfConfig.CHARGE_SPF, Context.MODE_PRIVATE)
            listener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key -> }
            sharedPreferences!!.registerOnSharedPreferenceChangeListener(listener)
        }
        if (globalSharedPreferences == null) {
            globalSharedPreferences = context.getSharedPreferences(SpfConfig.GLOBAL_SPF, Context.MODE_PRIVATE)
        }

        this.context = context
        try {
            val action = intent.action
            val onChanger = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1) == BatteryManager.BATTERY_STATUS_CHARGING
            val batteryLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)

            if (lastBatteryLeavel != batteryLevel) {
                lastBatteryLeavel = batteryLevel
            }

            //BatteryProtection
            if (sharedPreferences!!.getBoolean(SpfConfig.CHARGE_SPF_BP, false)) {
                if (onChanger) {
                    if (batteryLevel >= sharedPreferences!!.getInt(SpfConfig.CHARGE_SPF_BP_LEVEL, 85)) {
                        bp = true
                        DoCmd(Consts.DisableChanger)
                    }
                }
                //电量不足，恢复充电功能
                else if (action == Intent.ACTION_BATTERY_LOW) {
                    DoCmd(Consts.ResumeChanger)
                    Toast.makeText(this.context, "电池电量低，请及时充电！", Toast.LENGTH_SHORT).show()
                    bp = false
                } else if (bp && batteryLevel != -1 && batteryLevel < sharedPreferences!!.getInt(SpfConfig.CHARGE_SPF_BP_LEVEL, 85) - 20) {
                    //电量低于保护级别10
                    DoCmd(Consts.ResumeChanger)
                    bp = false
                }
            }

            if (action == Intent.ACTION_BATTERY_CHANGED) {
                //如果充电状态切换
                if (lastChangerState != onChanger) {
                    lastChangerState = onChanger
                }
                if (onChanger)
                    entryFastChanger(onChanger)
                return
            } else if (action == Intent.ACTION_POWER_CONNECTED) {
                lastChangerState = onChanger
                entryFastChanger(onChanger)
                return
            } else if (action == Intent.ACTION_BOOT_COMPLETED && onChanger) {
                lastChangerState = onChanger
                entryFastChanger(onChanger)
                return
            } else if (action == Intent.ACTION_POWER_DISCONNECTED) {
                lastChangerState = onChanger
                entryFastChanger(onChanger)
                return
            }
        } catch (ex: Exception) {
            ShowMsg("充电加速服务：\n" + ex.message, true);
        }

    }

    private fun entryFastChanger(onChanger: Boolean) {
        if (onChanger) {
            if (sharedPreferences!!.getBoolean(SpfConfig.CHARGE_SPF_QC_BOOSTER, false)) {
                FastCharger()
            }
        }
    }
}
