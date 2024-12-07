package mu.location.savmed.contacts

import android.provider.BaseColumns

object FeedReader {

    object FeedEntry : BaseColumns {
        const val TABLE_NAME = "Friends"
        const val COLUMN_NAME_TITLE = "friend"
        const val COLUMN_NAME_SUBTITLE = "friend"
    }
}