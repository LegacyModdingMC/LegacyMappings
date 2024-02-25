package io.github.legacymoddingmc.legacymappings;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.github.legacymoddingmc.legacymappings.LegacyMappingsPlugin;

public abstract class LegacyMappingsExtension {

    private final Project project;

    private Dependency userdev;
    private final List<Dependency> mcpLayers = new ArrayList<>();
    private Dependency yarn;
    private Dependency feather;
    private final List<String> layerOrder = new ArrayList<>();
    private final List<String> commentOrder = new ArrayList<>();

    public LegacyMappingsExtension(Project project) {
        this.project = project;
    }

    public void userdev(String userdevSpec) {
        this.userdev = project.getDependencies().create(userdevSpec);
    }

    public void mcp(String mcpSpec) {
        mcpLayers.add(project.getDependencies().create(mcpSpec));
    }

    public void yarn(String yarnSpec) {
        yarn = project.getDependencies().create(yarnSpec);
    }

    public void feather(String featherSpec) {
        feather = project.getDependencies().create(featherSpec);
    }

    public void layerOrder(String... order) {
        layerOrder.clear();
        layerOrder.addAll(Arrays.asList(order));
    }

    public void commentOrder(String... order) {
        commentOrder.clear();
        commentOrder.addAll(Arrays.asList(order));
    }

    public Dependency getUserdev() {
        return userdev;
    }

    public List<Dependency> getMcpLayers() {
        return mcpLayers;
    }

    public Dependency getYarn() {
        return yarn;
    }

    public Dependency getFeather() {
        return feather;
    }

    public List<String> getLayerOrder() {
        return layerOrder;
    }

    public List<String> getCommentOrder() {
        return commentOrder;
    }
}
