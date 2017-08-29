package holygradle.artifactory_manager

import org.gradle.api.Action
import org.gradle.api.logging.Logger
import java.util.*

import java.util.Calendar.*
import java.util.function.Predicate

class DeleteRequest(
        private val logger: Logger,
        private val modules: String,
        private val minRequestIntervalInMillis: Long
) {
    companion object {
        private fun getEndOfLatestInterval(date: Date, units: String): Date {
            val calendar = date.toCalendar().clearTime()
            calendar.lenient = true

            when (units) {
                "day", "days" -> calendar.add(DAY_OF_MONTH, 1)
                "week", "weeks" -> {
                    calendar.set(DAY_OF_WEEK, 1)
                    calendar.add(DAY_OF_MONTH, 7)
                }
                "month", "months" -> {
                    calendar.set(DAY_OF_MONTH, 1)
                    calendar.add(MONTH, 1)
                }
                "year", "years" -> {
                    calendar.set(DAY_OF_MONTH, 1)
                    calendar.set(MONTH, 1)
                    calendar.add(YEAR, 1)
                }
                else -> throw RuntimeException("Unknown unit '${units}'")
            }

            return calendar.time
        }

        private fun subtractDate(date: Date, countUnits: Int, units: String): Date {
            val calendar = date.toCalendar().clearTime()
            calendar.lenient = true

            when (units) {
                "day", "days" -> calendar.add(DAY_OF_MONTH, -countUnits)
                "week", "weeks" -> calendar.add(DAY_OF_MONTH, -countUnits*7)
                "month", "months" -> calendar.add(MONTH, -countUnits)
                "year", "years" -> calendar.add(YEAR, -countUnits)
                else -> throw RuntimeException("Unknown unit '${units}'")
            }

            return calendar.time
        }
    }

    private var deleteUnitCount = 0
    private var deleteUnits: String? = null
    private var deleteExplanation = ""
    private var keepExplanation = ""
    private var keepIntervalCount = 0
    private var keepIntervalUnit: String? = null
    private val versionsToKeep = mutableListOf<String>()
    private var versionRegex: Regex? = null

    fun olderThan(countUnits: Int, units: String) {
        deleteExplanation = "more than ${countUnits} ${units} old"
        deleteUnitCount = countUnits
        deleteUnits = units
    }

    fun keepOneBuildPer(countUnits: Int, units: String) {
        keepExplanation = ", keeping one artifact every ${countUnits} ${units}"
        keepIntervalCount = countUnits
        keepIntervalUnit = units
    }

    fun dontDelete(versionToKeep: String) {
        versionsToKeep.add(versionToKeep)
    }

    fun versionsMatching(versionRegex: String) {
        if (this.versionRegex != null) {
            throw IllegalStateException(
                "Cannot call versionsMatching with '${versionRegex}': it was already called with '${this.versionRegex}'"
            )
        }
        this.versionRegex = versionRegex.toRegex()
    }
    
    fun process(artifactoryApi: ArtifactoryAPI) {
        val moduleGroup = modules.split(",")
        if (moduleGroup.size == 1 && moduleGroup[0].isEmpty()) {
            throw IllegalStateException("You must specify at least one module in a call to 'delete() {...}'")
        }

        logger.info("  Deleting artifacts for '${modules}'")
        logger.info("  when they are ${deleteExplanation}${keepExplanation}.")

        val units = deleteUnits
        if (units == null) {
            logger.info("Not deleting any artifacts for module '${modules}' because no cut-off date was specified.")
            return
        }
        val cutoffDate = Companion.subtractDate(artifactoryApi.getNow(), deleteUnitCount, units)

        // Gather deletion candidates for each module.
        //noinspection GroovyAssignabilityCheck
        val (earliestDateFound, moduleDeleteCandidates) = collectDeletionCandidates(artifactoryApi, moduleGroup, cutoffDate)

        // If we need to keep regular releases then filter out some deletion candidates.
        if (keepIntervalCount > 0 && keepIntervalUnit != null) {
            filterCandidatesToKeepByInterval(earliestDateFound, cutoffDate, moduleDeleteCandidates)
        }
        
        // We may have a hard-coded list of versions to keep.
        filterExplicitVersionsToKeep(moduleDeleteCandidates)
        
        // Delete all candidates that remain.
        deleteModuleVersions(artifactoryApi, moduleDeleteCandidates)
    }

    private data class DeletionCandidates(
            val earliestDateFound: Date,
            val candidates: Map<String, PathInfo>
    )

    private fun collectDeletionCandidates(
            artifactoryApi: ArtifactoryAPI,
            moduleGroup: List<String>,
            cutoffDate: Date
    ): DeletionCandidates {
        val moduleDeleteCandidates = mutableMapOf<String, PathInfo>()
        var earliestDateFound = artifactoryApi.getNow()
        logger.debug("  versionRegex is ${versionRegex}")
        moduleGroup.forEach { module ->
            val path = module.replace(":", "/")
            val deleteCandidate = PathInfo(artifactoryApi, path)

            deleteCandidate.filter(Predicate { it ->
                // Keep this one if we can't make sense of the version
                if (it.version == null) {
                    return@Predicate true
                }

                // Keep track of the earliest date we've seen.
                val creationDate = it.creationDate
                if (creationDate.before(earliestDateFound)) {
                    earliestDateFound = creationDate
                }

                (
                        // Filter out any folders which represent the newest
                        isNewestVersion(it) ||
                        // Filter out any folders that were created after our cut-off date.
                        isAfterCutoff(creationDate, cutoffDate) ||
                        // Filter out any versions which don't match the regex (if it's non-null).
                        !matchesVersionRegex(it)
                )
            })

            moduleDeleteCandidates[module] = deleteCandidate
            logger.debug("Added candidate for ${module}: ${deleteCandidate}")
        }
        return DeletionCandidates(earliestDateFound, moduleDeleteCandidates)
    }

    private fun isAfterCutoff(creationDate: Date, cutoffDate: Date): Boolean {
        val afterCutoff = creationDate.after(cutoffDate)
        logger.debug("after cutoff? ${afterCutoff} ${creationDate}")
        return afterCutoff
    }

    private fun matchesVersionRegex(it: PathInfo): Boolean {
        // If we have no regex, we're not really filtering by regex
        val regex = versionRegex ?: return true

        val matches = it.version?.matches(regex) ?: false
        logger.debug("versionRegex match? ${if (matches) "y" else "N"} ${it.path}")
        return matches
    }

    private fun isNewestVersion(it: PathInfo): Boolean {
        val isNewest = (it.parent != null) && (it == it.parent.getNewestChild())
        logger.debug("newest? ${isNewest} ${it.path}")
        return isNewest
    }

    private fun filterCandidatesToKeepByInterval(
        earliestDateFound: Date,
        cutoffDate: Date,
        moduleDeleteCandidates: Map<String, PathInfo>
    ) {
        /*
            This method looks for versions to keep, according to the 'keepOneBuildPer "N", "Period"' syntax, which
            initialises keepIntervalCount and keepIntervalUnit.  E.g., 'keepOneBuildPer "4", "days"'.

             It does this by looking at a "sliding window" of time, moving back from the cutoff date until it passes
             the earliest date of any version seen.  This window is a half-open interval: versions exactly matching the
             start date/time of the window are considered for keeping, whereas versions matching the end are not.

             The window is aligned to calendar units (by calling getEndOfLatestInterval): weeks, months, or years.  The
             end of the interval is initially at the first calendar-aligned point after the cutoff date.  For example,
             interval is "2 weeks", and the cutoff date is Tuesday 2014-01-28, then the following windows are
             considered:

                 (Mon 2014-01-20, Mon 2014-02-03] (the end of this window is the first Monday after the cutoff)
                 (Mon 2014-01-06, Mon 2014-01-20]
                 (Mon 2013-12-23, Mon 2014-01-06]
                 ...

             However, if the same cutoff date is used with an interval of "1 month", the following windows are used:

                 (Mon 2013-12-31, Mon 2014-01-31] (the end of this window is the start of the next month after cutoff)
                 (Mon 2013-11-30, Mon 2013-12-31]
                 (Mon 2013-10-31, Mon 2013-11-30]
                 ...

            In each window, the method then builds a list of all version numbers which are shared by all modules in the
            deletion request, and filters out deletion candidates for all except the last such version.  (This means
            that, if there is no version common to all modules, no version will be kept.
         */

        val count = keepIntervalCount
        val unit = keepIntervalUnit
        if (count <= 0 || unit == null) {
            return
        }

        var laterDate = getEndOfLatestInterval(cutoffDate, unit)
        var earlierDate = subtractDate(laterDate, count, unit)

        // Keep going until we have gone back to the earliest date previously found.
        while (laterDate.after(earliestDateFound)) {
            logger.debug("    Checking period ${earlierDate} to ${laterDate}...")
            var anythingInRange = false
            val modulePathsInRange = mutableMapOf<String, List<PathInfo>>()
            moduleDeleteCandidates.forEach { (module, deleteCandidate) ->
                val inRange = mutableListOf<PathInfo>()
                logger.debug("        Looking at module $module")
                deleteCandidate.all(Action { it ->
                    if (it.version != null) {
                        val date = it.creationDate
                        // Note: we test "earlierDate <= date" and "laterDate > date" here because we want to
                            // know if "date" is in the half-open interval "[earlierDate, laterDate)".
                        if ((earlierDate <= date) && laterDate.after(date)) {
                            logger.debug("            ${it.path} - ${date}")
                            inRange.add(it)
                            anythingInRange = true
                        }
                    }
                })
                modulePathsInRange[module] = inRange
            }

            if (anythingInRange) {
                var commonVersions: Set<String?>? = null
                for (inRange in modulePathsInRange.values) {
                    if (inRange.isNotEmpty()) {
                        val versions = inRange.mapTo(mutableSetOf()) { it.version }
                        commonVersions = commonVersions?.intersect(versions) ?: versions
                    }
                }

                if (commonVersions != null && commonVersions.isNotEmpty()) {
                    val versionToSave = commonVersions.last()
                    logger.debug("        Saving version: ${versionToSave}")
                    for ((module, deleteCandidate) in moduleDeleteCandidates) {
                        deleteCandidate.filter(Predicate { it ->
                            // Filter out any versioned artifacts that we should save
                            it.version == versionToSave
                        })
                    }
                } else {
                    logger.debug("      No common version found for all modules.")
                }
            } else {
                logger.debug("      Nothing in range.")
            }

            laterDate = earlierDate
            earlierDate = subtractDate(laterDate, count, unit)
        }
    }

    private fun filterExplicitVersionsToKeep(moduleDeleteCandidates: Map<String, PathInfo>) {
        for (versionToKeep in versionsToKeep) {
            for (deleteCandidate in moduleDeleteCandidates.values) {
                deleteCandidate.filter(Predicate { it ->
                    // Filter out any versioned artifacts that we should save
                    val keepVersion = (it.version == versionToKeep)
                    if (keepVersion) {
                        logger.info("    Keeping version ${it.version}")
                    }
                    keepVersion
                })
            }
        }
    }

    private fun deleteModuleVersions(artifactoryApi: ArtifactoryAPI, moduleDeleteCandidates: Map<String, PathInfo>) {
        for (deleteCandidate in moduleDeleteCandidates.values) {
            deleteCandidate.all(Action { it ->
                // Only delete folders that look like a versioned artifact i.e. 
                // using a version number for the folder name.
                if (it.version != null) {
                    logger.info("    DELETE ${it.path} (created: ${it.creationDate})")
                    artifactoryApi.removeItem(it.path)
                    if (minRequestIntervalInMillis > 0) {
                        Thread.sleep(minRequestIntervalInMillis)
                    }
                }
            })
        }
    }
}

@Deprecated("Use Kotlin map instead", ReplaceWith("this.map(action)"))
private fun <T, U> Iterable<T>.collect(action: (T) -> U): List<U> {
    return map(action)
}

@Deprecated("Use Kotlin forEach instead", ReplaceWith("this.forEach(action)"))
private inline fun <T> Iterable<T>.each(action: (T) -> Unit) {
    forEach(action)
}
