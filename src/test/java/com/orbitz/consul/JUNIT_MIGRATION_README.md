# JUnit 4 to JUnit Jupiter (JUnit 5) Migration Guide

This document provides guidance on migrating tests from JUnit 4 to JUnit Jupiter (JUnit 5).

## Current Status

The project has been configured to support both JUnit 4 and JUnit Jupiter tests. This allows for a gradual migration of tests from JUnit 4 to JUnit Jupiter.

- JUnit Jupiter dependencies have been added to the project
- The test tasks have been configured to use the JUnit Platform
- The JUnit Vintage Engine has been added to support running JUnit 4 tests
- A sample test file (`JupiterMigrationExample.java`) has been created to demonstrate the migration approach

## Migration Steps

### 1. Update Imports

Replace JUnit 4 imports with JUnit Jupiter imports:

```java
// JUnit 4
import org.junit.Test;
import org.junit.Assert;
import org.junit.runner.RunWith;

// JUnit Jupiter
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;
```

### 2. Replace JUnit 4 Annotations

| JUnit 4                                | JUnit Jupiter                                                |
|----------------------------------------|-------------------------------------------------------------|
| `@Test`                                | `@Test`                                                      |
| `@Test(expected = Exception.class)`    | `assertThrows(Exception.class, () -> { ... })`              |
| `@Before`                              | `@BeforeEach`                                               |
| `@After`                               | `@AfterEach`                                                |
| `@BeforeClass`                         | `@BeforeAll`                                                |
| `@AfterClass`                          | `@AfterAll`                                                 |
| `@Ignore`                              | `@Disabled`                                                 |
| `@Category`                            | `@Tag`                                                      |
| `@RunWith(JUnitParamsRunner.class)`    | Remove and use `@ParameterizedTest` with `@MethodSource`    |
| `@Parameters(method = "methodName")`   | `@MethodSource("methodName")`                               |

### 3. Replace JUnit 4 Assertions

| JUnit 4                                | JUnit Jupiter                                                |
|----------------------------------------|-------------------------------------------------------------|
| `Assert.assertEquals(expected, actual)`| `assertEquals(expected, actual)`                            |
| `Assert.assertTrue(condition)`         | `assertTrue(condition)`                                     |
| `Assert.assertFalse(condition)`        | `assertFalse(condition)`                                    |
| `Assert.assertNull(object)`            | `assertNull(object)`                                        |
| `Assert.assertNotNull(object)`         | `assertNotNull(object)`                                     |
| `Assert.assertSame(expected, actual)`  | `assertSame(expected, actual)`                              |
| `Assert.assertNotSame(expected, actual)`| `assertNotSame(expected, actual)`                          |
| `Assert.fail(message)`                 | `fail(message)`                                             |

### 4. Update Parameterized Tests

JUnit 4 with JUnitParams:
```java
@RunWith(JUnitParamsRunner.class)
public class Test {
    @Test
    @Parameters(method = "getParameters")
    public void testSomething(String param1, int param2) {
        // Test code
    }
    
    public Object getParameters() {
        return new Object[] {
            new Object[] { "value1", 1 },
            new Object[] { "value2", 2 }
        };
    }
}
```

JUnit Jupiter:
```java
public class Test {
    @ParameterizedTest
    @MethodSource("getParameters")
    public void testSomething(String param1, int param2) {
        // Test code
    }
    
    static Stream<Arguments> getParameters() {
        return Stream.of(
            Arguments.of("value1", 1),
            Arguments.of("value2", 2)
        );
    }
}
```

### 5. Add Display Names (Optional)

JUnit Jupiter allows you to provide more descriptive names for tests:

```java
@Test
@DisplayName("Test that the calculation returns the correct result")
public void testCalculation() {
    // Test code
}
```

## Example

See `JupiterMigrationExample.java` for a complete example of a test class migrated from JUnit 4 to JUnit Jupiter.

## References

- [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/)
- [JUnit 5 Migration Guide](https://junit.org/junit5/docs/current/user-guide/#migrating-from-junit4)