package com.goldenshine.marble.domain

import java.math.BigDecimal
import java.math.RoundingMode

object AmountInWords {
    private val units = listOf("", "واحد", "اثنان", "ثلاثة", "أربعة", "خمسة", "ستة", "سبعة", "ثمانية", "تسعة")
    private val teens = listOf("عشرة", "أحد عشر", "اثنا عشر", "ثلاثة عشر", "أربعة عشر", "خمسة عشر", "ستة عشر", "سبعة عشر", "ثمانية عشر", "تسعة عشر")
    private val tens = listOf("", "", "عشرون", "ثلاثون", "أربعون", "خمسون", "ستون", "سبعون", "ثمانون", "تسعون")
    private val hundreds = listOf("", "مائة", "مائتان", "ثلاثمائة", "أربعمائة", "خمسمائة", "ستمائة", "سبعمائة", "ثمانمائة", "تسعمائة")
    private val scales = listOf(
        Scale("", "", ""),
        Scale("ألف", "ألفان", "آلاف"),
        Scale("مليون", "مليونان", "ملايين"),
        Scale("مليار", "ملياران", "مليارات")
    )

    fun egyptianPounds(amount: Double): String {
        require(amount.isFinite() && amount >= 0.0) { "المبلغ غير صحيح" }
        val rounded = BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP)
        val totalPiasters = rounded.movePointRight(2).longValueExact()
        val pounds = totalPiasters / 100
        val piasters = (totalPiasters % 100).toInt()
        require(pounds <= 999_999_999_999L) { "المبلغ أكبر من النطاق المدعوم" }

        val poundText = when (pounds) {
            0L -> "صفر جنيه مصري"
            1L -> "جنيه واحد"
            2L -> "جنيهان"
            else -> "${arabicNumber(pounds)} جنيهًا مصريًا"
        }
        val piasterText = when (piasters) {
            0 -> ""
            1 -> "قرش واحد"
            2 -> "قرشان"
            else -> "${arabicNumber(piasters.toLong())} قرشًا"
        }
        return listOf(poundText, piasterText).filter { it.isNotBlank() }.joinToString(" و") + " فقط لا غير"
    }

    fun englishEgyptianPounds(amount: Double): String {
        require(amount.isFinite() && amount >= 0.0) { "Invalid amount" }
        val rounded = BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP)
        val totalPiasters = rounded.movePointRight(2).longValueExact()
        val pounds = totalPiasters / 100
        val piasters = (totalPiasters % 100).toInt()
        require(pounds <= 999_999_999_999L) { "Amount exceeds supported range" }
        val poundText = when (pounds) {
            0L -> "Zero Egyptian pounds"
            1L -> "One Egyptian pound"
            else -> "${englishNumber(pounds)} Egyptian pounds"
        }
        val piasterText = when (piasters) {
            0 -> ""
            1 -> "One piastre"
            else -> "${englishNumber(piasters.toLong())} piastres"
        }
        return listOf(poundText, piasterText).filter { it.isNotBlank() }.joinToString(" and ") + " only"
    }

    private fun arabicNumber(value: Long): String {
        if (value == 0L) return "صفر"
        val parts = mutableListOf<String>()
        var remaining = value
        var scaleIndex = 0
        while (remaining > 0) {
            val group = (remaining % 1000).toInt()
            if (group > 0) {
                val scale = scales[scaleIndex]
                val part = when {
                    scaleIndex == 0 -> underThousand(group)
                    group == 1 -> scale.singular
                    group == 2 -> scale.dual
                    group in 3..10 -> "${underThousand(group)} ${scale.plural}"
                    else -> "${underThousand(group)} ${scale.singular}ًا"
                }
                parts.add(0, part)
            }
            remaining /= 1000
            scaleIndex++
        }
        return parts.joinToString(" و")
    }

    private fun underThousand(value: Int): String {
        require(value in 0..999)
        if (value == 0) return ""
        val h = value / 100
        val rest = value % 100
        val tail = when {
            rest == 0 -> ""
            rest < 10 -> units[rest]
            rest < 20 -> teens[rest - 10]
            else -> {
                val unit = rest % 10
                if (unit == 0) tens[rest / 10]
                else "${units[unit]} و${tens[rest / 10]}"
            }
        }
        return listOf(hundreds[h], tail).filter { it.isNotBlank() }.joinToString(" و")
    }

    private data class Scale(val singular: String, val dual: String, val plural: String)

    private fun englishNumber(value: Long): String {
        if (value == 0L) return "zero"
        val small = listOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen")
        val tensWords = listOf("", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety")
        fun underThousand(n: Int): String {
            val parts = mutableListOf<String>()
            if (n >= 100) {
                parts += "${small[n / 100]} hundred"
                if (n % 100 != 0) parts += "and"
            }
            val rest = n % 100
            when {
                rest < 20 && rest > 0 -> parts += small[rest]
                rest >= 20 -> {
                    val tensPart = tensWords[rest / 10]
                    val unit = rest % 10
                    parts += if (unit == 0) tensPart else "$tensPart-${small[unit]}"
                }
            }
            return parts.joinToString(" ")
        }
        val scalesEnglish = listOf("", "thousand", "million", "billion")
        var remaining = value
        var index = 0
        val parts = mutableListOf<String>()
        while (remaining > 0) {
            val group = (remaining % 1000).toInt()
            if (group > 0) {
                val chunk = underThousand(group)
                parts.add(0, if (index == 0) chunk else "$chunk ${scalesEnglish[index]}")
            }
            remaining /= 1000
            index++
        }
        return parts.joinToString(" ")
    }
}
