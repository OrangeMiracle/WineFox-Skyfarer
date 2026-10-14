package dev.winefox.skyfarer.compat.tlm;

import dev.winefox.skyfarer.flight.PropulsionDemand;

/** Native equipment/readiness and finite Riptide proof belong to this provider. */
final class RiptideProvider implements PropulsionResolver.Provider {
    public PropulsionDemand.Source source(){return PropulsionDemand.Source.RIPTIDE;}
    public void warm(PropulsionResolver.Context c){
        var resource=RiptideReadiness.of(c.maid());
        if(resource.strength()>0)c.planning().propulsion(c.start(),c.demand(),resource.strength(),c.maid().isInWaterOrBubble(),c.space());
    }
    public PropulsionResolver.Offer offer(PropulsionResolver.Context c){
        var resource=RiptideReadiness.of(c.maid());
        if(!c.maid().isInWaterOrRain()||resource.strength()<=0)return PropulsionResolver.Offer.rejected("ENVIRONMENT_OR_TRIDENT_UNAVAILABLE");
        var candidate=c.planning().propulsion(c.start(),c.demand(),resource.strength(),c.maid().isInWaterOrBubble(),c.space());
        boolean pending=c.planning().lastPending();
        if(c.motor().ready(c.maid())&&candidate!=null&&(c.demand().departure()?c.motor().beginLaunch(c.maid(),candidate,c.planning(),c.heading()):c.motor().begin(c.maid(),candidate,c.start(),c.planning(),c.heading())))
            return new PropulsionResolver.Offer(true,false,"RIPTIDE_ACCEPTED");
        String reason=!c.motor().ready(c.maid())?"PRECHARGE_"+resource.ticks():candidate==null?pending?"CERTIFICATE_PENDING":"NO_SAFE_RIPTIDE":c.motor().beginRejection();
        return new PropulsionResolver.Offer(false,pending||!c.motor().ready(c.maid())||reason.startsWith("SNAPSHOT_MISSING"),reason);
    }
}
