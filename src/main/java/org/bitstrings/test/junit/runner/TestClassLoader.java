/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
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
 */
package org.bitstrings.test.junit.runner;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * A Classloader that delegates the loading of the classes to the parent if it is in the
 * excluded list or does it itself otherwise.
 * Useful for a system that sets up a classloader per Test system.
 *
 * @author Rudy De Busscher
 * @author Pino Silvaggio
 * @since 1.0
 */
public class TestClassLoader
    extends ClassLoader
{
    /** scanned class path */
    private final List<String> fPathItems = new ArrayList<>();

    /** name of excluded properties file */
    private static final String EXCLUDED_FILE = "clptr-excludes.properties";
    /** excluded paths */
    private final List<String> fExcluded = new ArrayList<>();

    public static final String SYSTEM_PROP_BASE_NAME = TestClassLoader.class.getName();
    public static final String SYSTEM_PROP_EXCLUDES_NAME = SYSTEM_PROP_BASE_NAME + ".excludes";

    /**
     * Constructs a TestCaseLoader. It scans the class path
     * and the excluded package paths
     */
    public TestClassLoader()
    {
        this(System.getProperty("java.class.path"));
    }

    /**
     * Constructs a TestCaseLoader. It scans the class path
     * and the excluded package paths
     */
    public TestClassLoader(String classPath)
    {
        scanPath(classPath);
        readInternalExcludes();
        readUserExcludes();
    }

    private void scanPath(String classPath)
    {
        StringTokenizer st = new StringTokenizer(classPath, System.getProperty("path.separator"));
        while (st.hasMoreTokens())
        {
            fPathItems.add(st.nextToken());
        }
    }

    @Override
    public URL getResource(String name)
    {
        return ClassLoader.getSystemResource(name);
    }

    @Override
    public InputStream getResourceAsStream(String name)
    {
        return ClassLoader.getSystemResourceAsStream(name);
    }

    /**
     * Checks if path is excluded.
     *
     * @param name the name
     *
     * @return true, if is excluded
     */
    public boolean isExcluded(String name)
    {
        for (String excluded : fExcluded)
        {
            if (name.startsWith(excluded))
            {
                return true;
            }
        }
        return false;
    }

    @Override
    public synchronized Class<?> loadClass(String name, boolean resolve)
            throws ClassNotFoundException
    {
        Class c = findLoadedClass(name);

        if (c != null)
        {
            return c;
        }

        //
        // Delegate the loading of excluded classes to the
        // standard class loader.
        //
        if (isExcluded(name))
        {
            try
            {
                c = findSystemClass(name);
                return c;
            }
            catch (ClassNotFoundException e)
            {
                // keep searching
            }
        }
        if (c == null)
        {
            byte[] data = lookupClassData(name);
            if (data == null)
                throw new ClassNotFoundException();
            c = defineClass(name, data, 0, data.length);
        }
        if (resolve)
            resolveClass(c);
        return c;
    }

    /**
     * Lookup class data in one of the classpath elements configured.
     *
     * @param className the class name
     *
     * @return the bytes making up the class
     *
     * @throws ClassNotFoundException when class can't be found on the classpath elements.
     */
    private byte[] lookupClassData(String className)
            throws ClassNotFoundException
    {
        byte[] data = null;
        for (int i = 0; i < fPathItems.size(); i++)
        {
            String path = fPathItems.get(i);
            String fileName = className.replace('.', '/') + ".class";
            if (isJar(path))
            {
                data = loadJarData(path, fileName);
            }
            else
            {
                data = loadFileData(path, fileName);
            }
            if (data != null)
                return data;
        }
        throw new ClassNotFoundException(className);
    }

    /**
     * Checks if is jar.
     *
     * @param pathEntry the path entry
     *
     * @return true, if is jar
     */
    boolean isJar(String pathEntry)
    {
        return pathEntry.endsWith(".jar") || pathEntry.endsWith(".zip");
    }

    /**
     * Load class bytes from file.
     *
     * @param path the path
     * @param fileName the file name
     *
     * @return the bytes making up the class
     */
    private byte[] loadFileData(String path, String fileName)
    {
        File file = new File(path, fileName);
        if (file.exists())
        {
            return getClassData(file);
        }
        return null;
    }

    private byte[] getClassData(File f)
    {
        try
        {
            FileInputStream stream = new FileInputStream(f);
            ByteArrayOutputStream out = new ByteArrayOutputStream(1000);
            byte[] b = new byte[1000];
            int n;
            while ((n = stream.read(b)) != -1)
                out.write(b, 0, n);
            stream.close();
            out.close();
            return out.toByteArray();

        }
        catch (IOException e)
        {
        }
        return null;
    }

    /**
     * Load class bytes from jar.
     *
     * @param path the path
     * @param fileName the file name
     *
     * @return the bytes making up the class
     */
    private byte[] loadJarData(String path, String fileName)
    {
        InputStream stream = null;

        File archive = new File(path);

        if (!archive.exists())
        {
            return null;
        }

        try (ZipFile zipFile = new ZipFile(archive))
        {
            ZipEntry entry = zipFile.getEntry(fileName);

            if (entry == null)
            {
                return null;
            }

            int size = (int) entry.getSize();

            stream = zipFile.getInputStream(entry);
            byte[] data = new byte[size];
            int pos = 0;
            while (pos < size)
            {
                int n = stream.read(data, pos, data.length - pos);
                pos += n;
            }

            return data;
        }
        catch (IOException e) {}

        return null;
    }

    public void addExclude(String name)
    {
        fExcluded.add(name.replace("*", ""));
    }

    public void addExcludes(String... names)
    {
        for (String name : names)
        {
            addExclude(name);
        }
    }

    /**
     * Read excluded packages for which classes are read by the parent class loader.
     */
    private void readInternalExcludes()
    {
        readExcludes( this.getClass().getResource( EXCLUDED_FILE ) );
    }

    private void readUserExcludes()
    {
        String excludesPath = System.getProperty( SYSTEM_PROP_EXCLUDES_NAME );

        if (excludesPath == null)
        {
            excludesPath = EXCLUDED_FILE;
        }

        readExcludes( Thread.currentThread().getContextClassLoader().getResource( excludesPath ) );
    }

    private void readExcludes( URL excludesUrl )
    {
        if ( excludesUrl == null )
        {
            return;
        }

        try ( BufferedReader in = new BufferedReader( new InputStreamReader( excludesUrl.openStream() ) ) )
        {
            while (in.ready())
            {
                addExclude(in.readLine());
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }
}
