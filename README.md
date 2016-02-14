[![Build Status](https://travis-ci.org/bitstrings/junit-clptr.svg?branch=master)](https://travis-ci.org/bitstrings/junit-clptr)

# junit-clptr
ClassLoader per Test runner for JUnit 4.12+ based on myfaces-test "TestPerClassLoaderRunner".

Each test method will run with its very own ClassLoader which can be very useful for a variety of use cases (testing classes with static fields for example).

## Maven dependency
```xml
<dependency>
  <groupId>org.bitstrings.test</groupId>
  <artifactId>junit-clptr</artifactId>
  <version>1.2.2</version>
</dependency>
```

## Usage
```java
import org.bitstrings.test.junit.runner.ClassLoaderPerTestRunner;

@RunWith( ClassLoaderPerTestRunner.class )
public class Test
{
  ...
}
```

### Exclude classes using `@ClptrExclude` annotation
You can exclude a package or a specific class.
```
@RunWith( ClassLoaderPerTestRunner.class )
@ClptrExclude( "mockit." )
public class Test
{
    ...
}
```

```
@RunWith( ClassLoaderPerTestRunner.class )
@ClptrExclude( { "mockit.", "org.somethings.MyClass" } )
public class Test
{
    ...
}
```
You may use `@ClptrExclude` on classes and methods.

### Globally exclude classes from isolation using file

Use the default file `clptr-excludes.properties` to add excluded packages or classes from isolation.

The file should be on the classpath.

You can override the excludes file location using the system property `org.bitstrings.test.junit.runner.TestClassLoader.excludes`.
```
org.bitstrings.test.junit.runner.TestClassLoader.excludes=com/company/res/clptr-excludes.properties
```

The file is simply a list of packages and classes (see `@ClptrExclude`):
```
jmockit.
org.apache.
com.company.test.TestClass
```

## What's new

* @ClptrExclude annotation;
* Fix for "@Rule" annotated field and method;
* Threadsafe (Hopeful...);
* Support excludes.
