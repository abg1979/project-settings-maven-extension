package io.github.abg1979.maven.settings.building;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.apache.maven.settings.*;
import org.apache.maven.settings.merge.MavenSettingsMerger;
import org.apache.maven.shared.utils.StringUtils;
import org.codehaus.plexus.logging.Logger;

public class ProjectSettingsMerger extends MavenSettingsMerger {
    private final Logger logger;

    public ProjectSettingsMerger(Logger logger) {
        this.logger = logger;
    }

    /**
     * @param dominant             i.e, a project setting
     * @param recessive            a user or global level settings
     * @param recessiveSourceLevel souce level
     */
    @Override
    public void merge(
            final Settings dominant, final Settings recessive, final String recessiveSourceLevel) {
        if (null == dominant || null == recessive) {
            return;
        }

        // Always ignore some locally used fields in project settings
        if (StringUtils.isBlank(dominant.getLocalRepository())) {
            // if the local repository is not defined in project settings, use the one from user/global settings
            dominant.setLocalRepository(recessive.getLocalRepository());
        }
        // use the user/global settings for interactive mode, plugin registry and offline mode
        dominant.setInteractiveMode(recessive.isInteractiveMode());
        dominant.setUsePluginRegistry(recessive.isUsePluginRegistry());
        dominant.setOffline(recessive.isOffline());

        // All proxies are ignored, cause it is always defined at user side
        // and its authentication can't be config in <servers/>
        dominant.setProxies(new ArrayList<>());

        // deep merge server configuration from both Settings
        // NB: order of the elements in <servers/> does not matter.
        final Map<String, Server> serverById = new HashMap<>();
        for (final Server server : dominant.getServers()) {
            logger.info("Merging server configuration for id: " + server.getId());
            serverById.put(server.getId(), server);
        }

        for (final Server server : recessive.getServers()) {
            if (serverById.containsKey(server.getId())) {
                final Server dominantServer = serverById.get(server.getId());
                dominantServer.setSourceLevel(recessiveSourceLevel);
                // prefer user level fields for local only infos
                dominantServer.setUsername(server.getUsername());
                dominantServer.setPassword(server.getPassword());
                dominantServer.setPrivateKey(server.getPrivateKey());
                dominantServer.setPassphrase(server.getPassphrase());
                dominantServer.setFilePermissions(server.getFilePermissions());
                dominantServer.setDirectoryPermissions(server.getDirectoryPermissions());
            }
        }
        super.merge(dominant, recessive, recessiveSourceLevel);
    }
}
