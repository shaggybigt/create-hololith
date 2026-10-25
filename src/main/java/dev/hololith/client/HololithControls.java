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
    private EditBox dimension,x,z;
    private String error="";
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
        left=(width-328)/2;top=(height-280)/2;
        button(14,42,96,"Area: "+span+" × "+span,b->{span=span==32?64:span==64?128:32;b.setMessage(Component.literal("Area: "+span+" × "+span));});
        button(116,42,96,"Rotation: "+rotation*90+"°",b->{rotation=(rotation+1)%4;b.setMessage(Component.literal("Rotation: "+rotation*90+"°"));});
        button(218,42,96,"Relief: "+relief+"×",b->{relief=relief==1?2:1;b.setMessage(Component.literal("Relief: "+relief+"×"));});
        button(14,68,146,"Projection: "+(100+zoom*25)+"%",b->{zoom=(zoom+1)%3;b.setMessage(Component.literal("Projection: "+(100+zoom*25)+"%"));});
        button(168,68,146,modeLabel(),b->{mode=(mode+1)%3;if(mode==1&&table.sensorId()==null)mode=2;b.setMessage(Component.literal(modeLabel()));coordinateState();});
        dimension=new EditBox(font,left+14,top+113,300,18,Component.literal("Dimension"));dimension.setMaxLength(128);dimension.setValue(table.targetDimension().isEmpty()?"minecraft:overworld":table.targetDimension());addRenderableWidget(dimension);
        x=new EditBox(font,left+30,top+142,126,18,Component.literal("X coordinate"));x.setMaxLength(10);x.setValue(Integer.toString(table.targetX()));addRenderableWidget(x);
        z=new EditBox(font,left+188,top+142,126,18,Component.literal("Z coordinate"));z.setMaxLength(10);z.setValue(Integer.toString(table.targetZ()));addRenderableWidget(z);coordinateState();
        addRenderableWidget(new AbstractSliderButton(left+14,top+204,300,20,Component.empty(),(HololithClientConfig.intensity()-.4)/1.4){
            {updateMessage();setTooltip(Tooltip.create(Component.translatable("gui.hololith.intensity.tooltip")));}
            @Override protected void updateMessage(){setMessage(Component.translatable("gui.hololith.intensity",Math.round(HololithClientConfig.intensity()*100)));}
            @Override protected void applyValue(){HololithClientConfig.previewIntensity(.4+value*1.4);intensityChanged=true;}
        });
        button(14,244,146,"Apply survey settings",b->apply());button(168,244,146,"Close",b->onClose());
    }
    private String modeLabel(){return switch(mode){case 1->"Source: linked sensor";case 2->"Source: coordinates";default->"Source: table";};}
    private void coordinateState(){if(dimension==null)return;dimension.setEditable(mode==2);x.setEditable(mode==2);z.setEditable(mode==2);}
    private void apply(){
        try {
            var packet=new HololithNetwork.Configure(table.getBlockPos(),span,rotation,zoom,relief,mode,dimension.getValue().trim(),Integer.parseInt(x.getValue()),Integer.parseInt(z.getValue()));
            if(!HololithNetwork.valid(packet)){error="Enter a valid dimension and world coordinates.";return;}
            if(contraptionEntityId>=0)ContraptionPackets.configure(contraptionEntityId,contraptionEntityUuid,packet);
            else PacketDistributor.sendToServer(packet);
            onClose();
        }catch(NumberFormatException e){error="X and Z must be whole numbers.";}
    }
    @Override public void renderBackground(GuiGraphics g,int mouseX,int mouseY,float partialTick){
        // Screen.render draws the background before widgets. Blur the world first,
        // then draw labels so the status and panel title stay as sharp as buttons.
        super.renderBackground(g,mouseX,mouseY,partialTick);
        g.fill(left-2,top-2,left+330,top+282,0xffb79958);g.fill(left,top,left+328,top+280,0xff222e32);g.fill(left+8,top+8,left+320,top+32,0xff35464a);
        g.drawCenteredString(font,title,left+164,top+16,0xffe3c78b);
        g.drawString(font,"Coordinate target · dimension",left+14,top+100,0xffaac9c7);g.drawString(font,"X",left+14,top+147,0xffe3c78b);g.drawString(font,"Z",left+172,top+147,0xffe3c78b);
        g.drawWordWrap(font,Component.literal(table.status()),left+14,top+171,300,0xff73e7dc);
        if(!error.isEmpty())g.drawString(font,error,left+14,top+229,0xffff9999);
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void removed(){
        if(intensityChanged){HololithClientConfig.save();intensityChanged=false;}
        super.removed();
    }
}
