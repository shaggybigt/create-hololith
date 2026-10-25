package dev.hololith.client.ponder;
import com.simibubi.create.AllItems;
import dev.hololith.Hololith;
import dev.hololith.HololithTableEntity;
import dev.hololith.ReferenceMarkerBlock;
import java.util.List;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Original tutorial scenes; instructions only animate their own model. */
public final class HololithScenes {
    static final BlockPos TABLE=new BlockPos(3,1,7), SENSOR=new BlockPos(8,2,7), BEACON=new BlockPos(8,2,3), MARKER=new BlockPos(1,2,4);
    private HololithScenes(){}
    public static void table(SceneBuilder scene,SceneBuildingUtil util){
        scene.title("surveying_table","A landscape on your workbench");begin(scene,util);
        say(scene,TABLE,"Place the table on a clear 3 x 3 floor. Its nine sections form one instrument.",85);
        project(scene,32,0,1,1);
        say(scene,TABLE,"The light projection preserves terrain height, water, trees and surface structures. This example surveys the tutorial model.",110);
        scene.overlay().showControls(top(TABLE),Pointing.DOWN,50).rightClick();
        say(scene,TABLE,"Right-click the table to open its controls: survey area, source, rotation, magnification and vertical relief.",100);
        scene.overlay().showControls(top(TABLE),Pointing.DOWN,50).rightClick().withItem(AllItems.WRENCH.asStack());project(scene,32,1,1,1);
        say(scene,TABLE,"Use a Create Wrench to turn the map by a quarter turn. The surveyed world stays in place.",95);
        scene.overlay().showControls(top(TABLE),Pointing.DOWN,55).rightClick().whileSneaking().withItem(AllItems.WRENCH.asStack());project(scene,64,1,1,1);
        say(scene,TABLE,"Sneak-use the Wrench to cycle 32, 64 and 128 block survey areas. Larger areas cover more ground; 128 uses coarser samples.",110);
        project(scene,32,0,2,2);
        say(scene,TABLE,"Magnification enlarges the display. Vertical relief makes changes in elevation easier to read. Neither loads extra terrain.",105);
        scene.markAsFinished();
    }
    public static void sources(SceneBuilder scene,SceneBuildingUtil util){
        scene.title("survey_sources","Surveying somewhere else");begin(scene,util);project(scene,32,0,1,1);
        say(scene,TABLE,"A new table surveys around itself. A Survey Card can point it at a different source.",85);
        scene.world().showSection(util.select().position(SENSOR),Direction.DOWN);
        scene.overlay().showControls(top(SENSOR),Pointing.DOWN,55).rightClick().withItem(Hololith.CARD.toStack());
        say(scene,SENSOR,"Use a Survey Card on a Local Survey Sensor to record its identity.",85);
        scene.overlay().showControls(top(TABLE),Pointing.DOWN,55).rightClick().withItem(Hololith.CARD.toStack());scene.overlay().showLine(PonderPalette.BLUE,top(SENSOR),top(TABLE),95);
        say(scene,TABLE,"Use that card on the table. It follows the sensor's current world position, including supported moving ships and contraptions.",115);
        scene.world().showSection(util.select().position(BEACON),Direction.DOWN);
        scene.overlay().showControls(top(BEACON),Pointing.DOWN,50).rightClick().withItem(Hololith.CARD.toStack());scene.idle(20);
        scene.overlay().showControls(top(TABLE),Pointing.DOWN,50).rightClick().withItem(Hololith.CARD.toStack());scene.overlay().showLine(PonderPalette.GREEN,top(BEACON),top(TABLE),100);
        say(scene,BEACON,"A Remote Survey Beacon links the same way. Leave one at a base to revisit that area's survey from another table.",105);
        scene.overlay().showControls(new Vec3(4.5,2.1,4.5),Pointing.DOWN,55).rightClick().whileSneaking().withItem(Hololith.CARD.toStack());
        say(scene,new BlockPos(4,1,4),"Sneak-use a card on ordinary terrain to record coordinates, then apply it to the table. Exact coordinates can also be set in the controls.",115);
        scene.world().hideSection(util.select().position(BEACON),Direction.UP);
        say(scene,TABLE,"Surveys use loaded terrain and refresh gradually. A saved projection stays visible when its source goes offline; distant chunks are never force-loaded.",120);
        scene.markAsFinished();
    }
    public static void markers(SceneBuilder scene,SceneBuildingUtil util){
        scene.title("reference_markers","Marking places in the landscape");begin(scene,util);project(scene,32,0,2,1);
        scene.overlay().showControls(top(TABLE),Pointing.DOWN,55).rightClick().whileSneaking().withItem(Hololith.CARD.toStack());
        say(scene,TABLE,"Sneak-use a Survey Card on the table to copy its identity for Reference Markers.",95);
        scene.world().showSection(util.select().position(MARKER),Direction.DOWN);
        scene.overlay().showControls(top(MARKER),Pointing.DOWN,55).rightClick().withItem(Hololith.CARD.toStack());
        scene.addInstruction(s->TutorialSurvey.mark(s.getWorld(),TABLE,MARKER,DyeColor.CYAN));scene.overlay().showLine(PonderPalette.BLUE,top(MARKER),top(TABLE),85);
        say(scene,MARKER,"Use the card on a placed marker. Its colored pin appears at the matching horizontal position in that table's known survey.",110);
        scene.overlay().showControls(top(MARKER),Pointing.DOWN,55).rightClick().withItem(new ItemStack(Items.ORANGE_DYE));
        scene.world().modifyBlock(MARKER,state->state.setValue(ReferenceMarkerBlock.COLOR,DyeColor.ORANGE),false);
        scene.addInstruction(s->TutorialSurvey.mark(s.getWorld(),TABLE,MARKER,DyeColor.ORANGE));
        say(scene,MARKER,"Use any of the sixteen dyes to recolor the physical marker and its pin. Choose your own colors for bases, routes and landmarks.",110);
        say(scene,TABLE,"A sensor card can instead link markers to that sensor's survey group. Pins follow the map's rotation and scale; they are not entity trackers.",110);
        scene.overlay().showControls(top(MARKER),Pointing.DOWN,55).rightClick().whileSneaking().withItem(AllItems.WRENCH.asStack());
        scene.addInstruction(s->{if(s.getWorld().getBlockEntity(TABLE) instanceof HololithTableEntity table&&table.snapshot()!=null)table.acceptMarkers(table.tableId(),table.snapshot().key(),List.of());});
        say(scene,MARKER,"Sneak-use a Create Wrench on a marker to unlink it. The marker remains available to link again.",100);
        scene.markAsFinished();
    }
    private static void begin(SceneBuilder scene,SceneBuildingUtil util){
        scene.configureBasePlate(0,0,11);scene.scaleSceneView(.75f);scene.setSceneOffsetY(-1);scene.showBasePlate();scene.idle(10);
        scene.world().showSection(util.select().fromTo(0,1,0,10,7,5).substract(util.select().position(BEACON)).substract(util.select().position(MARKER)),Direction.DOWN);scene.idle(15);
        scene.world().showSection(util.select().fromTo(0,1,6,10,7,10).substract(util.select().position(SENSOR)),Direction.DOWN);scene.idle(20);
    }
    private static void project(SceneBuilder scene,int span,int rotation,int zoom,int relief){scene.addInstruction(s->TutorialSurvey.project(s.getWorld(),TABLE,span,rotation,zoom,relief));scene.idle(15);}
    private static void say(SceneBuilder scene,BlockPos at,String text,int ticks){scene.overlay().showText(ticks).text(text).pointAt(top(at)).placeNearTarget().attachKeyFrame();scene.idle(ticks+10);}
    private static Vec3 top(BlockPos pos){return Vec3.atCenterOf(pos).add(0,.6,0);}
}
