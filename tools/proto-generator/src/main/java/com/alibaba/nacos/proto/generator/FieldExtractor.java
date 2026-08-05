package com.alibaba.nacos.proto.generator;

import com.alibaba.nacos.api.remote.request.InternalRequest;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.request.ServerRequest;
import com.alibaba.nacos.api.remote.response.Response;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

public class FieldExtractor {

    private static final ObjectMapper JACKSON = new ObjectMapper();

    private static final Set<Class<?>> STOP_CLASSES = Set.of(
        Object.class, Request.class, Response.class,
        InternalRequest.class, ServerRequest.class
    );

    private static final List<String> REQUEST_BASE_FIELDS = List.of("requestId");
    private static final List<String> RESPONSE_BASE_FIELDS = List.of(
        "resultCode", "errorCode", "message", "requestId"
    );

    public List<FieldInfo> extract(Class<?> clazz) {
        List<FieldInfo> allFields = new ArrayList<>();

        JsonNameIndex jsonNames = introspectJsonNames(clazz);

        Set<String> seen = new HashSet<>();
        addBaseFields(clazz, allFields, seen);

        List<Class<?>> hierarchy = getHierarchy(clazz);

        // Build a map from field name to the most-derived class that declares it
        // (child class overrides parent if same name exists)
        Map<String, Class<?>> fieldOwnerClass = new LinkedHashMap<>();
        for (int i = hierarchy.size() - 1; i >= 0; i--) {
            for (Field field : hierarchy.get(i).getDeclaredFields()) {
                fieldOwnerClass.put(field.getName(), hierarchy.get(i));
            }
        }

        for (Class<?> level : hierarchy) {
            for (Field field : level.getDeclaredFields()) {
                if (!shouldExclude(field)
                    && fieldOwnerClass.get(field.getName()) == level
                    && seen.add(field.getName())) {
                    allFields.add(new FieldInfo(field.getName(), field.getType(), field.getGenericType(),
                        jsonNames.resolve(field)));
                }
            }
        }
        return allFields;
    }

    private void addBaseFields(Class<?> clazz, List<FieldInfo> fields, Set<String> seen) {
        List<String> baseFieldNames;
        Class<?> baseClass;
        if (Response.class.isAssignableFrom(clazz)) {
            baseFieldNames = RESPONSE_BASE_FIELDS;
            baseClass = Response.class;
        } else if (Request.class.isAssignableFrom(clazz)) {
            baseFieldNames = REQUEST_BASE_FIELDS;
            baseClass = Request.class;
        } else {
            // Domain objects (not Request/Response subclasses) have no base fields
            return;
        }

        for (String name : baseFieldNames) {
            try {
                Field f = baseClass.getDeclaredField(name);
                if (seen.add(name)) {
                    fields.add(new FieldInfo(f.getName(), f.getType(), f.getGenericType()));
                }
            } catch (NoSuchFieldException ignored) {
            }
        }
    }

    private List<Class<?>> getHierarchy(Class<?> clazz) {
        List<Class<?>> chain = new ArrayList<>();
        Class<?> current = clazz;
        while (current != null && !STOP_CLASSES.contains(current)) {
            chain.add(current);
            current = current.getSuperclass();
        }
        Collections.reverse(chain);
        return chain;
    }

    private boolean shouldExclude(Field field) {
        int mod = field.getModifiers();
        if (Modifier.isStatic(mod) || Modifier.isTransient(mod)) return true;
        if (field.isAnnotationPresent(JsonIgnore.class)) return true;
        if (field.getName().equals("headers")) return true;
        if (field.getName().startsWith("_")) return true;
        return false;
    }

    /**
     * Jackson wire names for a class, resolved via jackson-databind introspection so the
     * proto json_name matches the server's Jackson serialization byte-for-byte.
     */
    private JsonNameIndex introspectJsonNames(Class<?> clazz) {
        BeanDescription desc = JACKSON.getSerializationConfig()
            .introspect(JACKSON.constructType(clazz));
        Set<String> propertyNames = new HashSet<>();
        Map<String, String> byFieldName = new HashMap<>();
        for (BeanPropertyDefinition p : desc.findProperties()) {
            propertyNames.add(p.getName());
            if (p.getField() != null) {
                byFieldName.put(p.getField().getName(), p.getName());
            }
        }
        return new JsonNameIndex(propertyNames, byFieldName);
    }

    private record JsonNameIndex(Set<String> propertyNames, Map<String, String> byFieldName) {

        String resolve(Field field) {
            String linked = byFieldName.get(field.getName());
            if (linked != null) {
                return linked;
            }
            // Jackson treats `boolean isXxx` + is-getter as two implicit names: the field
            // candidate ("isXxx", dropped as non-visible) and the getter property ("xxx",
            // which survives with no linked field). Match the surviving property here.
            if (field.getType() == boolean.class || field.getType() == Boolean.class) {
                String n = field.getName();
                if (n.length() > 2 && n.startsWith("is") && Character.isUpperCase(n.charAt(2))) {
                    String stripped = Character.toLowerCase(n.charAt(2)) + n.substring(3);
                    if (propertyNames.contains(stripped) && !propertyNames.contains(n)) {
                        return stripped;
                    }
                }
            }
            return field.getName();
        }
    }
}
