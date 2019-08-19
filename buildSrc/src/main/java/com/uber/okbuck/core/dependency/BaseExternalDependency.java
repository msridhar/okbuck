package com.uber.okbuck.core.dependency;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.base.Splitter;
import com.google.common.collect.Streams;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
<<<<<<< HEAD
import java.util.Optional;
import java.util.stream.Collectors;
=======
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
>>>>>>> Pad version names so buck sorts them properly

import org.apache.commons.io.FilenameUtils;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencyArtifact;
import org.gradle.api.internal.artifacts.dependencies.DefaultDependencyArtifact;
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency;

@AutoValue
public abstract class BaseExternalDependency {
  public static final String AAR = "aar";
  public static final String JAR = "jar";

  private static final String NAME_DELIMITER = "-";

  public abstract VersionlessDependency versionless();

  public abstract String version();

  public abstract File realDependencyFile();

  public abstract Optional<File> realDependencySourceFile();

  abstract boolean isVersioned();

  abstract boolean usePaddedVersion();

  public static Builder builder() {
    return new AutoValue_BaseExternalDependency.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setVersionless(VersionlessDependency value);

    public abstract Builder setVersion(String value);

    public abstract Builder setIsVersioned(boolean value);

    public abstract Builder setUsePaddedVersion(boolean value);

    public abstract Builder setRealDependencyFile(File value);

    public abstract Builder setRealDependencySourceFile(Optional<File> value);

    public abstract BaseExternalDependency build();
  }

  @Override
  public String toString() {
    return this.getMavenCoords() + " -> " + realDependencyFile().toString();
  }

  @Memoized
  public String getMavenCoords() {
    return versionless().group()
        + VersionlessDependency.COORD_DELIMITER
        + versionless().name()
        + VersionlessDependency.COORD_DELIMITER
        + packaging()
        + versionless().classifier().map(c -> VersionlessDependency.COORD_DELIMITER + c).orElse("")
        + VersionlessDependency.COORD_DELIMITER
        + version();
  }

  @Memoized
  String getMavenCoordsForValidation() {
    return versionless().group()
        + VersionlessDependency.COORD_DELIMITER
        + versionless().name()
        + VersionlessDependency.COORD_DELIMITER
        + version();
  }

  @Memoized
  public String packaging() {
    return FilenameUtils.getExtension(realDependencyFile().getName());
  }

  @Memoized
  public String targetName() {
    StringBuilder targetName = new StringBuilder(versionless().name());
    if (isVersioned()) {
      if (!usePaddedVersion()) {
        targetName.append(NAME_DELIMITER).append(version());
      } else {
        targetName.append(NAME_DELIMITER);
        Iterable<String> versionSplit = Splitter.onPattern("(?<=\\.)").split(version());
        for (String part : versionSplit) {
          String padding = "0000".substring(Math.min(part.length(), 3));
          targetName.append(padding).append(part);
        }
      }
    }
    targetName.append(versionless().classifier().map(c -> NAME_DELIMITER + c).orElse(""));

    return targetName.toString();
  }

  @Memoized
  public String versionlessTargetName() {
    return versionless().name()
        + versionless().classifier().map(c -> NAME_DELIMITER + c).orElse("");
  }
  @Memoized
  Path basePath() {
    return Paths.get(versionless().group().replace('.', File.separatorChar));
  }

  @Memoized
  Dependency asGradleDependency() {
    // Internal class
    DefaultExternalModuleDependency externalDependency =
        new DefaultExternalModuleDependency(versionless().group(), versionless().name(), version());

    // Set transitive to false since this should just represent itself.
    externalDependency.setTransitive(false);

    // Internal class - Taken from ModuleFactoryHelper.java in gradle.
    Optional<String> classifier = versionless().classifier();
    if (classifier.isPresent()) {
      DependencyArtifact artifact =
          new DefaultDependencyArtifact(
              externalDependency.getName(), packaging(), packaging(), classifier.get(), null);
      externalDependency.addArtifact(artifact);
    }

    return externalDependency;
  }
}
