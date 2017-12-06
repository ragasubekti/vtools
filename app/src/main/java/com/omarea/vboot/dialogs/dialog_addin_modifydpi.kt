package com.omarea.vboot.dialogs

import android.content.Context
import android.os.Build
import android.support.v7.app.AlertDialog
import android.util.DisplayMetrics
import android.view.Display
import android.view.LayoutInflater
import android.widget.CheckBox
import android.widget.EditText
import com.omarea.shared.Consts
import com.omarea.shell.SuDo
import com.omarea.vboot.R

/**
 * Created by Hello on 2017/12/03.
 */

class dialog_addin_modifydpi {
    var context: Context

    constructor(context: Context) {
        this.context = context
    }

    fun modifyDPI(display: Display) {
        val layoutInflater = LayoutInflater.from(context)
        val dialog = layoutInflater.inflate(R.layout.dialog_addin_dpi, null)
        val dpiInput = dialog.findViewById(R.id.dialog_addin_dpi_dpiinput) as EditText
        val widthInput = dialog.findViewById(R.id.dialog_addin_dpi_width) as EditText
        val heightInput = dialog.findViewById(R.id.dialog_addin_dpi_height) as EditText
        val quickChange = dialog.findViewById(R.id.dialog_addin_dpi_quickchange) as CheckBox
        val dm = DisplayMetrics()
        display.getMetrics(dm)
        dpiInput.setText(dm.densityDpi.toString())
        widthInput.setText(dm.widthPixels.toString())
        heightInput.setText(dm.heightPixels.toString())
        if (Build.VERSION.SDK_INT >= 24) {
            quickChange.isChecked = true
        }

        AlertDialog.Builder(context).setTitle("DPI、分辨率").setView(dialog).setNegativeButton("确定", { d, w ->
            val dpi = if (dpiInput.text.length > 0) (dpiInput.text.toString().toInt()) else (0)
            val width = if (widthInput.text.length > 0) (widthInput.text.toString().toInt()) else (0)
            val height = if (heightInput.text.length > 0) (heightInput.text.toString().toInt()) else (0)
            val qc = quickChange.isChecked

            val cmd = StringBuilder()
            if (width >= 320 && height >= 480) {
                cmd.append("wm size ${width}x${height}")
                cmd.append("\n")
            }
            if (dpi >= 96) {
                cmd.append("wm density $dpi")
                cmd.append("\n")
            }
            if (!qc && dpi >= 96) {
                cmd.append(Consts.MountSystemRW)
                cmd.append("sed '/ro.sf.lcd_density=/'d /system/build.prop > /data/build.prop\n")
                cmd.append("sed '\$aro.sf.lcd_density=$dpi' /data/build.prop > /data/build2.prop\n")
                cmd.append("cp /system/build.prop /system/build.prop.dpi_bak\n")
                cmd.append("cp /data/build2.prop /system/build.prop\n")
                cmd.append("rm /data/build.prop\n")
                cmd.append("rm /data/build2.prop\n")
                cmd.append("chmod 0644 /system/build.prop\n")
                cmd.append("sync\n")
                cmd.append("reboot\n")
            }
            if (cmd.length > 0)
                SuDo(context).execCmdSync(cmd.toString())
        }).create().show()
    }
}
