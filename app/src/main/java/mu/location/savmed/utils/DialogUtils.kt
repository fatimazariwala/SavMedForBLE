package mu.location.savmed.utils

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.UiThread
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import mu.location.savmed.R
import mu.location.savmed.bluetooth.bluetoothLE.models.writeMessage
import mu.location.savmed.ui.chat.chatNew.model.ConfirmationDialogModel

class DialogUtils {
    companion object {
//        @UiThread
//        fun getOpenOrExportFileDialog(
//            context: Context,
//            viewModel: ConfirmationDialogModel
//        ): Dialog {
//            val binding: DialogOpenExportFileBinding = DataBindingUtil.inflate(
//                LayoutInflater.from(context),
//                R.layout.dialog_open_export_file,
//                null,
//                false
//            )
//            binding.viewModel = viewModel
//
//            return getDialog(context, binding)
//        }

        fun showSplashDialogNearBy(message: writeMessage,context: Context, callback: (Boolean) -> Unit) {
            val dialogBuilder = AlertDialog.Builder(context)
            val inflater = LayoutInflater.from(context)
            val dialogView = inflater.inflate(R.layout.pop_up_near_by, null) // Replace with your XML file name
            dialogBuilder.setView(dialogView)

            val alert = dialogBuilder.create()
            alert.show()

            dialogView.findViewById<TextView>(R.id.tv_above_circle).text = "Help needed by ${message.From}!"
            dialogView.findViewById<TextView>(R.id.tv_distance).text = "${message.dist}m away"
            dialogView.findViewById<TextView>(R.id.tv_approx).text = "[approx dist given]"
            // Reference the OK button and set its click listener
            dialogView.findViewById<Button>(R.id.btn_ok)?.setOnClickListener {
                alert.dismiss()
                callback(true) // Call the callback with true
            }

            dialogView.findViewById<ImageView>(R.id.btn_cancel).setOnClickListener() {
                alert.dismiss()
                callback(false)
            }

            // Auto-dismiss after a certain time
            alert.setOnShowListener {
                alert.getButton(AlertDialog.BUTTON_POSITIVE).postDelayed({
                    if (alert.isShowing) {
                        alert.dismiss()
                        callback(false) // Auto-dismiss and return false
                    }
                }, 2000)
            }
        }

        @UiThread
        private fun getDialog(context: Context, binding: ViewDataBinding): Dialog {
            val dialog = Dialog(context, R.style.Theme_LinphoneDialog)
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
            dialog.setContentView(binding.root)

            dialog.window?.apply {
                setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT
                )
                setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

                val d: Drawable = ColorDrawable(
                    context.getColor(R.color.black)
                )
                d.alpha = 153 // 60% opacity
                setBackgroundDrawable(d)
            }

            return dialog
        }
    }
}