package org.kosat

enum class VarValue {
    TRUE, FALSE, UNDEFINED;

    operator fun not(): VarValue {
        return when (this) {
            TRUE -> FALSE
            FALSE -> TRUE
            UNDEFINED -> UNDEFINED
        }
    }
}

data class VarState(
    var value: VarValue,
    var reason: Clause?,
    var level: Int,
)
