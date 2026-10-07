package com.brian.chorebuddy.data

sealed class JsonValue {
    data class Obj(val fields: Map<String, JsonValue>) : JsonValue()
    data class Arr(val items: List<JsonValue>) : JsonValue()
    data class Str(val value: String) : JsonValue()
    data class Num(val value: Double) : JsonValue()
    data class Bool(val value: Boolean) : JsonValue()
    data object Null : JsonValue()
}

fun JsonValue.asObj(): JsonValue.Obj? = this as? JsonValue.Obj

fun JsonValue.Obj.str(key: String): String? = (fields[key] as? JsonValue.Str)?.value

fun JsonValue.Obj.obj(key: String): JsonValue.Obj? = fields[key] as? JsonValue.Obj

fun JsonValue.Obj.int(key: String): Int? = jsonInt(fields[key])

fun jsonInt(value: JsonValue?): Int? = when (value) {
    is JsonValue.Num -> value.value.toInt()
    is JsonValue.Str -> value.value.toDoubleOrNull()?.toInt()
    else -> null
}

object Json {
    fun parse(text: String): JsonValue {
        val parser = Parser(text)
        val value = parser.parseValue()
        parser.skipWs()
        if (!parser.eof()) error("Unexpected trailing JSON")
        return value
    }

    private class Parser(private val s: String) {
        private var i = 0

        fun eof(): Boolean = i >= s.length

        fun skipWs() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        fun parseValue(): JsonValue {
            skipWs()
            if (eof()) error("Unexpected end of JSON")
            return when (s[i]) {
                '{' -> parseObj()
                '[' -> parseArr()
                '"' -> JsonValue.Str(parseString())
                't' -> {
                    expect("true")
                    JsonValue.Bool(true)
                }
                'f' -> {
                    expect("false")
                    JsonValue.Bool(false)
                }
                'n' -> {
                    expect("null")
                    JsonValue.Null
                }
                else -> parseNum()
            }
        }

        private fun parseObj(): JsonValue.Obj {
            expectChar('{')
            val fields = linkedMapOf<String, JsonValue>()
            skipWs()
            if (peek('}')) {
                i++
                return JsonValue.Obj(fields)
            }
            while (true) {
                skipWs()
                val key = parseString()
                skipWs()
                expectChar(':')
                fields[key] = parseValue()
                skipWs()
                when {
                    peek(',') -> i++
                    peek('}') -> {
                        i++
                        break
                    }
                    else -> error("Expected , or } in object")
                }
            }
            return JsonValue.Obj(fields)
        }

        private fun parseArr(): JsonValue.Arr {
            expectChar('[')
            val items = mutableListOf<JsonValue>()
            skipWs()
            if (peek(']')) {
                i++
                return JsonValue.Arr(items)
            }
            while (true) {
                items += parseValue()
                skipWs()
                when {
                    peek(',') -> i++
                    peek(']') -> {
                        i++
                        break
                    }
                    else -> error("Expected , or ] in array")
                }
            }
            return JsonValue.Arr(items)
        }

        private fun parseString(): String {
            expectChar('"')
            val out = StringBuilder()
            while (i < s.length) {
                val c = s[i++]
                when (c) {
                    '"' -> return out.toString()
                    '\\' -> {
                        if (i >= s.length) error("Bad escape")
                        when (val escaped = s[i++]) {
                            '"', '\\', '/' -> out.append(escaped)
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000C')
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'u' -> {
                                if (i + 4 > s.length) error("Bad unicode escape")
                                out.append(s.substring(i, i + 4).toInt(16).toChar())
                                i += 4
                            }
                            else -> error("Bad escape")
                        }
                    }
                    else -> out.append(c)
                }
            }
            error("Unclosed string")
        }

        private fun parseNum(): JsonValue.Num {
            val start = i
            if (i < s.length && s[i] == '-') i++
            if (i >= s.length || !s[i].isDigit()) error("Bad number")
            while (i < s.length && s[i].isDigit()) i++
            if (i < s.length && s[i] == '.') {
                i++
                while (i < s.length && s[i].isDigit()) i++
            }
            if (i < s.length && (s[i] == 'e' || s[i] == 'E')) {
                i++
                if (i < s.length && (s[i] == '+' || s[i] == '-')) i++
                while (i < s.length && s[i].isDigit()) i++
            }
            val token = s.substring(start, i)
            val number = token.toDoubleOrNull() ?: error("Bad number $token")
            return JsonValue.Num(number)
        }

        private fun peek(c: Char): Boolean = i < s.length && s[i] == c

        private fun expectChar(c: Char) {
            if (i >= s.length || s[i] != c) error("Expected $c")
            i++
        }

        private fun expect(literal: String) {
            if (!s.startsWith(literal, i)) error("Expected $literal")
            i += literal.length
        }
    }
}
