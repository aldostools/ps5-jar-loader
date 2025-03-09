package org.ps5jb.sdk.io;

import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.ps5jb.sdk.core.SdkException;
import org.ps5jb.sdk.core.SdkRuntimeException;
import org.ps5jb.sdk.include.ErrNo;
import org.ps5jb.sdk.include.sys.FCntl;
import org.ps5jb.sdk.include.sys.fcntl.OpenFlag;
import org.ps5jb.sdk.lib.LibKernel;
import org.ps5jb.sdk.res.ErrorMessages;

public class FileInputStream extends java.io.FileInputStream {
    public FileInputStream(String name) throws FileNotFoundException {
        super(new FileDescriptor());
        disableProxies();
        openFile(new java.io.File(name));
    }

    public FileInputStream(java.io.File file) throws FileNotFoundException {
        super(new FileDescriptor());
        disableProxies();
        openFile(file);
    }

    public FileInputStream(FileDescriptor fileDescriptor) {
        super(fileDescriptor);
        disableProxies();
    }

    private void disableProxies() {
        try {
            Field proxyField = java.io.FileInputStream.class.getDeclaredField("proxy");
            proxyField.setAccessible(true);
            proxyField.set(this, null);
        } catch (NoSuchFieldException e) {
            // Ignore
        } catch (IllegalAccessException e) {
            throw new SdkRuntimeException(e);
        }

        FileDescriptor fd = getFd();
        FileDescriptorFactory.disableFileDescriptorProxy(fd);
    }

    public FileDescriptor getFd() {
        try {
            Field fdField = java.io.FileInputStream.class.getDeclaredField("fd");
            fdField.setAccessible(true);
            return (FileDescriptor) fdField.get(this);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new SdkRuntimeException(e);
        }
    }

    private void openFile(java.io.File file) throws FileNotFoundException {
        LibKernel libKernel = new LibKernel();
        FCntl fcntl = new FCntl(libKernel);
        ErrNo errNo = new ErrNo(libKernel);
        try {
            FileDescriptorFactory.openFile(fcntl, getFd(), file.getAbsolutePath(), OpenFlag.O_RDONLY);
        } catch (SdkException e) {
            FileNotFoundException ex = new FileNotFoundException(ErrorMessages.getClassErrorMessage(getClass(),
                    "fileOpenException", file.getAbsolutePath(), errNo.getLastError()));
            ex.initCause(e);
            throw ex;
        } finally {
            libKernel.closeLibrary();
        }
    }
}
