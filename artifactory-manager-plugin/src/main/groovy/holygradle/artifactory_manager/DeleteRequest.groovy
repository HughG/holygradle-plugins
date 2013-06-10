package holygradle.artifactory_manager

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
    
    public DeleteRequest(String modules) {
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
    
    public void process(ArtifactoryAPI artifactoryApi) {
        String[] moduleGroup = modules.split(",")
        println "  Deleting artifacts for '${moduleGroup.join(", ")}'"
        println "  when they are ${deleteExplanation}${keepExplanation}."
        
        Date cutoffDate = null
        if (deleteBeforeDate != null) {
            cutoffDate = deleteBeforeDate
        } else if (deleteUnits != null) {
            cutoffDate = subtractDate(artifactoryApi.getNow(), deleteUnitCount, deleteUnits)
        } else {
            println "Not deleting any artifacts for module '${modules}' because no cut-off date was specified."
            return
        }
        
        // Gather deletion candidates for each module.
        Map<String, ArtifactInfo> moduleDeleteCandidates = [:]
        Date earliestDateFound = artifactoryApi.getNow()
        moduleGroup.each { module ->
            String path = module.replace(":", "/")
            ArtifactInfo deleteCandidate = new ArtifactInfo(artifactoryApi, path)
            
            //println "newest"
            deleteCandidate.filter { ArtifactInfo it ->
                // Filter out any folders which represent the newest 
                (it.parent != null) && (it == it.parent.newestChild)
            }
            //println "after cutoff"
            deleteCandidate.filter { ArtifactInfo it ->
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
            
            moduleDeleteCandidates[module] = deleteCandidate
        }
        
        // If we need to keep regular releases then filter out some deletion candidates.
        if (keepCount > 0 && keepInterval != null) {
            Date laterDate = getLatestDateForSlidingWindow(cutoffDate, keepInterval)
            Date earlierDate = subtractDate(laterDate, keepCount, keepInterval)
            
            // Keep going until we have gone back to the earliest date previously found.
            while (laterDate.after(earliestDateFound)) {
                //println "    Checking period ${earlierDate} to ${laterDate}..."
                boolean anythingInRange = false
                Map<String, List<ArtifactInfo>> modulePathsInRange = [:]
                moduleDeleteCandidates.each { module, deleteCandidate ->
                    List<ArtifactInfo> inRange = []
                    //println "looking at module $module"
                    deleteCandidate.all { ArtifactInfo it ->
                        if (it.version != null) {
                            Date date = it.creationDate
                            if (date.after(earlierDate) && laterDate.after(date)) {
                                //println "  ${it.path} - ${date}"
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
                        //println "      Saving version: ${versionToSave}"
                        moduleDeleteCandidates.each { module, deleteCandidate ->
                            deleteCandidate.filter { ArtifactInfo it ->
                                // Filter out any versioned artifacts that we should save
                                it.version == versionToSave
                            }
                        }
                    } else {
                        //println "      No common version found for all modules."
                    }
                } else {
                    //println "      Nothing in range."
                }
                
                laterDate = earlierDate
                earlierDate = subtractDate(laterDate, keepCount, keepInterval)
            }
        }
        
        // We may have a hard-coded list of versions to keep.
        versionsToKeep.each { versionToKeep ->
            moduleDeleteCandidates.each { String module, ArtifactInfo deleteCandidate ->
                deleteCandidate.filter { ArtifactInfo it ->
                    // Filter out any versioned artifacts that we should save
                    it.version == versionToKeep
                }
            }
        }
        
        // Delete all candidates that remain.
        moduleDeleteCandidates.each { String module, ArtifactInfo deleteCandidate ->
            deleteCandidate.all { ArtifactInfo it ->
                // Only delete folders that look like a versioned artifact i.e. 
                // using a version number for the folder name.
                if (it.getVersion() != null) {
                    println "    DELETE ${it.path} (created: ${it.getCreationDate()})"
                    artifactoryApi.removeItem(it.path)
                }
            }
        }
    }
}
