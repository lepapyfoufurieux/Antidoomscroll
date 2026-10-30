package com.antiscrolling.domain

sealed interface Decision {
    data object Allow : Decision
    data class Block(val reason: Reason) : Decision
    data class Friction(val delaySec: Int, val steps: List<Step>) : Decision
}

enum class Reason { FOCUS, SCHEDULE, QUOTA }
