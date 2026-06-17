package org.bdj.external;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import org.bdj.Status;
import org.bdj.api.*;

public class Autoloader {
    private static final String AUTOLOADER_ELF = "ps5-unified-autoloader.elf";

    private static API api;
    private static long getpid;
    private static long kill;
    private static long sceKernelCheckReachability;
    private static long open;
    private static long read;
    private static long close;

    static {
        try {
            api = API.getInstance();
            getpid = api.dlsym(API.LIBKERNEL_MODULE_HANDLE, "getpid");
            kill = api.dlsym(API.LIBKERNEL_MODULE_HANDLE, "kill");
            sceKernelCheckReachability = api.dlsym(API.LIBKERNEL_MODULE_HANDLE, "sceKernelCheckReachability");
            open = api.dlsym(API.LIBKERNEL_MODULE_HANDLE, "open");
            read = api.dlsym(API.LIBKERNEL_MODULE_HANDLE, "read");
            close = api.dlsym(API.LIBKERNEL_MODULE_HANDLE, "close");
        } catch (Exception e) {
            Status.printStackTrace("Failed to initialize Autoloader native symbols", e);
        }
    }

    public static void main(String[] args) {
        Status.setProgress(81, "Waiting for ELF loader...");
        
        boolean elfldrReady = false;
        for (int i = 0; i < 50; i++) {
            if (isPortOpen(9021)) {
                elfldrReady = true;
                break;
            }
            try { Thread.sleep(200); } catch (Exception ignored) {}
        }
        
        if (!elfldrReady) {
            Status.error("ELF loader failed to start on port 9021");
            Status.setProgress(100, "Finished (ELF loader failed)");
            try { Thread.sleep(2000); } catch (Exception ignored) {}
            killApp();
            return;
        }

        Status.setProgress(85, "Searching for unified autoloader...");

        String elfPath = findAutoloader();
        if (elfPath == null) {
            Status.error("Unified autoloader not found: " + AUTOLOADER_ELF);
            Status.setProgress(100, "Finished (Autoloader not found)");
            try { Thread.sleep(2000); } catch (Exception ignored) {}
            killApp();
            return;
        }

        Status.success("Found: " + elfPath);
        Status.setProgress(90, "Loading unified autoloader...");

        try {
            byte[] elfData = readFileNative(elfPath);
            Status.info("Payload size: " + elfData.length + " bytes");
            sendToPort(9021, elfData);
        } catch (Exception e) {
            Status.printStackTrace("Error loading unified autoloader", e);
        }

        Status.success("Finished");
        Status.setProgress(100, "Finished");
    }

    private static String findAutoloader() {
        String path = "/mnt/disc/" + AUTOLOADER_ELF;
        if (existsNative(path)) {
            return path;
        }
        return null;
    }

    private static boolean existsNative(String path) {
        if (sceKernelCheckReachability == 0) {
            // Fallback: try open
            int fd = (int) api.call(open, new Text(path).address(), 0L);
            if (fd >= 0) {
                api.call(close, (long) fd);
                return true;
            }
            return false;
        }
        try {
            Text pathText = new Text(path);
            int ret = (int) api.call(sceKernelCheckReachability, pathText.address());
            return ret == 0;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean isPortOpen(int port) {
        Socket s = null;
        try {
            s = new Socket("127.0.0.1", port);
            return true;
        } catch (IOException e) {
            return false;
        } finally {
            if (s != null) {
                try { s.close(); } catch (IOException ignored) {}
            }
        }
    }

    private static void sendToPort(int port, byte[] data) throws IOException {
        Socket s = null;
        try {
            s = new Socket("127.0.0.1", port);
            OutputStream os = s.getOutputStream();
            os.write(data);
            os.flush();
            Status.success("Successfully sent " + data.length + " bytes to port " + port);
        } catch (IOException e) {
            Status.error("Error sending data to port " + port + ": " + e.getMessage());
            throw e;
        } finally {
            if (s != null) {
                try { s.close(); } catch (IOException ignored) {}
            }
        }
    }

    private static byte[] readFileNative(String path) throws IOException {
        if (open == 0 || read == 0 || close == 0) throw new IOException("Native IO not available");
        
        Text pathText = new Text(path);
        int fd = (int) api.call(open, pathText.address(), 0 /* O_RDONLY */);
        if (fd < 0) throw new IOException("Failed to open " + path + " (errno=" + api.errno() + ")");
        
        try {
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            long buf = api.malloc(4096);
            try {
                while (true) {
                    long n = api.call(read, (long) fd, buf, 4096L);
                    if (n <= 0) break;
                    byte[] chunk = new byte[(int) n];
                    api.memcpy(chunk, buf, n);
                    bos.write(chunk);
                }
            } finally {
                api.free(buf);
            }
            return bos.toByteArray();
        } finally {
            api.call(close, (long) fd);
        }
    }

    private static void killApp() {
        try {
            int pid = (int) api.call(getpid);
            Status.info("Killing process " + pid);
            api.call(kill, (long) pid, 9L);
        } catch (Throwable t) {
            Status.printStackTrace("Failed to kill app", t);
        }
    }

}
