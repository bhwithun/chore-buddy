package com.brian.chorebuddy.data

object SnapshotCodec {
    fun encode(snapshot: LaundrySnapshot): String = buildString {
        append("v3\n")
        append(snapshot.fetchedAtEpochMs)
        append('\n')
        append(if (snapshot.usingSample) '1' else '0')
        append('\n')
        append(esc(snapshot.error))
        append('\n')
        append(snapshot.energyEpochDay)
        append('\n')
        append(encodeCycle(snapshot.washer))
        append('\n')
        append(encodeCycle(snapshot.dryer))
        append('\n')
        append(encodeCycle(snapshot.dishwasher))
    }

    fun decode(raw: String): LaundrySnapshot {
        val lines = raw.split('\n')
        if (lines.size < 7 || (lines[0] != "v1" && lines[0] != "v2" && lines[0] != "v3")) return sampleSnapshot()
        return try {
            val version3 = lines[0] == "v3"
            if (version3 && lines.size < 8) return sampleSnapshot()
            val cycleAt = if (version3) 5 else 4
            LaundrySnapshot(
                washer = decodeCycle(lines[cycleAt], ApplianceRole.WASHER),
                dryer = decodeCycle(lines[cycleAt + 1], ApplianceRole.DRYER),
                dishwasher = decodeCycle(lines[cycleAt + 2], ApplianceRole.DISHWASHER),
                fetchedAtEpochMs = lines[1].toLong(),
                error = unesc(lines[3]),
                usingSample = lines[2] == "1",
                energyEpochDay = if (version3) lines[4].toLongOrNull() ?: 0L else 0L,
            )
        } catch (_: Exception) {
            sampleSnapshot()
        }
    }

    fun encodeDevices(devices: List<ThinqDevice>): String =
        devices.joinToString("\n") { device ->
            listOf(device.id, device.type, device.alias, device.model).joinToString("|") { esc(it) }
        }

    fun decodeDevices(raw: String): List<ThinqDevice> {
        if (raw.isBlank()) return emptyList()
        return raw.lineSequence().mapNotNull { line ->
            val parts = splitFields(line)
            if (parts.size < 4 || parts[0].isBlank()) null
            else ThinqDevice(parts[0], parts[1], parts[2], parts[3])
        }.toList()
    }

    private fun encodeCycle(cycle: ApplianceCycle): String {
        val remain = cycle.remainMinutes?.toString().orEmpty()
        return listOf(
            if (cycle.linked) "1" else "0",
            if (cycle.running) "1" else "0",
            "%.4f".format(java.util.Locale.US, cycle.progress),
            remain,
            cycle.totalMinutes?.toString().orEmpty(),
            cycle.state,
            cycle.course,
            cycle.alias,
            cycle.lastMonthWh?.toString().orEmpty(),
            cycle.thisMonthWh?.toString().orEmpty(),
            encodeSeries(cycle.thisYearWh),
            encodeSeries(cycle.priorYearWh),
        ).joinToString("|") { esc(it) }
    }

    private fun encodeSeries(values: List<Int>?): String = when {
        values == null -> ""
        values.isEmpty() -> "-"
        else -> values.joinToString(",")
    }

    private fun decodeSeries(raw: String?): List<Int>? = when {
        raw.isNullOrEmpty() -> null
        raw == "-" -> emptyList()
        else -> raw.split(",").map { it.toIntOrNull() ?: 0 }
    }

    private fun decodeCycle(line: String, role: ApplianceRole): ApplianceCycle {
        val parts = splitFields(line)
        val linked = parts.getOrNull(0) == "1"
        val running = parts.getOrNull(1) == "1"
        val progress = parts.getOrNull(2)?.toFloatOrNull() ?: 0f
        val remain = parts.getOrNull(3)?.toIntOrNull()
        val hasTotal = parts.size >= 8
        val total = if (hasTotal) parts.getOrNull(4)?.toIntOrNull() else null
        val stateIndex = if (hasTotal) 5 else 4
        val courseIndex = if (hasTotal) 6 else 5
        val aliasIndex = if (hasTotal) 7 else 6
        val lastMonth = parts.getOrNull(aliasIndex + 1)?.toIntOrNull()
        val thisMonth = parts.getOrNull(aliasIndex + 2)?.toIntOrNull()
        return ApplianceCycle(
            role = role,
            alias = parts.getOrElse(aliasIndex) { role.title }.ifBlank { role.title },
            state = parts.getOrElse(stateIndex) { "" },
            course = parts.getOrElse(courseIndex) { "" },
            running = running,
            progress = progress,
            remainMinutes = remain,
            totalMinutes = total,
            linked = linked,
            lastMonthWh = lastMonth,
            thisMonthWh = thisMonth,
            thisYearWh = decodeSeries(parts.getOrNull(aliasIndex + 3)),
            priorYearWh = decodeSeries(parts.getOrNull(aliasIndex + 4)),
        )
    }

    private fun splitFields(line: String): List<String> {
        val out = mutableListOf<String>()
        val current = StringBuilder()
        var escaping = false
        for (ch in line) {
            if (escaping) {
                current.append('\\').append(ch)
                escaping = false
            } else when (ch) {
                '\\' -> escaping = true
                '|' -> {
                    out += unesc(current.toString())
                    current.clear()
                }
                else -> current.append(ch)
            }
        }
        out += unesc(current.toString())
        return out
    }

    private fun esc(value: String): String =
        value.replace("\\", "\\\\").replace("|", "\\p").replace("\n", " ")

    private fun unesc(value: String): String {
        val out = StringBuilder()
        var escaping = false
        for (ch in value) {
            if (escaping) {
                out.append(if (ch == 'p') '|' else ch)
                escaping = false
            } else if (ch == '\\') {
                escaping = true
            } else {
                out.append(ch)
            }
        }
        return out.toString()
    }
}
