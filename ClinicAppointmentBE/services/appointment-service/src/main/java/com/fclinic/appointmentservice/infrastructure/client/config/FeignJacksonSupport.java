package com.fclinic.appointmentservice.infrastructure.client.config;

import static com.fclinic.appointmentservice.infrastructure.client.config.FeignHeaderGuards.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fclinic.appointmentservice.domain.AppointmentError;
import com.fclinic.appointmentservice.infrastructure.client.auth.dto.IntrospectionResponseDto;
import feign.*;
import feign.codec.*;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.http.ResponseEntity;
import tools.jackson.core.*;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;

/** Strict wire schemas live here; application adapters consume typed values only. */
public final class FeignJacksonSupport implements Decoder {
  public static final JsonMapper JSON = JsonMapper.builder(JsonFactory.builder()
      .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
      .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(24).maxStringLength(262144).build()).build())
      .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
      .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
      .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).findAndAddModules().build();

  public Object decode(Response response, Type type) throws java.io.IOException {
    try {
      require(response.status() == 200 && values(response.headers(), "Content-Type").size() == 1
          && values(response.headers(), "Content-Type").getFirst().matches("(?i)application/json(?:\\s*;.*)?")
          && values(response.headers(), "Content-Encoding").stream().allMatch(v -> v.equalsIgnoreCase("identity")));
      byte[] bytes;
      try (var in = response.body().asInputStream()) { bytes = in.readNBytes(262145); }
      require(bytes.length <= 262144);
      boolean entity = type instanceof ParameterizedType p && p.getRawType() == ResponseEntity.class;
      Type bodyType = entity ? ((ParameterizedType) type).getActualTypeArguments()[0] : type;
      Object body = read(bytes, bodyType);
      if (!entity) return body;
      var headers = new org.springframework.http.HttpHeaders();
      response.headers().forEach((key, value) -> headers.put(key, new ArrayList<>(value)));
      return new ResponseEntity<>(body, headers, org.springframework.http.HttpStatus.OK);
    } catch (RuntimeException e) { throw AppointmentError.unavailable(); }
  }
  public static <T> T read(byte[] bytes, Class<T> type) { return type.cast(read(bytes, (Type) type)); }
  private static Object read(byte[] bytes, Type type) {
    var node = JSON.readTree(bytes);
    require(node != null && node.isObject());
    validate(node, type, Map.of());
    return JSON.readValue(bytes, JSON.constructType(type));
  }
  private static void validate(JsonNode node, Type type, Map<TypeVariable<?>, Type> variables) {
    if (type instanceof TypeVariable<?> v) { validate(node, variables.get(v), variables); return; }
    if (node.isNull()) return;
    if (type instanceof ParameterizedType p) {
      Class<?> raw = (Class<?>) p.getRawType();
      if (raw == List.class) {
        require(node.isArray() && node.size() <= 1000);
        for (var item : node) { require(!item.isNull()); validate(item, p.getActualTypeArguments()[0], variables); }
        return;
      }
      var resolved = new HashMap<TypeVariable<?>, Type>(variables);
      for (int i = 0; i < raw.getTypeParameters().length; i++) resolved.put(raw.getTypeParameters()[i], p.getActualTypeArguments()[i]);
      validate(node, raw, resolved); return;
    }
    Class<?> raw = (Class<?>) type;
    if (raw == Object.class) return;
    if (raw == String.class) { require(node.isString()); return; }
    if (raw == Boolean.class) { require(node.isBoolean()); return; }
    if (raw == Long.class) { require(node.isIntegralNumber() && node.canConvertToLong()); return; }
    if (raw == Integer.class) { require(node.isIntegralNumber() && node.canConvertToInt()); return; }
    if (raw == BigDecimal.class) { require(node.isNumber()); return; }
    require(raw.isRecord() && node.isObject());
    if (raw == IntrospectionResponseDto.class && node.path("active").isBoolean() && !node.path("active").asBoolean()) {
      require(node.propertyNames().equals(Set.of("active"))); return;
    }
    Set<String> fields = new HashSet<>();
    for (var field : raw.getRecordComponents()) {
      fields.add(field.getName()); require(node.has(field.getName()));
      validate(node.path(field.getName()), field.getGenericType(), variables);
    }
    var flexible = raw.getAnnotation(JsonIgnoreProperties.class);
    require(flexible != null && flexible.ignoreUnknown() ? node.propertyNames().containsAll(fields) : node.propertyNames().equals(fields));
  }
  public static Encoder encoder() {
    return (body, type, template) -> {
      if (body instanceof String text) template.body(text.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
      else template.body(JSON.writeValueAsBytes(body), StandardCharsets.UTF_8);
    };
  }
}

