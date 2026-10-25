package dev.hololith;

import dev.hololith.compat.ShipCoordinates;
import dev.hololith.survey.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class HololithTableEntity extends BlockEntity {
    private SurveySnapshot snapshot;
    private int span=64,rotation,zoom,relief=1,mode;
    private UUID sensorId;
    private UUID tableId=UUID.randomUUID();
    private List<MarkerPoint> markerPoints=List.of();
    private SurveyKey markerKey;
    private String targetDimension="minecraft:overworld",status="Waiting for survey";
    private int targetX,targetZ;
    private SurveyKey pendingKey;
    private long lastControlAt=-100;
    private Runnable mountedStateChanged;
    private java.util.function.BiConsumer<SurveyKey,List<MarkerPoint>> mountedMarkersChanged;
    public HololithTableEntity(BlockPos pos,BlockState state){super(Hololith.TABLE_ENTITY.get(),pos,state);}
    public SurveySnapshot snapshot(){return snapshot;}
    public int rotation(){return rotation;}
    public float magnification(){return 1+zoom*.25f;}
    public float verticalScale(){return relief;}
    public int span(){return span;}
    public int mode(){return mode;}
    public int zoom(){return zoom;}
    public String targetDimension(){return targetDimension;}
    public int targetX(){return targetX;}
    public int targetZ(){return targetZ;}
    public String status(){return status;}
    public UUID sensorId(){return sensorId;}
    public UUID tableId(){return tableId;}
    /** Runtime-only transport for Create's captured block entities. Never saved in NBT. */
    public void setMountedCallbacks(Runnable stateChanged,java.util.function.BiConsumer<SurveyKey,List<MarkerPoint>> markersChanged){
        mountedStateChanged=stateChanged;mountedMarkersChanged=markersChanged;
    }
    public List<MarkerPoint> markers(){return snapshot!=null&&snapshot.key().equals(markerKey)?markerPoints:List.of();}
    public void acceptMarkers(UUID identity,SurveyKey key,List<MarkerPoint> points){if(tableId.equals(identity)){markerKey=key;markerPoints=List.copyOf(points);}}
    private void refreshMarkers(){
        if(!(level instanceof ServerLevel serverLevel))return;
        SurveyKey key=snapshot==null?null:snapshot.key();
        List<MarkerPoint> points=ReferenceRegistry.get(serverLevel.getServer()).forTable(serverLevel.getServer(),this);
        if(!Objects.equals(markerKey,key)||!markerPoints.equals(points)){
            markerKey=key;markerPoints=points;
            if(mountedMarkersChanged!=null)mountedMarkersChanged.accept(key,points);else HololithNetwork.markers(serverLevel,this,key,points);
        }
    }
    @Override public void onLoad(){super.onLoad();if(level!=null&&!level.isClientSide)setChanged();}
    public boolean allowControl(){if(level==null||level.getGameTime()-lastControlAt<5)return false;lastControlAt=level.getGameTime();return true;}
    public void bind(CompoundTag card){
        if(card.hasUUID("SensorId")){sensorId=card.getUUID("SensorId");mode=1;}else{sensorId=null;mode=2;}
        targetDimension=card.getString("Dimension");targetX=card.getInt("X");targetZ=card.getInt("Z");snapshot=null;pendingKey=null;status="Acquiring survey";changed();
    }
    public void adjustDial(boolean scale){if(scale)span=span==32?64:span==64?128:32;else rotation=(rotation+1)%4;if(scale){snapshot=null;pendingKey=null;}changed();}
    public void configure(int span,int rotation,int zoom,int relief,int mode,String dimension,int x,int z){
        if(this.span==span&&this.rotation==rotation&&this.zoom==zoom&&this.relief==relief&&this.mode==mode&&(mode!=2||(targetDimension.equals(dimension)&&targetX==x&&targetZ==z)))return;
        boolean retarget=this.span!=span||this.mode!=mode||(mode==2&&(!this.targetDimension.equals(dimension)||targetX!=x||targetZ!=z));
        this.span=SurveyKey.validSpan(span);this.rotation=Math.floorMod(rotation,4);this.zoom=Math.clamp(zoom,0,2);this.relief=Math.clamp(relief,1,2);this.mode=Math.clamp(mode,0,2);
        if(mode==2){targetDimension=dimension;targetX=x;targetZ=z;}
        if(retarget){snapshot=null;pendingKey=null;status="Acquiring survey";}changed();
    }
    public static void tick(Level level,BlockPos pos,BlockState state,HololithTableEntity table){
        Optional<net.minecraft.world.phys.Vec3> location=table.mode==0&&(level.getGameTime()+pos.asLong())%20==0?ShipCoordinates.toWorld(level,pos):Optional.empty();
        tickAt(level,pos,state,table,location);
    }
    /** Uses an authoritative transformed position supplied by a moving platform. */
    public static void tickAt(Level level,BlockPos pos,BlockState state,HololithTableEntity table,Optional<net.minecraft.world.phys.Vec3> location){
        if(!(level instanceof ServerLevel))return;
        if((level.getGameTime()+pos.asLong())%5==0)table.refreshMarkers();
        if((level.getGameTime()+pos.asLong())%20!=0)return;
        var server=((ServerLevel)level).getServer();SurveyStore store=SurveyStore.get(server);
        String dimension=table.targetDimension;int x=table.targetX,z=table.targetZ;boolean online=true;
        if(table.mode==0){
            if(location.isEmpty()){table.updateStatus(SurveyStatus.retained("Position unavailable",table.snapshot));return;}
            dimension=level.dimension().location().toString();x=(int)Math.floor(location.get().x);z=(int)Math.floor(location.get().z);
        }else if(table.mode==1){
            var sensor=table.sensorId==null?null:store.sensor(table.sensorId);online=sensor!=null&&sensor.online();
            if(sensor!=null){dimension=sensor.dimension();x=sensor.x();z=sensor.z();}
        }
        SurveyKey key=SurveyKey.at(dimension,x,z,table.span);
        // Re-seed the shared cache after eviction/restart from this table's own persistent survey.
        if(table.snapshot!=null&&table.snapshot.key().equals(key)&&store.find(key)==null)store.put(table.snapshot);
        SurveyManager manager=SurveyManager.get(server);
        SurveyKey requested=table.pendingKey==null?key:table.pendingKey;
        SurveySnapshot next=manager.request(requested);
        table.pendingKey=manager.isPending(requested)?requested:null;
        boolean changed=false;
        if(next!=null){
            changed=table.snapshot==null||!table.snapshot.key().equals(next.key())||table.snapshot.revision!=next.revision;
            table.snapshot=next;
        }
        SurveySnapshot displayed=table.snapshot;
        var dimensionId=net.minecraft.resources.ResourceLocation.tryParse(key.dimension());
        boolean dimensionAvailable=dimensionId!=null&&server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,dimensionId))!=null;
        String text=SurveyStatus.describe(displayed,key,online,manager.hasChecked(key),manager.isPending(requested),dimensionAvailable);
        if(!text.equals(table.status)){table.status=text;changed=true;}
        if(changed)table.changed();
    }
    private void updateStatus(String text){if(!status.equals(text)){status=text;changed();}}
    public void changed(){if(mountedStateChanged!=null){mountedStateChanged.run();return;}setChanged();if(level!=null&&!level.isClientSide)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2);}
    private void writeSettings(CompoundTag tag){
        tag.putInt("Span",span);tag.putInt("Rotation",rotation);tag.putInt("Zoom",zoom);tag.putInt("Relief",relief);tag.putInt("Mode",mode);if(sensorId!=null)tag.putUUID("SensorId",sensorId);tag.putString("Dimension",targetDimension);tag.putInt("X",targetX);tag.putInt("Z",targetZ);tag.putString("Status",status);
    }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider lookup){super.saveAdditional(tag,lookup);tag.putUUID("TableId",tableId);writeSettings(tag);if(snapshot!=null)tag.put("Survey",snapshot.save());}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider lookup){
        if(tag.hasUUID("TableId"))tableId=tag.getUUID("TableId");
        if(tag.contains("Markers")){
            markerPoints=MarkerPoint.load(tag.getList("Markers",net.minecraft.nbt.Tag.TAG_COMPOUND));
            markerKey=tag.contains("MarkerDimension")?new SurveyKey(tag.getString("MarkerDimension"),tag.getInt("MarkerX"),tag.getInt("MarkerZ"),tag.getInt("MarkerSpan")):null;
        }
        super.loadAdditional(tag,lookup);span=SurveyKey.validSpan(tag.getInt("Span"));rotation=Math.floorMod(tag.getInt("Rotation"),4);zoom=Math.clamp(tag.getInt("Zoom"),0,2);relief=Math.clamp(tag.getInt("Relief"),1,2);mode=Math.clamp(tag.getInt("Mode"),0,2);sensorId=tag.hasUUID("SensorId")?tag.getUUID("SensorId"):null;targetDimension=tag.getString("Dimension");targetX=tag.getInt("X");targetZ=tag.getInt("Z");status=tag.getString("Status");snapshot=tag.contains("Survey")?SurveySnapshot.load(tag.getCompound("Survey"),lookup):null;
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider lookup){
        CompoundTag tag=saveWithoutMetadata(lookup);tag.put("Markers",MarkerPoint.save(markerPoints));
        if(markerKey!=null){tag.putString("MarkerDimension",markerKey.dimension());tag.putInt("MarkerX",markerKey.x());tag.putInt("MarkerZ",markerKey.z());tag.putInt("MarkerSpan",markerKey.span());}
        return tag;
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
}
