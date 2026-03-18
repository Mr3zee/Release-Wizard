package com.github.mr3zee.mavenpublication

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.slf4j.LoggerFactory
import org.w3c.dom.NodeList
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

class MavenMetadataFetcher(private val httpClient: HttpClient) {

    private val logger = LoggerFactory.getLogger(MavenMetadataFetcher::class.java)

    // XXE-safe factory, created once and reused across polls.
    // Thread-safe: newDocumentBuilder() creates a fresh builder per call.
    private val xmlFactory: DocumentBuilderFactory = DocumentBuilderFactory.newInstance().apply {
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        isXIncludeAware = false
        isExpandEntityReferences = false
        isNamespaceAware = false
    }

    suspend fun fetch(repoUrl: String, groupId: String, artifactId: String): Set<String>? {
        val url = buildMetadataUrl(repoUrl, groupId, artifactId)
        return try {
            val response = httpClient.get(url)
            if (!response.status.isSuccess()) {
                logger.warn("Maven metadata fetch returned HTTP {}: {}", response.status.value, url)
                return null
            }
            parseVersions(response.bodyAsText(), url)
        } catch (e: Exception) {
            logger.warn("Maven metadata fetch failed for {}: {}", url, e.message)
            null
        }
    }

    internal fun buildMetadataUrl(repoUrl: String, groupId: String, artifactId: String): String {
        val groupPath = groupId.replace('.', '/')
        return "${repoUrl.trimEnd('/')}/$groupPath/$artifactId/maven-metadata.xml"
    }

    private fun parseVersions(xml: String, url: String): Set<String>? {
        return try {
            val doc = xmlFactory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
            // Navigate specifically to metadata/versioning/versions/version elements
            // rather than a document-wide search, to avoid picking up the top-level
            // <version> element present in some snapshot metadata formats.
            val versioningNodes = doc.getElementsByTagName("versioning")
            if (versioningNodes.length == 0) return emptySet()
            val versioningNode = versioningNodes.item(0)
            val childNodes = versioningNode.childNodes
            var versionsNode: org.w3c.dom.Node? = null
            for (i in 0 until childNodes.length) {
                if (childNodes.item(i).nodeName == "versions") {
                    versionsNode = childNodes.item(i)
                    break
                }
            }
            if (versionsNode == null) return emptySet()
            val versionNodes: NodeList = (versionsNode as org.w3c.dom.Element).getElementsByTagName("version")
            (0 until versionNodes.length)
                .mapNotNull { versionNodes.item(it).textContent?.trim()?.takeIf { v -> v.isNotBlank() } }
                .toSet()
        } catch (e: Exception) {
            logger.error("Failed to parse Maven metadata XML from {}: {}", url, e.message)
            null
        }
    }
}
