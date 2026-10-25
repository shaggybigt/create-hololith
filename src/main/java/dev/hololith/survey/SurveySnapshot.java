package dev.hololith.survey;

import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Immutable after publication. State palette is saved by registry name, never runtime numeric IDs. */
public final class SurveySnapshot {
    public final int size,span,centerX,centerZ;
    public final String dimension;
    public final long capturedAt,revision;
    public final int[] groundY,surfaceY,groundColors,surfaceColors;
    public final BlockState[] groundStates,surfaceStates;
    public final byte[] flags;
    public final int loadedColumns;
    public SurveySnapshot(SurveyKey key,long capturedAt,long revision,int[] groundY,int[] surfaceY,int[] groundColors,int[] surfaceColors,BlockState[] groundStates,BlockState[] surfaceStates,byte[] flags,int loadedColumns){
        this.size=key.size();this.span=key.span();this.centerX=key.x();this.centerZ=key.z();this.dimension=key.dimension();this.capturedAt=capturedAt;this.revision=revision;
        this.groundY=groundY;this.surfaceY=surfaceY;this.groundColors=groundColors;this.surfaceColors=surfaceColors;this.groundStates=groundStates;this.surfaceStates=surfaceStates;this.flags=flags;this.loadedColumns=loadedColumns;
    }
    public SurveyKey key(){return new SurveyKey(dimension,centerX,centerZ,span);}
    public int knownColumns(){int n=0;for(byte f:flags)if((f&1)!=0)n++;return n;}
    public boolean sameTerrain(SurveySnapshot other){return other!=null&&key().equals(other.key())&&Arrays.equals(groundY,other.groundY)&&Arrays.equals(surfaceY,other.surfaceY)&&Arrays.equals(groundColors,other.groundColors)&&Arrays.equals(surfaceColors,other.surfaceColors)&&Arrays.equals(groundStates,other.groundStates)&&Arrays.equals(surfaceStates,other.surfaceStates)&&Arrays.equals(flags,other.flags);}
    public CompoundTag save(){
        CompoundTag t=new CompoundTag();t.putInt("Format",1);t.putString("Dimension",dimension);t.putInt("X",centerX);t.putInt("Z",centerZ);t.putInt("Span",span);t.putLong("Captured",capturedAt);t.putLong("Revision",revision);t.putInt("Loaded",loadedColumns);
        t.putIntArray("GroundY",groundY);t.putIntArray("SurfaceY",surfaceY);t.putIntArray("GroundColor",groundColors);t.putIntArray("SurfaceColor",surfaceColors);t.putByteArray("Flags",flags);
        Map<BlockState,Integer> indices=new LinkedHashMap<>();ListTag palette=new ListTag();int[] states=new int[size*size];
        for(int i=0;i<states.length;i++){
            BlockState ground=groundStates[i]==null?Blocks.AIR.defaultBlockState():groundStates[i],surface=surfaceStates[i]==null?Blocks.AIR.defaultBlockState():surfaceStates[i];
            int g=indices.computeIfAbsent(ground,s->{palette.add(NbtUtils.writeBlockState(s));return palette.size()-1;});
            int s=indices.computeIfAbsent(surface,v->{palette.add(NbtUtils.writeBlockState(v));return palette.size()-1;});
            states[i]=(g&65535)|(s<<16);
        }
        t.put("Palette",palette);t.putIntArray("States",states);return t;
    }
    public static SurveySnapshot load(CompoundTag t,HolderLookup.Provider lookup){
        int span=t.getInt("Span");if(t.getInt("Format")!=1||(span!=32&&span!=64&&span!=128))return null;
        int n=Math.min(span,64),count=n*n;int[] gy=t.getIntArray("GroundY"),sy=t.getIntArray("SurfaceY"),gc=t.getIntArray("GroundColor"),sc=t.getIntArray("SurfaceColor"),states=t.getIntArray("States");byte[] flags=t.getByteArray("Flags");
        if(gy.length!=count||sy.length!=count||gc.length!=count||sc.length!=count||states.length!=count||flags.length!=count)return null;
        ListTag palette=t.getList("Palette",Tag.TAG_COMPOUND);if(palette.isEmpty()||palette.size()>8192)return null;
        BlockState[] p=new BlockState[palette.size()];for(int i=0;i<p.length;i++)p[i]=NbtUtils.readBlockState(lookup.lookupOrThrow(Registries.BLOCK),palette.getCompound(i));
        BlockState[] g=new BlockState[count],s=new BlockState[count];for(int i=0;i<count;i++){
            int a=states[i]&65535,b=states[i]>>>16;if(a>=p.length||b>=p.length||gy[i]<-4096||gy[i]>4095||sy[i]<-4096||sy[i]>4095)return null;g[i]=p[a];s[i]=p[b];
        }
        return new SurveySnapshot(new SurveyKey(t.getString("Dimension"),t.getInt("X"),t.getInt("Z"),span),t.getLong("Captured"),t.getLong("Revision"),gy,sy,gc,sc,g,s,flags,Math.clamp(t.getInt("Loaded"),0,count));
    }
}
