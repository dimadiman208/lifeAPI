package com.lifeapi.app.vm

/** Water quick-add amounts from Profile.waterQuick ("250,500,750"). */
fun parseQuick(s: String): List<Int> =
    s.split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..5000 }.distinct().take(4).ifEmpty { listOf(150, 250, 330, 500) }
