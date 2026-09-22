package com.swcsoftware.valuelens.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class CompanyRefCore(val ticker: String, val cik: Long, val name: String) {
    val cikPadded: String get() = cik.toString().padStart(10, '0')
}

/** One XBRL fact from SEC's companyfacts bundle. */
class Fact(
    val taxonomy: String, val tag: String, val unit: String, val value: Double,
    val end: Day, val start: Day?, val form: String, val fp: String?, val fy: Int?,
    val accession: String, val filed: Day, val frame: String?,
) {
    val durationDays: Long? get() = start?.daysUntil(end)
    val isInstant: Boolean get() = start == null
}

class CompanyFacts(val ref: CompanyRefCore, val entityName: String, private val facts: Map<String, List<Fact>>) {
    fun get(taxonomy: String, tag: String): List<Fact> = facts["$taxonomy:$tag"] ?: emptyList()
    val tagKeys: Set<String> get() = facts.keys
    /** Facts under a "taxonomy:tag" key — used by coverage to judge candidate tags. */
    fun factsFor(key: String): List<Fact> = facts[key] ?: emptyList()
}

object CompanyFactsParser {
    private val json = Json { ignoreUnknownKeys = true }

    /** Pure parse of the companyfacts JSON text (same structure the Python reference reads). */
    fun parse(text: String, ref: CompanyRefCore): CompanyFacts {
        val root = json.parseToJsonElement(text).jsonObject
        val entityName = root["entityName"]?.jsonPrimitive?.content ?: ref.name
        val out = LinkedHashMap<String, MutableList<Fact>>()
        val facts = root["facts"]?.jsonObject ?: JsonObject(emptyMap())
        for ((taxonomy, tags) in facts) {
            for ((tag, body) in tags.jsonObject) {
                val units = body.jsonObject["units"]?.jsonObject ?: continue
                val bucket = mutableListOf<Fact>()
                for ((unit, rows) in units) {
                    for (row in rows.jsonArray) {
                        val o = row.jsonObject
                        val value = o["val"]?.jsonPrimitive?.doubleOrNull ?: continue
                        val end = Day.parse(o["end"]?.jsonPrimitive?.content) ?: continue
                        val filed = Day.parse(o["filed"]?.jsonPrimitive?.content) ?: continue
                        bucket += Fact(
                            taxonomy, tag, unit, value, end, Day.parse(o["start"]?.jsonPrimitive?.content),
                            o["form"]?.jsonPrimitive?.content ?: "", o["fp"]?.jsonPrimitive?.content,
                            o["fy"]?.jsonPrimitive?.intOrNull, o["accn"]?.jsonPrimitive?.content ?: "", filed,
                            o["frame"]?.jsonPrimitive?.content,
                        )
                    }
                }
                if (bucket.isNotEmpty()) out["$taxonomy:$tag"] = bucket
            }
        }
        return CompanyFacts(ref, entityName, out)
    }
}
