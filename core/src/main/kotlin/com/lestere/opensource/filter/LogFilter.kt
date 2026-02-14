package com.lestere.opensource.filter

import com.lestere.opensource.logger.Logger
import com.lestere.opensource.logger.SoulLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.random.Random

interface LogFilter {
    fun shouldLog(entry: Logger): Boolean
    fun shouldSample(entry: Logger): Boolean = true
    fun getName(): String = this::class.simpleName ?: "LogFilter"
}

class LevelFilter(
    private val minLevel: SoulLogger.Level = SoulLogger.Level.INFO
) : LogFilter {
    override fun shouldLog(entry: Logger): Boolean {
        return entry.logLevel >= minLevel
    }
    
    override fun getName(): String = "LevelFilter($minLevel)"
}

class CompositeFilter(
    private val filters: List<LogFilter>
) : LogFilter {
    
    constructor(vararg filters: LogFilter) : this(filters.toList())
    
    override fun shouldLog(entry: Logger): Boolean {
        return filters.all { it.shouldLog(entry) }
    }
    
    override fun shouldSample(entry: Logger): Boolean {
        return filters.all { it.shouldSample(entry) }
    }
    
    override fun getName(): String = "CompositeFilter(${filters.size} filters)"
}

class RateFilter(
    private val sampleRate: Double = 1.0,
    private val strategy: SamplingStrategy = SamplingStrategy.RANDOM,
    private val seed: Long = System.nanoTime()
) : LogFilter {
    
    enum class SamplingStrategy {
        RANDOM,
        SYSTEMATIC,
        ADAPTIVE,
        BURST
    }
    
    private val random = Random(seed)
    private var counter = 0L
    private var errorBoostEndTime = 0L
    private var currentBoostRate = 1.0
    
    @Volatile
    private var _sampleRate: Double = sampleRate
    
    var dynamicSampleRate: Double
        get() = _sampleRate
        set(value) { _sampleRate = value.coerceIn(0.0, 1.0) }
    
    override fun shouldLog(entry: Logger): Boolean = true
    
    override fun shouldSample(entry: Logger): Boolean {
        // Check if error boost is active
        val now = System.currentTimeMillis()
        if (entry.logLevel >= SoulLogger.Level.ERROR && now < errorBoostEndTime) {
            return true // Always sample errors during boost period
        }
        
        return when (strategy) {
            SamplingStrategy.RANDOM -> random.nextDouble() < _sampleRate * currentBoostRate
            SamplingStrategy.SYSTEMATIC -> {
                counter++
                (counter % (1.0 / (_sampleRate * currentBoostRate)).toLong()) == 0L
            }
            SamplingStrategy.ADAPTIVE -> {
                val rate = if (entry.logLevel >= SoulLogger.Level.ERROR) {
                    1.0 // Always sample errors
                } else {
                    _sampleRate * currentBoostRate
                }
                random.nextDouble() < rate
            }
            SamplingStrategy.BURST -> {
                if (entry.logLevel >= SoulLogger.Level.ERROR) {
                    enableErrorBoost(60_000) // 1 minute
                    true
                }
                random.nextDouble() < _sampleRate
            }
        }
    }
    
    fun enableErrorBoost(durationMs: Long) {
        errorBoostEndTime = System.currentTimeMillis() + durationMs
        currentBoostRate = 10.0 // Boost by 10x
    }
    
    fun disableErrorBoost() {
        errorBoostEndTime = 0
        currentBoostRate = 1.0
    }
    
    fun reset() {
        counter = 0
        disableErrorBoost()
    }
    
    override fun getName(): String = "RateFilter($_sampleRate, $strategy)"
}

class RegexFilter(
    private val includePatterns: List<Regex> = emptyList(),
    private val excludePatterns: List<Regex> = emptyList()
) : LogFilter {
    
    constructor(
        includePattern: String?,
        excludePattern: String?
    ) : this(
        includePattern?.let { listOf(Regex(it)) } ?: emptyList(),
        excludePattern?.let { listOf(Regex(it)) } ?: emptyList()
    )
    
    override fun shouldLog(entry: Logger): Boolean {
        // If include patterns exist, must match at least one
        if (includePatterns.isNotEmpty()) {
            val matches = includePatterns.any { 
                it.containsMatchIn(entry.command) || it.containsMatchIn(entry.entry)
            }
            if (!matches) return false
        }
        
        // If exclude patterns exist, must not match any
        if (excludePatterns.isNotEmpty()) {
            val matches = excludePatterns.any { 
                it.containsMatchIn(entry.command) || it.containsMatchIn(entry.entry)
            }
            if (matches) return false
        }
        
        return true
    }
    
    override fun getName(): String = "RegexFilter(include=${includePatterns.size}, exclude=${excludePatterns.size})"
}

class DynamicFilter(
    private val rules: List<FilterRule> = emptyList()
) : LogFilter {
    
    data class FilterRule(
        val name: String,
        val condition: (Logger) -> Boolean,
        val action: FilterAction
    )
    
    enum class FilterAction {
        LOG,
        DROP,
        SAMPLE_WITH_RATE,
        BOOST_LEVEL
    }
    
    private val _activeRules = MutableStateFlow<List<FilterRule>>(rules)
    val activeRules: StateFlow<List<FilterRule>> = _activeRules
    
    fun addRule(rule: FilterRule) {
        _activeRules.value = _activeRules.value + rule
    }
    
    fun removeRule(name: String) {
        _activeRules.value = _activeRules.value.filter { it.name != name }
    }
    
    fun clearRules() {
        _activeRules.value = emptyList()
    }
    
    override fun shouldLog(entry: Logger): Boolean {
        for (rule in _activeRules.value) {
            when (rule.action) {
                FilterAction.DROP -> {
                    if (rule.condition(entry)) return false
                }
                FilterAction.LOG -> {
                    // Default behavior, continue checking
                }
                else -> {}
            }
        }
        return true
    }
    
    override fun shouldSample(entry: Logger): Boolean {
        for (rule in _activeRules.value) {
            when (rule.action) {
                FilterAction.SAMPLE_WITH_RATE -> {
                    if (rule.condition(entry)) {
                        return Random.nextDouble() < 0.5 // 50% sample rate for matching entries
                    }
                }
                else -> {}
            }
        }
        return true
    }
    
    override fun getName(): String = "DynamicFilter(${rules.size} rules)"
}

class FilterChain(
    private val levelFilter: LevelFilter = LevelFilter(),
    private val rateFilter: RateFilter = RateFilter(),
    private val regexFilter: RegexFilter = RegexFilter(),
    private val dynamicFilter: DynamicFilter = DynamicFilter()
) : LogFilter {
    
    private val allFilters: List<LogFilter> = listOf(levelFilter, rateFilter, regexFilter, dynamicFilter)
    
    override fun shouldLog(entry: Logger): Boolean {
        return allFilters.all { it.shouldLog(entry) }
    }
    
    override fun shouldSample(entry: Logger): Boolean {
        return allFilters.all { it.shouldSample(entry) }
    }
    
    fun getFilters(): List<LogFilter> = allFilters
    
    fun setMinLevel(level: SoulLogger.Level) {
        // Would need to make LevelFilter mutable or recreate
    }
    
    fun setSampleRate(rate: Double) {
        rateFilter.dynamicSampleRate = rate
    }
    
    fun enableErrorBoost(durationMs: Long = 60_000) {
        rateFilter.enableErrorBoost(durationMs)
    }
    
    fun disableErrorBoost() {
        rateFilter.disableErrorBoost()
    }
    
    override fun getName(): String = "FilterChain"
}

data class FilterConfig(
    val enabled: Boolean = true,
    val minLevel: SoulLogger.Level = SoulLogger.Level.INFO,
    val samplingEnabled: Boolean = false,
    val sampleRate: Double = 1.0,
    val samplingStrategy: RateFilter.SamplingStrategy = RateFilter.SamplingStrategy.RANDOM,
    val includePatterns: List<String> = emptyList(),
    val excludePatterns: List<String> = emptyList(),
    val errorBoostEnabled: Boolean = true,
    val errorBoostDurationMs: Long = 60_000,
    val errorBoostRate: Double = 10.0
) {
    fun toFilterChain(): FilterChain {
        return FilterChain(
            levelFilter = LevelFilter(minLevel),
            rateFilter = RateFilter(
                sampleRate = sampleRate,
                strategy = when (samplingStrategy) {
                    RateFilter.SamplingStrategy.RANDOM -> RateFilter.SamplingStrategy.RANDOM
                    RateFilter.SamplingStrategy.SYSTEMATIC -> RateFilter.SamplingStrategy.SYSTEMATIC
                    RateFilter.SamplingStrategy.ADAPTIVE -> RateFilter.SamplingStrategy.ADAPTIVE
                    RateFilter.SamplingStrategy.BURST -> RateFilter.SamplingStrategy.BURST
                }
            ),
            regexFilter = RegexFilter(
                includePatterns.firstOrNull(),
                excludePatterns.firstOrNull()
            )
        )
    }
}
