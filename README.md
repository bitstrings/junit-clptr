# junit-clptr
ClassLoader per Test runner for JUnit 4.12+ based on myfaces-test "TestPerClassLoader".

Each test method will run with its very own ClassLoader which can be very useful for a variety of use cases (testing classes with static fields for example).

## Usage

```java
@RunWith( ClassLoaderPerTestRunner.class )
public class MyTest
{
  ...
}
```
