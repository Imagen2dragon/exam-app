package et.tibeb.examprep.util

/**
 * Turns the simple LaTeX the AI writes ($x^2$, \frac{a}{b}, \sqrt{x}, \theta, H_2O ...) into
 * plain text pieces marked normal / superscript / subscript. Pure Kotlin (unit-tested); the UI
 * turns the pieces into styled text. No WebView or math library, so the APK stays tiny.
 */
data class MathSeg(val text: String, val script: Int) // 0 normal, 1 superscript, -1 subscript

private val SYMBOLS = mapOf(
    "pi" to "π", "theta" to "θ", "alpha" to "α", "beta" to "β", "gamma" to "γ", "Gamma" to "Γ",
    "delta" to "δ", "Delta" to "Δ", "epsilon" to "ε", "varepsilon" to "ε", "lambda" to "λ", "mu" to "μ",
    "rho" to "ρ", "sigma" to "σ", "Sigma" to "Σ", "tau" to "τ", "phi" to "φ", "varphi" to "φ", "Phi" to "Φ",
    "omega" to "ω", "Omega" to "Ω", "eta" to "η", "nu" to "ν", "chi" to "χ", "psi" to "ψ",
    "infty" to "∞", "le" to "≤", "leq" to "≤", "ge" to "≥", "geq" to "≥", "ne" to "≠", "neq" to "≠",
    "times" to "×", "cdot" to "·", "div" to "÷", "pm" to "±", "mp" to "∓", "approx" to "≈", "equiv" to "≡",
    "to" to "→", "rightarrow" to "→", "leftarrow" to "←", "Rightarrow" to "⇒", "Leftrightarrow" to "⇔",
    "rightleftharpoons" to "⇌", "in" to "∈", "notin" to "∉", "subset" to "⊂", "subseteq" to "⊆",
    "cup" to "∪", "cap" to "∩", "emptyset" to "∅", "forall" to "∀", "exists" to "∃", "sum" to "Σ",
    "prod" to "Π", "int" to "∫", "partial" to "∂", "nabla" to "∇", "circ" to "°", "degree" to "°",
    "angle" to "∠", "perp" to "⊥", "parallel" to "∥", "triangle" to "△", "propto" to "∝", "therefore" to "∴",
    "lim" to "lim", "log" to "log", "ln" to "ln", "sin" to "sin", "cos" to "cos", "tan" to "tan",
    "sec" to "sec", "csc" to "csc", "cot" to "cot", "exp" to "exp", "min" to "min", "max" to "max",
    "quad" to "  ", "qquad" to "   ", "," to " ", ";" to " ", "!" to "", " " to " ",
    "{" to "{", "}" to "}", "%" to "%", "$" to "$", "ldots" to "…", "cdots" to "⋯", "dots" to "…",
    "langle" to "⟨", "rangle" to "⟩", "lvert" to "|", "rvert" to "|", "mid" to "|",
)
private val BLACKBOARD = mapOf("R" to "ℝ", "N" to "ℕ", "Z" to "ℤ", "Q" to "ℚ", "C" to "ℂ")
private val DROP = setOf("left", "right", "big", "Big", "bigg", "displaystyle", "limits")
private val TEXT_CMDS = setOf("text", "mathrm", "mathbf", "mathit", "operatorname", "textbf", "vec", "overline", "hat", "bar")

fun parseMath(input: String): List<MathSeg> {
    val out = mutableListOf<MathSeg>()
    Parser(input, out).parse(script = 0, mathMode = false)
    return merge(out)
}

private fun merge(segs: List<MathSeg>): List<MathSeg> {
    val res = mutableListOf<MathSeg>()
    for (s in segs) {
        if (s.text.isEmpty()) continue
        val last = res.lastOrNull()
        if (last != null && last.script == s.script) res[res.lastIndex] = last.copy(text = last.text + s.text) else res += s
    }
    return res
}

private class Parser(val s: String, val out: MutableList<MathSeg>) {
    var i = 0

    fun parse(script: Int, mathMode: Boolean, stopAtBrace: Boolean = false) {
        var math = mathMode
        while (i < s.length) {
            val c = s[i]
            when {
                c == '}' && stopAtBrace -> { i++; return }
                c == '$' -> { i++; math = !math }
                c == '\\' -> command(script)
                c == '^' && i + 1 < s.length && s[i + 1] != ' ' -> { i++; argument(1) }
                c == '_' && math && i + 1 < s.length && s[i + 1] != '_' -> { i++; argument(-1) }
                c == '_' && !math && i + 1 < s.length && s[i + 1] == '{' -> { i++; argument(-1) }
                (c == '{' || c == '}') && math -> i++
                else -> { out += MathSeg(c.toString(), script); i++ }
            }
        }
    }

    /** Superscript/subscript argument: a {group} or a single character. Nested scripts flatten. */
    private fun argument(script: Int) {
        if (i >= s.length) return
        when (s[i]) {
            '{' -> { i++; parse(script, true, stopAtBrace = true) }
            '\\' -> command(script)
            else -> { out += MathSeg(s[i].toString(), script); i++ }
        }
    }

    private fun group(): String {
        if (i >= s.length || s[i] != '{') return if (i < s.length) s[i++].toString() else ""
        var depth = 0; val start = i
        while (i < s.length) {
            if (s[i] == '{') depth++ else if (s[i] == '}') { depth--; if (depth == 0) { i++; return s.substring(start + 1, i - 1) } }
            i++
        }
        return s.substring(start + 1)
    }

    private fun emitInline(text: String, script: Int, wrap: Boolean) {
        val inner = mutableListOf<MathSeg>()
        Parser(text, inner).parse(script, mathMode = true)
        val plain = inner.joinToString("") { it.text }
        val simple = plain.length <= 1 || plain.all { it.isLetterOrDigit() || it == '.' } && inner.all { it.script == script }
        if (wrap && !simple) out += MathSeg("(", script)
        out += inner
        if (wrap && !simple) out += MathSeg(")", script)
    }

    private fun command(script: Int) {
        i++ // backslash
        if (i >= s.length) return
        val start = i
        if (s[i].isLetter()) while (i < s.length && s[i].isLetter()) i++ else i++
        val name = s.substring(start, i)
        when {
            name == "frac" || name == "dfrac" || name == "tfrac" -> {
                val a = group(); val b = group()
                emitInline(a, script, wrap = true); out += MathSeg("/", script); emitInline(b, script, wrap = true)
            }
            name == "sqrt" -> {
                var root = ""
                if (i < s.length && s[i] == '[') { val e = s.indexOf(']', i); if (e > 0) { root = s.substring(i + 1, e); i = e + 1 } }
                out += MathSeg(when (root) { "" , "2" -> "√"; "3" -> "∛"; "4" -> "∜"; else -> "$root√" }, script)
                emitInline(group(), script, wrap = true)
            }
            name == "mathbb" -> { val g = group(); out += MathSeg(BLACKBOARD[g] ?: g, script) }
            name in TEXT_CMDS -> { val g = group(); emitInline(g, script, wrap = false); if (name == "vec") out += MathSeg("⃗", script) }
            name in DROP -> {}
            else -> out += MathSeg(SYMBOLS[name] ?: name, script)
        }
    }
}
