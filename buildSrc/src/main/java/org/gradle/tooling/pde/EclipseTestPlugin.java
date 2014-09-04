package org.gradle.tooling.pde;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.internal.file.FileResolver;

import javax.inject.Inject;

public class EclipseTestPlugin implements Plugin<Project> {

    private final FileResolver fileResolver;

    @Inject
    public EclipseTestPlugin(FileResolver fileResolver) {
        this.fileResolver = fileResolver;
    }

    @Override
    public void apply(Project project) {
        project.getExtensions().create("eclipseTestExt", EclipseTestExtension.class);
    }

    public FileResolver getFileResolver() {
        return fileResolver;
    }
}
