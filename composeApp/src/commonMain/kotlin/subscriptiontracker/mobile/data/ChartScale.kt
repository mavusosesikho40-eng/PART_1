package subscriptiontracker.mobile.data

/** Round numbers for a chart's value axis. */
object ChartScale {

    /**
     * Tick values (in cents) from 0 up to at least [max], about [count]
     * steps, each step a round 1, 2, 2.5 or 5 times a power of ten:
     * 0 / 500 / 1,000 / 1,500 rather than 0 / 437 / 874.
     */
    fun ticks(max: Long, count: Int = 4): List<Long> {
        if (max <= 0) return listOf(0, 10_000)
        val rough = max.toDouble() / count
        var power = 1L
        while (power * 10 <= rough) power *= 10
        val step = listOf(1.0, 2.0, 2.5, 5.0, 10.0)
            .map { (it * power).toLong() }
            .first { it >= rough }
        val top = ((max + step - 1) / step) * step
        return (0..(top / step)).map { it * step }
    }
}
