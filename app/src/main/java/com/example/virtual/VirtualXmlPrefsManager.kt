package com.example.virtual

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.StringReader
import java.io.StringWriter

enum class XmlPrefType(val tagName: String) {
    STRING("string"),
    INT("int"),
    LONG("long"),
    FLOAT("float"),
    BOOLEAN("boolean")
}

data class XmlPrefEntry(
    val key: String,
    val value: String,
    val type: XmlPrefType
)

/**
 * Real-time SharedPreferences XML Reader & Editor for INS Manager.
 * Reads and modifies standard Android `<map>` SharedPreferences XML files inside
 * `/virtual/user/<instance>/<package_name>/shared_prefs/`.
 */
object VirtualXmlPrefsManager {

    fun parseXmlFile(xmlFile: File): List<XmlPrefEntry> {
        if (!xmlFile.exists()) return emptyList()
        return parseXmlString(xmlFile.readText())
    }

    fun parseXmlString(xmlContent: String): List<XmlPrefEntry> {
        val entries = mutableListOf<XmlPrefEntry>()
        try {
            val parser = Xml.newPullParser()
            parser.setInput(StringReader(xmlContent))
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    val tag = parser.name
                    val key = parser.getAttributeValue(null, "name")
                    if (!key.isNullOrBlank()) {
                        when (tag) {
                            "string" -> {
                                val textVal = parser.nextText()
                                entries.add(XmlPrefEntry(key, textVal, XmlPrefType.STRING))
                            }
                            "int" -> {
                                val v = parser.getAttributeValue(null, "value") ?: "0"
                                entries.add(XmlPrefEntry(key, v, XmlPrefType.INT))
                            }
                            "long" -> {
                                val v = parser.getAttributeValue(null, "value") ?: "0"
                                entries.add(XmlPrefEntry(key, v, XmlPrefType.LONG))
                            }
                            "float" -> {
                                val v = parser.getAttributeValue(null, "value") ?: "0.0"
                                entries.add(XmlPrefEntry(key, v, XmlPrefType.FLOAT))
                            }
                            "boolean" -> {
                                val v = parser.getAttributeValue(null, "value") ?: "false"
                                entries.add(XmlPrefEntry(key, v, XmlPrefType.BOOLEAN))
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (_: Exception) {
            // Return entries parsed so far
        }
        return entries
    }

    fun serializeEntriesToXmlString(entries: List<XmlPrefEntry>): String {
        val writer = StringWriter()
        val serializer = Xml.newSerializer()
        serializer.setOutput(writer)
        serializer.startDocument("utf-8", true)
        try {
            serializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true)
        } catch (_: Exception) {
        }
        serializer.startTag(null, "map")
        for (entry in entries) {
            when (entry.type) {
                XmlPrefType.STRING -> {
                    serializer.startTag(null, "string")
                    serializer.attribute(null, "name", entry.key)
                    serializer.text(entry.value)
                    serializer.endTag(null, "string")
                }
                else -> {
                    serializer.startTag(null, entry.type.tagName)
                    serializer.attribute(null, "name", entry.key)
                    serializer.attribute(null, "value", entry.value)
                    serializer.endTag(null, entry.type.tagName)
                }
            }
        }
        serializer.endTag(null, "map")
        serializer.endDocument()
        return writer.toString()
    }

    fun writeEntriesToXml(xmlFile: File, entries: List<XmlPrefEntry>) {
        xmlFile.parentFile?.mkdirs()
        xmlFile.writeText(serializeEntriesToXmlString(entries))
    }

    fun saveRawXml(xmlFile: File, rawXml: String): Result<List<XmlPrefEntry>> {
        return runCatching {
            val parsed = parseXmlString(rawXml)
            if (!rawXml.contains("<map")) {
                throw IllegalArgumentException("XML SharedPreferences harus memiliki root tag <map>...</map>")
            }
            xmlFile.writeText(rawXml)
            parsed
        }
    }
}
