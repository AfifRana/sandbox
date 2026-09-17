# E2E — orders-v0.10.0: PIT Mutation Testing

Reproduces the mutation-testing milestone for order-service. PIT mutates the
application and domain logic, runs the existing JUnit 5 suite, and reports
whether the tests detect each behavior change.

## Pre-conditions

- Java 21 and Maven are installed.
- Run from the repository root.
- `mvn -pl services/order-service test` passes before running PIT.

## Steps

### 1. Run the ordinary test suite

```powershell
mvn -pl services/order-service test
# expect: BUILD SUCCESS; 16 tests, 0 failures
```

### 2. Run PIT

PIT is configured as an opt-in Maven plugin in
`services/order-service/pom.xml`; normal Maven test and verify commands do not
run mutation analysis.

```powershell
mvn -pl services/order-service org.pitest:pitest-maven:mutationCoverage
# expect: BUILD SUCCESS; mutation score is at least 70%
```

The HTML report is generated at
`services/order-service/target/pit-reports/index.html`.

## Results

The verified run used PIT 1.17.4 with the JUnit 5 plugin 1.2.1:

| Metric | Result |
|---|---:|
| Mutated scope | `com.example.order.application.*`, `com.example.order.domain.*` |
| Mutations generated | 13 |
| Mutations killed | 11 |
| Mutation score | 85% |
| Mutated-class line coverage | 90% |
| Test strength | 100% |
| No-coverage mutations | 2 |
| Survived mutations | 0 |

## Post-conditions

- PIT exits successfully with the configured 70% mutation threshold.
- The report contains no survived mutations in the application/domain scope.
- Normal `mvn test` remains unchanged and continues to pass.

## Gotchas

- PIT is intentionally limited to application and domain logic. Including all
  Spring adapters and infrastructure classes makes the score mostly measure
  missing integration-test coverage rather than the unit-test contract.
- The two no-coverage mutations are recorded rather than hidden by lowering
  the threshold.
- The HTML report is under `target/`, so it is a local build artifact and is
  not committed.

## Interview talking points

- Line coverage asks whether a line executed; mutation testing asks whether
  the test suite detects a meaningful behavioral change.
- A killed mutant demonstrates an assertion or interaction contract that
  protects the behavior. A survived mutant identifies a test-strength gap.
- PIT is opt-in because mutation analysis is substantially more expensive than
  the normal unit-test lifecycle.
