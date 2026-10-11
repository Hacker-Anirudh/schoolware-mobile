package dev.schoolgrades

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ---- Wire format (field names exactly as Schoolware sends them) ----

@Serializable
data class ApiList<T>(
    val success: Boolean = true,
    val data: List<T> = emptyList(),
    val message: String? = null,
)

@Serializable
data class Student(
    @SerialName("ID") val id: Long,
    @SerialName("Naam") val lastName: String = "",
    @SerialName("Voornaam") val firstName: String = "",
)

@Serializable
data class SchoolYear(
    @SerialName("ID") val id: Long,
    @SerialName("Van") val from: String,
    @SerialName("Tot") val to: String = "",
    @SerialName("EindeVakantie") val holidayEnd: String = "",
)

@Serializable
data class LoopbaanRow(
    @SerialName("KL_ID") val klasId: Long = 0,
    @SerialName("KL_OMSCHRIJVING") val klasOmschrijving: String = "",
    @SerialName("KL_CODE") val klasCode: String = "",
)

@Serializable
data class SchoolAgenda(
    @SerialName("ID") val id: Long = 0,
)

@Serializable
data class LoopbaanData(
    val rows: List<LoopbaanRow> = emptyList(),
)

@Serializable
data class RapportVakItem(
    @SerialName("Omschrijving") val omschrijving: String = "",
    @SerialName("Gewicht") val gewicht: Int = 1,
)

@Serializable
data class RapportVakBlok(
    @SerialName("Vakken") val vakken: List<RapportVakItem> = emptyList(),
)

@Serializable
data class RapportVakkenModel(
    @SerialName("VakBlokken") val vakBlokken: List<RapportVakBlok> = emptyList(),
)

@Serializable
data class RapportAfdrukItem(
    @SerialName("ID") val id: Long = 0,
    @SerialName("RapportVakkenModel") val vakkenModel: RapportVakkenModel? = null,
)

@Serializable
data class RapportAfdrukResponse(
    val data: List<RapportAfdrukItem> = emptyList(),
)

@Serializable
data class RpcPointRow(
    @SerialName("VAK") val subject: String = "",
    @SerialName("PS_NAAM") val lastName: String = "",
    @SerialName("PS_VOORNAAM") val firstName: String = "",
)

@Serializable
data class RpcPointResult(
    val data: List<RpcPointRow> = emptyList(),
)

@Serializable
data class RpcPointResponse(
    val result: RpcPointResult = RpcPointResult(),
)

@Serializable
data class ReportPdfRow(
    @SerialName("D_ID") val id: Long = 0,
    @SerialName("OMSCHRIJVING") val filename: String = "",
    @SerialName("D_AANGEMAAKTOP") val date: String = "",
)

@Serializable
data class ReportPdfData(
    val rows: List<ReportPdfRow> = emptyList(),
)

@Serializable
data class AgendaPoint(
    @SerialName("ID") val id: Long = 0,
    @SerialName("Titel") val title: String = "",
    @SerialName("Van") val startsAt: String = "",
    @SerialName("Tot") val endsAt: String = "",
    @SerialName("TypePunt") val type: Int = 0,
    @SerialName("VakNaam") val subject: String = "",
    @SerialName("Commentaar") val comment: String = "",
)

data class SubjectMeta(
    val teacher: String = "",
    val weight: Double = 1.0,
)

/** One record per subject; the app merges them. */
@Serializable
data class GradeRecord(
    @SerialName("Beoordelingen") val items: List<Assessment> = emptyList(),
)

@Serializable
data class Assessment(
    @SerialName("BeoordelingMomentID") val id: Long = 0,
    @SerialName("BeoordelingMomentOmschrijving") val title: String = "",
    @SerialName("BeoordelingMomentType_") val type: String = "",
    @SerialName("BeoordelingMomentDatum") val date: String = "",
    @SerialName("BeoordelingMomentNoemer") val max: Double = 0.0,
    @SerialName("BeoordelingMomentMediaan") val median: Double? = null,
    @SerialName("BeoordelingMomentGewicht") val weight: Double? = null,
    @SerialName("BeoordelingMomentCategorieID") val categoryId: Long = 0,
    @SerialName("BeoordelingMomentCategorieOmschrijving") val categoryName: String = "",
    @SerialName("BeoordelingMomentCategorieGewicht") val categoryWeight: Double = 100.0,
    @SerialName("BeoordelingWaarde") val result: Result? = null,
    @SerialName("ParentPeriodeEvaluatieCode") val period: String = "",
    @SerialName("IngerichtVakNaamgebruiker") val subject: String = "",
    @SerialName("Leraar") val teacherName: String = "",
    @SerialName("Leerkracht") val teacherNameAlt: String = "",
    @SerialName("PersoneelNaam") val teacherLastName: String = "",
    @SerialName("PersoneelVoornaam") val teacherFirstName: String = "",
    @SerialName("PS_NAAM") val psLastName: String = "",
    @SerialName("PS_VOORNAAM") val psFirstName: String = "",
)

@Serializable
data class Result(
    @SerialName("Afwezig") val absent: String = "-",          // "+" = absent
    @SerialName("Commentaar") val comment: String = "",
    @SerialName("NumeriekAsString") val numeric: String = "",  // fraction of max, e.g. "0.875"
)

// ---- What the UI uses ----

data class Test(
    val id: Long,
    val subject: String,
    val title: String,
    val isTask: Boolean,
    val date: String,
    val max: Double,
    val median: Double?,
    val weight: Double?,
    val categoryId: Long,
    val categoryName: String,
    val categoryWeight: Double,
    val score: Double?,       // null = not graded (yet)
    val absent: Boolean,
    val comment: String,
    val period: String,
    val teacher: String = "",
) {
    val effectiveWeight: Double
        get() = if (weight != null && weight > 0) weight else if (max > 0) max else 1.0

    val label: String
        get() = when {
            absent -> "Afwezig"
            score == null -> "Leeg"
            else -> {
                val formattedScore = "${formatNumber(score)} / ${formatNumber(max)}"
                if (max > 0) {
                    val pct = formatNumber((score / max) * 100)
                    "$formattedScore ($pct%)"
                } else {
                    formattedScore
                }
            }
        }
}

data class Subject(
    val name: String,
    val tests: List<Test>,
    val fallbackTeacher: String = "",
    val subjectWeight: Double = 1.0,
) {
    val teacher: String
        get() {
            val fromTest = tests.firstNotNullOfOrNull { it.teacher.ifBlank { null } }
            if (!fromTest.isNullOrEmpty()) return fromTest
            return fallbackTeacher
        }

    val graded: Int get() = tests.count { it.score != null }
    val pending: Int get() = tests.count { it.score == null && !it.absent }

    val weightedAverage: Double?
        get() = calculateAssessmentAverage(tests, excludeAbsent = true) { test ->
            if (test.max > 0) test.score?.div(test.max) else null
        }

    val medianAverage: Double?
        get() = calculateAssessmentAverage(tests, excludeAbsent = false) { it.median }

    val weightedAverageLabel: String?
        get() {
            val avgRatio = weightedAverage ?: return null
            val percentage = avgRatio * 100
            val scoreOn20 = avgRatio * 20
            return "${formatNumber(scoreOn20)} / 20 (${formatNumber(percentage)}%)"
        }

    val weightedAveragePercentageLabel: String?
        get() {
            val avgRatio = weightedAverage ?: return null
            val percentage = avgRatio * 100
            return "${formatNumber(percentage)}%"
        }

    val medianAveragePercentageLabel: String?
        get() {
            val avgRatio = medianAverage ?: return null
            return "${formatNumber(avgRatio * 100)}%"
        }
}

private fun calculateAssessmentAverage(
    tests: List<Test>,
    excludeAbsent: Boolean,
    ratioFor: (Test) -> Double?,
): Double? {
    val weightedTests = tests.mapNotNull { test ->
        ratioFor(test)?.let { ratio -> test to ratio }
    }.filter { (test, _) -> test.max > 0 && (!excludeAbsent || !test.absent) }
    if (weightedTests.isEmpty()) return null

    val categoryAverages = weightedTests.groupBy { it.first.categoryId }.mapNotNull { (_, categoryTests) ->
        val categoryWeight = categoryTests.firstOrNull()?.first?.categoryWeight ?: 100.0
        val totalWeightedScore = categoryTests.sumOf { (test, ratio) -> ratio * test.max * test.effectiveWeight }
        val totalWeightedMaximum = categoryTests.sumOf { (test, _) -> test.max * test.effectiveWeight }
        if (totalWeightedMaximum > 0) {
            (totalWeightedScore / totalWeightedMaximum) to if (categoryWeight > 0) categoryWeight else 100.0
        } else {
            null
        }
    }
    if (categoryAverages.isEmpty()) return null

    val totalWeightedScore = categoryAverages.sumOf { (average, weight) -> average * weight }
    val totalWeight = categoryAverages.sumOf { (_, weight) -> weight }
    return if (totalWeight > 0) totalWeightedScore / totalWeight else null
}

/** Calculates the overall average ratio weighted by each subject's dynamic curriculum weight */
fun calculateOverallAverage(subjects: List<Subject>): Double? {
    return calculateOverallWeightedRatio(subjects) { it.weightedAverage }
}

fun calculateOverallMedian(subjects: List<Subject>): Double? {
    return calculateOverallWeightedRatio(subjects) { it.medianAverage }
}

private fun calculateOverallWeightedRatio(subjects: List<Subject>, ratioFor: (Subject) -> Double?): Double? {
    val weightedSubjects = subjects.mapNotNull { subject ->
        ratioFor(subject)?.let { ratio -> subject to ratio }
    }
    if (weightedSubjects.isEmpty()) return null
    val totalWeightedScore = weightedSubjects.sumOf { (subject, ratio) -> ratio * subject.subjectWeight }
    val totalSubjectWeight = weightedSubjects.sumOf { (subject, _) -> subject.subjectWeight }
    return if (totalSubjectWeight > 0) totalWeightedScore / totalSubjectWeight else null
}

fun formatNumber(v: Double): String {
    val r = kotlin.math.round(v * 10) / 10.0
    return if (r % 1.0 == 0.0) r.toInt().toString() else r.toString()
}

fun normalizeSubjectName(name: String): String {
    val trimmed = name.trim()
    val lower = trimmed.lowercase()
    val normalized = java.text.Normalizer.normalize(lower, java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")

    return when {
        normalized.contains("lichamelijke opvoeding") ||
        normalized.contains("education physique") ||
        normalized.contains("physical education") -> "LO"
        else -> trimmed
    }
}

private fun Assessment.toTest(): Test {
    val ratio = result?.numeric?.trim()?.replace(',', '.')?.toDoubleOrNull()
    val extractedTeacher = listOf(
        teacherName,
        teacherNameAlt,
        listOfNotNull(
            teacherFirstName.ifBlank { psFirstName }.ifBlank { null },
            teacherLastName.ifBlank { psLastName }.ifBlank { null },
        ).joinToString(" "),
    ).firstOrNull { it.isNotBlank() }.orEmpty().trim()

    return Test(
        id = id,
        subject = normalizeSubjectName(subject.ifBlank { "Other" }),
        title = title.trim(),
        isTask = type == "bmtTaak",
        date = date,
        max = max,
        median = median,
        weight = weight,
        categoryId = categoryId,
        categoryName = categoryName,
        categoryWeight = if (categoryWeight > 0) categoryWeight else 100.0,
        score = ratio?.times(max),
        absent = result?.absent == "+",
        comment = result?.comment.orEmpty().trim(),
        period = period,
        teacher = extractedTeacher,
    )
}

fun List<GradeRecord>.toSubjects(metaMap: Map<String, SubjectMeta> = emptyMap()): List<Subject> =
    flatMap { it.items }
        .map { it.toTest() }
        .groupBy { it.subject }
        .map { (name, tests) ->
            val meta = metaMap[name]
                ?: metaMap.entries.firstOrNull { (k, _) ->
                    val normK = normalizeSubjectName(k)
                    normK.equals(name, ignoreCase = true) || name.contains(normK, ignoreCase = true) || normK.contains(name, ignoreCase = true)
                }?.value
            Subject(
                name = name,
                tests = tests.sortedByDescending { it.date },
                fallbackTeacher = meta?.teacher.orEmpty(),
                subjectWeight = meta?.weight ?: 1.0,
            )
        }
        .sortedBy { it.name.lowercase() }
