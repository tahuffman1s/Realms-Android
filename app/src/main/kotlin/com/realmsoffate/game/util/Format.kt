package com.realmsoffate.game.util

fun formatSigned(n: Int): String = if (n >= 0) "+$n" else n.toString()
