package com.goldenshine.marble.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class AmountInWordsTest {
    @Test fun formatsWholeEgyptianPounds() {
        assertEquals("جنيه واحد فقط لا غير", AmountInWords.egyptianPounds(1.0))
        assertEquals("جنيهان فقط لا غير", AmountInWords.egyptianPounds(2.0))
        assertEquals("أحد عشر جنيهًا مصريًا فقط لا غير", AmountInWords.egyptianPounds(11.0))
    }

    @Test fun formatsThousandsAndHundreds() {
        assertEquals("ألف ومائتان وخمسون جنيهًا مصريًا فقط لا غير", AmountInWords.egyptianPounds(1250.0))
    }

    @Test fun includesPiasters() {
        assertEquals("ألف ومائتان وخمسون جنيهًا مصريًا وخمسة وسبعون قرشًا فقط لا غير", AmountInWords.egyptianPounds(1250.75))
    }

    @Test fun roundsToTwoDecimalPlaces() {
        assertEquals("جنيه واحد وخمسة وسبعون قرشًا فقط لا غير", AmountInWords.egyptianPounds(1.745))
    }

    @Test fun handlesAmountsBelowOnePound() {
        assertEquals("خمسة وسبعون قرشًا فقط لا غير", AmountInWords.egyptianPounds(0.75))
    }

    @Test fun formatsEnglishAmounts() {
        assertEquals("One thousand two hundred and fifty Egyptian pounds and seventy-five piastres only",
            AmountInWords.englishEgyptianPounds(1250.75))
    }

    @Test fun handlesZero() {
        assertEquals("صفر جنيه مصري فقط لا غير", AmountInWords.egyptianPounds(0.0))
    }
}
