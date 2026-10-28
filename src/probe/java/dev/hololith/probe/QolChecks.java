package dev.hololith.probe;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.hololith.*;
import dev.hololith.client.*;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.createmod.ponder.foundation.element.TextWindowElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Bounded client-only checks. Call once per client tick after the normal survey probe. */
final class QolChecks {
    private static final BlockPos MODEL_TABLE=new BlockPos(3,3,7),MODEL_MARKER=new BlockPos(1,2,4),MODEL_LINK=new BlockPos(3,2,8);
    private final BlockPos tablePos;
    private final BiConsumer<String,Boolean> check;
    private int stage,ticks,phaseTick,renderFrames,phaseFrames;
    private boolean done,oldHideGui,chosenFlicker,configRestoreRequested;
    private int configRestoreAt;
    private Object originalMesh;
    private float chosenIntensity;
    private int originalSpan,originalRotation;
    private UUID tutorialMarker;
    private CompoundTag savedPowerSurvey;
    private int powerNavigationSteps;
    private PonderUI ponder;

    QolChecks(BlockPos table,BiConsumer<String,Boolean> check){
        this.tablePos=table.immutable();this.check=check;
        // One fixture per disposable process. The listener becomes a no-op on completion.
        NeoForge.EVENT_BUS.addListener((RenderFrameEvent.Post event)->{if(!done)renderFrames++;});
    }
    private void next(){stage++;phaseTick=ticks;phaseFrames=renderFrames;}
    private boolean waited(int count){return ticks-phaseTick>=count&&renderFrames-phaseFrames>=8;}
    private void record(String text,boolean passed){check.accept(text,passed);}
    private HololithTableEntity worldTable(){
        var level=Minecraft.getInstance().level;
        return level!=null&&level.getBlockEntity(tablePos) instanceof HololithTableEntity table?table:null;
    }
    private HololithTableEntity tutorialTable(){return ponder.getActiveScene().getWorld().getBlockEntity(MODEL_TABLE) instanceof HololithTableEntity table?table:null;}
    private static Object mesh(HololithTableEntity table)throws ReflectiveOperationException{
        Field cache=HololithRenderer.class.getDeclaredField("CACHE");cache.setAccessible(true);
        Object entry=((Map<?,?>)cache.get(null)).get(table);if(entry==null)return null;
        Field value=entry.getClass().getDeclaredField("mesh");value.setAccessible(true);return value.get(entry);
    }
    private void screenshot(String name){var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),c->System.out.println(c.getString()));}
    private void open(ItemStack stack){
        ponder=PonderUI.of(stack);if(ponder==null)throw new IllegalStateException("PonderUI.of returned null");
        Minecraft.getInstance().setScreen(ponder);ponder.setComfyReadingEnabled(false);
    }
    private void displayedCaption(String fragment)throws ReflectiveOperationException{
        var captions=new java.util.ArrayList<String>();
        Field baked=TextWindowElement.class.getDeclaredField("bakedText");baked.setAccessible(true);
        ponder.getActiveScene().forEachVisible(TextWindowElement.class,element->{
            try{if(baked.get(element) instanceof String text)captions.add(text);}
            catch(IllegalAccessException failure){throw new IllegalStateException(failure);}
        });
        record("Ponder renders the expected caption: "+fragment,captions.stream().anyMatch(text->text.contains(fragment))
                &&captions.stream().noneMatch(text->text.contains("hololith.ponder.")||text.contains("Format error:")));
    }
    private void scene(String path,String title,String textFragment)throws ReflectiveOperationException{
        PonderScene scene=ponder.getActiveScene();
        record("Ponder "+path+" has expected localized title",scene.getId().equals(Hololith.id(path))&&title.equals(scene.getTitle()));
        record("Ponder "+path+" has localized first displayed caption",scene.getString("text_1").contains(textFragment));
        int captions=path.equals("powering_table")?10:path.equals("reference_markers")?5:6;
        boolean localized=true;
        for(int index=1;index<=captions;index++)localized&=!scene.getString("text_"+index).startsWith("hololith.ponder.");
        record("Ponder "+path+" localizes every displayed caption through its last",localized);
        HololithTableEntity table=tutorialTable();
        record("Ponder "+path+" operates its actual powered table",table!=null&&table.isProjecting()&&table.actualRpm()==32);
        record("Ponder "+path+" captures its real 121-column tutorial model",table!=null&&table.snapshot()!=null&&table.snapshot().knownColumns()==121);
        record("Ponder "+path+" actually builds a terrain GPU mesh",table!=null&&mesh(table)!=null);
        if(table!=null&&mesh(table)==null)System.out.println("HOLOLITH PONDER: tutorial BER mesh missing; inspect GUI pose distance culling before claiming projection rendering.");
    }
    private boolean savedIntensity(double expected)throws Exception{
        var file=Minecraft.getInstance().gameDirectory.toPath().resolve("config/hololith-client.toml");
        if(!Files.isRegularFile(file))return false;
        var match=Pattern.compile("(?m)^\\s*hologramIntensity\\s*=\\s*([0-9.]+)\\s*(?:#.*)?$").matcher(Files.readString(file));
        return match.find()&&Math.abs(Double.parseDouble(match.group(1))-expected)<.001;
    }
    private boolean savedFlicker(boolean expected)throws Exception{
        var file=Minecraft.getInstance().gameDirectory.toPath().resolve("config/hololith-client.toml");
        if(!Files.isRegularFile(file))return false;
        var match=Pattern.compile("(?m)^\\s*hologramFlicker\\s*=\\s*(true|false)\\s*(?:#.*)?$").matcher(Files.readString(file));
        return match.find()&&Boolean.parseBoolean(match.group(1))==expected;
    }
    private void powerState(String label,String state,boolean projecting,float rpm){
        var table=tutorialTable();
        record("power Ponder "+label+" has expected operating state",table!=null&&table.powerStatusKey().equals("power.hololith."+state)&&table.isProjecting()==projecting&&Math.abs(table.actualRpm()-rpm)<.001f);
        record("power Ponder "+label+" retains a saved survey",table!=null&&table.snapshot()!=null);
    }
    private void samePowerSurvey(String label){
        var table=tutorialTable();
        record("power Ponder "+label+" preserves the cached terrain",table!=null&&table.snapshot()!=null&&savedPowerSurvey!=null&&savedPowerSurvey.equals(table.snapshot().save()));
    }
    private float modelSpeed(BlockPos pos){
        var blockEntity=ponder.getActiveScene().getWorld().getBlockEntity(pos);
        if(!(blockEntity instanceof KineticBlockEntity kinetic))throw new IllegalStateException("Ponder drive is missing its kinetic block entity at "+pos);
        return kinetic.getTheoreticalSpeed();
    }
    private void close(){
        HololithClientConfig.previewIntensity(1.0);HololithClientConfig.HOLOGRAM_FLICKER.set(true);HololithClientConfig.save();
        var mc=Minecraft.getInstance();if(mc.screen!=null)mc.screen.onClose();mc.options.hideGui=oldHideGui;done=true;
    }

    /** Returns true after all checks (including failures) have been delivered to the parent. */
    boolean tick(){
        if(done)return true;ticks++;var mc=Minecraft.getInstance();
        try{
            if(ticks>2400)throw new IllegalStateException("QoL probe timed out at stage "+stage);
            switch(stage){
                case 0->{
                    var table=worldTable();if(table==null)throw new IllegalStateException("QoL fixture table is missing");
                    oldHideGui=mc.options.hideGui;mc.options.hideGui=false;
                    originalMesh=mesh(table);originalSpan=table.span();originalRotation=table.rotation();
                    record("QoL panel starts with an existing terrain GPU mesh",originalMesh!=null);
                    HololithControls.open(tablePos);next();
                }
                case 1->{
                    if(!waited(10))return false;
                    if(!(mc.screen instanceof HololithControls controls))throw new IllegalStateException("QoL instrument panel did not open");
                    record("instrument panel exposes physical sources without coordinate entry",controls.children().stream().noneMatch(c->c instanceof net.minecraft.client.gui.components.EditBox)
                        &&controls.children().stream().filter(c->c instanceof Button).map(c->((Button)c).getMessage().getString()).noneMatch(text->text.contains("coordinates")));
                    var slider=controls.children().stream().filter(c->c instanceof AbstractSliderButton).map(c->(AbstractSliderButton)c).findFirst().orElseThrow();
                    slider.onClick(slider.getX()+4+(slider.getWidth()-8)*.85,slider.getY()+10);
                    slider.onRelease(slider.getX()+4+(slider.getWidth()-8)*.85,slider.getY()+10);
                    chosenIntensity=HololithClientConfig.intensity();
                    record("actual brightness slider previews a bounded local value",chosenIntensity>1.0f&&chosenIntensity<=1.8f);
                    boolean previousFlicker=HololithClientConfig.HOLOGRAM_FLICKER.get();
                    var flicker=controls.children().stream().filter(c->c instanceof Button).map(c->(Button)c)
                        .filter(b->b.getMessage().getContents() instanceof TranslatableContents text&&text.getKey().equals("gui.hololith.flicker")).findFirst().orElseThrow();
                    flicker.onPress();chosenFlicker=HololithClientConfig.HOLOGRAM_FLICKER.get();
                    record("actual flicker button toggles its local setting",chosenFlicker!=previousFlicker);
                    record("flicker button displays localized text",!flicker.getMessage().getString().contains("gui.hololith."));
                    next();
                }
                case 2->{
                    if(!waited(25))return false;
                    record("brightness panel adjustment reuses the terrain GPU mesh",originalMesh!=null&&originalMesh==mesh(worldTable()));
                    record("flicker toggle reuses the existing terrain GPU mesh",originalMesh!=null&&originalMesh==mesh(worldTable()));
                    record("local brightness leaves shared survey settings unchanged",worldTable().span()==originalSpan&&worldTable().rotation()==originalRotation);
                    screenshot("hololith-qol-controls.png");mc.screen.onClose();next();
                }
                case 3->{
                    if(!waited(10))return false;
                    if(!configRestoreRequested){
                        if((!savedIntensity(chosenIntensity)||!savedFlicker(chosenFlicker))&&!waited(120))return false;
                        record("closing the brightness panel saves its client config",savedIntensity(chosenIntensity));
                        record("closing the panel saves the flicker toggle",savedFlicker(chosenFlicker));
                        HololithClientConfig.previewIntensity(1.0);HololithClientConfig.HOLOGRAM_FLICKER.set(true);HololithClientConfig.save();
                        configRestoreRequested=true;configRestoreAt=ticks;return false;
                    }
                    if(ticks-configRestoreAt<5)return false;
                    if((!savedIntensity(1.0)||!savedFlicker(true))&&ticks-configRestoreAt<120)return false;
                    record("QoL probe restores default brightness on disk",savedIntensity(1.0));
                    record("QoL probe restores default flicker on disk",savedFlicker(true));
                    for(var item:Map.of("surveying_table",4,"local_sensor",1,"remote_beacon",1,"survey_card",2,"reference_marker",1).entrySet())
                        record("Ponder association count for "+item.getKey(),PonderIndex.getSceneAccess().compile(Hololith.id(item.getKey())).size()==item.getValue());
                    open(Hololith.TABLE_ITEM.toStack());next();
                }
                case 4->{if(!waited(10))return false;ponder.seekToTime(180);next();}
                case 5->{
                    if(!waited(35))return false;
                    scene("surveying_table","A landscape on your workbench","clear 3 x 3");
                    displayedCaption("tutorial model");
                    record("table Ponder seek advances the actual scene",ponder.getActiveScene().getCurrentTime()>=180);
                    screenshot("hololith-ponder-table.png");open(Hololith.SENSOR_ITEM.toStack());next();
                }
                case 6->{if(!waited(10))return false;ponder.seekToTime(660);next();}
                case 7->{
                    if(!waited(25))return false;
                    scene("survey_sources","Surveying somewhere else","A new table");
                    displayedCaption("never force-loaded");
                    record("source Ponder explains offline cache without forced chunks",ponder.getActiveScene().getString("text_6").contains("offline")&&ponder.getActiveScene().getString("text_6").contains("never force-loaded"));
                    record("source Ponder teaches physical survey sources",ponder.getActiveScene().getString("text_5").contains("physical instruments")&&!ponder.getActiveScene().getString("text_5").contains("coordinates"));
                    screenshot("hololith-ponder-sources.png");open(Hololith.MARKER_ITEM.toStack());next();
                }
                case 8->{if(!waited(10))return false;ponder.seekToTime(340);next();}
                case 9->{
                    if(!waited(25))return false;
                    scene("reference_markers","Marking places in the landscape","Sneak-use a Survey Card");
                    displayedCaption("sixteen dyes");
                    var table=tutorialTable();var state=ponder.getActiveScene().getWorld().getBlockState(MODEL_MARKER);
                    record("marker Ponder dye changes the physical model",state.is(Hololith.MARKER.get())&&state.getValue(ReferenceMarkerBlock.COLOR)==DyeColor.ORANGE);
                    record("marker Ponder dye changes the terrain pin",table!=null&&table.markers().size()==1&&table.markers().getFirst().color()==(DyeColor.ORANGE.getTextureDiffuseColor()&0xffffff));
                    if(table!=null&&!table.markers().isEmpty())tutorialMarker=table.markers().getFirst().id();
                    screenshot("hololith-ponder-markers.png");ponder.seekToTime(560);next();
                }
                case 10->{
                    if(!waited(25))return false;
                    var table=tutorialTable();record("marker Ponder unlink removes the projected pin",table!=null&&table.markers().isEmpty());
                    screenshot("hololith-ponder-unlinked.png");ponder.seekToTime(340);next();
                }
                case 11->{
                    if(!waited(35))return false;
                    var table=tutorialTable();
                    record("Ponder rewind replays the linked colored pin",table!=null&&table.markers().size()==1&&table.markers().getFirst().color()==(DyeColor.ORANGE.getTextureDiffuseColor()&0xffffff)&&table.markers().getFirst().id().equals(tutorialMarker));
                    record("replayed Ponder model rebuilds a terrain GPU mesh",table!=null&&mesh(table)!=null);
                    screenshot("hololith-ponder-replay.png");open(Hololith.TABLE_ITEM.toStack());next();
                }
                case 12->{
                    if(!waited(10))return false;
                    if(ponder.getActiveScene().getId().equals(Hololith.id("powering_table"))){
                        record("actual Ponder navigation reaches the power tutorial",powerNavigationSteps>0&&"Powering the projection".equals(ponder.getActiveScene().getTitle()));
                        ponder.seekToTime(100);next();
                    }else{
                        if(powerNavigationSteps++>=4)throw new IllegalStateException("Ponder navigation did not reach the power tutorial");
                        if(!ponder.mouseScrolled(ponder.width*.5,ponder.height*.5,0,1))throw new IllegalStateException("Ponder declined next-scene scroll");
                        phaseTick=ticks;phaseFrames=renderFrames;
                    }
                }
                case 13->{
                    if(!waited(25))return false;
                    powerState("without drive","unpowered",false,0);
                    record("unpowered Ponder table does not build projection geometry",tutorialTable()!=null&&mesh(tutorialTable())==null);
                    displayedCaption("shaft socket underneath the center");
                    screenshot("hololith-ponder-power-off.png");ponder.seekToTime(230);next();
                }
                case 14->{
                    if(!waited(25))return false;
                    powerState("below minimum speed","too_slow",false,8);
                    record("power Ponder slow gears actually rotate in opposite directions",modelSpeed(MODEL_TABLE.below())==8&&modelSpeed(MODEL_LINK)==-8);
                    record("slow Ponder table still has no projection geometry",tutorialTable()!=null&&mesh(tutorialTable())==null);
                    displayedCaption("at least 16 RPM");ponder.seekToTime(350);next();
                }
                case 15->{
                    if(!waited(35))return false;
                    scene("powering_table","Powering the projection","shaft socket underneath the center");
                    powerState("at operating speed","running",true,32);
                    var table=tutorialTable();
                    record("power Ponder 32-block load matches the real table",table!=null&&table.span()==32&&table.zoom()==0&&table.stressImpact()==4&&table.stressUsage()==128);
                    var model=ponder.getActiveScene().getWorld();
                    record("power Ponder includes an animated motor and connected gear train",AllBlocks.CREATIVE_MOTOR.has(model.getBlockState(new BlockPos(3,0,9)))&&modelSpeed(new BlockPos(3,0,9))==32&&modelSpeed(new BlockPos(3,1,9))==32&&modelSpeed(new BlockPos(3,2,9))==32&&modelSpeed(MODEL_LINK)==-32&&modelSpeed(MODEL_TABLE.below())==32);
                    displayedCaption("128 SU at this speed");screenshot("hololith-ponder-power-cogs.png");ponder.seekToTime(500);next();
                }
                case 16->{
                    if(!waited(25))return false;
                    var table=tutorialTable();
                    record("power Ponder 64-block load matches the real table",table!=null&&table.isProjecting()&&table.span()==64&&table.zoom()==0&&table.stressImpact()==8&&table.stressUsage()==256);
                    displayedCaption("256 SU at 32 RPM");ponder.seekToTime(620);next();
                }
                case 17->{
                    if(!waited(25))return false;
                    var table=tutorialTable();
                    record("power Ponder 128-block load matches the real table",table!=null&&table.isProjecting()&&table.span()==128&&table.zoom()==0&&table.stressImpact()==16&&table.stressUsage()==512);
                    displayedCaption("512 SU at 32 RPM");ponder.seekToTime(760);next();
                }
                case 18->{
                    if(!waited(35))return false;
                    var table=tutorialTable();
                    record("power Ponder 150-percent magnification applies quadratic load",table!=null&&table.isProjecting()&&table.span()==32&&table.zoom()==2&&table.stressImpact()==9&&table.stressUsage()==288);
                    record("magnified power tutorial actually renders terrain geometry",table!=null&&mesh(table)!=null);
                    if(table!=null&&table.snapshot()!=null)savedPowerSurvey=table.snapshot().save();
                    displayedCaption("2.25 times the load");ponder.seekToTime(910);next();
                }
                case 19->{
                    if(!waited(25))return false;
                    powerState("when overstressed","overstressed",false,0);samePowerSurvey("overload");
                    record("overstressed tutorial stops its gear train",modelSpeed(MODEL_TABLE.below())==0&&modelSpeed(MODEL_LINK)==0);
                    displayedCaption("including a cached map");screenshot("hololith-ponder-power-overloaded.png");ponder.seekToTime(1030);next();
                }
                case 20->{
                    if(!waited(25))return false;
                    powerState("after power recovery","running",true,32);samePowerSurvey("power recovery");
                    record("restored power displays terrain geometry again",tutorialTable()!=null&&mesh(tutorialTable())!=null);
                    displayedCaption("saved survey is visible again");ponder.seekToTime(1150);next();
                }
                case 21->{
                    if(!waited(25))return false;
                    powerState("with a broken connection","unpowered",false,0);samePowerSurvey("disconnection");
                    record("disconnected tutorial physically removes the transmission cog",ponder.getActiveScene().getWorld().getBlockState(MODEL_LINK).isAir());
                    displayedCaption("missing drive connection");screenshot("hololith-ponder-power-disconnected.png");ponder.seekToTime(1270);next();
                }
                case 22->{
                    if(!waited(35))return false;
                    powerState("after reconnection","running",true,32);samePowerSurvey("reconnection");
                    record("reconnected tutorial restores its rotating transmission cog",AllBlocks.COGWHEEL.has(ponder.getActiveScene().getWorld().getBlockState(MODEL_LINK))&&modelSpeed(MODEL_LINK)==-32);
                    record("reconnected power tutorial displays real terrain geometry",tutorialTable()!=null&&mesh(tutorialTable())!=null);
                    displayedCaption("Reconnect the gear train");screenshot("hololith-ponder-power-reconnected.png");close();
                }
                default->throw new IllegalStateException("Unexpected QoL stage "+stage);
            }
        }catch(Throwable failure){
            failure.printStackTrace();record("QoL probe completed without error (stage "+stage+")",false);
            try{close();}catch(Throwable cleanup){cleanup.printStackTrace();done=true;}
        }
        return done;
    }
}
