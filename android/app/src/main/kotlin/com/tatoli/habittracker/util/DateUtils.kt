package com.tatoli.habittracker.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

fun todayKey(): String = LocalDate.now().format(DATE_FORMAT)
