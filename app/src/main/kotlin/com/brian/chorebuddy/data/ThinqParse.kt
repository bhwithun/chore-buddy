package com.brian.chorebuddy.data

data class ParsedCycle(
    val state: String,
    val course: String,
    val remainMinutes: Int?,
    val totalMinutes: Int?,
)

class ThinqException(message: String, val code: String = "") : Exception(message)

fun unwrapThinq(text: String): JsonValue {
    val root = Json.parse(text).asObj() ?: error("LG response was not an object")
    val errorObj = root.obj("error")
    if (errorObj != null) {
        throw ThinqException(
            message = errorObj.str("message") ?: "LG API error",
            code = errorObj.str("code").orEmpty(),
        )
    }
    return root.fields["response"] ?: root.fields["result"] ?: root
}

fun parseDevices(text: String): List<ThinqDevice> {
    val payload = unwrapThinq(text)
    val array = when (payload) {
        is JsonValue.Arr -> payload
        is JsonValue.Obj -> {
            (payload.fields["devices"] as? JsonValue.Arr)
                ?: (payload.fields["item"] as? JsonValue.Arr)
        }
        else -> null
    } ?: return emptyList()
    return array.items.mapNotNull { item ->
        val obj = item.asObj() ?: return@mapNotNull null
        val info = obj.obj("deviceInfo")
        val id = obj.str("deviceId") ?: info?.str("deviceId") ?: return@mapNotNull null
        val type = info?.str("deviceType") ?: obj.str("deviceType") ?: ""
        val alias = info?.str("alias") ?: obj.str("alias") ?: ""
        val model = info?.str("modelName") ?: obj.str("modelName") ?: ""
        ThinqDevice(id = id, type = type, alias = alias, model = model)
    }
}

fun parseCycle(root: JsonValue, role: ApplianceRole, zone: String): ParsedCycle {
    val obj = root.asObj() ?: return ParsedCycle("", "", null, null)
    val zoned = if (zone.isNotBlank()) {
        obj.fields.entries.firstOrNull { it.key.equals(zone, ignoreCase = true) }?.value?.asObj()
    } else {
        null
    }
    val node = zoned ?: bestNode(obj, role) ?: obj
    return readNode(node)
}

fun lgErrorCode(body: String): String = try {
    Json.parse(body).asObj()?.obj("error")?.str("code").orEmpty()
} catch (_: Exception) {
    ""
}

fun errorMessage(body: String, http: Int): String {
    return try {
        val root = Json.parse(body).asObj()
        val err = root?.obj("error")
        val message = err?.str("message") ?: root?.str("message")
        val code = err?.str("code").orEmpty()
        when {
            code == "1103" || code == "1218" || http == 401 ->
                "LG rejected the token. Create a new one and paste it here."
            code == "1306" -> "LG rate limit reached. The widget will try again later."
            code == "1222" -> "A machine is offline."
            !message.isNullOrBlank() -> message
            else -> "LG request failed ($http)"
        }
    } catch (_: Exception) {
        if (http == 401) "LG rejected the token. Create a new one and paste it here."
        else "LG request failed ($http)"
    }
}

private fun bestNode(root: JsonValue.Obj, role: ApplianceRole): JsonValue.Obj? {
    val nodes = mutableListOf<Pair<String, JsonValue.Obj>>()
    fun walk(path: String, node: JsonValue.Obj) {
        val hasCycle = node.fields.containsKey("runState") ||
            node.fields.containsKey("timer") ||
            node.fields.containsKey("currentState")
        if (hasCycle && path.isNotEmpty()) nodes += path to node
        node.fields.forEach { (key, value) ->
            if (value is JsonValue.Obj) {
                val next = if (path.isEmpty()) key else "$path.$key"
                walk(next, value)
            }
        }
    }
    walk("", root)
    if (root.fields.containsKey("runState") || root.fields.containsKey("timer")) {
        nodes += "" to root
    }
    if (nodes.isEmpty()) return null
    val best = nodes.maxBy { score(it.first, role) }
    if (score(best.first, role) > 0) return best.second
    return nodes.firstOrNull { it.first.isEmpty() }?.second ?: nodes.first().second
}

private fun score(path: String, role: ApplianceRole): Int {
    val key = path.lowercase()
    if (key.isEmpty()) return 0
    val dish = key.contains("dish")
    val dryer = key.contains("dryer") || key.contains("dry")
    val washer = key.contains("washer") || (key.contains("wash") && !dish)
    return when (role) {
        ApplianceRole.DISHWASHER -> when {
            dish -> 10
            washer || dryer -> -5
            else -> 0
        }
        ApplianceRole.DRYER -> when {
            dryer && !dish -> 10
            washer || dish -> -5
            else -> 0
        }
        ApplianceRole.WASHER -> when {
            washer && !dryer && !dish -> 10
            dryer || dish -> -5
            else -> 0
        }
    }
}

private fun readNode(node: JsonValue.Obj): ParsedCycle {
    val (remain, total) = readTimer(node)
    return ParsedCycle(
        state = readState(node),
        course = readCourse(node),
        remainMinutes = remain,
        totalMinutes = total,
    )
}

private fun readState(node: JsonValue.Obj): String {
    val run = node.obj("runState")
    return (run?.str("currentState") ?: node.str("currentState") ?: node.str("state") ?: "").trim()
}

private fun readTimer(node: JsonValue.Obj): Pair<Int?, Int?> {
    val timer = node.obj("timer") ?: node
    val remain = minutes(
        timer.int("remainHour") ?: timer.int("remainTimeHour"),
        timer.int("remainMinute") ?: timer.int("remainTimeMinute"),
    )
    val total = minutes(
        timer.int("totalHour") ?: timer.int("initialHour") ?: timer.int("initialTimeHour"),
        timer.int("totalMinute") ?: timer.int("initialMinute") ?: timer.int("initialTimeMinute"),
    )
    return remain to total
}

private fun minutes(hour: Int?, minute: Int?): Int? {
    if (hour == null && minute == null) return null
    return (hour ?: 0).coerceAtLeast(0) * 60 + (minute ?: 0).coerceAtLeast(0)
}

private fun readCourse(node: JsonValue.Obj): String {
    val hits = mutableListOf<String>()
    fun walk(obj: JsonValue.Obj, depth: Int) {
        if (depth > 3) return
        obj.fields.forEach { (key, value) ->
            val name = key.lowercase()
            if (value is JsonValue.Str && (name.contains("course") || name.contains("cycle"))) {
                val text = value.value.trim()
                val token = normalizeState(text)
                if (text.isNotEmpty() && token != "NOT_SELECTED" && token != "NONE" && token != "NO_COURSE") {
                    hits += text
                }
            } else if (value is JsonValue.Obj) {
                walk(value, depth + 1)
            }
        }
    }
    walk(node, 0)
    return hits.firstOrNull()?.let(::prettyToken).orEmpty()
}

fun toCycle(role: ApplianceRole, alias: String, parsed: ParsedCycle, linked: Boolean): ApplianceCycle {
    val running = linked && isRunningState(parsed.state)
    val baseline = countdownBaseline(
        ApplianceCycle(
            role = role,
            alias = alias,
            state = parsed.state,
            course = parsed.course,
            running = running,
            progress = 0f,
            remainMinutes = parsed.remainMinutes,
            totalMinutes = parsed.totalMinutes,
            linked = linked,
        ),
    )
    return ApplianceCycle(
        role = role,
        alias = alias.ifBlank { role.title },
        state = parsed.state,
        course = parsed.course,
        running = running,
        progress = cycleProgress(running, baseline, parsed.totalMinutes),
        remainMinutes = parsed.remainMinutes,
        totalMinutes = parsed.totalMinutes?.takeIf { it > 0 },
        linked = linked,
    )
}
