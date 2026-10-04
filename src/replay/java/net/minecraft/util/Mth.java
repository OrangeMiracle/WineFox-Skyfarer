package net.minecraft.util;
public class Mth {
 public static double clamp(double v,double a,double b){return Math.clamp(v,a,b);}
 public static float clamp(float v,float a,float b){return Math.clamp(v,a,b);}
 public static float approachDegrees(float a,float b,float rate){return (float)dev.winefox.skyfarer.flight.FlightPrediction.turn(a,b,rate);}
}
