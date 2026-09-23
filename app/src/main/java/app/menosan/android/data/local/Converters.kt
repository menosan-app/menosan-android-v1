package app.menosan.android.data.local

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalDate

/**
 * Instants are stored as epoch milliseconds (UTC). Dates are stored as `YYYY-MM-DD` text, which sorts correctly
 * and matches the API. Enums use Room's built-in by-name TEXT mapping.
 */
class Converters {
    @TypeConverter
    fun instantToMillis(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun millisToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun dateToText(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun textToDate(value: String?): LocalDate? = value?.let(LocalDate::parse)
}
