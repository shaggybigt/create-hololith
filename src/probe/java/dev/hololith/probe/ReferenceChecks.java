package dev.hololith.probe;
import dev.hololith.*;
import dev.hololith.compat.ShipCoordinates;
import dev.hololith.survey.*;
import java.util.*;
import java.util.function.BiConsumer;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
final class ReferenceChecks {
    final BiConsumer<String,Boolean> check;
    final List<BlockPos> shipRefs=new ArrayList<>();
    final Map<UUID,BlockPos> shipIds=new HashMap<>();
    static final DyeColor[] COLORS={DyeColor.RED,DyeColor.LIME,DyeColor.BLUE,DyeColor.YELLOW};
    UUID tableId;
    long terrainRevision;
    ReferenceChecks(BiConsumer<String,Boolean> check){this.check=check;}
    static List<BlockPos> buried(int cx,int cz){return List.of(new BlockPos(cx-6,198,cz-6),new BlockPos(cx+6,198,cz-6),new BlockPos(cx+6,198,cz+6),new BlockPos(cx-6,198,cz+6));}
    void setup(ServerPlayer p,BlockPos table,UUID sensor,int cx,int cz){
        var level=p.serverLevel();var t=(HololithTableEntity)level.getBlockEntity(table);tableId=t.tableId();terrainRevision=t.snapshot().revision;
        var positions=buried(cx,cz);
        for(int i=0;i<4;i++){
            var at=positions.get(i);level.setBlockAndUpdate(at,Hololith.MARKER.get().defaultBlockState().setValue(ReferenceMarkerBlock.COLOR,COLORS[i]));
            CompoundTag link=new CompoundTag();link.putUUID(i<2?"TableId":"SensorId",i<2?t.tableId():sensor);((ReferenceMarkerEntity)level.getBlockEntity(at)).bind(link);
        }
    }
    void verifyBuried(ServerPlayer p,BlockPos table,int cx,int cz){
        var t=(HololithTableEntity)p.serverLevel().getBlockEntity(table);
        check.accept("four buried references visible through solid terrain",t.markers().size()==4&&buried(cx,cz).stream().allMatch(at->Arrays.stream(Direction.values()).allMatch(d->!p.serverLevel().getBlockState(at.relative(d)).isAir())));
        check.accept("each reference keeps its own dye color",t.markers().stream().map(MarkerPoint::color).distinct().count()==4);
        check.accept("marker registration does not change terrain revision",t.snapshot().revision==terrainRevision);
        var full=t.saveWithFullMetadata(p.registryAccess());full.merge(t.getUpdateTag(p.registryAccess()));
        var initial=(HololithTableEntity)BlockEntity.loadStatic(table,t.getBlockState(),full,p.registryAccess());
        check.accept("initial chunk tag includes live reference frame",initial!=null&&initial.markers().equals(t.markers()));
    }
    void addShipReferences(ServerLevel level,BlockPos table,UUID sensor,List<BlockPos> blocks){
        tableId=((HololithTableEntity)level.getBlockEntity(table)).tableId();shipRefs.clear();shipIds.clear();
        for(int i=0;i<4;i++){
            var at=table.offset(i==0||i==3?-4:4,-2,i<2?-4:4);shipRefs.add(at);
            level.setBlockAndUpdate(at,Hololith.MARKER.get().defaultBlockState().setValue(ReferenceMarkerBlock.COLOR,COLORS[i]));
            CompoundTag card=new CompoundTag();card.putUUID("SensorId",sensor);var marker=(ReferenceMarkerEntity)level.getBlockEntity(at);marker.bind(card);shipIds.put(marker.markerId(),at);blocks.add(at);
            for(var direction:Direction.values()){var shell=at.relative(direction);level.setBlockAndUpdate(shell,Blocks.STONE_BRICKS.defaultBlockState());if(!blocks.contains(shell))blocks.add(shell);}
            int sx=Integer.signum(at.getX()-table.getX()),sz=Integer.signum(at.getZ()-table.getZ());
            for(int n=0;n<=3;n++)for(var beam:List.of(table.offset(sx*n,-2,sz*4),table.offset(0,-2,sz*n))){
                if(!blocks.contains(beam)){level.setBlockAndUpdate(beam,Blocks.STONE_BRICKS.defaultBlockState());blocks.add(beam);}
            }
        }
    }
    void verifyShip(ServerPlayer p,BlockPos table,ShipFixture fixture,double dx,double dz,double yaw,String stage){
        var t=(HololithTableEntity)p.serverLevel().getBlockEntity(table);var found=t.markers().stream().filter(m->shipIds.containsKey(m.id())).toList();
        check.accept(stage+" four concealed ship markers synchronized",found.size()==4);
        check.accept(stage+" table identity retained",tableId.equals(t.tableId()));
        var delta=new org.joml.Quaterniond(fixture.startRotation).rotateY(yaw).mul(new org.joml.Quaterniond(fixture.startRotation).invert());
        boolean aligned=found.size()==4;
        for(var point:found){
            var original=shipIds.get(point.id());var expected=new org.joml.Vector3d(original.getX()+.5,original.getY()+.5,original.getZ()+.5).sub(fixture.startPosition);
            delta.transform(expected).add(fixture.startPosition).add(dx,0,dz);
            aligned&=Math.abs(expected.x-point.x())<.004&&Math.abs(expected.z-point.z())<.004;
            if(Math.abs(expected.x-point.x())>=.004||Math.abs(expected.z-point.z())>=.004)System.out.println("REFERENCE POSITION "+stage+" original="+original+" expected="+expected+" received="+point+" currentTransform="+ShipCoordinates.toWorld(p.serverLevel(),fixture.inPlot(original)));
            var actual=(ReferenceMarkerEntity)p.serverLevel().getBlockEntity(fixture.inPlot(original));aligned&=actual!=null&&actual.markerId().equals(point.id());
        }
        check.accept(stage+" markers match independently computed ship coordinates",aligned);
        check.accept(stage+" markers sample terrain instead of reference altitude",found.stream().allMatch(m->SurveyGeometry.at(t.snapshot()).position(m.x(),m.z()).map(pos->pos.y()<.8).orElse(false)));
    }
    void verifyRestart(ServerPlayer p,BlockPos table,int cx,int cz){
        var t=(HololithTableEntity)p.serverLevel().getBlockEntity(table);var positions=buried(cx,cz);
        if(!(p.serverLevel().getBlockEntity(positions.getFirst()) instanceof ReferenceMarkerEntity)){
            check.accept("legacy table obtains persistent table identity",t.saveWithFullMetadata(p.registryAccess()).hasUUID("TableId"));return;
        }
        boolean links=true,colors=true;
        for(int i=0;i<4;i++){var marker=(ReferenceMarkerEntity)p.serverLevel().getBlockEntity(positions.get(i));links&=marker!=null&&Objects.equals(marker.targetId(),i<2?t.tableId():t.sensorId());colors&=marker!=null&&marker.getBlockState().getValue(ReferenceMarkerBlock.COLOR)==COLORS[i];}
        check.accept("reference UUID links survive actual restart",links);
        check.accept("individual reference colors survive actual restart",colors);
        check.accept("ephemeral marker registry recovers from loaded blocks",t.markers().size()==4);
    }
}
