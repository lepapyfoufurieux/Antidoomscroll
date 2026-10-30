package com.antiscrolling.ui

import java.time.Instant
import java.time.ZoneId

/** Minutes depuis minuit → « 07:30 ». */
fun hhmm(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

/** Heure locale du téléphone → « 07:30 ». */
fun hhmm(instant: Instant): String = instant.atZone(ZoneId.systemDefault()).let { hhmm(it.hour * 60 + it.minute) }

/** 25 → « 25 min », 60 → « 1 h », 90 → « 1 h 30 ». */
fun durationLabel(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} h"
    else -> "${minutes / 60} h ${minutes % 60}"
}

/** Format court des cartes : « 42m », « 5h10 ». */
fun shortDuration(ms: Long): String {
    val minutes = ms / 60_000
    return if (minutes < 60) "${minutes}m" else "${minutes / 60}h${"%02d".format(minutes % 60)}"
}

/** 0 ou 1 au singulier, comme en français. */
fun count(n: Int, word: String) = if (n <= 1) "$n $word" else "$n ${word}s"
