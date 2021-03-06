/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.headwire.aem.tooling.intellij.util;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.util.io.IOUtil;
import org.apache.commons.lang.StringUtils;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;

/**
 * Created by Andreas Schaefer (Headwire.com) on 4/30/15.
 */
public class Util {

    public static final Key<Long> MODIFICATION_DATE_KEY = Key.create("modification date");

    public static enum FolderLocation{start, middle, end, same, nowhere}

    // This is only used to test different style folder separators
    private static char folderSeparator = File.separatorChar;

    public static VirtualFile findFileOrFolder(VirtualFile rootFile, String name, boolean isFolder) {
        VirtualFile ret = null;
        for(VirtualFile child: rootFile.getChildren()) {
            if(child.isDirectory()) {
                if(isFolder) {
                    if(child.getName().equals(name)) {
                        return child;
                    }
                }
                ret = findFileOrFolder(child, name, isFolder);
                if(ret != null) { break; }
            } else {
                if(child.getName().equals(name)) {
                    ret = child;
                    break;
                }
            }
        }
        return ret;
    }

    public static boolean pathEndsWithFolder(String path, String folder) {
        return pathContainsFolder(path, folder) == FolderLocation.end;
    }

    public static FolderLocation pathContainsFolder(String path, String folder) {
        FolderLocation ret = FolderLocation.nowhere;
        if(path != null && folder != null) {
            int index = path.indexOf(folder);
            if (index >= 0) {
                if (index == 0) {
                    // Relative path start starts with the folder. If path longer than folder check if next character is file path separator
                    if(path.length() > folder.length()) {
                        ret = FolderLocation.start;
                    } else {
                        ret = FolderLocation.same;
                    }
                } else if(index == path.length() - folder.length()) {
                    // Folder is at the end of the path and path is longer as folder
                    if(path.charAt(index - 1) == folderSeparator) {
                        ret = FolderLocation.end;
                    }
                } else {
                    // Folder is in the middle somewhere
                    if(
                        path.charAt(index - 1) == folderSeparator
                        && path.charAt(index + folder.length()) == folderSeparator
                    ) {
                        ret = FolderLocation.middle;
                    }
                }
            }
        }
        return ret;
    }

    public static int convertToInt(String value, int defaultValue) {
        int ret = defaultValue;
        if(StringUtils.isNotBlank(value)) {
            try {
                ret = Integer.parseInt(value);
            } catch(NumberFormatException e) {
                // Ignore
            }
        }
        return ret;
    }

    public static long convertToLong(String value, long defaultValue) {
        long ret = defaultValue;
        if(StringUtils.isNotBlank(value)) {
            try {
                ret = Long.parseLong(value);
            } catch(NumberFormatException e) {
                // Ignore
            }
        }
        return ret;
    }

    public static <T extends Enum> T convertToEnum(String name, T defaultValue) {
        T ret = defaultValue;
        if(defaultValue == null) {
            throw new IllegalArgumentException("Default Value for Enumeration must be provided");
        }
        if(name != null) {
            try {
                ret = (T) defaultValue.valueOf(defaultValue.getClass(), name);
            } catch(IllegalArgumentException e) {
                // Enum was not found so use the default value instead
            }
        }
        return ret;
    }

    private static final FileAttribute MODIFICATION_STAMP_FILE_ATTRIBUTE = new FileAttribute("modificationStampFileAttribute", 1, true);

    public static boolean isOutdated(VirtualFile file) {
        long savedModificationTimeStamp = getModificationStamp(file);
        long actualModificationTimeStamp = file.getTimeStamp();
        return savedModificationTimeStamp < actualModificationTimeStamp;
    }

    public static void resetModificationStamp(VirtualFile fileOrFolder, boolean recursive) {
        VfsUtilCore.visitChildrenRecursively(
            fileOrFolder,
            new ResetFileVisitor(recursive)
        );
    }

    public static class ResetFileVisitor
        extends VirtualFileVisitor
    {
        private boolean recursive = false;

        public ResetFileVisitor(boolean recursive) {
            this.recursive = recursive;
        }

        @Override
        public boolean visitFile(VirtualFile file) {
            file.putUserData(MODIFICATION_DATE_KEY, null);
            if(file instanceof NewVirtualFile) {
                final DataOutputStream os = MODIFICATION_STAMP_FILE_ATTRIBUTE.writeAttribute(file);
                try {
                    try {
                        IOUtil.writeString(StringUtil.notNullize("0"), os);
                    } finally {
                        os.close();
                    }
                } catch(IOException e) {
                    // Ignore it but we might need to throw an exception
                    String message = e.getMessage();
                }
            }
            return recursive;
        }
    }

    public static long getModificationStamp(VirtualFile file) {
        long ret = -1;
        Long temporary = file.getUserData(Util.MODIFICATION_DATE_KEY);
        if(temporary == null || temporary <= 0) {
            if(file instanceof NewVirtualFile) {
                final DataInputStream is = MODIFICATION_STAMP_FILE_ATTRIBUTE.readAttribute(file);
                if(is != null) {
                    try {
                        try {
                            if(is.available() > 0) {
                                String value = IOUtil.readString(is);
                                ret = convertToLong(value, ret);
                                if(ret > 0) {
                                    file.putUserData(Util.MODIFICATION_DATE_KEY, ret);
                                }
                            }
                        } finally {
                            is.close();
                        }
                    } catch(IOException e) {
                        // Ignore it but we might need to throw an exception
                        String message = e.getMessage();
                    }
                }
            }
        } else {
            ret = temporary;
        }
        return ret;
    }

    public static void setModificationStamp(VirtualFile file) {
        // Store it in memory first
        if(file != null) {
            file.putUserData(Util.MODIFICATION_DATE_KEY, file.getTimeStamp());
            if(file instanceof NewVirtualFile) {
                final DataOutputStream os = MODIFICATION_STAMP_FILE_ATTRIBUTE.writeAttribute(file);
                try {
                    try {
                        IOUtil.writeString(StringUtil.notNullize(file.getTimeStamp() + ""), os);
                    } finally {
                        os.close();
                    }
                } catch(IOException e) {
                    // Ignore it but we might need to throw an exception
                    String message = e.getMessage();
                }
            }
        }
    }
}
