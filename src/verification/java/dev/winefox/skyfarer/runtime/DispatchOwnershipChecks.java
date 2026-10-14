package dev.winefox.skyfarer.runtime;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.util.*;

/** Execute the compiled admission branch with symbolic external receivers. No copied policy. */
final class DispatchOwnershipChecks {
    static void verify(ClassNode manager){
        var assign=manager.methods.stream().filter(m->m.name.equals("assign")&&Type.getReturnType(m.desc).getSort()==Type.OBJECT).findFirst().orElseThrow();
        AbstractInsnNode start=null;
        for(var i:assign.instructions)if(i instanceof FieldInsnNode f&&f.owner.endsWith("$Order")&&f.name.equals("transit"))start=i.getNext();
        if(start==null)throw new AssertionError("missing assignment control handoff");
        int controller=-1;
        for(var i=start;i!=null;i=i.getNext())if(i instanceof VarInsnNode v){controller=v.var;break;}
        for(boolean transit:new boolean[]{false,true})for(boolean existing:new boolean[]{false,true}){
            var stack=new ArrayList<Object>();stack.add(transit?1:0);var locals=new HashMap<Integer,Object>();
            locals.put(controller,existing?new Object():null);int accept=0,handoff=0,ensure=0,steps=0;
            for(var at=start;at!=null&&++steps<100;at=at.getNext()){
                int op=at.getOpcode();if(op<0)continue;
                if(at instanceof VarInsnNode v){if(op==Opcodes.ALOAD)stack.add(locals.containsKey(v.var)?locals.get(v.var):new Object());else if(op==Opcodes.ASTORE)locals.put(v.var,pop(stack));else throw new AssertionError("unexpected local opcode "+op);}
                else if(at instanceof JumpInsnNode j){
                    boolean jump=switch(op){case Opcodes.GOTO->true;case Opcodes.IFNULL->pop(stack)==null;case Opcodes.IFNONNULL->pop(stack)!=null;case Opcodes.IFEQ->((Integer)pop(stack))==0;case Opcodes.IFNE->((Integer)pop(stack))!=0;default->throw new AssertionError("unexpected branch "+op);};
                    if(jump)at=j.label;
                }else if(at instanceof FieldInsnNode f){pop(stack);stack.add(new Object());}
                else if(at instanceof TypeInsnNode t){if(op==Opcodes.INSTANCEOF){pop(stack);stack.add(1);}else if(op!=Opcodes.CHECKCAST)throw new AssertionError("unexpected type opcode");}
                else if(at instanceof MethodInsnNode m){
                    for(var arg:Type.getArgumentTypes(m.desc))pop(stack);if(op!=Opcodes.INVOKESTATIC)pop(stack);
                    if(m.name.equals("acceptCommand"))accept++;if(m.name.equals("handoffToTransit"))handoff++;if(m.name.equals("ensure"))ensure++;
                    if(Type.getReturnType(m.desc).getSort()!=Type.VOID)stack.add(new Object());
                }else if(op==Opcodes.ACONST_NULL)stack.add(null);
                else if(op==Opcodes.ARETURN)break;
                else throw new AssertionError("re-audit changed handoff bytecode opcode="+op);
            }
            if(steps>=100||accept!=(transit?0:1)||handoff!=(transit&&existing?1:0)||ensure!=(transit?0:1))
                throw new AssertionError("admission owns wrong controller transit="+transit+" existing="+existing+" accept="+accept+" handoff="+handoff+" ensure="+ensure);
        }
        System.out.println("Actual compiled dispatch branch: transit never constructs/accepts combat, old control closes once, local combat still accepts (four cases) passed");
    }
    static Object pop(ArrayList<Object> stack){return stack.removeLast();}
}
