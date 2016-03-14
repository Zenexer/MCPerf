package com.earth2me.mcperf;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.util.*;

public class ServiceLoader<T> implements Iterable<T> {
    private static final String SERVICES_DIR = "META-INF/services/";

    private final String path;
    private final Class<T> serviceType;
    private final ClassLoader context;

    public static <T> ServiceLoader<T> load(Class<T> serviceType, ClassLoader context) {
        return new ServiceLoader<>(serviceType, context);
    }

    public static <T> ServiceLoader<T> load(Class<T> serviceType) {
        return new ServiceLoader<>(serviceType, Thread.currentThread().getContextClassLoader());
    }

    private ServiceLoader(Class<T> serviceType, ClassLoader context) {
        this.serviceType = serviceType;
        this.context = context;
        this.path = SERVICES_DIR + serviceType.getName();
    }

    private ServiceConfigurationError error(Throwable cause, String message) {
        return new ServiceConfigurationError(serviceType.getName() + ": " + message, cause);
    }

    private ServiceConfigurationError error(Throwable cause, String format, Object... args) {
        return error(cause, String.format(format, args));
    }

    @Override
    public Iterator<T> iterator() {
        Enumeration<URL> urls;
        try {
            urls = context.getResources(path);
        } catch (IOException e) {
            throw error(e, "Error locating configuration files");
        }

        return new Iterator<T>() {
            private URL url;
            private InputStream in;
            private BufferedReader rx;
            private String line;
            private boolean ranHasNext;
            private boolean hasNext;

            private void close(Closeable... closeables) {
                for (Closeable closeable : closeables) {
                    if (closeable == null) {
                        continue;
                    }

                    try {
                        closeable.close();
                    } catch (IOException e) {
                        // ignore
                    }
                }
            }

            private boolean hasNextUrl() {
                close(in, rx);
                in = null;
                rx = null;

                while (urls.hasMoreElements()) {
                    url = urls.nextElement();

                    try {
                        URLConnection conn = url.openConnection();
                        conn.setUseCaches(false);
                        in = conn.getInputStream();
                        rx = new BufferedReader(new InputStreamReader(in, "utf-8"));
                        return true;
                    } catch (IOException e) {
                        // ignore
                    }
                }

                return false;
            }

            private boolean hasNextLine() {
                String line;
                do {
                    try {
                        line = rx.readLine();
                    } catch (IOException e) {
                        this.line = null;
                        return false;
                    }

                    if (line == null) {
                        this.line = null;
                        return false;
                    }

                    line = line.trim();
                } while (line.startsWith("#"));

                this.line = line;
                return true;
            }

            @Override
            public boolean hasNext() {
                if (ranHasNext) {
                    return hasNext;
                }

                while (!hasNextLine()) {
                    if (!hasNextUrl()) {
                        hasNext = false;
                        ranHasNext = true;
                        return false;
                    }
                }

                ranHasNext = true;
                hasNext = true;
                return true;
            }

            @Override
            public T next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }

                if (line == null) {
                    throw new IllegalStateException();
                }

                Class<?> type;
                try {
                    type = context.loadClass(line);
                } catch (ClassNotFoundException e) {
                    throw error(e, "Provider %s not found", line);
                }

                if (!serviceType.isAssignableFrom(type)) {
                    throw error(null, "Provider %s not a subtype", type.getName());
                }

                T service;
                try {
                    service = serviceType.cast(type.newInstance());
                } catch (Throwable e) {
                    throw error(e, "Provider %s could not be instantiated", type.getName());
                }

                return service;
            }
        };
    }
}
