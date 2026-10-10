package dev.schoolgrades

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class SessionExpiredException : IOException("Your session has expired. Please sign in again.")

/** Read-only client for the parts of Schoolware's REST layer the student grade screen uses. */
class SchoolwareApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun stamp(d: Date = Date()): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(d)

    /** With a valid session this returns the logged-in student; no user id needed. */
    suspend fun student(cookie: String): Student =
        get(cookie, "Leerling/", mapOf("limit" to "0", "page" to "1", "start" to "0"), Student.serializer())
            .firstOrNull() ?: throw IOException("No student record returned")

    suspend fun currentSchoolYear(cookie: String): SchoolYear {
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance()
        cal.add(Calendar.YEAR, -1)
        val from = day.format(cal.time)
        cal.add(Calendar.YEAR, 2)
        val to = day.format(cal.time)
        val years = get(
            cookie, "schooljaar",
            mapOf("mineindevakantie" to from, "maxvan" to to),
            SchoolYear.serializer(),
        )
        val now = stamp()
        return years.firstOrNull { y -> y.from <= now && now < y.holidayEnd.ifBlank { y.to } }
            ?: years.maxByOrNull { it.from }
            ?: throw IOException("No school year found")
    }

    suspend fun grades(cookie: String, studentId: Long, yearStart: String): List<GradeRecord> =
        get(
            cookie, "PuntenbladGridLeerling",
            mapOf(
                "Leerling" to studentId.toString(),
                "BeoordelingMomentVan" to yearStart,
                "BeoordelingMomentTot" to stamp(),
            ),
            GradeRecord.serializer(),
        )

    suspend fun subjectMetadata(cookie: String, studentId: Long): Map<String, SubjectMeta> = withContext(Dispatchers.IO) {
        val map = mutableMapOf<String, SubjectMeta>()
        try {
            val loopbaanUrl = "${Config.API}/Provider/LoopbaanGegevens/Read/?LL_ID=$studentId&_dc=${System.currentTimeMillis()}".toHttpUrl()
            val req1 = Request.Builder()
                .url(loopbaanUrl)
                .header("Cookie", cookie)
                .header("Accept", "application/json")
                .build()
            val body1 = client.newCall(req1).execute().use { it.body?.string().orEmpty() }
            if (!body1.startsWith("{")) return@withContext emptyMap()
            val loopbaan = json.decodeFromString(LoopbaanData.serializer(), body1)
            val klasId = loopbaan.rows.firstOrNull()?.klasId ?: return@withContext emptyMap()

            val rapportUrl = "${Config.API}/REST/RapportAfdrukVolledig?Klas=$klasId&Leerling=$studentId&_dc=${System.currentTimeMillis()}".toHttpUrl()
            val req2 = Request.Builder()
                .url(rapportUrl)
                .header("Cookie", cookie)
                .header("Accept", "application/json")
                .build()
            val body2 = client.newCall(req2).execute().use { it.body?.string().orEmpty() }
            if (!body2.startsWith("{")) return@withContext emptyMap()
            val rapport = json.decodeFromString(RapportAfdrukResponse.serializer(), body2)
            val rapportItem = rapport.data.firstOrNull() ?: return@withContext emptyMap()
            val rapportId = rapportItem.id

            for (block in rapportItem.vakkenModel?.vakBlokken.orEmpty()) {
                for (vak in block.vakken) {
                    val subName = normalizeSubjectName(vak.omschrijving)
                    val w = if (vak.gewicht > 0) vak.gewicht.toDouble() else 1.0
                    if (subName.isNotBlank()) {
                        map[subName] = SubjectMeta(teacher = "", weight = w)
                    }
                }
            }

            val rpcUrl = "${Config.API}/RPC/ROUTER".toHttpUrl()
            val rpcBody = """{"action":"RapportAPI","method":"GetPunten","data":[$rapportId,"",4,["$studentId"],"",1],"type":"rpc","tid":"1"}"""
            val req3 = Request.Builder()
                .url(rpcUrl)
                .header("Cookie", cookie)
                .header("Content-Type", "application/json;charset=UTF-8")
                .header("Accept", "application/json")
                .post(rpcBody.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            val body3 = client.newCall(req3).execute().use { it.body?.string().orEmpty() }
            if (body3.startsWith("{")) {
                val rpcData = json.decodeFromString(RpcPointResponse.serializer(), body3)
                for (row in rpcData.result.data) {
                    val sub = normalizeSubjectName(row.subject)
                    val fullName = "${row.firstName.trim()} ${row.lastName.trim()}".trim()
                    if (sub.isNotBlank() && fullName.isNotBlank()) {
                        val formatted = fullName.split(" ")
                            .filter { it.isNotBlank() }
                            .joinToString(" ") { word -> word.lowercase().replaceFirstChar { c -> c.uppercase() } }
                        val existing = map[sub]
                        map[sub] = SubjectMeta(teacher = formatted, weight = existing?.weight ?: 1.0)
                    }
                }
            }
        } catch (e: Exception) {
            // Silently fall back if network/session issue occurs
        }
        map
    }

    suspend fun studierichting(cookie: String, studentId: Long): String = withContext(Dispatchers.IO) {
        try {
            val loopbaanUrl = "${Config.API}/Provider/LoopbaanGegevens/Read/?LL_ID=$studentId&_dc=${System.currentTimeMillis()}".toHttpUrl()
            val req = Request.Builder()
                .url(loopbaanUrl)
                .header("Cookie", cookie)
                .header("Accept", "application/json")
                .build()
            val body = client.newCall(req).execute().use { it.body?.string().orEmpty() }
            if (!body.startsWith("{")) return@withContext ""
            val loopbaan = json.decodeFromString(LoopbaanData.serializer(), body)
            val row = loopbaan.rows.firstOrNull() ?: return@withContext ""
            val code = row.klasCode.trim()
            val desc = row.klasOmschrijving.trim()
            listOfNotNull(
                code.ifBlank { null },
                desc.ifBlank { null },
            ).joinToString(" · ")
        } catch (e: Exception) {
            ""
        }
    }

    suspend fun reportPdfs(cookie: String, studentId: Long): List<ReportPdfRow> = withContext(Dispatchers.IO) {
        try {
            val url = "${Config.API}/Provider/rapportenleerling/Read/?LL_ID=$studentId&_dc=${System.currentTimeMillis()}".toHttpUrl()
            val request = Request.Builder()
                .url(url)
                .header("Cookie", cookie)
                .header("Accept", "application/json")
                .build()
            client.newCall(request).execute().use { resp ->
                if (resp.code == 401 || resp.code == 403) throw SessionExpiredException()
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) return@withContext emptyList()
                if (!body.trimStart().startsWith("{")) return@withContext emptyList()
                val parsed = json.decodeFromString(ReportPdfData.serializer(), body)
                parsed.rows
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Downloads a stored document (e.g. a report PDF) to [dest].
     * Schoolware serves documents from filemanager.cgi, not from the REST/Provider layer.
     */
    suspend fun downloadDocument(cookie: String, docId: Long, dest: File) = withContext(Dispatchers.IO) {
        val url = "${Config.APP}/bin/filemanager.cgi/Doc/get".toHttpUrl().newBuilder()
            .addQueryParameter("D_ID", docId.toString())
            .build()
        val request = Request.Builder().url(url).header("Cookie", cookie).build()
        val tmp = File(dest.parentFile, dest.name + ".part")
        try {
            client.newCall(request).execute().use { resp ->
                if (resp.code == 401 || resp.code == 403) throw SessionExpiredException()
                if (!resp.isSuccessful) throw IOException("Server returned ${resp.code}")
                val body = resp.body ?: throw IOException("Empty response")
                body.byteStream().use { input -> tmp.outputStream().use { out -> input.copyTo(out) } }
            }
            val head = ByteArray(5)
            val read = tmp.inputStream().use { it.read(head) }
            if (read < 5 || String(head, Charsets.ISO_8859_1) != "%PDF-") {
                throw IOException("Server did not return a PDF. If this keeps happening, sign out and in again.")
            }
            if (!tmp.renameTo(dest)) tmp.copyTo(dest, overwrite = true)
        } finally {
            tmp.delete()
        }
    }

    private suspend fun <T> get(
        cookie: String,
        resource: String,
        params: Map<String, String>,
        item: KSerializer<T>,
    ): List<T> = withContext(Dispatchers.IO) {
        val url = "${Config.API}/REST/$resource".toHttpUrl().newBuilder().apply {
            params.forEach { (k, v) -> addQueryParameter(k, v) }
            addQueryParameter("_dc", System.currentTimeMillis().toString())
        }.build()
        val request = Request.Builder()
            .url(url)
            .header("Cookie", cookie)
            .header("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { resp ->
            if (resp.code == 401 || resp.code == 403) throw SessionExpiredException()
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException("Server returned ${resp.code}")
            // An expired session usually gets redirected to an HTML login page.
            if (!body.trimStart().startsWith("{")) throw SessionExpiredException()
            val parsed = json.decodeFromString(ApiList.serializer(item), body)
            if (!parsed.success) throw IOException(parsed.message ?: "Request failed")
            parsed.data
        }
    }
}
