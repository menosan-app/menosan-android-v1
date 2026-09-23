package app.menosan.android.data.repo

import app.menosan.android.core.time.WeekCalc
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.Clock
import java.time.Duration
import java.time.LocalDate

/**
 * The current Manila week start, re-emitted when the week rolls over (Sunday 00:00 PHT). It re-checks the [clock] at
 * least every [maxTick], because a single long delay doesn't advance while the phone sleeps. (AN-1)
 */
fun currentWeekStartFlow(clock: Clock, maxTick: Duration = Duration.ofMinutes(1)): Flow<LocalDate> = flow {
    while (true) {
        val week = WeekCalc.currentWeekStart(clock)
        emit(week)
        val untilRollover = Duration.between(clock.instant(), WeekCalc.endExclusive(week)).toMillis()
        delay(untilRollover.coerceIn(1L, maxTick.toMillis()))
    }
}.distinctUntilChanged()
