# E2E — orders-v0.10.0: PIT Mutation Testing

Reproduces the mutation-testing milestone for order-service, payment-service,
and product-service. PIT mutates application and domain logic, runs each
service's JUnit 5 suite, and reports whether the tests detect each behavior
change.

## Pre-conditions

- Java 21 and Maven are installed.
- Run from the repository root.
- `mvn -pl services/order-service test` and
  `mvn -pl services/payment-service test` and
  `mvn -pl services/product-service test` pass before running PIT.

## Steps

### 1. Run the ordinary test suite

```powershell
mvn -pl services/order-service test
# expect: BUILD SUCCESS; 18 tests, 0 failures
mvn -pl services/payment-service test
# expect: BUILD SUCCESS; 15 tests, 0 failures
mvn -pl services/product-service test
# expect: BUILD SUCCESS; 9 tests, 0 failures
```

### 2. Run PIT

PIT is configured as an opt-in Maven plugin in
`services/order-service/pom.xml`; normal Maven test and verify commands do not
run mutation analysis.

```powershell
mvn -pl services/order-service org.pitest:pitest-maven:mutationCoverage
# expect: BUILD SUCCESS; 13/13 mutations killed
mvn -pl services/payment-service org.pitest:pitest-maven:mutationCoverage
# expect: BUILD SUCCESS; 17/17 mutations killed
mvn -pl services/product-service org.pitest:pitest-maven:mutationCoverage
# expect: BUILD SUCCESS; 6/6 mutations killed
```

The HTML report is generated at
`services/order-service/target/pit-reports/index.html`.

## Results

The verified run used PIT 1.17.4 with the JUnit 5 plugin 1.2.1:

| Service | Mutated scope | Generated | Killed | Score | No coverage | Survived |
|---|---|---:|---:|---:|---:|---:|
| order-service | `com.example.order.application.*`, `com.example.order.domain.*` | 13 | 13 | 100% | 0 | 0 |
| payment-service | `com.example.payment.application.*`, `com.example.payment.domain.*` | 17 | 17 | 100% | 0 | 0 |
| product-service | `com.example.product.application.*`, `com.example.product.domain.*` | 6 | 6 | 100% | 0 | 0 |

## Post-conditions

- PIT exits successfully with the configured 70% mutation threshold.
- All three reports contain no survived mutations in their application/domain
  scopes.
- Normal `mvn test` remains unchanged and continues to pass.

## Gotchas

- PIT is intentionally limited to application and domain logic. Including all
  Spring adapters and infrastructure classes makes the score mostly measure
  missing integration-test coverage rather than the unit-test contract.
- Every generated mutation in all three services is covered and killed by the
  test suites.
- The HTML report is under `target/`, so it is a local build artifact and is
  not committed.

## Interview talking points

- Line coverage asks whether a line executed; mutation testing asks whether
  the test suite detects a meaningful behavioral change.
- A killed mutant demonstrates an assertion or interaction contract that
  protects the behavior. A survived mutant identifies a test-strength gap.
- PIT is opt-in because mutation analysis is substantially more expensive than
  the normal unit-test lifecycle.
