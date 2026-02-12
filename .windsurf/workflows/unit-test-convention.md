---
description: Unit test naming convention and structure rules for Quarkus tests
---

## Unit Test Naming Convention

All unit test methods MUST follow this naming pattern:

```
test{MethodName}_when{Condition}_should{ExpectedResult}
```

### Examples

- `testCreateClip_whenValidRequest_shouldReturnPersistedClip`
- `testCreateClip_whenProjectNotFound_shouldThrowIllegalArgumentException`
- `testGetProjects_whenNoMatchingProjects_shouldReturnEmptyList`

### Rules

1. **Method name** - The method being tested (e.g., `CreateClip`, `GetProjects`)
2. **Condition** - The specific scenario (e.g., `whenValidRequest`, `whenPersistFails`)
3. **Expected result** - What should happen (e.g., `shouldReturnPersistedClip`, `shouldThrowRuntimeException`)
4. Use camelCase for each segment
5. Do NOT add comments to test methods (no `// Arrange`, `// Act`, `// Assert`, no section separators)

## Test Structure

1. Use `@QuarkusTest` annotation on the test class
2. Use `@InjectMock` for dependencies to mock
3. Use `@Inject` for the service under test
4. Use `@RunOnVertxContext` and `UniAsserter` parameter for reactive methods that use `@WithTransaction`
5. Use `asserter.assertThat()` for success assertions
6. Use `asserter.assertFailedWith()` for failure assertions
7. Place mock verifications (`verify()`) inside the asserter lambda (async context)

## Test Categories

Every service test should cover these categories:

- **Positive cases** - Valid inputs, successful operations
- **Negative cases** - Invalid inputs, failures, exceptions
- **Edge cases** - Boundary values, empty strings, null optional fields, very long strings
- **Normal cases** - Standard CRUD operations (list, search, etc.)

## Example

```java
@QuarkusTest
public class MyServiceTest {

    @InjectMock
    MyRepository myRepository;

    @Inject
    MyService myService;

    @Test
    @RunOnVertxContext
    void testCreateEntity_whenValidRequest_shouldReturnPersistedEntity(UniAsserter asserter) {
        MyEntity mockEntity = new MyEntity();
        mockEntity.id = UUID.randomUUID();
        mockEntity.name = "Test";

        when(myRepository.persist(any(MyEntity.class))).thenReturn(Uni.createFrom().item(mockEntity));

        asserter.assertThat(() -> myService.create(request),
            entity -> {
                assertNotNull(entity);
                assertEquals("Test", entity.name);
                verify(myRepository).persist(any(MyEntity.class));
            });
    }

    @Test
    @RunOnVertxContext
    void testCreateEntity_whenPersistFails_shouldThrowRuntimeException(UniAsserter asserter) {
        when(myRepository.persist(any(MyEntity.class)))
            .thenReturn(Uni.createFrom().failure(new RuntimeException("DB error")));

        asserter.assertFailedWith(() -> myService.create(request),
            throwable -> {
                assertTrue(throwable instanceof RuntimeException);
                assertEquals("DB error", throwable.getMessage());
            });
    }
}
```
