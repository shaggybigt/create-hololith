package dev.hololith.client.ponder;
import dev.hololith.Hololith;
import dev.hololith.HololithTableEntity;
import dev.hololith.survey.*;
import java.util.List;
import java.util.UUID;
import net.createmod.ponder.api.level.PonderLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.state.BlockState;
/** Bounded scene-only capture of the actual tutorial model, never a server survey. */
final class TutorialSurvey {
    private static final UUID PIN=UUID.fromString("5a9168bc-7331-40cf-a2f5-a87ed47f28a8");
    static void project(PonderLevel level,BlockPos at,int span,int rotation,int zoom,int relief){
        if(!(level.getBlockEntity(at) instanceof HololithTableEntity table))return;
        SurveySnapshot snapshot=capture(level,span);CompoundTag tag=table.getUpdateTag(level.registryAccess());
        tag.putInt("Span",span);tag.putInt("Rotation",rotation);tag.putInt("Zoom",zoom);tag.putInt("Relief",relief);tag.putString("Status","Tutorial model");tag.put("Survey",snapshot.save());table.loadWithComponents(tag,level.registryAccess());
    }
    static void mark(PonderLevel level,BlockPos tablePos,BlockPos markerPos,DyeColor color){
        if(!(level.getBlockEntity(tablePos) instanceof HololithTableEntity table)||table.snapshot()==null)return;
        table.acceptMarkers(table.tableId(),table.snapshot().key(),List.of(new MarkerPoint(PIN,markerPos.getX()+.5,markerPos.getZ()+.5,color.getTextureDiffuseColor())));
    }
    private static SurveySnapshot capture(PonderLevel level,int span){
        SurveyKey key=new SurveyKey("minecraft:overworld",5,5,span);int count=key.size()*key.size();
        int[] gy=new int[count],sy=new int[count],gc=new int[count],sc=new int[count];BlockState[] gs=new BlockState[count],ss=new BlockState[count];byte[] flags=new byte[count];int known=0;
        for(int i=0;i<count;i++){
            int x=key.sampleX(i),z=key.sampleZ(i);if(x<0||x>10||z<0||z>10)continue;
            int y=7;BlockPos pos=new BlockPos(x,y,z);BlockState top=level.getBlockState(pos);
            while(y>0&&(top.isAir()||top.is(Hololith.MARKER.get()))){pos=new BlockPos(x,--y,z);top=level.getBlockState(pos);}if(top.isAir())continue;
            boolean water=top.getFluidState().is(FluidTags.WATER),leaves=top.is(BlockTags.LEAVES);int ground=y;BlockState bottom=top;
            if(water||leaves)while(ground>0){BlockPos below=new BlockPos(x,--ground,z);bottom=level.getBlockState(below);if(!bottom.isAir()&&bottom.getFluidState().isEmpty()&&!bottom.is(BlockTags.LEAVES)&&!bottom.is(BlockTags.LOGS))break;}
            gy[i]=ground;sy[i]=y;gs[i]=bottom;ss[i]=top;gc[i]=color(level,bottom,new BlockPos(x,ground,z));sc[i]=color(level,top,pos);flags[i]=(byte)(1|(water?2:0)|(leaves?4:0));known++;
        }
        return new SurveySnapshot(key,0,1,gy,sy,gc,sc,gs,ss,flags,known);
    }
    private static int color(PonderLevel level,BlockState state,BlockPos pos){int tint=Minecraft.getInstance().getBlockColors().getColor(state,level,pos,0);if(tint!=-1)return tint&0xffffff;int map=state.getMapColor(level,pos).col;return map==0?0xaaa9a2:map;}
}
