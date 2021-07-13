package com.timejet.bio.timejet.utils.MskedEditTextUtils

import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText

import java.lang.ref.WeakReference

class MaskedWatcher(maskedFormatter: MaskedFormatter, editText: EditText) : TextWatcher {

    // ===========================================================
    // Constructors
    // ===========================================================

    // ===========================================================
    // Fields
    // ===========================================================

    private val mMaskFormatter: WeakReference<MaskedFormatter> = WeakReference(maskedFormatter)
    private val mEditText: WeakReference<EditText> = WeakReference(editText)
    private var oldFormattedValue = ""
    private var oldCursorPosition: Int = 0

    // ===========================================================
    // Listeners, methods for/from Interfaces
    // ===========================================================

    private fun setFormattedText(formattedString: IFormattedString) {
        val editText = mEditText.get() ?: return

        val deltaLength = formattedString.length - oldFormattedValue.length


        editText.removeTextChangedListener(this)
        editText.setText(formattedString)
        editText.addTextChangedListener(this)

        var newCursorPosition = oldCursorPosition

        if (deltaLength > 0) {
            newCursorPosition += deltaLength
        } else if (deltaLength < 0) {
            newCursorPosition -= 1
        } else {
            var mask: Mask? = null
            mMaskFormatter.get()?.mMask?.let { mask = it }

            var maskLength = 0
            mMaskFormatter.get()?.maskLength?.let { maskLength = it}
            newCursorPosition = 1.coerceAtLeast(newCursorPosition.coerceAtMost(maskLength))


            var isPopulate = false
            mask?.get(newCursorPosition - 1)?.isPrepopulate?.let { isPopulate = it }
            if (isPopulate)
                newCursorPosition -= 1
        }
        newCursorPosition = 0.coerceAtLeast(newCursorPosition.coerceAtMost(formattedString.length))
        editText.setSelection(newCursorPosition)
    }

    private fun setFormattedText(formattedString: IFormattedString, isReplace: Boolean) {
        val editText = mEditText.get() ?: return

        val deltaLength = formattedString.length - oldFormattedValue.length


        editText.removeTextChangedListener(this)
        editText.setText(formattedString)
        editText.addTextChangedListener(this)

        var newCursorPosition = oldCursorPosition

        if (deltaLength > 0) {
            newCursorPosition += deltaLength
        } else if (deltaLength < 0) {
            newCursorPosition -= 1
        } else {
            var mask: Mask? = null
            mMaskFormatter.get()?.mMask?.let { mask = it }

            var maskLength = 0
            mMaskFormatter.get()?.maskLength?.let { maskLength = it}
            if(oldCursorPosition == 0) {
                newCursorPosition = 1
            } else {
                if(oldCursorPosition == 4){
                    newCursorPosition = 6
                } else {
                    newCursorPosition = 1.coerceAtLeast(newCursorPosition.coerceAtMost(maskLength)) + 1
                }
            }


            var isPopulate = false
            mask?.get(newCursorPosition - 1)?.isPrepopulate?.let { isPopulate = it }
            if (isPopulate)
                newCursorPosition -= 1
        }
        newCursorPosition = 0.coerceAtLeast(newCursorPosition.coerceAtMost(formattedString.length))
        editText.setSelection(newCursorPosition)
    }

    override fun afterTextChanged(s: Editable?) {
        if (s == null)
            return

        var value = s.toString()

        var maskLength = 0
        mMaskFormatter.get()?.maskLength?.let { maskLength = it}

        if (value.length > oldFormattedValue.length && maskLength < value.length) {
            if(maskLength < value.length && oldFormattedValue == ""){
                val formattedString = mMaskFormatter.get()?.formatString("9999:59")

                formattedString?.let { setFormattedText(it) }
                oldFormattedValue = formattedString.toString()

            } else {
                if (!value.equals(oldFormattedValue)) {
                    for (x in 0..6) {
                        if (value.get(x) != oldFormattedValue.get(x)) {
                            if (oldFormattedValue.get(x).equals(':')) {
                                value = oldFormattedValue.substring(0, x + 1) + value.get(x) + oldFormattedValue.substring(x + 2)
                                val formattedString = mMaskFormatter.get()?.formatString(value)

                                formattedString?.let { setFormattedText(it, true) }
                                oldFormattedValue = formattedString.toString()
                                break
                            } else {
                                value = oldFormattedValue.substring(0, x) + value.get(x) + oldFormattedValue.substring(x + 1)
                                val formattedString = mMaskFormatter.get()?.formatString(value)

                                formattedString?.let { setFormattedText(it, true) }
                                oldFormattedValue = formattedString.toString()
                                break
                            }
                        }
                    }
                } else {
                    val formattedString = mMaskFormatter.get()?.formatString(value)

                    formattedString?.let { setFormattedText(it) }
                    oldFormattedValue = formattedString.toString()
                }
            }
//            value = oldFormattedValue
        } else {
            val formattedString = mMaskFormatter.get()?.formatString(value)

            formattedString?.let { setFormattedText(it) }
            oldFormattedValue = formattedString.toString()
        }
    }

    override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
        mEditText.get()?.selectionStart?.let { this.oldCursorPosition = it }
    }

    override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
}
