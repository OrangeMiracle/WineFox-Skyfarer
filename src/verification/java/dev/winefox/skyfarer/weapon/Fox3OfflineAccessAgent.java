package dev.winefox.skyfarer.weapon;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.nio.charset.StandardCharsets;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarFile;
import org.objectweb.asm.*;

/** Test JVM only. Replays the installed TLM jar's declared access-transformer rules, which the
 * normal game loader applies before EntityMaid is loaded. Neither dependency jars nor production
 * classes are rewritten on disk; no behavior, method body or targeting policy is changed. */
public final class Fox3OfflineAccessAgent {
    private record Rule(String owner, String member, boolean removeFinal) {}
    private static final Set<String> APPLIED = ConcurrentHashMap.newKeySet();
    public static boolean applied(String owner, String member) { return APPLIED.contains(owner + "#" + member); }

    public static void premain(String argument, Instrumentation instrumentation) throws Exception {
        if (argument == null || argument.isBlank()) throw new IllegalArgumentException("TLM dependency jar path required");
        var rules = new LinkedHashMap<String,List<Rule>>();
        try (var jar = new JarFile(argument)) {
            var entry = jar.getJarEntry("META-INF/accesstransformer.cfg");
            if (entry == null) throw new IllegalStateException("Installed TLM has no access-transformer declarations");
            String content;
            try (var stream = jar.getInputStream(entry)) { content = new String(stream.readAllBytes(), StandardCharsets.UTF_8); }
            for (String raw : content.split("\\R")) {
                String line = raw.split("#", 2)[0].strip(); if (line.isEmpty()) continue;
                String[] words = line.split("\\s+");
                if (words.length < 2 || words.length > 3 || !Set.of("public", "public-f").contains(words[0]))
                    throw new IllegalStateException("Re-audit unsupported TLM access rule: " + line);
                var rule = new Rule(words[1].replace('.', '/'), words.length == 3 ? words[2] : null, words[0].equals("public-f"));
                rules.computeIfAbsent(rule.owner, owner -> new ArrayList<>()).add(rule);
            }
        }
        instrumentation.addTransformer(new ClassFileTransformer() {
            @Override public byte[] transform(ClassLoader loader, String name, Class<?> redefined,
                                               ProtectionDomain protection, byte[] bytes) {
                List<Rule> local = rules.get(name);
                boolean hasNestedRule = rules.keySet().stream().anyMatch(owner -> owner.startsWith(name + "$"));
                if (local == null && !hasNestedRule) return null;
                var reader = new ClassReader(bytes); var writer = new ClassWriter(reader, 0);
                reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
                    private int flags(int access, String member) {
                        if (local == null) return access;
                        for (var rule : local) if (Objects.equals(rule.member, member)) {
                            access = publicFlags(access, rule.removeFinal); APPLIED.add(name + "#" + member);
                        }
                        return access;
                    }
                    @Override public void visit(int version, int access, String className, String signature, String superclass, String[] interfaces) {
                        super.visit(version, flags(access, null), className, signature, superclass, interfaces);
                    }
                    @Override public FieldVisitor visitField(int access, String field, String descriptor, String signature, Object value) {
                        return super.visitField(flags(access, field), field, descriptor, signature, value);
                    }
                    @Override public MethodVisitor visitMethod(int access, String method, String descriptor, String signature, String[] exceptions) {
                        return super.visitMethod(flags(access, method + descriptor), method, descriptor, signature, exceptions);
                    }
                    @Override public void visitInnerClass(String nestedName, String outer, String inner, int access) {
                        var nested = rules.get(nestedName);
                        if (nested != null) for (var rule : nested) if (rule.member == null) access = publicFlags(access, rule.removeFinal);
                        super.visitInnerClass(nestedName, outer, inner, access);
                    }
                }, 0);
                return writer.toByteArray();
            }
        }, false);
        System.out.println("Fox3 offline fixture: replaying " + rules.values().stream().mapToInt(List::size).sum() + " declared TLM access rules in this test JVM only.");
    }
    private static int publicFlags(int access, boolean removeFinal) {
        return (access & ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED | (removeFinal ? Opcodes.ACC_FINAL : 0))) | Opcodes.ACC_PUBLIC;
    }
}
