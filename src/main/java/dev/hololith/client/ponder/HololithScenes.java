package dev.hololith.client.ponder;
import com.simibubi.create.AllItems;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import dev.hololith.Hololith;
import dev.hololith.HololithTableEntity;
import dev.hololith.ReferenceMarkerBlock;
import java.util.List;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/** Original tutorial scenes; instructions only animate their own model. */
public final class HololithScenes {
    static final BlockPos TABLE=new BlockPos(3,3,7), SENSOR=new BlockPos(8,2,7), BEACON=new BlockPos(8,2,3), MARKER=new BlockPos(1,2,4);
    static final BlockPos INPUT=TABLE.below(), LINK=INPUT.south(), MOTOR=INPUT.south(2).below(2);
    private HololithScenes(){}
    public static void table(SceneBuilder scene,SceneBuildingUtil util){
        scene.title("surveying_table","A landscape on your workbench");begin(scene,util);drive(scene,util,32,false);
        say(scene,TABLE,"Place the table on a clear 3 x 3 floor. This raised bench has a 32 RPM drive connected underneath its center.",85);
        project(scene,32,0,1,1);
        say(scene,TABLE,"The light projection preserves terrain height, water, trees and surface structures. This example surveys the tutorial model.",110);
        scene.overlay().showControls(top(TABLE),Pointing.DOWN,50).rightClick();
        say(scene,TABLE,"Right-click the table to open its controls: survey area, source, rotation, magnification and vertical relief.",100);
        scene.overlay().showControls(top(TABLE),Pointing.DOWN,50).rightClick().withItem(AllItems.WRENCH.asStack());project(scene,32,1,1,1);
        say(scene,TABLE,"Use a Create Wrench to turn the map by a quarter turn. The surveyed world stays in place.",95);
        scene.overlay().showControls(top(TABLE),Pointing.DOWN,55).rightClick().whileSneaking().withItem(AllItems.WRENCH.asStack());project(scene,64,1,1,1);
        say(scene,TABLE,"Sneak-use the Wrench to cycle 32, 64 and 128 block survey areas. Larger areas cover more ground; 128 uses coarser samples.",110);
        project(scene,32,0,2,2);
        say(scene,TABLE,"Magnification enlarges the display and increases its stress load. Vertical relief changes elevation only. Neither loads extra terrain.",105);
        scene.markAsFinished();
    }
    public static void sources(SceneBuilder scene,SceneBuildingUtil util){
        scene.title("survey_sources","Surveying somewhere else");begin(scene,util);drive(scene,util,32,false);project(scene,32,0,1,1);
        say(scene,TABLE,"A new table surveys around itself. This bench is powered from below; a Survey Card can point it at a different source.",85);
        scene.world().showSection(util.select().position(SENSOR),Direction.DOWN);
        scene.overlay().showControls(top(SENSOR),Pointing.DOWN,55).rightClick().withItem(Hololith.CARD.toStack());
        say(scene,SENSOR,"Use a Survey Card on a Local Survey Sensor to record its identity.",85);
        scene.overlay().showControls(top(TABLE),Pointing.DOWN,55).rightClick().withItem(Hololith.CARD.toStack());scene.overlay().showLine(PonderPalette.BLUE,top(SENSOR),top(TABLE),95);
        say(scene,TABLE,"Use that card on the table. It follows the sensor's current world position, including supported moving ships and contraptions.",115);
        scene.world().showSection(util.select().position(BEACON),Direction.DOWN);
        scene.overlay().showControls(top(BEACON),Pointing.DOWN,50).rightClick().withItem(Hololith.CARD.toStack());scene.idle(20);
        scene.overlay().showControls(top(TABLE),Pointing.DOWN,50).rightClick().withItem(Hololith.CARD.toStack());scene.overlay().showLine(PonderPalette.GREEN,top(BEACON),top(TABLE),100);
        say(scene,BEACON,"A Remote Survey Beacon links the same way. Leave one at a base to revisit that area's survey from another table.",105);
        scene.overlay().showControls(top(TABLE),Pointing.DOWN,55).rightClick();
        say(scene,TABLE,"The controls switch between this table's position and its linked sensor or beacon. Survey targets must be physical instruments in the world.",115);
        scene.world().hideSection(util.select().position(BEACON),Direction.UP);
        say(scene,TABLE,"When a sensor or beacon goes offline, only its saved terrain remains while the table is powered. Live updates resume when it loads again; chunks are never force-loaded.",120);
        scene.markAsFinished();
    }
    public static void markers(SceneBuilder scene,SceneBuildingUtil util){
        scene.title("reference_markers","Marking places in the landscape");begin(scene,util);drive(scene,util,32,false);project(scene,32,0,2,1);
        scene.overlay().showControls(top(TABLE),Pointing.DOWN,55).rightClick().whileSneaking().withItem(Hololith.CARD.toStack());
        say(scene,TABLE,"Sneak-use a Survey Card on the table to copy its identity for Reference Markers. The drive below powers this display.",95);
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
    public static void power(SceneBuilder scene,SceneBuildingUtil util){
        scene.title("powering_table","Powering the projection");begin(scene,util);
        // View the southern drive from its exposed side, below the tabletop's overhang.
        scene.rotateCameraY(180);
        scene.addInstruction(s->s.getTransform().xRotation.chase(-20,.1,LerpedFloat.Chaser.EXP));
        project(scene,32,0,0,1);
        outlineDrive(scene,util,125);
        say(scene,INPUT,"Connect rotational power to the shaft socket underneath the center section. The motor here is a tutorial stand-in for a survival power source.",125);
        drive(scene,util,8,false);
        new CreateSceneBuilder(scene).effects().rotationSpeedIndicator(INPUT);
        outlineDrive(scene,util,110);
        say(scene,INPUT,"The gears turn at 8 RPM, but the projection stays off. A Hololith needs at least 16 RPM in either direction.",110);
        drive(scene,util,32,false);
        new CreateSceneBuilder(scene).effects().rotationSpeedIndicator(INPUT);
        outlineDrive(scene,util,120);
        say(scene,TABLE,"At 32 RPM the projection starts. At 100% magnification, a 32-block survey uses 4 SU per RPM: 128 SU at this speed.",120);
        project(scene,64,0,0,1);
        say(scene,TABLE,"A 64-block survey uses 8 SU per RPM at 100% magnification: 256 SU at 32 RPM.",100);
        project(scene,128,0,0,1);
        say(scene,TABLE,"A 128-block survey uses 16 SU per RPM at 100% magnification: 512 SU at 32 RPM. Supply enough network capacity before expanding.",115);
        project(scene,32,0,2,1);
        say(scene,TABLE,"Magnification multiplies stress by its square. At 150%, the same 32-block survey needs 2.25 times the load: 9 SU per RPM, or 288 SU here.",130);
        drive(scene,util,0,true);
        say(scene,INPUT,"An overstressed survival network stops the machine and blanks the entire projection, including a cached map. Reduce the load or add capacity.",120);
        drive(scene,util,32,false);
        say(scene,TABLE,"Once sufficient power returns, the saved survey is visible again. Power loss does not erase the map or its links.",110);
        scene.world().setBlock(LINK,Blocks.AIR.defaultBlockState(),false);
        drive(scene,util,0,false);
        scene.overlay().showOutline(PonderPalette.RED,new Object(),util.select().position(LINK),110);
        say(scene,LINK,"A missing drive connection also turns the projection off. The table cannot display its saved map without rotational power.",110);
        scene.world().setBlock(LINK,AllBlocks.COGWHEEL.getDefaultState().setValue(BlockStateProperties.AXIS,Direction.Axis.Y),false);
        drive(scene,util,32,false);
        outlineDrive(scene,util,100);
        say(scene,INPUT,"Reconnect the gear train and provide at least 16 RPM with enough stress capacity to resume surveying.",100);
        scene.markAsFinished();
    }
    private static void begin(SceneBuilder scene,SceneBuildingUtil util){
        scene.configureBasePlate(0,0,11);scene.scaleSceneView(.75f);scene.setSceneOffsetY(-1);scene.showBasePlate();scene.idle(10);
        scene.world().showSection(util.select().fromTo(0,1,0,10,7,5).substract(util.select().position(BEACON)).substract(util.select().position(MARKER)),Direction.DOWN);scene.idle(15);
        scene.world().showSection(util.select().fromTo(0,1,6,10,7,10).substract(util.select().position(SENSOR)),Direction.DOWN);scene.idle(20);
    }
    private static void project(SceneBuilder scene,int span,int rotation,int zoom,int relief){scene.addInstruction(s->TutorialSurvey.project(s.getWorld(),TABLE,span,rotation,zoom,relief));scene.idle(15);}
    private static void drive(SceneBuilder scene,SceneBuildingUtil util,float speed,boolean overloaded){
        CreateSceneBuilder create=new CreateSceneBuilder(scene);
        create.world().setKineticSpeed(util.select().fromTo(MOTOR,TABLE),speed);
        create.world().setKineticSpeed(util.select().position(LINK),-speed);
        scene.addInstruction(s->TutorialSurvey.power(s.getWorld(),TABLE,overloaded));
    }
    private static void outlineDrive(SceneBuilder scene,SceneBuildingUtil util,int ticks){
        scene.overlay().showOutline(PonderPalette.BLUE,new Object(),util.select().fromTo(MOTOR,MOTOR.above()),ticks);
        scene.overlay().showOutline(PonderPalette.GREEN,new Object(),util.select().fromTo(INPUT,INPUT.south(2)),ticks);
    }
    private static void say(SceneBuilder scene,BlockPos at,String text,int ticks){scene.overlay().showText(ticks).text(text).pointAt(top(at)).placeNearTarget().attachKeyFrame();scene.idle(ticks+10);}
    private static Vec3 top(BlockPos pos){return Vec3.atCenterOf(pos).add(0,.6,0);}
}
