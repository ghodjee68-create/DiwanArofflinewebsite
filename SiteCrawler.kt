package com.diwanoffline

import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.util.ArrayDeque
import java.util.concurrent.TimeUnit

data class CrawlResult(val pages: Int, val assets: Int)

/**
 * Bounded same-host GET crawler. It preserves static HTML and local resources,
 * and rewrites discovered same-site links to saved local files.
 * Dynamic POST/search workflows may require site-specific handling.
 */
class SiteCrawler(private val root: File, private val report: (String) -> Unit) {
    private val client = OkHttpClient.Builder()
        .followRedirects(true).followSslRedirects(true)
        .connectTimeout(25, TimeUnit.SECONDS).readTimeout(45, TimeUnit.SECONDS)
        .build()
    private val pagesDir = File(root, "pages").apply { mkdirs() }
    private val assetsDir = File(root, "assets").apply { mkdirs() }
    private val pageMap = linkedMapOf<String, String>()
    private val assetMap = linkedMapOf<String, String>()
    private val host = "www.diwanalarabia.com"

    fun crawl(start: String, maxPages: Int): CrawlResult {
        val queue = ArrayDeque<String>()
        val seen = linkedSetOf<String>()
        queue.add(start)
        var assetCount = 0
        while (queue.isNotEmpty() && seen.size < maxPages) {
            val url = normalize(queue.removeFirst()) ?: continue
            if (!seen.add(url)) continue
            try {
                val request = Request.Builder().url(url).header("User-Agent", "DiwanOfflineAndroid/1.0").build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val type = response.header("Content-Type", "").lowercase()
                    if (type.contains("text/html") || url.endsWith(".aspx", true) || url.endsWith("/")) {
                        val html = response.body?.string() ?: return@use
                        val doc = Jsoup.parse(html, url)
                        val localName = if (url == normalize(start)) "index.html" else fileName(url) + ".html"
                        pageMap[url] = localName
                        // Discover all same-host navigational links before rewriting.
                        doc.select("a[href], area[href]").forEach { el ->
                            val target = normalize(el.absUrl("href"))
                            if (target != null && !looksLikeAsset(target) && !seen.contains(target)) queue.add(target)
                        }
                        // Download page resources (images, stylesheets, scripts, fonts and media).
                        doc.select("[src], link[href]").forEach { el ->
                            val attr = if (el.hasAttr("src")) "src" else "href"
                            val abs = el.absUrl(attr)
                            if (abs.isBlank()) return@forEach
                            val local = saveAsset(abs)
                            if (local != null) {
                                el.attr(attr, "../assets/$local")
                                assetCount++
                            }
                        }
                        // Rewrite same-domain links to their corresponding local page names.
                        doc.select("a[href], area[href]").forEach { el ->
                            val target = normalize(el.absUrl("href")) ?: return@forEach
                            if (target == url) {
                                el.attr("href", localName)
                            } else if (!looksLikeAsset(target)) {
                                val targetName = pageMap[target] ?: (fileName(target) + ".html")
                                el.attr("href", targetName)
                            }
                        }
                        // Keep fragments, remove online form submissions that cannot work offline.
                        doc.select("form").forEach { form ->
                            form.attr("action", "#")
                            form.attr("onsubmit", "return false;")
                        }
                        File(pagesDir, localName).writeText(doc.outerHtml(), Charsets.UTF_8)
                    } else {
                        // If a discovered URL is a file, save it as a downloadable resource.
                        val saved = saveBytes(url, response.body?.bytes(), type)
                        if (saved != null) assetCount++
                    }
                }
            } catch (_: Exception) { /* continue past unavailable pages */ }
            if (seen.size % 20 == 0) report("تم فحص ${seen.size} عنواناً؛ حُفظ ${pageMap.size} صفحة…")
        }
        // Resolve links discovered before their destination page was visited.
        pageMap.forEach { (url, name) ->
            val file = File(pagesDir, name)
            if (file.exists()) {
                var html = file.readText(Charsets.UTF_8)
                // Links already point to deterministic filenames; no second pass needed.
                file.writeText(html, Charsets.UTF_8)
            }
        }
        report("انتهى الفحص: ${pageMap.size} صفحة.")
        return CrawlResult(pageMap.size, assetCount)
    }

    private fun saveAsset(url: String): String? {
        val uri = try { URI(url) } catch (_: Exception) { return null }
        if (uri.host?.lowercase() != host) return null
        val known = assetMap[url]
        if (known != null) return known
        return try {
            val req = Request.Builder().url(url).header("User-Agent", "DiwanOfflineAndroid/1.0").build()
            client.newCall(req).execute().use { r ->
                if (!r.isSuccessful) null else saveBytes(url, r.body?.bytes(), r.header("Content-Type", ""))
            }
        } catch (_: Exception) { null }
    }

    private fun saveBytes(url: String, bytes: ByteArray?, contentType: String): String? {
        if (bytes == null) return null
        val ext = when {
            contentType.contains("css") -> ".css"
            contentType.contains("javascript") -> ".js"
            contentType.contains("png") -> ".png"
            contentType.contains("jpeg") -> ".jpg"
            contentType.contains("gif") -> ".gif"
            contentType.contains("svg") -> ".svg"
            contentType.contains("webp") -> ".webp"
            contentType.contains("font") || url.contains(".woff") -> ".woff"
            contentType.contains("pdf") -> ".pdf"
            else -> File(URI(url).path ?: "").extension.take(8).let { if (it.isBlank()) ".bin" else ".$it" }
        }
        val name = hash(url) + ext
        File(assetsDir, name).writeBytes(bytes)
        assetMap[url] = name
        return name
    }

    private fun normalize(raw: String): String? {
        if (raw.isBlank()) return null
        return try {
            val u = URI(raw).normalize()
            if (u.scheme !in listOf("http", "https") || u.host?.lowercase() != host) return null
            val path = u.path.ifBlank { "/" }
            if (path.endsWith(".jpg", true) || path.endsWith(".png", true) ||
                path.endsWith(".gif", true) || path.endsWith(".css", true) ||
                path.endsWith(".js", true) || path.endsWith(".pdf", true) ||
                path.endsWith(".woff", true) || path.endsWith(".svg", true)) return null
            URI("https", null, host, -1, path, u.query, null).toString()
        } catch (_: Exception) { null }
    }

    private fun looksLikeAsset(url: String): Boolean {
        val p = try { URI(url).path.lowercase() } catch (_: Exception) { return false }
        return listOf(".jpg",".jpeg",".png",".gif",".webp",".svg",".css",".js",".pdf",".zip",".woff",".mp4").any { p.endsWith(it) }
    }

    private fun fileName(url: String): String = hash(url)
    private fun hash(s: String): String = MessageDigest.getInstance("SHA-256")
        .digest(s.toByteArray()).take(12).joinToString("") { "%02x".format(it) }
}
