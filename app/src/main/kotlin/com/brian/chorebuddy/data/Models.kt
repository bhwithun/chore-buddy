package com.brian.chorebuddy.data

enum class ApplianceRole {
    WASHER,
    DRYER,
    DISHWASHER;

    val title: String
        get() = when (this) {
            WASHER -> "Wash"
            DRYER -> "Dry"
            DISHWASHER -> "Dishes"
        }
}

data class ApplianceCycle(
    val role: ApplianceRole,
    val alias: String,
    val state: String,
    val course: String,
    val running: Boolean,
    val progress: Float,
    val remainMinutes: Int?,
    val totalMinutes: Int? = null,
    val linked: Boolean,
    val lastMonthWh: Int? = null,
    val thisMonthWh: Int? = null,
    /** Watt-hours for January–December of this year. Null until loaded. Empty when unsupported. */
    val thisYearWh: List<Int>? = null,
    /** Watt-hours for January–December of last year. Null until loaded. Empty when unsupported. */
    val priorYearWh: List<Int>? = null,
)

data class LaundrySnapshot(
    val washer: ApplianceCycle,
    val dryer: ApplianceCycle,
    val dishwasher: ApplianceCycle,
    val fetchedAtEpochMs: Long,
    val error: String,
    val usingSample: Boolean,
    /** Local epoch day of the last energy reading. Zero means energy has not been loaded. */
    val energyEpochDay: Long = 0,
) {
    fun cycles(): List<ApplianceCycle> = listOf(washer, dryer, dishwasher)

    fun cycle(role: ApplianceRole): ApplianceCycle = when (role) {
        ApplianceRole.WASHER -> washer
        ApplianceRole.DRYER -> dryer
        ApplianceRole.DISHWASHER -> dishwasher
    }
}

data class ThinqDevice(
    val id: String,
    val type: String,
    val alias: String,
    val model: String,
) {
    val label: String
        get() {
            val name = alias.ifBlank { type }
            val kind = type.removePrefix("DEVICE_").replace('_', ' ').lowercase()
                .replaceFirstChar { it.uppercase() }
            return if (kind.isBlank() || name.equals(kind, ignoreCase = true)) name else "$name · $kind"
        }
}

data class ApplianceSlot(
    val deviceId: String,
    val zone: String,
    val alias: String,
)

data class SlotMap(
    val washer: ApplianceSlot?,
    val dryer: ApplianceSlot?,
    val dishwasher: ApplianceSlot?,
)
