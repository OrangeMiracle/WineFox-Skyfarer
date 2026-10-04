package net.neoforged.neoforge.common;
public class ModConfigSpec {
 public boolean isLoaded(){return true;} public void save(){}
 public static class DoubleValue {double v;DoubleValue(double v){this.v=v;}public double get(){return v;}public void set(double v){this.v=v;}}
 public static class BooleanValue {boolean v;BooleanValue(boolean v){this.v=v;}public boolean get(){return v;}public void set(boolean v){this.v=v;}}
 public static class Builder {
  public Builder push(String s){return this;} public Builder pop(){return this;} public Builder comment(String s){return this;}
  public DoubleValue defineInRange(String s,double n,double a,double b){return new DoubleValue(n);}public BooleanValue define(String s,boolean v){return new BooleanValue(v);}
  public ModConfigSpec build(){return new ModConfigSpec();}
 }
}
