package holygradle.artifactory_manager

import org.gradle.api.logging.Logger

import java.util.regex.Pattern

import static java.util.Calendar.*

class DeleteRequest {
    private String modules
    private Date deleteBeforeDate = null
    private int deleteUnitCount = 0
    private String deleteUnits = null
    private String deleteExplanation = ""
    private String keepExplanation = ""
    private int keepCount = 0
    private String keepInterval = null
    private List<String> versionsToKeep = []
    private Pattern versionRegex = null
    private final Logger logger

    public DeleteRequest(Logger logger, String modules) {
        this.logger = logger
        this.modules = modules
    }
    
    public void olderThan(Date deleteOlder) {
        deleteBeforeDate = deleteOlder
        deleteExplanation = "older than ${deleteOlder}"
    }
    
    public void olderThan(int countUnits, String units) {
        deleteExplanation = "more than ${countUnits} ${units} old"
        deleteUnitCount = countUnits
        deleteUnits = units
    }
    
    private static Date getLatestDateForSlidingWindow(Date date, String units) {
        Calendar calendar = date.toCalendar().clearTime()
        calendar.lenient = true;
        
        switch (units) {
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
        keepCount = countUnits
        keepInterval = units
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
        
        Date cutoffDate = null
        if (deleteBeforeDate != null) {
            cutoffDate = deleteBeforeDate
        } else if (deleteUnits != null) {
            cutoffDate = subtractDate(artifactoryApi.getNow(), deleteUnitCount, deleteUnits)
        } else {
            logger.info "Not deleting any artifacts for module '${modules}' because no cut-off date was specified."
            return
        }
        
        // Gather deletion candidates for each module.
        Map<String, PathInfo> moduleDeleteCandidates = [:]
        Date earliestDateFound = artifactoryApi.getNow()
        logger.debug "  versionRegex is ${versionRegex}"
        moduleGroup.each { module ->
            String path = module.replace(":", "/")
            PathInfo deleteCandidate = new PathInfo(artifactoryApi, path)
            
            logger.debug "newest"
            deleteCandidate.filter { PathInfo it ->
                // Filter out any folders which represent the newest 
                (it.parent != null) && (it == it.parent.newestChild)
            }
            logger.debug "after cutoff"
            deleteCandidate.filter { PathInfo it ->
                // Filter out any folders that were created after our cut-off date.
                if (it.version == null) {
                    false
                } else {
                    Date creationDate = it.creationDate
                    if (creationDate.before(earliestDateFound)) {
                        earliestDateFound = creationDate
                    }
                    creationDate.after(cutoffDate)
                }
            }
            if (versionRegex != null) {
                logger.debug "regex"
                deleteCandidate.filter { PathInfo it ->
                    // Filter out any folders which don't match our version regex
                    boolean doesNotMatchVersionRegex = !it.version.matches(versionRegex)
                    logger.debug "versionRegex match? ${doesNotMatchVersionRegex ? 'N' : 'y'} ${it.path}"
                    doesNotMatchVersionRegex
                }
            }
            
            moduleDeleteCandidates[module] = deleteCandidate
            logger.debug "Added candidate for ${module}: ${deleteCandidate}"
        }
        
        // If we need to keep regular releases then filter out some deletion candidates.
        if (keepCount > 0 && keepInterval != null) {
            Date laterDate = getLatestDateForSlidingWindow(cutoffDate, keepInterval)
            Date earlierDate = subtractDate(laterDate, keepCount, keepInterval)
            
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
                            if (date.after(earlierDate) && laterDate.after(date)) {
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
                        String versionToSave = commonVersions[0]
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
                earlierDate = subtractDate(laterDate, keepCount, keepInterval)
            }
        }
        
        // We may have a hard-coded list of versions to keep.
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
        
        // Delete all candidates that remain.
        moduleDeleteCandidates.each { String module, PathInfo deleteCandidate ->
            deleteCandidate.all { PathInfo it ->
                // Only delete folders that look like a versioned artifact i.e. 
                // using a version number for the folder name.
                if (it.getVersion() != null) {
                    logger.info "    DELETE ${it.path} (created: ${it.getCreationDate()})"
                    artifactoryApi.removeItem(it.path)
                }
            }
        }
    }
}
