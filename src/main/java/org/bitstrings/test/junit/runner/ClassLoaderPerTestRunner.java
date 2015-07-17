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

import java.io.File;
import java.lang.annotation.Annotation;
import java.util.List;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.internal.runners.statements.Fail;
import org.junit.internal.runners.statements.RunAfters;
import org.junit.internal.runners.statements.RunBefores;
import org.junit.rules.MethodRule;
import org.junit.rules.TestRule;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;
import org.junit.runners.model.TestClass;

/**
 * A Junit 4 runner that executes each Test method with a new Custom classloader so that all variables,
 * also the final ones, are reinitialized.
 *
 * @author Rudy De Busscher
 * @author Pino Silvaggio
 * @since 1.0
 */
public class ClassLoaderPerTestRunner
    extends NamedRunner
{
    private static final Logger LOG = Logger.getLogger(ClassLoaderPerTestRunner.class.getName());

    // The classpath is needed because the custom class loader looks there to find the classes.
    private String classPath;
    private boolean classPathDetermined = false;

    // Some data related to the class from the custom class loader.
    private TestClass testClassFromClassLoader;
    private Object beforeFromClassLoader;
    private Object afterFromClassLoader;
    private Object ruleFromClassLoader;

    /**
     * Instantiates a new test per class loader runner.
     *
     * @param klass the class
     *
     * @throws InitializationError the initialization error
     */
    public ClassLoaderPerTestRunner(Class<?> klass)
        throws InitializationError
    {
        super(klass);
    }

    @Override
    protected synchronized Object createTest()
        throws Exception
    {
        // Need an instance now from the class loaded by the custom loader.
        return testClassFromClassLoader.getJavaClass().newInstance();
    }

    /**
     * Load classes (TestCase, @Before and @After with custom class loader.
     *
     * @throws ClassNotFoundException the class not found exception
     */
    private void loadClassesWithCustomClassLoader( FrameworkMethod method )
        throws ClassNotFoundException
    {
        // We need the classpath so that our custom loader can search for the requested classes.
        String testPath = getClassPath();

        TestClassLoader classLoader =
            testPath == null
                ? new TestClassLoader()
                : new TestClassLoader( testPath );

        ClptrExclude clptrExclude = getTestClass().getAnnotation( ClptrExclude.class );

        if ( clptrExclude != null )
        {
            classLoader.addExcludes( clptrExclude.value() );
        }

        clptrExclude = method.getAnnotation( ClptrExclude.class );

        if ( clptrExclude != null )
        {
            classLoader.addExcludes(clptrExclude.value() );
        }

        Thread.currentThread().setContextClassLoader(classLoader);
        testClassFromClassLoader = new TestClass(classLoader.loadClass(getTestClass().getJavaClass().getName()));


        // See withAfters and withBefores for the reason.
        beforeFromClassLoader = classLoader.loadClass(Before.class.getName());
        afterFromClassLoader = classLoader.loadClass(After.class.getName());
        ruleFromClassLoader = classLoader.loadClass(Rule.class.getName());
    }

    @Override
    protected synchronized Statement methodBlock(FrameworkMethod method)
    {
        FrameworkMethod newMethod = null;
        try
        {
            // Need the class from the custom loader now, so lets load the class.
            loadClassesWithCustomClassLoader( method );
            // The method as parameter is from the original class and thus not found in our
            // class loaded by the custom name (reflection is class loader sensitive)
            // So find the same method but now in the class from the class Loader.
            newMethod =
                new FrameworkMethod(
                        testClassFromClassLoader.getJavaClass().getMethod(method.getName()));
        }
        catch (Exception e)
        {
            // Show any problem nicely as a JUnit Test failure.
            return new Fail(e);
        }

        // We can carry out the normal JUnit functionality with our newly discoverd method now.
        return super.methodBlock(newMethod);
    }

    @Override
    protected synchronized Statement withAfters(FrameworkMethod method, Object target, Statement statement)
    {
        // We now to need to search in the class from the custom loader.
        //We also need to search with the annotation loaded by the custom class loader or otherwise we don't find any method.
        List<FrameworkMethod> afters =
            testClassFromClassLoader
                .getAnnotatedMethods((Class<? extends Annotation>) afterFromClassLoader);

        return new RunAfters(statement, afters, target);
    }

    @Override
    protected synchronized Statement withBefores(FrameworkMethod method, Object target, Statement statement)
    {
        // We now to need to search in the class from the custom loader.
        //We also need to search with the annotation loaded by the custom class loader or otherwise we don't find any method.
        List<FrameworkMethod> befores =
            testClassFromClassLoader
                .getAnnotatedMethods((Class<? extends Annotation>) beforeFromClassLoader);

        return new RunBefores(statement, befores, target);
    }

    @Override
    protected synchronized List<TestRule> getTestRules(Object target)
    {
        List<TestRule> result =
            testClassFromClassLoader
                .getAnnotatedMethodValues(target, (Class<? extends Annotation>) ruleFromClassLoader, TestRule.class);

        result.addAll(
            testClassFromClassLoader
                .getAnnotatedFieldValues(target, (Class<? extends Annotation>) ruleFromClassLoader, TestRule.class));

        return result;
    }

    @Override
    protected synchronized List<MethodRule> rules(Object target)
    {
        List<MethodRule> rules =
            testClassFromClassLoader
                .getAnnotatedMethodValues(target, (Class<? extends Annotation>) ruleFromClassLoader, MethodRule.class);

        rules.addAll(
            testClassFromClassLoader
                .getAnnotatedFieldValues(target, (Class<? extends Annotation>) ruleFromClassLoader, MethodRule.class));

        return rules;
    }

    /**
     * Gets the class path. This value is cached in a static variable for performance reasons.
     *
     * @return the class path
     */
    private String getClassPath()
    {
        if (classPathDetermined)
        {
            return classPath;
        }

        classPathDetermined = true;
        // running from maven, we have the classpath in this property.
        classPath = System.getProperty("surefire.test.class.path");
        if (classPath != null)
        {
            return classPath;
        }

        // For a multi module project, running it from the top we have to find it using another way.
        // We also need to set useSystemClassLoader=true in the POM so that we gets a jar with the classpath in it.
        String booterClassPath = System.getProperty("java.class.path");
        Vector<String> pathItems = null;
        if (booterClassPath != null)
        {
            pathItems = scanPath(booterClassPath);
        }
        // Do we have just 1 entry as classpath which is a jar?
        if (pathItems != null && pathItems.size() == 1 && isJar(pathItems.get(0)))
        {
            classPath = loadJarManifestClassPath(pathItems.get(0), "META-INF/MANIFEST.MF");
        }
        return classPath;

    }

    /**
     * Load jar manifest class path.
     *
     * @param path the path
     * @param fileName the file name
     *
     * @return the string
     */
    private static String loadJarManifestClassPath(String path, String fileName)
    {
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

            Manifest mf = new Manifest();

            mf.read(zipFile.getInputStream(entry));

            return
                mf.getMainAttributes()
                    .getValue(Attributes.Name.CLASS_PATH)
                    .replaceAll(" ", System.getProperty("path.separator"))
                    .replaceAll("file:/", "");
        }
        catch (Exception e)
        {
            LOG.throwing("ClassLoaderTestSuite", "loadJarManifestClassPath", e);
        }

        return null;
    }

    /**
     * Checks if is jar.
     *
     * @param pathEntry the path entry
     *
     * @return true, if is jar
     */
    private static boolean isJar(String pathEntry)
    {
        return pathEntry.endsWith(".jar") || pathEntry.endsWith(".zip");
    }

    /**
     * Scan path for all directories.
     *
     * @param classPath the class path
     *
     * @return the vector< string>
     */
    private static Vector<String> scanPath(String classPath)
    {
        String separator = System.getProperty("path.separator");
        Vector<String> pathItems = new Vector<String>(10);
        StringTokenizer st = new StringTokenizer(classPath, separator);
        while (st.hasMoreTokens())
        {
            pathItems.addElement(st.nextToken());
        }
        return pathItems;
    }
}
