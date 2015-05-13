package com.headwire.aem.tooling.intellij.eclipse;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.headwire.aem.tooling.intellij.config.ServerConfiguration.Module;

/**
 * This is a wrapper class for the Eclipse IResource class used in some methods.
 * It should map the IntelliJ properties to the Eclipse IResource to avoid having
 * to rewrite certain classes.
 *
 * Created by schaefa on 5/13/15.
 */
public class IResource {
    public static final int FILE = 1;
    public static final int FOLDER = 2;
    public static final int CHECK_ANCESTORS = 3;

    protected VirtualFile file;
    protected Module module;
    protected IProject project;
    protected IPath location;

    public IResource(@NotNull Module module, @NotNull VirtualFile file) {
        this.module = module;
        this.file = file;
        project = new IProject(module);
        location = new IPath(file.getPath());
    }

    public String getName() {
        return file.getName();
    }

    public IProject getProject() {
        return project;
    }

    public IPath getLocation() {
        return location;
    }

    public int getType() {
        if(file.isDirectory()) { return FOLDER; }
        return FILE;
    }

    public boolean exists() {
        return file.exists();
    }

    public IPath getFullPath() {
        return new IPath(file.getPath());
    }

    public IResource getParent() {
        //AS TODO: Maybe we should create this once but lazy
        return new IFolder(module, file.getParent());
    }

    public List<IResource> members() {
        return null;
    }

    public IFile getFile(IPath parentSerializationFilePath) {
        return null;
    }

    public boolean isTeamPrivateMember(int checkAncestors) {
        return false;
    }
}