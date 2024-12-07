package mu.location.savmed.utils

import android.app.Dialog
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.Window
import android.view.WindowManager
import androidx.annotation.UiThread
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import mu.location.savmed.R
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