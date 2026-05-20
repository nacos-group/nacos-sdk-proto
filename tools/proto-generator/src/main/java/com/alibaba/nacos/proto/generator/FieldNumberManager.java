package com.alibaba.nacos.proto.generator;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class FieldNumberManager {

    private final Path lockFilePath;
    private final Map<String, MessageNumbers> data;

    public record MessageNumbers(Map<String, Integer> fields, List<Integer> reserved) {
        public MessageNumbers {
            fields = new LinkedHashMap<>(fields);
            reserved = new ArrayList<>(reserved);
        }
    }

    public FieldNumberManager(Path lockFilePath) throws IOException {
        this.lockFilePath = lockFilePath;
        if (Files.exists(lockFilePath)) {
            ObjectMapper om = new ObjectMapper();
            var wrapper = om.readValue(lockFilePath.toFile(),
                new TypeReference<Map<String, Object>>() {});
            @SuppressWarnings("unchecked")
            var messages = (Map<String, Map<String, Object>>) wrapper.get("messages");
            this.data = new LinkedHashMap<>();
            if (messages != null) {
                for (var entry : messages.entrySet()) {
                    @SuppressWarnings("unchecked")
                    var fields = (Map<String, Integer>) entry.getValue().get("fields");
                    @SuppressWarnings("unchecked")
                    var reserved = (List<Integer>) entry.getValue().getOrDefault("reserved", List.of());
                    this.data.put(entry.getKey(), new MessageNumbers(
                        fields != null ? fields : Map.of(),
                        reserved != null ? reserved : List.of()
                    ));
                }
            }
        } else {
            this.data = new LinkedHashMap<>();
        }
    }

    public Map<String, Integer> assignNumbers(String messageName, List<String> fieldNames) {
        MessageNumbers existing = data.get(messageName);
        Map<String, Integer> result = new LinkedHashMap<>();
        List<Integer> reserved = new ArrayList<>();
        int maxNumber = 0;

        if (existing != null) {
            for (var entry : existing.fields().entrySet()) {
                if (fieldNames.contains(entry.getKey())) {
                    result.put(entry.getKey(), entry.getValue());
                } else {
                    reserved.add(entry.getValue());
                }
                maxNumber = Math.max(maxNumber, entry.getValue());
            }
            reserved.addAll(existing.reserved());
            for (int r : reserved) {
                maxNumber = Math.max(maxNumber, r);
            }
        }

        for (String name : fieldNames) {
            if (!result.containsKey(name)) {
                maxNumber++;
                result.put(name, maxNumber);
            }
        }

        data.put(messageName, new MessageNumbers(result, reserved));
        return result;
    }

    public List<Integer> getReserved(String messageName) {
        MessageNumbers mn = data.get(messageName);
        return mn != null ? mn.reserved() : List.of();
    }

    public void save() throws IOException {
        Map<String, Object> wrapper = new LinkedHashMap<>();
        wrapper.put("version", 1);
        wrapper.put("messages", data);
        ObjectMapper om = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        if (lockFilePath.getParent() != null) {
            Files.createDirectories(lockFilePath.getParent());
        }
        om.writeValue(lockFilePath.toFile(), wrapper);
    }
}
