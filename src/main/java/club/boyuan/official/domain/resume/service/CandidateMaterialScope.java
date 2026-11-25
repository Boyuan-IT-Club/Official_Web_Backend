package club.boyuan.official.domain.resume.service;

/** Checks the viewer's current interview permission and the material's own resume on an open board. */
public interface CandidateMaterialScope {
    java.util.List<String> currentGrants(Integer viewer);
    boolean canReadResume(Integer viewerUserId, Integer resumeId);
}
