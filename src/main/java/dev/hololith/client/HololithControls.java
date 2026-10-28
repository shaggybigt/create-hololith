package dev.hololith.client;

import dev.hololith.*;
import dev.hololith.compat.ContraptionPackets;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** A physical table opens this small instrument panel; no commands or computer required. */
public final class HololithControls extends Screen {
    private final HololithTableEntity table;
    private final int contraptionEntityId;
    private final UUID contraptionEntityUuid;
    private int span,rotation,zoom,relief,mode,left,top;
    private boolean intensityChanged;
    private HololithControls(HololithTableEntity table,int contraptionEntityId,UUID contraptionEntityUuid){
        super(Component.literal("HOLOLITH · SURVEY INSTRUMENT"));this.table=table;span=table.span();rotation=table.rotation();zoom=table.zoom();relief=(int)table.verticalScale();mode=table.mode();
        this.contraptionEntityId=contraptionEntityId;this.contraptionEntityUuid=contraptionEntityUuid;
    }
    public static void open(BlockPos pos){var mc=Minecraft.getInstance();if(mc.level!=null&&mc.level.getBlockEntity(pos) instanceof HololithTableEntity table)mc.setScreen(new HololithControls(table,-1,null));}
    public static void open(HololithTableEntity table,int contraptionEntityId){
        var mc=Minecraft.getInstance();
        if(mc.level!=null&&mc.level.getEntity(contraptionEntityId) instanceof com.simibubi.create.content.contraptions.AbstractContraptionEntity entity)
            mc.setScreen(new HololithControls(table,contraptionEntityId,entity.getUUID()));
    }
    private Button button(int x,int y,int width,String label,Button.OnPress press){return addRenderableWidget(Button.builder(Component.literal(label),press).bounds(left+x,top+y,width,20).build());}
    @Override protected void init(){
        left=(width-328)/2;top=(height-260)/2;
        button(14,42,96,"Area: "+span+" × "+span,b->{span=span==32?64:span==64?128:32;b.setMessage(Component.literal("Area: "+span+" × "+span));});
        button(116,42,96,"Rotation: "+rotation*90+"°",b->{rotation=(rotation+1)%4;b.setMessage(Component.literal("Rotation: "+rotation*90+"°"));});
        button(218,42,96,"Relief: "+relief+"×",b->{relief=relief==1?2:1;b.setMessage(Component.literal("Relief: "+relief+"×"));});
        button(14,68,146,"Projection: "+(100+zoom*25)+"%",b->{zoom=(zoom+1)%3;b.setMessage(Component.literal("Projection: "+(100+zoom*25)+"%"));});
        var source=button(168,68,146,modeLabel(),b->{mode=mode==0?1:0;b.setMessage(Component.literal(modeLabel()));});
        source.active=table.sensorId()!=null;
        source.setTooltip(Tooltip.create(Component.translatable("gui.hololith.source.tooltip")));
        addRenderableWidget(new AbstractSliderButton(left+14,top+162,300,20,Component.empty(),(HololithClientConfig.intensity()-.4)/1.4){
            {updateMessage();setTooltip(Tooltip.create(Component.translatable("gui.hololith.intensity.tooltip")));}
            @Override protected void updateMessage(){setMessage(Component.translatable("gui.hololith.intensity",Math.round(HololithClientConfig.intensity()*100)));}
            @Override protected void applyValue(){HololithClientConfig.previewIntensity(.4+value*1.4);intensityChanged=true;}
        });
        var shimmer=button(14,188,300,"",b->{
            HololithClientConfig.HOLOGRAM_FLICKER.set(!HololithClientConfig.HOLOGRAM_FLICKER.get());
            b.setMessage(flickerLabel());intensityChanged=true;
        });
        shimmer.setMessage(flickerLabel());shimmer.setTooltip(Tooltip.create(Component.translatable("gui.hololith.flicker.tooltip")));
        button(14,226,146,"Apply survey settings",b->apply());button(168,226,146,"Close",b->onClose());
    }
    private String modeLabel(){return mode==1?"Source: linked sensor":"Source: table";}
    private static Component flickerLabel(){return Component.translatable("gui.hololith.flicker",Component.translatable(HololithClientConfig.HOLOGRAM_FLICKER.get()?"options.on":"options.off"));}
    private void apply(){
        var packet=new HololithNetwork.Configure(table.getBlockPos(),span,rotation,zoom,relief,mode,
            table.getLevel().dimension().location().toString(),0,0);
        if(!HololithNetwork.valid(packet))return;
        if(contraptionEntityId>=0)ContraptionPackets.configure(contraptionEntityId,contraptionEntityUuid,packet);
        else PacketDistributor.sendToServer(packet);
        onClose();
    }
    @Override public void renderBackground(GuiGraphics g,int mouseX,int mouseY,float partialTick){
        // Screen.render draws the background before widgets. Blur the world first,
        // then draw labels so the status and panel title stay as sharp as buttons.
        super.renderBackground(g,mouseX,mouseY,partialTick);
        g.fill(left-2,top-2,left+330,top+262,0xffb79958);g.fill(left,top,left+328,top+260,0xff222e32);g.fill(left+8,top+8,left+320,top+32,0xff35464a);
        g.drawCenteredString(font,title,left+164,top+16,0xffe3c78b);
        g.drawWordWrap(font,Component.translatable(table.powerStatusKey()),left+14,top+96,300,table.isProjecting()?0xff73e7dc:0xffffce80);
        g.drawString(font,Component.translatable("gui.hololith.power_cost",dev.hololith.power.ProjectionPower.impact(span,zoom),table.requiredRpm()),left+14,top+119,0xffd8caab);
        g.drawWordWrap(font,Component.translatable("gui.hololith.survey_status",table.status()),left+14,top+135,300,0xffaac9c7);
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void removed(){
        if(intensityChanged){HololithClientConfig.save();intensityChanged=false;}
        super.removed();
    }
}
