package et.tibeb.examprep

import et.tibeb.examprep.util.MathSeg
import et.tibeb.examprep.util.parseMath
import org.junit.Assert.assertEquals
import org.junit.Test

class MathFormatTest {
    private fun flat(s: String) = parseMath(s).joinToString("") {
        when (it.script) { 1 -> "^[${it.text}]"; -1 -> "_[${it.text}]"; else -> it.text }
    }

    @Test fun powersAndFractions() {
        assertEquals("x^[2] + 3x", flat("\$x^2 + 3x\$"))
        assertEquals("(x+1)/2", flat("\$\\frac{x+1}{2}\$"))
        assertEquals("e^[2x+1]", flat("e^{2x+1}"))
        assertEquals("a/b", flat("\\frac{a}{b}"))
    }

    @Test fun rootsGreekAndRelations() {
        assertEquals("√(x+1) ≤ 2π", flat("\$\\sqrt{x+1} \\le 2\\pi\$"))
        assertEquals("∛8 = 2", flat("\$\\sqrt[3]{8} = 2\$"))
        assertEquals("sin θ → 0", flat("\$\\sin \\theta \\to 0\$"))
        assertEquals("x ∈ ℝ", flat("\$x \\in \\mathbb{R}\$"))
    }

    @Test fun chemistrySubscriptsAndText() {
        assertEquals("H_[2]O", flat("\$H_2O\$"))
        assertEquals("CO_[2] (g)", flat("\$CO_2\\text{ (g)}\$"))
        assertEquals("10 m/s^[2]", flat("\$10 \\text{m/s}^2\$"))
    }

    @Test fun plainTextIsUntouched() {
        assertEquals("Fill in the blank: ____ is a noun.", flat("Fill in the blank: ____ is a noun."))
        assertEquals("Price is 50 birr", flat("Price is 50 birr"))
        assertEquals(listOf(MathSeg("abc", 0)), parseMath("abc"))
    }
}
