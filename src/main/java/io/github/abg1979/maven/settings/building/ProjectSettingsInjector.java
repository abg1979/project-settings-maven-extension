package io.github.abg1979.maven.settings.building;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import javax.inject.Inject;
import javax.inject.Named;

import org.apache.maven.building.FileSource;
import org.apache.maven.building.Source;
import org.apache.maven.building.StringSource;
import org.apache.maven.cli.MavenCli;
import org.apache.maven.eventspy.AbstractEventSpy;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.TrackableBase;
import org.apache.maven.settings.building.*;
import org.apache.maven.settings.io.SettingsParseException;
import org.apache.maven.settings.io.SettingsReader;
import org.apache.maven.settings.io.SettingsWriter;
import org.apache.maven.settings.validation.SettingsValidator;
import org.codehaus.plexus.logging.Logger;
import org.jspecify.annotations.NonNull;

/**
 * Spy the SettingsBuildingRequest to inject project settings. This is a fast implemention, a better
 * way is to implement a new ConfigurationProcessor.
 */
@Named("project-settings")
public class ProjectSettingsInjector extends AbstractEventSpy {

    @Inject
    private Logger logger;

    @Inject
    private SettingsReader settingsReader;

    @Inject
    private SettingsWriter settingsWriter;

    @Inject
    private SettingsValidator settingsValidator;

    private ProjectSettingsMerger settingsMerger;

    private static final String PROJECT_SETTINGS_FILENAME = ".mvn/settings.xml";
    public static final String PROJECT_SETTINGS_SKIP_KEY = "skipProjectSettings";
    public static final String PROJECT_SETTINGS_DUMP_KEY = "projectSettings.dumpMergedSettings";

    private List<SettingsProblem> injectingProblems;

    @Override
    public void onEvent(final Object event) throws SettingsBuildingException {
        if (event instanceof SettingsBuildingResult settingsBuildingResult && null != injectingProblems) {
            // Assuming the SettingsBuilding{Request,Result} events will be dispatched in paired order.
            settingsBuildingResult.getProblems().addAll(0, injectingProblems);
            injectingProblems = null;
            return;
        } else if (!(event instanceof SettingsBuildingRequest)) {
            // dropping all irrelevant events
            return;
        }
        settingsMerger = new ProjectSettingsMerger(logger);
        final SettingsBuildingRequest request = (SettingsBuildingRequest) event;

        if (Boolean.parseBoolean(getProperty(request, PROJECT_SETTINGS_SKIP_KEY, "false"))) {
            logger.warn("Skip loading project settings.");
            return;
        }

        final String multiModuleProjectDirectory =
                getProperty(request.getSystemProperties(), MavenCli.MULTIMODULE_PROJECT_DIRECTORY);

        if (null == multiModuleProjectDirectory) {
            logger.debug(
                    "property "
                            + MavenCli.MULTIMODULE_PROJECT_DIRECTORY
                            + " is not set while searching project settings.xml.");
            return;
        }

        final File projectSettingsFile =
                new File(multiModuleProjectDirectory, PROJECT_SETTINGS_FILENAME);

        if (!projectSettingsFile.exists()) {
            logger.debug(
                    "Project settings file "
                            + projectSettingsFile.getPath()
                            + " does not exist, skip loading project settings.");
            return;
        }

        final List<SettingsProblem> problems = new ArrayList<>();

        logger.info("Reading project settings from " + projectSettingsFile.getPath());
        final Source projectSettingsSource = new FileSource(projectSettingsFile);
        final Settings projectSettings = readSettings(projectSettingsSource, problems);

        final Source globalSettingsSource = getSettingsSource(SettingType.GLOBAL, request);
        final Source userSettingsSource = getSettingsSource(SettingType.USER, request);

        // use user settings if it exists, otherwise use global settings
        SettingType injectType =
                null != userSettingsSource || null == globalSettingsSource
                        ? SettingType.USER
                        : SettingType.GLOBAL;
        final Source injectSource = injectType == SettingType.USER ? userSettingsSource : globalSettingsSource;

        final Settings injectSettings = readSettings(injectSource, problems);
        settingsMerger.merge(projectSettings, injectSettings, TrackableBase.USER_LEVEL);
        String location = generateLocationDescription(projectSettingsSource, injectSource);
        final StringSource resultSource = writeSettings(projectSettings, location);

        // let us write the merged settings to logger for debug purpose
        if (logger.isDebugEnabled()) {
            logger.debug("Project settings merged into " + injectType + " settings from " + location);
            logger.debug("Merged settings:\n" + resultSource.getContent());
        }

        if (Boolean.parseBoolean(getProperty(request, PROJECT_SETTINGS_DUMP_KEY, "false"))) {
            // write to file for debug purpose
            try {
                File debugFile = new File(projectSettingsFile.getParentFile(), "merged-settings.xml");
                settingsWriter.write(debugFile, null, projectSettings);
                logger.debug("Merged settings written to " + debugFile.getPath());
            } catch (IOException e) {
                logger.warn("Failed to write merged settings to file: " + e.getMessage(), e);
            }
        }

        if (injectType == SettingType.USER) {
            request.setUserSettingsFile(null).setUserSettingsSource(wrap(resultSource));
        } else {
            request.setGlobalSettingsFile(null).setGlobalSettingsSource(wrap(resultSource));
        }

        for (final SettingsProblem problem : problems) {
            if (SettingsProblem.Severity.ERROR.compareTo(problem.getSeverity()) >= 0) {
                throw new SettingsBuildingException(problems);
            }
        }

        // to save warning problems, insert back on the SettingsBuildingResult event
        injectingProblems = problems.isEmpty() ? null : problems;
    }

    @SuppressWarnings("deprecation")
    private SettingsSource wrap(Source source) {
        if (source instanceof SettingsSource settingsSource) {
            return settingsSource;
        }
        return new SettingsSource() {
            @Override
            public InputStream getInputStream() throws IOException {
                return source.getInputStream();
            }

            @Override
            public String getLocation() {
                return source.getLocation();
            }
        };
    }

    private static @NonNull String generateLocationDescription(Source projectSettingsSource, Source injectSource) {
        String location = "memory(" + projectSettingsSource.getLocation();
        if (null != injectSource) {
            location += ":" + injectSource.getLocation();
        }
        location += ")";
        return location;
    }

    private enum SettingType {
        USER, GLOBAL
    }
    private Source getSettingsSource(SettingType type, SettingsBuildingRequest request) {
        if (type == SettingType.USER) {
            if (null != request.getUserSettingsSource()) {
                return request.getUserSettingsSource();
            } else if (null != request.getUserSettingsFile() && request.getUserSettingsFile().exists()) {
                return new FileSource(request.getUserSettingsFile());
            }
        }
        if (type == SettingType.GLOBAL) {
            if (null != request.getGlobalSettingsSource()) {
                return request.getGlobalSettingsSource();
            } else if (null != request.getGlobalSettingsFile() && request.getGlobalSettingsFile().exists()) {
                return new FileSource(request.getGlobalSettingsFile());
            }
        }
        return null;
    }

    private Settings readSettingsAndCollectSettingsErrors(Source settingsSource, SettingsProblemCollector problems, boolean strict) throws IOException {
        final Map<String, ?> options = Collections.singletonMap(SettingsReader.IS_STRICT, strict);
        try {
            return settingsReader.read(settingsSource.getInputStream(), options);
        } catch (final SettingsParseException err) {
            SettingsProblem.Severity severity = strict ? SettingsProblem.Severity.FATAL : SettingsProblem.Severity.WARNING;
            problems.add(
                    severity,
                    "Non-parseable settings " + settingsSource.getLocation() + ": " + err.getMessage(),
                    err.getLineNumber(),
                    err.getColumnNumber(),
                    err);
            return null;
        }
    }
    private Settings readSettings(final Source settingsSource, final List<SettingsProblem> problems) {
        if (settingsSource == null) {
            return new Settings();
        }

        final SettingsProblemCollector problemsAdder =
                (severity, message, line, col, cause) -> {
                    if (line <= 0 && col <= 0 && cause instanceof SettingsParseException e) {
                        line = e.getLineNumber();
                        col = e.getColumnNumber();
                    }
                    problems.add(
                            new DefaultSettingsProblem(
                                    message, severity, settingsSource.getLocation(), line, col, cause));
                };

        Settings settings = null;
        // Strict mode is used to detect invalid settings, but we still want to read them in non-strict mode
        try {
            settings = readSettingsAndCollectSettingsErrors(settingsSource, problemsAdder, true);
        } catch (final IOException err) {
            problemsAdder.add(
                    SettingsProblem.Severity.FATAL,
                    "Non-readable settings " + settingsSource.getLocation() + ": " + err.getMessage(),
                    -1,
                    -1,
                    err);
            // we cannot retry io errors, so return empty settings
            return new Settings();
        }
        // if strict mode failed, try to read in non-strict mode to get as much settings as possible
        if (settings == null) {
            try {
                settings = readSettingsAndCollectSettingsErrors(settingsSource, problemsAdder, false);
            } catch (final IOException err) {
                problemsAdder.add(
                        SettingsProblem.Severity.FATAL,
                        "Non-parseable settings " + settingsSource.getLocation() + ": " + err.getMessage(),
                        0,
                        0,
                        err);
                return new Settings();
            }
        }

        settingsValidator.validate(settings, problemsAdder);

        return settings;
    }

    private StringSource writeSettings(final Settings settings, final String location) {
        final StringWriter writer = new StringWriter(1024 * 4);
        try {
            settingsWriter.write(writer, null, settings);
            return new StringSource(writer.toString(), location);
        } catch (final IOException err) {
            throw new IllegalStateException("Failed to serialize settings to memory", err);
        }
    }

    private String getProperty(final Properties fromProperties, final String key) {
        return getProperty(fromProperties, key, null);
    }

    private String getProperty(final Properties fromProperties, final String key, final String def) {
        return null == fromProperties ? null : fromProperties.getProperty(key, def);
    }

    private String getProperty(final SettingsBuildingRequest fromRequest, final String key) {
        return getProperty(fromRequest, key, null);
    }

    private String getProperty(
            final SettingsBuildingRequest fromRequest, final String key, final String def) {
        String val = getProperty(fromRequest.getUserProperties(), key);
        return null != val ? val : getProperty(fromRequest.getSystemProperties(), key, def);
    }
}
