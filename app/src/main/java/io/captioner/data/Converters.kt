package io.captioner.data

import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.fromColorLong
import androidx.compose.ui.graphics.toColorLong
import androidx.room.TypeConverter
import java.time.LocalDateTime
import java.time.ZoneOffset
import androidx.core.net.toUri

class Converters {
    @TypeConverter
    fun timestampToDate(value: Long?): LocalDateTime? {
        return value?.let {
            LocalDateTime.ofEpochSecond(it, 0, ZoneOffset.UTC)
        }
    }

    @TypeConverter
    fun dateToTimestamp(date: LocalDateTime?): Long? {
        return date?.toEpochSecond(ZoneOffset.UTC)
    }

    @TypeConverter
    fun uriToStringX(uri: Uri?): String? {
        return uri?.toString()
    }

    @TypeConverter
    fun stringToUriX(string: String?): Uri? {
        return string?.toUri()
    }

    @TypeConverter
    fun uriToString(uri: Uri): String {
        return uri.toString()
    }

    @TypeConverter
    fun stringToUri(string: String): Uri {
        return string.toUri()
    }

    @TypeConverter
    fun colorToColorLong(color: Color): Long {
        return color.toColorLong()
    }

    @TypeConverter
    fun colorLongToColor(colorLong: Long): Color {
        return Color.fromColorLong(colorLong)
    }
}