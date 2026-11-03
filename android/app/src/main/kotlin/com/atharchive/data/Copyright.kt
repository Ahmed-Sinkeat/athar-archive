package com.atharchive.data

import android.content.Context
import android.content.Intent
import android.net.Uri

const val CopyrightEmail = "admin@arthurarchive.com"

const val CopyrightNotice =
    "الكتب في التطبيق منشورة من قبل. إن كنت صاحب حقٍّ في كتابٍ وأردت حذفه، فراسلنا على البريد وسنحذفه."

/** Opens the mail app on a copyright request; nothing happens without one installed. */
fun emailCopyright(context: Context) {
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$CopyrightEmail"))
        .putExtra(Intent.EXTRA_SUBJECT, "طلب حذف كتاب لحقوق النشر")
    runCatching { context.startActivity(intent) }
}
