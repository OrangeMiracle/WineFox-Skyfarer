package dev.winefox.skyfarer.flight;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.AnnotationNode;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarFile;

/** Checks the optional hooks against actual 2.6.5 bytecode, without loading YSM or a client. */
public final class YsmCompatibilityChecks {
    private static void require(boolean value,String reason) { if(!value) throw new AssertionError(reason); }
    private static ClassNode read(byte[] data) {
        var node=new ClassNode(); new ClassReader(data).accept(node,ClassReader.SKIP_DEBUG); return node;
    }
    private static Object value(AnnotationNode annotation,String key) {
        for(int i=0;i<annotation.values.size();i+=2) if(annotation.values.get(i).equals(key)) return annotation.values.get(i+1);
        return null;
    }
    public static void main(String[] args) throws Exception {
        int hooks=0;
        try(var jar=new JarFile("tools/ysm-reference.jar")) {
            for(String name:new String[]{"YsmMaidAnimationMixin","YsmMaidAnimationControllerMixin","YsmMaidRendererMixin","YsmElytraQueriesMixin"}) {
                var mixin=read(java.nio.file.Files.readAllBytes(Path.of("build/classes/java/main/dev/winefox/skyfarer/mixin/ysm",name+".class")));
                var annotation=mixin.invisibleAnnotations.stream().filter(a->a.desc.endsWith("/Mixin;")).findFirst().orElseThrow();
                String target=((List<String>)value(annotation,"targets")).getFirst().replace('.','/');
                var entry=jar.getJarEntry(target+".class"); require(entry!=null,"optional target missing: "+target);
                var node=read(jar.getInputStream(entry).readAllBytes());
                for(var method:mixin.methods) {
                    if(method.visibleAnnotations==null) continue;
                    for(var injection:method.visibleAnnotations) if(injection.desc.endsWith("/Inject;") || injection.desc.endsWith("/WrapOperation;")) {
                        for(String selector:(List<String>)value(injection,"method")) {
                            require(node.methods.stream().filter(m->selector.equals(m.name+m.desc) || selector.equals(m.name)).count()==1,
                                "ambiguous or missing exact hook: "+target+" "+selector);
                            hooks++;
                            if(injection.desc.endsWith("/WrapOperation;")) {
                                var at=((List<AnnotationNode>)value(injection,"at")).getFirst();
                                String invoke=(String)value(at,"target");
                                require(node.methods.stream().filter(m->selector.equals(m.name+m.desc))
                                    .flatMap(m->java.util.stream.StreamSupport.stream(m.instructions.spliterator(),false))
                                    .filter(i->i instanceof org.objectweb.asm.tree.MethodInsnNode)
                                    .map(i->(org.objectweb.asm.tree.MethodInsnNode)i)
                                    .filter(i->invoke.equals("L"+i.owner+";"+i.name+i.desc)).count()==1,"custom controller invocation must match exactly");
                            }
                        }
                    }
                }
            }
            var maidSelector=read(jar.getInputStream(jar.getJarEntry("com/elfmcys/yesstevemodel/OOOOOo0o0Oo00Ooo0OO0O0Oo.class")).readAllBytes());
            boolean nativeFly=maidSelector.methods.stream().flatMap(m->java.util.stream.StreamSupport.stream(m.instructions.spliterator(),false))
                .filter(i->i instanceof org.objectweb.asm.tree.LdcInsnNode).anyMatch(i->"elytra_fly".equals(((org.objectweb.asm.tree.LdcInsnNode)i).cst));
            require(!nativeFly,"reference changed: reevaluate upstream maid support");
            require(hooks==5,"all animation, custom controller, rotation, wings and query hooks must be inspected");
        }
        System.out.println(hooks+" exact optional YSM 2.6.5 hooks and upstream missing elytra state verified without loading a game.");
    }
}
