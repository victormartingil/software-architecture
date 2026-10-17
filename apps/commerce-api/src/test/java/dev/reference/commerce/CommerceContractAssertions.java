package dev.reference.commerce;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.*;
import java.nio.file.Path;
import org.springframework.test.web.servlet.MvcResult;

/** Reads the canonical contract directly; there is no generated test model. */
final class CommerceContractAssertions {
  private static final ObjectMapper JSON = new ObjectMapper();

  static JsonNode document() throws Exception {
    return JSON.readTree(Path.of("../../contracts/http/commerce.openapi.json").toFile());
  }

  static JsonSchema schema(JsonNode fragment) throws Exception {
    ObjectNode root = JSON.createObjectNode();
    root.put("$schema", "https://json-schema.org/draft/2020-12/schema");
    root.set("components", document().path("components"));
    fragment.fields().forEachRemaining(entry -> root.set(entry.getKey(), entry.getValue()));
    return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(root);
  }

  static void response(String path, String method, MvcResult result) throws Exception {
    var operation = document().path("paths").path(path).path(method);
    assertThat(operation.path("security").get(0).has("bearerAuth")).isTrue();
    var response =
        operation.path("responses").path(Integer.toString(result.getResponse().getStatus()));
    assertThat(response.isMissingNode()).as("Declared HTTP status").isFalse();
    String media =
        result.getResponse().getStatus() >= 400 ? "application/problem+json" : "application/json";
    assertThat(result.getResponse().getContentType()).startsWith(media);
    assertThat(
            schema(response.path("content").path(media).path("schema"))
                .validate(JSON.readTree(result.getResponse().getContentAsString())))
        .isEmpty();
  }

  static void request(String path, String method, String body, boolean valid) throws Exception {
    var fragment =
        document()
            .path("paths")
            .path(path)
            .path(method)
            .path("requestBody")
            .path("content")
            .path("application/json")
            .path("schema");
    assertThat(schema(fragment).validate(JSON.readTree(body)).isEmpty()).isEqualTo(valid);
  }
}
