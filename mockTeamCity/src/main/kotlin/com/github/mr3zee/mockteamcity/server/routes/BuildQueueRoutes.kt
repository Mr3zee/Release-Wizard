package com.github.mr3zee.mockteamcity.server.routes

import com.github.mr3zee.mockteamcity.model.MockTeamCityState
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

fun Routing.buildQueueRoutes(state: MockTeamCityState) {
    post("/app/rest/buildQueue") {
        val body = call.receiveText()
        val parsed = parseQueueXml(body)

        val build = state.triggerBuild(
            buildTypeId = parsed.buildTypeId,
            branchName = parsed.branchName,
            properties = parsed.properties,
        )

        call.respond(
            buildJsonObject {
                put("id", build.id)
                put("state", "queued")
                putBuildType(state, build.buildTypeId)
            }
        )
    }
}

private data class ParsedBuildRequest(
    val buildTypeId: String,
    val branchName: String?,
    val properties: Map<String, String>,
)

private fun parseQueueXml(xml: String): ParsedBuildRequest {
    val factory = DocumentBuilderFactory.newInstance()
    // Disable external entities for security
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    val builder = factory.newDocumentBuilder()
    val doc = builder.parse(InputSource(StringReader(xml)))

    val buildTypeNodes = doc.getElementsByTagName("buildType")
    val buildTypeId = if (buildTypeNodes.length > 0) {
        buildTypeNodes.item(0).attributes.getNamedItem("id")?.nodeValue ?: ""
    } else ""

    val branchNodes = doc.getElementsByTagName("branchName")
    val branchName = if (branchNodes.length > 0) {
        branchNodes.item(0).textContent?.takeIf { it.isNotBlank() }
    } else null

    val properties = mutableMapOf<String, String>()
    val propertyNodes = doc.getElementsByTagName("property")
    for (i in 0 until propertyNodes.length) {
        val node = propertyNodes.item(i)
        val name = node.attributes.getNamedItem("name")?.nodeValue ?: continue
        val value = node.attributes.getNamedItem("value")?.nodeValue ?: continue
        properties[name] = value
    }

    return ParsedBuildRequest(buildTypeId, branchName, properties)
}
