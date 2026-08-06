package com.alibaba.nacos.proto.generator;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.Collection;
import java.util.Map;

public class ProtoGenerator {

    private final ClassScanner scanner = new ClassScanner();
    private final FieldExtractor extractor = new FieldExtractor();
    private final TypeMapper typeMapper = new TypeMapper();
    private final ModuleClassifier classifier = new ModuleClassifier();
    final ProtoFileWriter writer = new ProtoFileWriter();

    public void generate(Path outputDir, Path lockFilePath, boolean dryRun) throws IOException {
        List<Class<?>> classes = scanner.scan();
        System.out.printf("Discovered %d Payload classes%n", classes.size());
        generateForClasses(classes, outputDir, lockFilePath, dryRun);
    }

    // Package-private seam: lets tests drive generation from fixture classes
    // without going through the ServiceLoader-based scanner.
    void generateForClasses(List<Class<?>> classes, Path outputDir, Path lockFilePath, boolean dryRun)
            throws IOException {
        FieldNumberManager numberManager = new FieldNumberManager(lockFilePath);
        classToMessageName.clear();
        genericMessageNames.clear();

        List<MessageDescriptor> allMessages = new ArrayList<>();
        Set<Class<?>> processedDomainObjects = new HashSet<>();
        // Track message names per module to detect collisions within the same proto package
        Map<String, Set<String>> messageNamesByModule = new HashMap<>();

        for (Class<?> clazz : classes) {
            MessageDescriptor msg = buildDescriptor(clazz, numberManager, messageNamesByModule);
            allMessages.add(msg);
            discoverDomainObjects(msg.fields(), allMessages, processedDomainObjects, numberManager,
                    messageNamesByModule, 0);
        }

        Map<String, List<MessageDescriptor>> byFile = new LinkedHashMap<>();
        for (MessageDescriptor msg : allMessages) {
            byFile.computeIfAbsent(msg.fileName(), k -> new ArrayList<>()).add(msg);
        }

        if (dryRun) {
            System.out.println("=== Dry-run Report ===");
            for (var entry : byFile.entrySet()) {
                Path existing = outputDir.resolve(entry.getKey());
                if (!Files.exists(existing)) {
                    System.out.printf("[NEW]     %s (+%d messages)%n", entry.getKey(), entry.getValue().size());
                } else {
                    System.out.printf("[CHECK]   %s (%d messages)%n", entry.getKey(), entry.getValue().size());
                }
            }
        } else {
            writer.setClassToMessageName(classToMessageName);
            writer.setGenericMessageNames(genericMessageNames);
            writer.write(outputDir, byFile);
            numberManager.save();
            System.out.printf("Generated %d messages in %d files%n", allMessages.size(), byFile.size());
        }
    }

    // Maps Java class to proto message name for consistent references
    private final Map<Class<?>, String> classToMessageName = new HashMap<>();

    // Maps generic instantiations (e.g. Page<McpTool>) to their monomorphized message name
    private final Map<Type, String> genericMessageNames = new HashMap<>();

    public Map<Class<?>, String> getClassToMessageName() {
        return Collections.unmodifiableMap(classToMessageName);
    }

    private MessageDescriptor buildDescriptor(Class<?> clazz, FieldNumberManager numberManager,
            Map<String, Set<String>> messageNamesByModule) {
        List<FieldInfo> fields = extractor.extract(clazz);
        String module = classifier.classify(clazz);
        String fileName = classifier.getFileName(clazz, module);

        // Inner classes always use EnclosingClass + SimpleName to avoid cross-class collisions
        String messageName = clazz.getEnclosingClass() != null
            ? clazz.getEnclosingClass().getSimpleName() + clazz.getSimpleName()
            : clazz.getSimpleName();
        Set<String> moduleNames = messageNamesByModule.computeIfAbsent(module, k -> new HashSet<>());
        // If name still collides within the same module, disambiguate with sub-package
        if (moduleNames.contains(messageName)) {
            String pkg = clazz.getPackage().getName();
            String[] parts = pkg.split("\\.");
            String subPkg = parts[parts.length - 1];
            messageName = capitalize(subPkg) + messageName;
        }
        moduleNames.add(messageName);
        classToMessageName.put(clazz, messageName);

        List<String> fieldNames = fields.stream().map(FieldInfo::name).toList();
        Map<String, Integer> numbers = numberManager.assignNumbers(messageName, fieldNames);
        List<Integer> reserved = numberManager.getReserved(messageName);
        String chain = scanner.getInheritanceChain(clazz);
        return new MessageDescriptor(messageName, module, module + "/" + fileName, chain, fields, numbers, reserved);
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static final int MAX_DEPTH = 5;

    private void discoverDomainObjects(List<FieldInfo> fields, List<MessageDescriptor> allMessages,
            Set<Class<?>> processed, FieldNumberManager numberManager,
            Map<String, Set<String>> messageNamesByModule, int depth) {
        if (depth >= MAX_DEPTH) return;
        for (FieldInfo field : fields) {
            if (isGenericDomainField(field)) {
                monomorphize((ParameterizedType) field.genericType(), allMessages, processed,
                        numberManager, messageNamesByModule, depth);
                continue;
            }
            List<Class<?>> candidates = extractDomainTypes(field);
            for (Class<?> candidate : candidates) {
                if (candidate.isPrimitive() || candidate.getName().startsWith("java.") || candidate.isEnum()) continue;
                if (processed.contains(candidate)) continue;
                processed.add(candidate);

                MessageDescriptor domainMsg = buildDescriptor(candidate, numberManager, messageNamesByModule);
                allMessages.add(domainMsg);
                discoverDomainObjects(domainMsg.fields(), allMessages, processed, numberManager,
                        messageNamesByModule, depth + 1);
            }
        }
    }

    // A field like Page<AgentCatalogEntry>: parameterized domain class that is neither
    // a Collection nor a Map, so it cannot map to repeated/map and needs its own message.
    private boolean isGenericDomainField(FieldInfo field) {
        return field.genericType() instanceof ParameterizedType pt
            && pt.getRawType() instanceof Class<?> raw
            && !Collection.class.isAssignableFrom(raw)
            && !Map.class.isAssignableFrom(raw)
            && !raw.getName().startsWith("java.");
    }

    // Generate a concrete message per generic instantiation (monomorphization),
    // e.g. Page<AgentCatalogEntry> -> message AgentCatalogEntryPage.
    private void monomorphize(ParameterizedType pt, List<MessageDescriptor> allMessages,
            Set<Class<?>> processed, FieldNumberManager numberManager,
            Map<String, Set<String>> messageNamesByModule, int depth) {
        if (genericMessageNames.containsKey(pt)) return;

        Class<?> raw = (Class<?>) pt.getRawType();
        Type[] args = pt.getActualTypeArguments();
        List<Class<?>> argClasses = new ArrayList<>();
        for (Type arg : args) {
            if (!(arg instanceof Class<?> cls)) {
                throw new IllegalStateException("Unsupported generic instantiation " + pt.getTypeName()
                    + ": only concrete class type arguments are supported");
            }
            argClasses.add(cls);
        }

        StringBuilder nameBuilder = new StringBuilder();
        for (Class<?> argClass : argClasses) {
            nameBuilder.append(capitalize(argClass.getSimpleName()));
        }
        String messageName = nameBuilder.append(raw.getSimpleName()).toString();

        // Place the message in the module of the first domain-class type argument so that
        // references (both to and from this message) stay within one proto package.
        Class<?> moduleAnchor = argClasses.stream()
            .filter(cls -> !cls.getName().startsWith("java."))
            .findFirst().orElse(raw);
        String module = classifier.classify(moduleAnchor);

        Set<String> moduleNames = messageNamesByModule.computeIfAbsent(module, k -> new HashSet<>());
        if (moduleNames.contains(messageName)) {
            throw new IllegalStateException("Message name collision for generic instantiation "
                + pt.getTypeName() + " -> " + messageName + " in module " + module);
        }
        moduleNames.add(messageName);
        genericMessageNames.put(pt, messageName);

        Map<TypeVariable<?>, Type> substitution = new HashMap<>();
        TypeVariable<?>[] typeVars = raw.getTypeParameters();
        for (int i = 0; i < typeVars.length && i < args.length; i++) {
            substitution.put(typeVars[i], args[i]);
        }

        List<FieldInfo> fields = new ArrayList<>();
        for (FieldInfo field : extractor.extract(raw)) {
            Type resolved = substitute(field.genericType(), substitution);
            Class<?> resolvedClass = resolved instanceof Class<?> cls ? cls : field.type();
            fields.add(new FieldInfo(field.name(), resolvedClass, resolved, field.jsonName()));
        }

        String fileName = switch (module) {
            case "common" -> "common.proto";
            case "lock" -> "lock.proto";
            default -> messageName.toLowerCase() + ".proto";
        };
        List<String> fieldNames = fields.stream().map(FieldInfo::name).toList();
        Map<String, Integer> numbers = numberManager.assignNumbers(messageName, fieldNames);
        List<Integer> reserved = numberManager.getReserved(messageName);
        String chain = raw.getSimpleName() + "<"
            + argClasses.stream().map(Class::getSimpleName).collect(java.util.stream.Collectors.joining(", "))
            + ">";
        allMessages.add(new MessageDescriptor(messageName, module, module + "/" + fileName,
                chain, fields, numbers, reserved));

        discoverDomainObjects(fields, allMessages, processed, numberManager, messageNamesByModule, depth + 1);
    }

    private static Type substitute(Type type, Map<TypeVariable<?>, Type> substitution) {
        if (type instanceof TypeVariable<?> typeVar) {
            return substitution.getOrDefault(typeVar, typeVar);
        }
        if (type instanceof ParameterizedType pt) {
            Type[] newArgs = Arrays.stream(pt.getActualTypeArguments())
                .map(arg -> substitute(arg, substitution))
                .toArray(Type[]::new);
            return new SyntheticParameterizedType(pt.getRawType(), pt.getOwnerType(), newArgs);
        }
        return type;
    }

    private record SyntheticParameterizedType(Type rawType, Type ownerType, Type[] typeArgs)
            implements ParameterizedType {
        @Override
        public Type[] getActualTypeArguments() {
            return typeArgs.clone();
        }

        @Override
        public Type getRawType() {
            return rawType;
        }

        @Override
        public Type getOwnerType() {
            return ownerType;
        }
    }

    private List<Class<?>> extractDomainTypes(FieldInfo field) {
        List<Class<?>> types = new ArrayList<>();
        Class<?> fieldType = field.type();

        if (Collection.class.isAssignableFrom(fieldType) && field.genericType() instanceof ParameterizedType pt) {
            Type elemType = pt.getActualTypeArguments()[0];
            if (elemType instanceof Class<?> cls) {
                types.add(cls);
            }
        } else if (Map.class.isAssignableFrom(fieldType) && field.genericType() instanceof ParameterizedType pt) {
            for (Type arg : pt.getActualTypeArguments()) {
                if (arg instanceof Class<?> cls) {
                    types.add(cls);
                }
            }
        } else {
            types.add(fieldType);
        }
        return types;
    }

    public static void main(String[] args) throws Exception {
        Path outputDir = null;
        Path lockFile = null;
        boolean dryRun = false;
        boolean verify = false;
        String goModuleBase = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--output" -> outputDir = Path.of(args[++i]);
                case "--lockfile" -> lockFile = Path.of(args[++i]);
                case "--dry-run" -> dryRun = true;
                case "--verify" -> verify = true;
                case "--go-module-base" -> goModuleBase = args[++i];
            }
        }

        if (outputDir == null || lockFile == null || goModuleBase == null) {
            System.err.println("Usage: --output <dir> --lockfile <file> --go-module-base <base> [--dry-run] [--verify]");
            System.exit(1);
        }

        ProtoGenerator generator = new ProtoGenerator();
        generator.writer.setGoModuleBase(goModuleBase);
        generator.generate(outputDir, lockFile, dryRun);

        if (verify) {
            System.out.println("Verification mode: checking proto compilation...");
        }
    }
}
