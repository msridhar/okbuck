package com.uber.okbuck.core.util;

import com.google.errorprone.annotations.Var;
import com.uber.okbuck.core.dependency.VersionlessDependency;
import com.uber.okbuck.core.model.base.Scope;
import com.uber.okbuck.core.model.base.TargetCache;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.gradle.api.Project;

public class ProjectCache {

  private static final String SCOPE_CACHE = "okbuckScopeCache";
  private static final String TARGET_CACHE = "okbuckTargetCache";

  private static final String INFO_CACHE = "okbuckInfoCache";
  private static final String SUBSTITUTION_CACHE = "okbuckSubstitutionCache";

  private ProjectCache() {}

  public static Map<String, Scope> getScopeCache(Project project) {
    String scopeCacheKey = getCacheKey(project, SCOPE_CACHE);

    Map<String, Scope> scopeCache = (Map<String, Scope>) project.property(scopeCacheKey);
    if (scopeCache == null) {
      throw new RuntimeException(
          "Scope cache external property '" + scopeCacheKey + "' is not set.");
    }
    return scopeCache;
  }

  public static TargetCache getTargetCache(Project project) {
    String targetCacheKey = getCacheKey(project, TARGET_CACHE);

    TargetCache targetCache = (TargetCache) project.property(targetCacheKey);
    if (targetCache == null) {
      throw new RuntimeException(
          "Target cache external property '" + targetCacheKey + "' is not set.");
    }
    return targetCache;
  }

  public static void initScopeCache(Project project) {
    project
        .getExtensions()
        .getExtraProperties()
        .set(getCacheKey(project, SCOPE_CACHE), new ConcurrentHashMap<>());
  }

  public static void resetScopeCache(Project project) {
    project.getExtensions().getExtraProperties().set(getCacheKey(project, SCOPE_CACHE), null);
  }

  public static void initTargetCacheForAll(Project project) {
    initTargetCache(project);
    project.getSubprojects().forEach(ProjectCache::initTargetCache);
  }

  public static void resetTargetCacheForAll(Project project) {
    resetTargetCache(project);
    project.getSubprojects().forEach(ProjectCache::resetTargetCache);
  }

  private static void initTargetCache(Project project) {
    project
        .getExtensions()
        .getExtraProperties()
        .set(getCacheKey(project, TARGET_CACHE), new TargetCache(project));
  }

  private static void resetTargetCache(Project project) {
    project.getExtensions().getExtraProperties().set(getCacheKey(project, TARGET_CACHE), null);
  }

  private static String getCacheKey(Project project, String prefix) {
    return prefix + project.getPath();
  }

  public static void initInfoCache(Project project) {
    Project rootProject = project.getRootProject();

    HashMap<VersionlessDependency, String> projectMap = new HashMap<>();
    rootProject
        .getSubprojects()
        .forEach(
            pp -> {
              VersionlessDependency dependency =
                  VersionlessDependency.builder()
                      .setGroup((String) pp.getGroup())
                      .setName(pp.getName())
                      .build();
              String path = pp.getPath().replace("/", ":");
              projectMap.put(dependency, path);
            });

    rootProject
        .getExtensions()
        .getExtraProperties()
        .set(getCacheKey(project, INFO_CACHE), projectMap);
  }

  public static void initSubstitutionCache(Project project) {
    Project rootProject = project.getRootProject();

    ConcurrentHashMap<VersionlessDependency, String> projectMap = new ConcurrentHashMap<>();
    rootProject
        .getExtensions()
        .getExtraProperties()
        .set(getCacheKey(project, SUBSTITUTION_CACHE), projectMap);
  }

  public static void resetInfoCache(Project project) {
    Project rootProject = project.getRootProject();

    project.getExtensions().getExtraProperties().set(getCacheKey(rootProject, INFO_CACHE), null);
  }

  public static void resetSubstitutionCache(Project project) {
    Project rootProject = project.getRootProject();

    ConcurrentHashMap<String, HashMap<VersionlessDependency, String>> subsCacheMap =
        getSubstitutionCache(rootProject);

    HashMap<VersionlessDependency, String> subsCache = new HashMap<>();
    HashMap<VersionlessDependency, Integer> subsCacheCount = new HashMap<>();

    subsCacheMap
        .values()
        .stream()
        .map(HashMap::entrySet)
        .flatMap(Collection::stream)
        .forEach(
            i -> {
              VersionlessDependency vd = i.getKey();
              subsCache.put(vd, i.getValue());

              @Var int count = 1;
              if (subsCacheCount.containsKey(vd)) {
                count = count + subsCacheCount.get(vd);
              }
              subsCacheCount.put(vd, count);
            });

    subsCache
        .keySet()
        .stream()
        .sorted()
        .forEach(
            k -> {
              String v = subsCache.get(k);
              System.out.println(
                  String.format(
                      "substitute module(\"%s:%s\") with project(\"%s\"), %d",
                      k.group(), k.name(), v, subsCacheCount.get(k)));
            });

    subsCacheMap.forEach(
        (key, value) -> {
          System.out.println(key);
          value
              .values()
              .forEach(
                  i -> {
                    System.out.println(String.format("implementation project(\"%s\")", i));
                  });
        });
    project
        .getExtensions()
        .getExtraProperties()
        .set(getCacheKey(rootProject, SUBSTITUTION_CACHE), null);
  }

  public static Map<VersionlessDependency, String> getInfoCache(Project project) {
    Project rootProject = project.getRootProject();

    String infoCacheKey = getCacheKey(rootProject, INFO_CACHE);

    Map<VersionlessDependency, String> infoCache =
        (Map<VersionlessDependency, String>) rootProject.property(infoCacheKey);
    if (infoCache == null) {
      throw new RuntimeException("Info cache external property '" + infoCacheKey + "' is not set.");
    }
    return infoCache;
  }

  public static ConcurrentHashMap<String, HashMap<VersionlessDependency, String>>
      getSubstitutionCache(Project project) {
    Project rootProject = project.getRootProject();

    String substitutionCacheKey = getCacheKey(rootProject, SUBSTITUTION_CACHE);

    ConcurrentHashMap<String, HashMap<VersionlessDependency, String>> substitutionCache =
        (ConcurrentHashMap<String, HashMap<VersionlessDependency, String>>)
            rootProject.property(substitutionCacheKey);
    if (substitutionCache == null) {
      throw new RuntimeException(
          "Substitution cache external property '" + substitutionCacheKey + "' is not set.");
    }
    return substitutionCache;
  }
}
