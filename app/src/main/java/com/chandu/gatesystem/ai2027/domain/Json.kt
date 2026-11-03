package com.chandu.gatesystem.ai2027.domain

/**
 * Minimal dependency-free JSON reader/writer.
 *
 * It is used by the domain layer so that plan loading, AI-response parsing and
 * backup code can be unit-tested on a plain JVM (android.org.json is stubbed in
 * local unit tests). Objects -> Map<String, Any?>, arrays -> List<Any?>,
 * integers -> Long, other numbers -> Double.
 */
object Json {
    class JsonException(message: String) : Exception(message)

    fun parse(text: String): Any? {
        val p = Parser(text)
        p.skipWs()
        val v = p.value()
        p.skipWs()
        if (!p.atEnd()) throw JsonException("Trailing characters at ${p.pos}")
        return v
    }

    @Suppress("UNCHECKED_CAST")
    fun parseObject(text: String): Map<String, Any?> =
        parse(text) as? Map<String, Any?> ?: throw JsonException("Top-level JSON value is not an object")

    fun stringify(v: Any?): String = StringBuilder().also { write(it, v) }.toString()

    private fun write(sb: StringBuilder, v: Any?) {
        when (v) {
            null -> sb.append("null")
            is String -> quote(sb, v)
            is Boolean -> sb.append(v.toString())
            is Int, is Long -> sb.append(v.toString())
            is Double -> if (v.isNaN() || v.isInfinite()) sb.append("null") else sb.append(v.toString())
            is Float -> sb.append(v.toString())
            is Map<*, *> -> {
                sb.append('{'); var first = true
                for ((k, x) in v) {
                    if (!first) sb.append(','); first = false
                    quote(sb, k.toString()); sb.append(':'); write(sb, x)
                }
                sb.append('}')
            }
            is Iterable<*> -> {
                sb.append('['); var first = true
                for (x in v) { if (!first) sb.append(','); first = false; write(sb, x) }
                sb.append(']')
            }
            is Set<*> -> write(sb, v.toList())
            else -> quote(sb, v.toString())
        }
    }

    private fun quote(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) when (c) {
            '"' -> sb.append("\\\""); '\\' -> sb.append("\\\\")
            '\n' -> sb.append("\\n"); '\r' -> sb.append("\\r"); '\t' -> sb.append("\\t")
            else -> if (c < ' ') sb.append(String.format("\\u%04x", c.code)) else sb.append(c)
        }
        sb.append('"')
    }

    private class Parser(val s: String) {
        var pos = 0
        fun atEnd() = pos >= s.length
        fun skipWs() { while (pos < s.length && s[pos].isWhitespace()) pos++ }
        fun value(): Any? {
            if (atEnd()) throw JsonException("Unexpected end of input")
            return when (val c = s[pos]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> lit("true", true)
                'f' -> lit("false", false)
                'n' -> lit("null", null)
                else -> if (c == '-' || c.isDigit()) num() else throw JsonException("Unexpected '$c' at $pos")
            }
        }
        fun lit(w: String, v: Any?): Any? {
            if (!s.startsWith(w, pos)) throw JsonException("Bad literal at $pos")
            pos += w.length; return v
        }
        fun obj(): Map<String, Any?> {
            val m = LinkedHashMap<String, Any?>()
            pos++; skipWs()
            if (s.getOrNull(pos) == '}') { pos++; return m }
            while (true) {
                skipWs()
                if (s.getOrNull(pos) != '"') throw JsonException("Expected key at $pos")
                val k = str(); skipWs()
                if (s.getOrNull(pos) != ':') throw JsonException("Expected ':' at $pos")
                pos++; skipWs()
                m[k] = value(); skipWs()
                when (s.getOrNull(pos)) {
                    ',' -> pos++
                    '}' -> { pos++; return m }
                    else -> throw JsonException("Expected ',' or '}' at $pos")
                }
            }
        }
        fun arr(): List<Any?> {
            val l = ArrayList<Any?>()
            pos++; skipWs()
            if (s.getOrNull(pos) == ']') { pos++; return l }
            while (true) {
                skipWs(); l.add(value()); skipWs()
                when (s.getOrNull(pos)) {
                    ',' -> pos++
                    ']' -> { pos++; return l }
                    else -> throw JsonException("Expected ',' or ']' at $pos")
                }
            }
        }
        fun str(): String {
            val sb = StringBuilder(); pos++
            while (true) {
                if (atEnd()) throw JsonException("Unterminated string")
                val c = s[pos++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (atEnd()) throw JsonException("Bad escape")
                        when (val e = s[pos++]) {
                            '"' -> sb.append('"'); '\\' -> sb.append('\\'); '/' -> sb.append('/')
                            'n' -> sb.append('\n'); 'r' -> sb.append('\r'); 't' -> sb.append('\t')
                            'b' -> sb.append('\b'); 'f' -> sb.append('\u000c')
                            'u' -> {
                                if (pos + 4 > s.length) throw JsonException("Bad unicode escape")
                                sb.append(s.substring(pos, pos + 4).toInt(16).toChar()); pos += 4
                            }
                            else -> throw JsonException("Bad escape \\$e")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }
        fun num(): Any {
            val st = pos
            if (s[pos] == '-') pos++
            while (pos < s.length && (s[pos].isDigit() || s[pos] in ".eE+-")) pos++
            val t = s.substring(st, pos)
            return t.toLongOrNull() ?: t.toDoubleOrNull() ?: throw JsonException("Bad number '$t'")
        }
    }
}

/** Typed accessors for parsed JSON maps. */
@Suppress("UNCHECKED_CAST")
fun Map<String, Any?>.str(k: String): String? = this[k] as? String
fun Map<String, Any?>.int(k: String): Int? = (this[k] as? Number)?.toInt()
fun Map<String, Any?>.dbl(k: String): Double? = (this[k] as? Number)?.toDouble()
fun Map<String, Any?>.bool(k: String): Boolean? = this[k] as? Boolean
@Suppress("UNCHECKED_CAST")
fun Map<String, Any?>.list(k: String): List<Any?>? = this[k] as? List<Any?>
@Suppress("UNCHECKED_CAST")
fun Map<String, Any?>.obj(k: String): Map<String, Any?>? = this[k] as? Map<String, Any?>
