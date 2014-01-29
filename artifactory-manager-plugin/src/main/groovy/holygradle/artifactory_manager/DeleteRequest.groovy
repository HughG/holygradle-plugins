package holygradle.artifactory_manager

import org.gradle.api.logging.Logger

import java.util.regex.Pattern

import static java.util.Calendar.*

class DeleteRequest {
    private final Logger logger
    private final String modules
    private final long minRequestIntervalInMillis

    private int deleteUnitCount = 0
    private String deleteUnits = null
    private String deleteExplanation = ""
    private String keepExplanation = ""
    private int keepIntervalCount = 0
    private String keepIntervalUnit = null
    private List<String> versionsToKeep = []
    private Pattern versionRegex = null

    public DeleteRequest(Logger logger, String modules, long minRequestIntervalInMillis) {
        this.logger = logger
        this.modules = modules
        this.minRequestIntervalInMillis = minRequestIntervalInMillis
    }

    public void olderThan(int countUnits, String units) {
        deleteExplanation = "more than ${countUnits} ${units} old"
        deleteUnitCount = countUnits
        deleteUnits = units
    }
    
    private static Date getEndOfLatestInterval(Date date, String units) {
        Calendar calendar = date.toCalendar().clearTime()
        calendar.lenient = true;
        
        switch (units) {
            case "day":
            case "days":
                calendar.add(DAY_OF_MONTH, 1)
                break
            case "week":
            case "weeks":
                calendar.set(DAY_OF_WEEK, 1)
                calendar.add(DAY_OF_MONTH, 7)
                break
            case "month":
            case "months":
                calendar.set(DAY_OF_MONTH, 1)
                calendar.add(MONTH, 1)
                break
            case "year":
            case "years":
                calendar.set(DAY_OF_MONTH, 1)
                calendar.set(MONTH, 1)
                calendar.add(YEAR, 1)
                break
            default:
                throw new RuntimeException("Unknown unit '${units}'")
        }
        
        return calendar.time
    }

    private static Date subtractDate(Date date, int countUnits, String units) {
        Calendar calendar = date.toCalendar().clearTime()
        calendar.lenient = true;

        switch (units) {
            case "day":
            case "days":
                calendar.add(DAY_OF_MONTH, -countUnits)
                break
            case "week":
            case "weeks":
                calendar.add(DAY_OF_MONTH, -countUnits*7)
                break
            case "month":
            case "months":
                calendar.add(MONTH, -countUnits)
                break
            case "year":
            case "years":
                calendar.add(YEAR, -countUnits)
                break
            default:
                throw new RuntimeException("Unknown unit '${units}'")
        }
        
        return calendar.time
    }
    
    public void keepOneBuildPer(int countUnits, String units) {
        keepExplanation = ", keeping one artifact every ${countUnits} ${units}"
        keepIntervalCount = countUnits
        keepIntervalUnit = units
    }

    public void dontDelete(String versionToKeep) {
        versionsToKeep.add(versionToKeep)
    }

    public void versionsMatching(String versionRegex) {
        if (this.versionRegex != null) {
            throw new IllegalStateException(
                "Cannot call versionsMatching with '${versionRegex}': it was already called with '${this.versionRegex}'"
            )
        }
        this.versionRegex = ~versionRegex
    }
    
    public void process(ArtifactoryAPI artifactoryApi) {
        String[] moduleGroup = modules.split(",")
        if (moduleGroup.length == 1 && moduleGroup[0].empty) {
            throw new IllegalStateException("You must specify at least one module in a call to 'delete() {...}'")
        }

        logger.info "  Deleting artifacts for '${moduleGroup.join(", ")}'"
        logger.info "  when they are ${deleteExplanation}${keepExplanation}."
        
        if (deleteUnits == null) {
            logger.info "Not deleting any artifacts for module '${modules}' because no cut-off date was specified."
            return
        }
        Date cutoffDate = subtractDate(artifactoryApi.getNow(), deleteUnitCount, deleteUnits)

        // Gather deletion candidates for each module.
        //noinspection GroovyAssignabilityCheck
        def (Date earliestDateFound, Map<String, PathInfo> moduleDeleteCandidates) =
            collectDeletionCandidates(artifactoryApi, moduleGroup, cutoffDate)

        // If we need to keep regular releases then filter out some deletion candidates.
        if (keepIntervalCount > 0 && keepIntervalUnit != null) {
            filterCandidatesToKeepByInterval(earliestDateFound, cutoffDate, moduleDeleteCandidates)
        }
        
        // We may have a hard-coded list of versions to keep.
        filterExplicitVersionsToKeep(moduleDeleteCandidates)
        
        // Delete all candidates that remain.
        deleteModuleVersions(artifactoryApi, moduleDeleteCandidates)
    }


    private List collectDeletionCandidates(ArtifactoryAPI artifactoryApi, String[] moduleGroup, Date cutoffDate) {
        Map<String, PathInfo> moduleDeleteCandidates = [:]
        Date earliestDateFound = artifactoryApi.getNow()
        logger.debug "  versionRegex is ${versionRegex}"
        moduleGroup.each { module ->
            String path = module.replace(":", "/")
            PathInfo deleteCandidate = new PathInfo(artifactoryApi, path)

            deleteCandidate.filter { PathInfo it ->
                // Keep this one if we can't make sense of the version
                if (it.version == null) {
                    return true
                }

                // Keep track of the earliest date we've seen.
                Date creationDate = it.creationDate
                if (creationDate.before(earliestDateFound)) {
                    earliestDateFound = creationDate
                }

                return (
                    // Filter out any folders which represent the newest
                    isNewestVersion(it) ||
                    // Filter out any folders that were created after our cut-off date.
                    isAfterCutoff(creationDate, cutoffDate) ||
                    // Filter out any versions which don't match the regex (if it's non-null).
                    !matchesVersionRegex(it)
                )
            }

            moduleDeleteCandidates[module] = deleteCandidate
            logger.debug "Added candidate for ${module}: ${deleteCandidate}"
        }
        return [earliestDateFound, moduleDeleteCandidates]
    }

    private boolean isAfterCutoff(Date creationDate, Date cutoffDate) {
        boolean afterCutoff = creationDate.after(cutoffDate)
        logger.debug "after cutoff? ${afterCutoff} ${creationDate}"
        afterCutoff
    }

    private boolean matchesVersionRegex(PathInfo it) {
        // If we have no regex, we're not really filtering by regex
        if (versionRegex == null) {
            return true
        }

        boolean matches = it.version.matches(versionRegex)
        logger.debug "versionRegex match? ${matches ? 'y' : 'N'} ${it.path}"
        matches
    }

    private boolean isNewestVersion(PathInfo it) {
        boolean isNewest = (it.parent != null) && (it == it.parent.newestChild)
        logger.debug "newest? ${isNewest} ${it.path}"
        isNewest
    }

    private void filterCandidatesToKeepByInterval(
        Date earliestDateFound,
        Date cutoffDate,
        Map<String, PathInfo> moduleDeleteCandidates
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

        Date laterDate = getEndOfLatestInterval(cutoffDate, keepIntervalUnit)
        Date earlierDate = subtractDate(laterDate, keepIntervalCount, keepIntervalUnit)

        // Keep going until we have gone back to the earliest date previously found.
        while (laterDate.after(earliestDateFound)) {
            logger.debug "    Checking period ${earlierDate} to ${laterDate}..."
            boolean anythingInRange = false
            Map<String, List<PathInfo>> modulePathsInRange = [:]
            moduleDeleteCandidates.each { module, deleteCandidate ->
                List<PathInfo> inRange = []
                logger.debug "        Looking at module $module"
                deleteCandidate.all { PathInfo it ->
                    if (it.version != null) {
                        Date date = it.creationDate
                        // Note: we test "earlierDate <= date" and "laterDate > date" here because we want to
                            // know if "date" is in the half-open interval "[earlierDate, laterDate)".
                        if ((earlierDate.compareTo(date) <= 0) && laterDate.after(date)) {
                            logger.debug "            ${it.path} - ${date}"
                            inRange.add(it)
                            anythingInRange = true
                        }
                    }
                }
                modulePathsInRange[module] = inRange
            }

            if (anythingInRange) {
                List<String> commonVersions = null
                modulePathsInRange.each { module, inRange ->
                    if (inRange.size() > 0) {
                        List<String> versions = inRange.collect { it.version }
                        if (commonVersions == null) {
                            commonVersions = versions
                        } else {
                            commonVersions = commonVersions.intersect(versions)
                        }
                    }
                }

                if (commonVersions != null && commonVersions.size() > 0) {
                    String versionToSave = commonVersions.last()
                    logger.debug "        Saving version: ${versionToSave}"
                    moduleDeleteCandidates.each { module, deleteCandidate ->
                        deleteCandidate.filter { PathInfo it ->
                            // Filter out any versioned artifacts that we should save
                            it.version == versionToSave
                        }
                    }
                } else {
                    logger.debug "      No common version found for all modules."
                }
            } else {
                logger.debug "      Nothing in range."
            }

            laterDate = earlierDate
            earlierDate = subtractDate(laterDate, keepIntervalCount, keepIntervalUnit)
        }
    }

    private void filterExplicitVersionsToKeep(moduleDeleteCandidates) {
        versionsToKeep.each { versionToKeep ->
            moduleDeleteCandidates.each { String module, PathInfo deleteCandidate ->
                deleteCandidate.filter { PathInfo it ->
                    // Filter out any versioned artifacts that we should save
                    boolean keepVersion = (it.version == versionToKeep)
                    if (keepVersion) {
                        logger.info "    Keeping version ${it.version}"
                    }
                    keepVersion
                }
            }
        }
    }

    private void deleteModuleVersions(artifactoryApi, Map<String, PathInfo> moduleDeleteCandidates) {
        moduleDeleteCandidates.each { String module, PathInfo deleteCandidate ->
            deleteCandidate.all { PathInfo it ->
                // Only delete folders that look like a versioned artifact i.e. 
                // using a version number for the folder name.
                if (it.getVersion() != null) {
                    logger.info "    DELETE ${it.path} (created: ${it.getCreationDate()})"
                    artifactoryApi.removeItem(it.path)
                    if (minRequestIntervalInMillis > 0) {
                        Thread.sleep(minRequestIntervalInMillis)
                    }
                }
            }
        }
    }
}
