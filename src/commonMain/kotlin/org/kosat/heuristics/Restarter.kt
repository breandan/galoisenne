package org.kosat.heuristics

import org.kosat.CDCL

// used for restarts between searches (luby restarts are used now)
class Restarter(private val solver: CDCL) {

    private val lubyMultiplierConstant = 50.0
    private var restartNumber = lubyMultiplierConstant
    private var numberOfConflictsAfterRestart = 0

    // 1, 1, 2, 1, 1, 2, 4, 1, 1, 2, 1, 1, 2, 4, 8, ...
    // return i'th element of luby sequence
    private fun luby(i: Int, initialDeg: Int = 1): Int {
        if (i == 2) return 1
        var deg = initialDeg
        while (deg <= i) {
            deg *= 2
        }
        while (deg / 2 > i) {
            deg /= 2
        }
        if (deg - 1 == i) {
            return deg / 2
        }
        return luby(i - deg / 2 + 1, deg / 2)
    }

    private var lubyPosition = 1

    fun restart() {
        restartNumber = lubyMultiplierConstant * luby(lubyPosition++)
        solver.reset()
    }


    fun update() {
        numberOfConflictsAfterRestart++
        if (numberOfConflictsAfterRestart >= restartNumber) {
            numberOfConflictsAfterRestart = 0
            restart()
        }
    }
}
