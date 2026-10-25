package dev.hololith.probe;

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
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Bounded client-only checks. Call once per client tick after the normal survey probe. */
final class QolChecks {
    private static final BlockPos MODEL_TABLE=new BlockPos(3,1,7),MODEL_MARKER=new BlockPos(2,2,2);
    private final BlockPos tablePos;
    private final BiConsumer<String,Boolean> check;
    private int stage,ticks,phaseTick,renderFrames,phaseFrames;
    private boolean done,oldHideGui;
    private Object originalMesh;
    private float chosenIntensity;
    private int originalSpan,originalRotation;
    private UUID tutorialMarker;
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
    private void scene(String path,String title,String textFragment)throws ReflectiveOperationException{
        PonderScene scene=ponder.getActiveScene();
        record("Ponder "+path+" has expected localized title",scene.getId().equals(Hololith.id(path))&&title.equals(scene.getTitle()));
        record("Ponder "+path+" has localized tutorial text",scene.getString("text_0").contains(textFragment));
        HololithTableEntity table=tutorialTable();
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
    private void close(){
        HololithClientConfig.previewIntensity(1.0);HololithClientConfig.save();
        var mc=Minecraft.getInstance();if(mc.screen!=null)mc.screen.onClose();mc.options.hideGui=oldHideGui;done=true;
    }

    /** Returns true after all checks (including failures) have been delivered to the parent. */
    boolean tick(){
        if(done)return true;ticks++;var mc=Minecraft.getInstance();
        try{
            if(ticks>1600)throw new IllegalStateException("QoL probe timed out at stage "+stage);
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
                    var slider=controls.children().stream().filter(c->c instanceof AbstractSliderButton).map(c->(AbstractSliderButton)c).findFirst().orElseThrow();
                    slider.onClick(slider.getX()+4+(slider.getWidth()-8)*.85,slider.getY()+10);
                    slider.onRelease(slider.getX()+4+(slider.getWidth()-8)*.85,slider.getY()+10);
                    chosenIntensity=HololithClientConfig.intensity();
                    record("actual brightness slider previews a bounded local value",chosenIntensity>1.0f&&chosenIntensity<=1.8f);
                    next();
                }
                case 2->{
                    if(!waited(25))return false;
                    record("brightness panel adjustment reuses the terrain GPU mesh",originalMesh!=null&&originalMesh==mesh(worldTable()));
                    record("local brightness leaves shared survey settings unchanged",worldTable().span()==originalSpan&&worldTable().rotation()==originalRotation);
                    screenshot("hololith-qol-controls.png");mc.screen.onClose();next();
                }
                case 3->{
                    if(!waited(10))return false;
                    record("closing the brightness panel saves its client config",savedIntensity(chosenIntensity));
                    HololithClientConfig.previewIntensity(1.0);HololithClientConfig.save();
                    record("QoL probe restores default brightness on disk",savedIntensity(1.0));
                    for(var item:Map.of("surveying_table",3,"local_sensor",1,"remote_beacon",1,"survey_card",2,"reference_marker",1).entrySet())
                        record("Ponder association count for "+item.getKey(),PonderIndex.getSceneAccess().compile(Hololith.id(item.getKey())).size()==item.getValue());
                    open(Hololith.TABLE_ITEM.toStack());next();
                }
                case 4->{if(!waited(10))return false;ponder.seekToTime(180);next();}
                case 5->{
                    if(!waited(35))return false;
                    scene("surveying_table","A landscape on your workbench","clear 3 x 3");
                    record("table Ponder seek advances the actual scene",ponder.getActiveScene().getCurrentTime()>=180);
                    screenshot("hololith-ponder-table.png");open(Hololith.SENSOR_ITEM.toStack());next();
                }
                case 6->{if(!waited(10))return false;ponder.seekToTime(660);next();}
                case 7->{
                    if(!waited(25))return false;
                    scene("survey_sources","Surveying somewhere else","A new table");
                    record("source Ponder explains offline cache without forced chunks",ponder.getActiveScene().getString("text_5").contains("offline")&&ponder.getActiveScene().getString("text_5").contains("never force-loaded"));
                    screenshot("hololith-ponder-sources.png");open(Hololith.MARKER_ITEM.toStack());next();
                }
                case 8->{if(!waited(10))return false;ponder.seekToTime(340);next();}
                case 9->{
                    if(!waited(25))return false;
                    scene("reference_markers","Marking places in the landscape","Sneak-use a Survey Card");
                    var table=tutorialTable();var state=ponder.getActiveScene().getWorld().getBlockState(MODEL_MARKER);
                    record("marker Ponder dye changes the physical model",state.is(Hololith.MARKER.get())&&state.getValue(ReferenceMarkerBlock.COLOR)==DyeColor.ORANGE);
                    record("marker Ponder dye changes the terrain pin",table!=null&&table.markers().size()==1&&table.markers().getFirst().color()==DyeColor.ORANGE.getTextureDiffuseColor());
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
                    record("Ponder rewind replays the linked colored pin",table!=null&&table.markers().size()==1&&table.markers().getFirst().color()==DyeColor.ORANGE.getTextureDiffuseColor()&&table.markers().getFirst().id().equals(tutorialMarker));
                    record("replayed Ponder model rebuilds a terrain GPU mesh",table!=null&&mesh(table)!=null);
                    screenshot("hololith-ponder-replay.png");close();
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
