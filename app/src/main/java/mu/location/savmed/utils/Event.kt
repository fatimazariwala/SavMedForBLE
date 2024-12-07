package mu.location.savmed.utils

import androidx.annotation.AnyThread
import androidx.annotation.UiThread
import java.util.concurrent.atomic.AtomicBoolean

open class Event<out T> @AnyThread constructor(private val content: T) {
    private val handled = AtomicBoolean(false)

    @UiThread
    fun consume(handleContent: (T) -> Unit) {
        if (!handled.get()) {
            handled.set(true)
            handleContent(content)
        }
    }

    @UiThread
    fun consumed(): Boolean {
        return handled.get()
    }
}