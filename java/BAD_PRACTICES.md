# Bad Engineering Practices in This Codebase

This kata intentionally introduces bad practices so engineers can identify, understand, and refactor them.

---

## 1. Anemic Domain Model

`Order`, `OrderItem`, `Product`, and `Category` are pure data holders — just fields, getters, and setters with no behavior. All business logic lives outside the domain, in controllers. This is the root cause of most other problems below.

---

## 2. Tell Don't Ask Violations

Controllers query an object's state to make decisions, then tell it what to do:

- `OrderApprovalController` asks `order.getStatus()`, decides externally whether to approve or reject, then calls `order.setStatus()`.
- `OrderShipmentController` asks `order.getStatus()`, decides externally whether shipment is valid, then calls `order.setStatus(OrderStatus.SHIPPED)`.
- `OrderCreationController` manually accumulates `total` and `tax` by getting them, adding, and setting them back.

Objects should be told what to do and handle their own state transitions internally.

---

## 3. Business Logic in Controllers

State transition rules (which transitions are valid and which exceptions to throw) belong to the domain, not the application layer. If a new controller needed to approve an order, it would have to duplicate this logic.

---

## 4. Single Responsibility Principle Violation in `OrderCreationController`

`OrderCreationController.run()` does too many things in a single method:

- Product lookup
- Tax calculation (unitary tax, taxed amount, total tax per line)
- Order item construction
- Accumulation of order totals
- Persistence

---

## 5. Law of Demeter Violations (Train Wrecks)

In `OrderCreationController`:
```java
product.getCategory().getTaxPercentage()
```

In the tests:
```java
insertedOrder.getItems().get(0).getProduct().getName()
insertedOrder.getItems().get(0).getProduct().getPrice()
```

Controllers and tests reach deep into the object graph rather than asking each object directly for what they need.

---

## 6. Mutable Domain Objects with No Invariant Protection

All fields across all domain objects are exposed with public setters. This means:

- Objects can be created in invalid states (e.g., an `Order` with no status, no items, no currency).
- State can be changed arbitrarily from anywhere — e.g., calling `order.setStatus(CREATED)` on a shipped order.
- No constructors enforce required fields.

---

## 7. Mutating Internal Collections via Getters

In `OrderCreationController`:
```java
order.getItems().add(orderItem);
```

`getItems()` returns the actual internal `ArrayList`, and the controller modifies it directly. The `Order` object has no awareness that its items changed, breaking encapsulation.

---

## 8. Object Construction Scattered in Controllers

The controller manually constructs and initializes the `Order` object:
```java
Order order = new Order();
order.setStatus(OrderStatus.CREATED);
order.setItems(new ArrayList<>());
order.setCurrency("EUR");
order.setTotal(new BigDecimal("0.00"));
order.setTax(new BigDecimal("0.00"));
```

This logic belongs inside the `Order` class — either as a constructor or factory method.

---

## 9. Magic Values and Hardcoded Constants

- `"EUR"` is a hardcoded magic string in `OrderCreationController` — not a constant, not configurable.
- `100` in the tax calculation (`divide(valueOf(100))`) is a magic number with no named constant.
- `"0.00"` as initial values for total and tax.

---

## 10. Primitive Obsession

- `Order.currency` is a raw `String` instead of a `Currency` type or enum.
- `Order.id` is a primitive `int` with no domain meaning or generation strategy.

---

## 11. Misplaced Exceptions

All exceptions live in `controller.exception`, but they represent domain-level invariants (e.g., "a shipped order cannot be changed", "a rejected order cannot be approved"). These are domain rules, so the exceptions belong in the domain layer.

---

## 12. If-Else Chains for State Machine Logic

`OrderApprovalController` and `OrderShipmentController` use chained `if` statements to check order status:
```java
if (order.getStatus().equals(OrderStatus.SHIPPED)) { ... }
if (request.isApproved() && order.getStatus().equals(OrderStatus.REJECTED)) { ... }
if (!request.isApproved() && order.getStatus().equals(OrderStatus.APPROVED)) { ... }
```

This is a state machine that belongs inside the domain, not spread across controllers.

---

## 13. Vague Method Names

All three controllers expose a method named `run()`. This name conveys no intent — `createOrder()`, `approveOrder()`, and `shipOrder()` would all be clearer.

---

## 14. Returning `null` from Repository

`InMemoryProductCatalog.getByName()` returns `null` when a product is not found:
```java
return products.stream()...findFirst().orElse(null);
```

This forces null checks in callers (`if (product == null)`). `Optional<Product>` would be the correct approach.

---

## 15. Unsafe `.get()` on Optional in Test Double

`TestOrderRepository.getById()` calls `.get()` directly on an `Optional`:
```java
return orders.stream().filter(o -> o.getId() == orderId).findFirst().get();
```

This throws `NoSuchElementException` with no meaningful error message when an order is not found.

---

## 16. Double Brace Initialization Anti-Pattern in Tests

In `OrderCreationControllerTest`:
```java
private Category food = new Category() {{
    setName("food");
    setTaxPercentage(new BigDecimal("10"));
}};;
```

This creates anonymous subclasses on every instantiation, which can cause memory leaks because the anonymous class holds a reference to the enclosing instance. There is also a stray double semicolon `}};;`.

---

## 17. Overly Broad `throws Exception` in Tests

Every test method declares `throws Exception`:
```java
public void sellMultipleItems() throws Exception {
```

No checked exceptions are actually thrown — this declaration is unnecessarily broad.

---

## 18. Complex Inline Tax Calculation

The tax calculation is inlined as four long chained `BigDecimal` expressions inside a loop, with no extraction to a method or domain object, making it hard to read, test, or reuse:
```java
final BigDecimal unitaryTax = product.getPrice().divide(valueOf(100)).multiply(product.getCategory().getTaxPercentage()).setScale(2, HALF_UP);
final BigDecimal unitaryTaxedAmount = product.getPrice().add(unitaryTax).setScale(2, HALF_UP);
```

---

## 19. Tax Calculation Rounding Inconsistency

`taxedAmount` is rounded with `HALF_UP`, but `taxAmount` is not:
```java
final BigDecimal taxedAmount = unitaryTaxedAmount.multiply(...).setScale(2, HALF_UP);
final BigDecimal taxAmount = unitaryTax.multiply(BigDecimal.valueOf(itemRequest.getQuantity())); // no rounding
```

This produces an accumulated tax value with more than 2 decimal places.

---

## 20. No Input Validation at System Boundaries

`SellItemRequest`, `OrderApprovalRequest`, and `OrderShipmentRequest` perform no validation: quantity could be zero or negative, product name could be null or empty, and order ID could be negative. There is no guard at the system entry point.
