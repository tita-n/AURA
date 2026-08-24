package com.aura.resolver.l1

import com.aura.domain.AuraAction
import com.aura.domain.ResolvedResult
import com.aura.domain.ResultType

/**
 * Unit conversion — deterministic table, no external APIs.
 * Supports: 10 km in miles, 5 kg in pounds, 100 C in F, etc.
 * Forms: "<value> <unit> in|to <unit>" case-insensitive.
 */
class UnitGrammar : L1Grammar {
    override fun name() = "Unit"

    private enum class UnitKind { Length, Mass, Temperature }

    private data class UnitDef(val aliases: Set<String>, val kind: UnitKind, val toBase: (Double) -> Double, val fromBase: (Double) -> Double)

    // Canonical base units: meters for length (via km), kilograms for mass, celsius for temp
    private val units: Map<String, UnitDef> = buildMap {
        // Length — base = kilometers
        val kmDef = UnitDef(setOf("km", "kms", "kilometer", "kilometers"), UnitKind.Length, { it }, { it })
        val mileDef = UnitDef(setOf("mile", "miles", "mi"), UnitKind.Length, { it / 0.621371 }, { it * 0.621371 })
        // Also support meters/feet as extra, but not required for spec examples — keep minimal
        // Mass — base = kg
        val kgDef = UnitDef(setOf("kg", "kgs", "kilogram", "kilograms", "kilo"), UnitKind.Mass, { it }, { it })
        val lbDef = UnitDef(setOf("pound", "pounds", "lb", "lbs"), UnitKind.Mass, { it / 2.20462 }, { it * 2.20462 })
        // Temperature — base = celsius
        val cDef = UnitDef(setOf("c", "celsius", "centigrade"), UnitKind.Temperature, { it }, { it })
        val fDef = UnitDef(setOf("f", "fahrenheit"), UnitKind.Temperature, { (it - 32) * 5.0/9.0 }, { it * 9.0/5.0 + 32 })

        listOf(kmDef, mileDef, kgDef, lbDef, cDef, fDef).forEach { def ->
            def.aliases.forEach { alias -> put(alias, def) }
        }
    }

    private val pattern = Regex("""^\s*(-?\d+(?:\.\d+)?)\s+([a-zA-Z]+)\s+(?:in|to)\s+([a-zA-Z]+)\s*$""", RegexOption.IGNORE_CASE)

    override fun parse(normalized: String, raw: String): L1Result {
        // Use normalized (lowercased, collapsed) for matching to be case-insensitive
        val m = pattern.matchEntire(normalized)
            ?: pattern.matchEntire(raw.trim()) // fallback to raw trimmed
        if (m == null) return L1Result.Unrecognized
        val valueStr = m.groupValues[1]
        val fromStr = m.groupValues[2].lowercase()
        val toStr = m.groupValues[3].lowercase()
        val value = valueStr.toDoubleOrNull() ?: return L1Result.Invalid("Invalid number")

        val fromDef = units[fromStr]
        val toDef = units[toStr]
        if (fromDef == null || toDef == null) return L1Result.Invalid("Unsupported unit")
        if (fromDef.kind != toDef.kind) return L1Result.Invalid("Incompatible units")
        // Edge: same unit -> just echo
        if (fromStr == toStr) {
            val fmt = format(value)
            return L1Result.Resolved(
                ResolvedResult(
                    id = "conversion:${raw.trim()}",
                    title = fmt,
                    subtitle = null,
                    type = ResultType.Conversion,
                    action = AuraAction.Copy(fmt),
                    inlineValue = fmt,
                    inlineQuery = raw.trim()
                )
            )
        }
        // Convert via base
        val base = fromDef.toBase(value)
        val converted = toDef.fromBase(base)
        val formatted = format(converted)
        return L1Result.Resolved(
            ResolvedResult(
                id = "conversion:${raw.trim()}",
                title = formatted,
                subtitle = null,
                type = ResultType.Conversion,
                action = AuraAction.Copy(formatted),
                inlineValue = formatted,
                inlineQuery = raw.trim()
            )
        )
    }

    private fun format(value: Double): String {
        // For temperature, keep reasonable decimal
        return if (value % 1.0 == 0.0) value.toLong().toString()
        else {
            // Round to 4 decimal for length/mass, 2 for temp? Keep generic 4
            var s = String.format("%.4f", value)
            s = s.trimEnd('0').trimEnd('.')
            s
        }
    }
}
