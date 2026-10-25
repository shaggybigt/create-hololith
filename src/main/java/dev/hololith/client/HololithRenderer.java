package dev.hololith.client;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.mojang.logging.LogUtils;
import dev.hololith.HololithTableEntity;
import dev.hololith.survey.SurveySnapshot;
import dev.hololith.survey.SurveyGeometry;
import java.lang.reflect.Method;
import java.util.*;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
/** Uses the supplied BER pose, including Sable's ship transform. */
public final class HololithRenderer implements BlockEntityRenderer<HololithTableEntity> {
    private static final LinkedHashMap<HololithTableEntity,Entry> CACHE=new LinkedHashMap<>(16,.75f,true);
    private static long ticks,lastBuildTick=-1;
    private static boolean shaderChecked;
    private static Object iris;
    private static Method shadowPass;
    public HololithRenderer(BlockEntityRendererProvider.Context context){}
    @Override public void render(HololithTableEntity table,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        if(isShadowPass())return;
        Matrix4f local=pose.last().pose();if(local.m30()*local.m30()+local.m31()*local.m31()+local.m32()*local.m32()>96*96)return;
        SurveySnapshot snapshot=table.snapshot();Entry entry=CACHE.computeIfAbsent(table,ignored->new Entry());entry.lastUsed=ticks;
        if(sameArea(entry.snapshot,snapshot)&&entry.snapshot.revision==snapshot.revision)entry.snapshot=snapshot;
        if(entry.snapshot!=snapshot&&!sameArea(entry.snapshot,snapshot))entry.close();
        if(snapshot!=null&&entry.snapshot!=snapshot&&lastBuildTick!=ticks&&ticks-entry.lastAttempt>=10){
            lastBuildTick=ticks;entry.lastAttempt=ticks;
            try{SurveyMesh next=SurveyMesh.build(snapshot);entry.close();entry.mesh=next;entry.snapshot=snapshot;}
            catch(RuntimeException failure){entry.snapshot=snapshot;LogUtils.getLogger().warn("Could not build Hololith survey projection",failure);}
        }
        while(CACHE.size()>8){var first=CACHE.entrySet().iterator();var old=first.next();old.getValue().close();first.remove();}
        pose.pushPose();
        try{
            pose.translate(.5,1.35,.5);pose.mulPose(Axis.YP.rotationDegrees(table.rotation()*90f));pose.scale(table.magnification(),table.verticalScale(),table.magnification());
            float intensity=HololithClientConfig.intensity();
            if(entry.mesh!=null){Matrix4f transform=new Matrix4f(RenderSystem.getModelViewMatrix()).mul(pose.last().pose());entry.mesh.draw(transform,intensity);}
            decorations(pose.last(),buffers.getBuffer(HololithRenderTypes.LIGHT),ticks+partial);
            if(entry.mesh!=null&&entry.mesh.geometry()!=null&&entry.snapshot!=null&&snapshot!=null&&entry.snapshot.key().equals(snapshot.key()))
                markers(table,entry.mesh.geometry(),pose.last(),buffers,intensity);
        }finally{pose.popPose();}
    }
    private static boolean sameArea(SurveySnapshot a,SurveySnapshot b){return a!=null&&b!=null&&a.span==b.span&&a.centerX==b.centerX&&a.centerZ==b.centerZ&&a.dimension.equals(b.dimension);}
    static void tick(){ticks++;if(ticks%100!=0)return;var it=CACHE.entrySet().iterator();while(it.hasNext()){var e=it.next();if(e.getKey().isRemoved()||ticks-e.getValue().lastUsed>600){e.getValue().close();it.remove();}}}
    public static void clear(){for(Entry entry:CACHE.values())entry.close();CACHE.clear();lastBuildTick=-1;}
    @Override public int getViewDistance(){return 96;}
    @Override public boolean shouldRender(HololithTableEntity table,Vec3 camera){return true;}
    @Override public AABB getRenderBoundingBox(HololithTableEntity table){return new AABB(-1.4,0,-1.4,2.4,3.2,2.4).move(table.getBlockPos());}
    private static void decorations(PoseStack.Pose pose,VertexConsumer out,float time){
        float r=SurveyMesh.WIDTH/2f,w=.003f,y=-.008f;
        for(int i=0;i<=8;i++){float p=-r+2*r*i/8f;int c=i==0||i==8?0x9060E5EE:0x263CBBCD;flat(pose,out,-r,p-w,r,p+w,y,c);flat(pose,out,p-w,-r,p+w,r,y,c);}
        float sweep=-r+(time%100)/100f*2*r;flat(pose,out,-r,sweep-.008f,r,sweep+.008f,y+.003f,0x353AE4FF);
        flat(pose,out,-r,-r,r,r,.02f+(time%160)/160f*.72f,0x103AE4FF);
        for(int sx:new int[]{-1,1})for(int sz:new int[]{-1,1}){float x=sx*r,z=sz*r;quad(pose,out,0xA063DCEB,x-w,-.32f,z,x+w,-.32f,z,x+w,.18f,z,x-w,.18f,z);quad(pose,out,0xA063DCEB,x,-.32f,z-w,x,-.32f,z+w,x,.18f,z+w,x,.18f,z-w);}
    }
    private static void markers(HololithTableEntity table,SurveyGeometry geometry,PoseStack.Pose pose,MultiBufferSource buffers,float intensity){
        List<Pin> pins=new ArrayList<>(Math.min(64,table.markers().size()));
        int visited=0;
        for(var marker:table.markers()){
            if(visited++>=64)break;
            var position=geometry.position(marker.x(),marker.z());if(position.isEmpty())continue;
            var point=position.get();pins.add(new Pin((float)point.x(),(float)point.y()+.006f,(float)point.z(),marker.color()&0xFFFFFF));
        }
        if(pins.isEmpty())return;
        VertexConsumer out=buffers.getBuffer(HololithRenderTypes.LIGHT);
        for(Pin pin:pins){
            float x=pin.x,y=pin.y,z=pin.z,tip=y+.070f,w=.0035f;
            int stem=brightness(0xC0000000|pin.color,intensity),cap=brightness(0xE8000000|pin.color,intensity);
            quad(pose,out,stem,x-w,y,z,x+w,y,z,x+w,tip,z,x-w,tip,z);
            quad(pose,out,stem,x,y,z-w,x,y,z+w,x,tip,z+w,x,tip,z-w);
            box(pose,out,x-.025f,tip,z-.025f,x+.025f,tip+.035f,z+.025f,cap);
            box(pose,out,x-.007f,tip+.033f,z-.007f,x+.007f,tip+.047f,z+.007f,brightness(0xF0FFFFFF,intensity));
        }
        out=buffers.getBuffer(HololithRenderTypes.GLOW);
        for(Pin pin:pins){
            float x=pin.x,z=pin.z,outer=.055f,inner=.044f,y=pin.y;int color=brightness(0x38000000|pin.color,intensity);
            flat(pose,out,x-outer,z-outer,x+outer,z-inner,y,color);flat(pose,out,x-outer,z+inner,x+outer,z+outer,y,color);
            flat(pose,out,x-outer,z-inner,x-inner,z+inner,y,color);flat(pose,out,x+inner,z-inner,x+outer,z+inner,y,color);
        }
    }
    private static void box(PoseStack.Pose p,VertexConsumer out,float x0,float y0,float z0,float x1,float y1,float z1,int color){
        flat(p,out,x0,z0,x1,z1,y1,color);flat(p,out,x0,z0,x1,z1,y0,color);
        quad(p,out,color,x0,y0,z0,x0,y0,z1,x0,y1,z1,x0,y1,z0);quad(p,out,color,x1,y0,z0,x1,y0,z1,x1,y1,z1,x1,y1,z0);
        quad(p,out,color,x0,y0,z0,x1,y0,z0,x1,y1,z0,x0,y1,z0);quad(p,out,color,x0,y0,z1,x1,y0,z1,x1,y1,z1,x0,y1,z1);
    }
    private static int brightness(int color,float intensity){
        int r=Math.min(255,Math.round((color>>16&255)*intensity)),g=Math.min(255,Math.round((color>>8&255)*intensity)),b=Math.min(255,Math.round((color&255)*intensity));return color&0xFF000000|r<<16|g<<8|b;
    }
    private record Pin(float x,float y,float z,int color){}
    private static void flat(PoseStack.Pose p,VertexConsumer o,float x0,float z0,float x1,float z1,float y,int c){quad(p,o,c,x0,y,z0,x0,y,z1,x1,y,z1,x1,y,z0);}
    private static void quad(PoseStack.Pose p,VertexConsumer o,int c,float x0,float y0,float z0,float x1,float y1,float z1,float x2,float y2,float z2,float x3,float y3,float z3){
        o.addVertex(p,x0,y0,z0).setColor(c).setLight(LightTexture.FULL_BRIGHT);o.addVertex(p,x1,y1,z1).setColor(c).setLight(LightTexture.FULL_BRIGHT);o.addVertex(p,x2,y2,z2).setColor(c).setLight(LightTexture.FULL_BRIGHT);o.addVertex(p,x3,y3,z3).setColor(c).setLight(LightTexture.FULL_BRIGHT);
    }
    private static boolean isShadowPass(){
        if(!shaderChecked){shaderChecked=true;try{Class<?> api=Class.forName("net.irisshaders.iris.api.v0.IrisApi");iris=api.getMethod("getInstance").invoke(null);shadowPass=api.getMethod("isRenderingShadowPass");}catch(ReflectiveOperationException|LinkageError unavailable){iris=null;shadowPass=null;}}
        if(iris==null||shadowPass==null)return false;try{return (Boolean)shadowPass.invoke(iris);}catch(ReflectiveOperationException ignored){return false;}
    }
    private static final class Entry {SurveySnapshot snapshot;SurveyMesh mesh;long lastUsed,lastAttempt=-100;void close(){if(mesh!=null)mesh.close();mesh=null;snapshot=null;}}
}
