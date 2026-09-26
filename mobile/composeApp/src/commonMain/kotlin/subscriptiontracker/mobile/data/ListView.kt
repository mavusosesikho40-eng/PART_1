package subscriptiontracker.mobile.data

/** How the subscriptions list is searched, filtered and sorted. */
object ListView {

    enum class Sort(val label: String) {
        NEXT_PAYMENT("Next payment"),
        NAME("Name"),
        COST("Cost per month"),
        CATEGORY("Category"),
    }

    /**
     * Whether a subscription is shown: the search matches its name or
     * category, ignoring case; a null cycle means any cycle.
     */
    fun matches(s: Subscription, search: String, cycle: BillingCycle?): Boolean {
        if (cycle != null && s.cycle != cycle) return false
        val needle = search.trim().lowercase()
        return needle.isEmpty() || needle in s.name.lowercase() || needle in s.category.lowercase()
    }

    /**
     * Sorted as chosen; ties go by name, then by next payment. Costs are
     * highest first, compared per month in your currency.
     */
    fun sort(list: List<Subscription>, sort: Sort, manager: SubscriptionManager? = null): List<Subscription> {
        val byName = compareBy(String.CASE_INSENSITIVE_ORDER) { s: Subscription -> s.name }
        val comparator = when (sort) {
            Sort.NEXT_PAYMENT -> compareBy<Subscription> { it.nextPayment }.then(byName)
            Sort.NAME -> byName.thenBy { it.nextPayment }
            Sort.COST -> compareByDescending<Subscription> { manager?.homeMonthly(it) ?: it.monthlyCost }.then(byName)
            Sort.CATEGORY -> compareBy(String.CASE_INSENSITIVE_ORDER) { s: Subscription -> s.category }.then(byName)
        }
        return list.sortedWith(comparator)
    }
}
