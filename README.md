# junit-clptr
ClassLoader per Test runner for JUnit 4.12+ based on myfaces-test "TestPerClassLoaderRunner".

Each test method will run with its very own ClassLoader which can be very useful for a variety of use cases (testing classes with static fields for example).

## Usage

```java
import org.bitstrings.test.junit.runner.ClassLoaderPerTestRunner;

@RunWith( ClassLoaderPerTestRunner.class )
public class MyTest
{
  ...
}
```

## Maven dependency

```xml
<dependency>
  <groupId>org.bitstrings.test</groupId>
  <artifactId>junit-clptr</artifactId>
  <version>1.1</version>
</dependency>
```

### Exclude classes from isolation

Use the default file `clptr-excludes.properties` to add excluded packages or classes from isolation.

The file should be on the classpath.

You can override the excludes file location using the system property `org.bitstrings.test.junit.runner.TestClassLoader.excludes`.
```
org.bitstrings.test.junit.runner.TestClassLoader.excludes=com/company/res/clptr-excludes.properties
```

The file is simply a list of packages and classes:
```
jmockit.
org.apache.
com.company.test.TestClass
```

## What's new

* Fix for "@Rule" annotated field and method
* Threadsafe (Hopeful...)
* Support excludes
