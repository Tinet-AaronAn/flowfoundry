package com.tinet.flowfoundry.interpreter.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class VariableStoreTest {

  @Test
  void readsNestedObjectPaths() {
    VariableStore variables =
        new VariableStore(
            Map.of(
                "customer",
                Map.of(
                    "name", "Alice",
                    "address", Map.of("city", "Beijing"))));

    assertThat(variables.resolve("$.input.customer.name")).isEqualTo("Alice");
    assertThat(variables.resolve("$.input.customer.address.city")).isEqualTo("Beijing");
  }

  @Test
  void readsArrayIndexAndNestedFields() {
    List<Map<String, Object>> contacts = new ArrayList<>();
    contacts.add(Map.of("name", "张三", "phone", "13800000001"));
    contacts.add(Map.of("name", "李四", "phone", "13800000002"));

    VariableStore variables = new VariableStore(Map.of("contacts", contacts));

    assertThat(variables.resolve("$.input.contacts[0].phone")).isEqualTo("13800000001");
    assertThat(variables.resolve("$.input.contacts[1].name")).isEqualTo("李四");
    assertThat(variables.resolve("contacts[0].phone")).isEqualTo("13800000001");
  }

  @Test
  void readsLastElementWithPythonStyleNegativeIndex() {
    List<Map<String, Object>> contacts = new ArrayList<>();
    contacts.add(Map.of("name", "张三", "phone", "13800000001"));
    contacts.add(Map.of("name", "李四", "phone", "13800000002"));

    VariableStore variables = new VariableStore(Map.of("contacts", contacts));

    assertThat(variables.resolve("$.input.contacts[-1].phone")).isEqualTo("13800000002");
    assertThat(variables.resolve("$.input.contacts[-2].name")).isEqualTo("张三");
    assertThat(variables.resolve("contacts[-1].phone")).isEqualTo("13800000002");
  }

  @Test
  void writesLastElementWithNegativeIndex() {
    VariableStore variables = new VariableStore(Map.of());
    variables.assign(
        "contacts",
        new ArrayList<>(
            List.of(
                Map.of("phone", "13800000001"),
                new LinkedHashMap<>(Map.of("phone", "13800000002")))));

    variables.assign("contacts[-1].phone", "13800000099");

    assertThat(variables.resolve("$.vars.contacts[-1].phone")).isEqualTo("13800000099");
    assertThat(variables.resolve("$.vars.contacts[1].phone")).isEqualTo("13800000099");
  }

  @Test
  void returnsNullWhenNegativeIndexIsOutOfBoundsOnRead() {
    VariableStore variables =
        new VariableStore(Map.of("contacts", List.of(Map.of("phone", "13800000001"))));

    assertThat(variables.resolve("$.input.contacts[-2].phone")).isNull();
    assertThat(variables.resolve("$.input.contacts[-1].phone")).isEqualTo("13800000001");
  }

  @Test
  void readsArrayIndexFromVarsAndLastResult() {
    VariableStore variables = new VariableStore(Map.of());
    variables.assign(
        "items",
        List.of(
            Map.of("sku", "A-1", "qty", 2),
            Map.of("sku", "B-2", "qty", 5)));
    variables.setLastResult(Map.of("results", List.of(Map.of("score", 90))));

    assertThat(variables.resolve("$.vars.items[0].sku")).isEqualTo("A-1");
    assertThat(variables.resolve("$.vars.items[1].qty")).isEqualTo(5);
    assertThat(variables.resolve("$.lastResult.results[0].score")).isEqualTo(90);
  }

  @Test
  void returnsNullWhenArrayIndexIsOutOfBounds() {
    VariableStore variables =
        new VariableStore(Map.of("contacts", List.of(Map.of("phone", "13800000001"))));

    assertThat(variables.resolve("$.input.contacts[1].phone")).isNull();
    assertThat(variables.resolve("$.input.contacts[0].missing")).isNull();
  }

  @Test
  void writesNestedArrayPaths() {
    VariableStore variables = new VariableStore(Map.of());

    variables.assign("contacts[0].phone", "13800000001");
    variables.assign("contacts[0].name", "张三");
    variables.assign("contacts[1].phone", "13800000002");

    assertThat(variables.resolve("$.vars.contacts[0].phone")).isEqualTo("13800000001");
    assertThat(variables.resolve("$.vars.contacts[0].name")).isEqualTo("张三");
    assertThat(variables.resolve("$.vars.contacts[1].phone")).isEqualTo("13800000002");
  }

  @Test
  void writesThroughExistingNestedStructure() {
    Map<String, Object> order = new LinkedHashMap<>();
    order.put("items", new ArrayList<>(List.of(new LinkedHashMap<>(Map.of("sku", "A-1")))));
    VariableStore variables = new VariableStore(Map.of());
    variables.assign("order", order);

    variables.assign("order.items[0].qty", 3);
    variables.assign("order.items[1].sku", "B-2");

    assertThat(variables.resolve("$.vars.order.items[0].sku")).isEqualTo("A-1");
    assertThat(variables.resolve("$.vars.order.items[0].qty")).isEqualTo(3);
    assertThat(variables.resolve("$.vars.order.items[1].sku")).isEqualTo("B-2");
  }

  @Test
  void rejectsInvalidPathSegments() {
    VariableStore variables = new VariableStore(Map.of());

    assertThatThrownBy(() -> variables.assign("contacts[-1].phone", "138"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("out of bounds");
    assertThatThrownBy(() -> variables.assign("contacts[abc].phone", "138"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid array index");
  }

  @Test
  void rejectsAssignmentToInputOrLastResult() {
    VariableStore variables = new VariableStore(Map.of("phone", "138"));

    assertThatThrownBy(() -> variables.assign("$.input.contacts[0].phone", "139"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Only workflow variables");
    assertThatThrownBy(() -> variables.assign("$.lastResult.phone", "139"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Only workflow variables");
  }
}
