package dev.hololith.client;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.hololith.survey.SurveyGeometry;
import com.mojang.blaze3d.vertex.*;
import dev.hololith.survey.SurveySnapshot;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Matrix4f;
/** At most 64x64 displayed cells, ten faces per cell, immutable GPU mesh. */
final class SurveyMesh implements AutoCloseable {
    static final float WIDTH=2.3f;
    private static final int MAX_EDGE_QUADS=4096;
    private final ProjectionBuffer ground,detail,edges;
    private final SurveyGeometry geometry;
    private SurveyMesh(ProjectionBuffer ground,ProjectionBuffer detail,ProjectionBuffer edges,SurveyGeometry geometry){this.ground=ground;this.detail=detail;this.edges=edges;this.geometry=geometry;}
    SurveyGeometry geometry(){return geometry;}
    static SurveyMesh build(SurveySnapshot s){
        if(s.size<=0||s.size>128||s.span<=0)return new SurveyMesh(null,null,null,null);
        SurveyGeometry geometry=SurveyGeometry.at(s);
        int stride=Math.max(1,(s.size+63)/64),n=(s.size+stride-1)/stride,minY=Integer.MAX_VALUE,maxY=Integer.MIN_VALUE;
        int[] cells=new int[n*n];
        for(int z=0;z<n;z++)for(int x=0;x<n;x++){
            int selected=-1;
            for(int dz=0;dz<stride&&z*stride+dz<s.size;dz++)for(int dx=0;dx<stride&&x*stride+dx<s.size;dx++){
                int i=(z*stride+dz)*s.size+x*stride+dx;if((s.flags[i]&1)==0)continue;
                minY=Math.min(minY,s.groundY[i]);maxY=Math.max(maxY,Math.max(s.groundY[i],s.surfaceY[i])+1);if(selected<0||s.surfaceY[i]>s.surfaceY[selected])selected=i;
            }
            cells[z*n+x]=selected;
        }
        if(minY==Integer.MAX_VALUE)return new SurveyMesh(null,null,null,geometry);
        // Compress the whole vertical range together: tall mountains keep their relief,
        // instead of clipping every summit against a flat projection ceiling.
        float verticalStep=geometry.step();
        float[] heights=new float[cells.length],tops=new float[cells.length];
        for(int i=0;i<cells.length;i++)if(cells[i]>=0){
            int source=cells[i];heights[i]=geometry.height(s.groundY[source]+1);tops[i]=heights[i];
            if((s.flags[source]&6)!=0&&s.surfaceY[source]>=s.groundY[source])tops[i]=Math.max(heights[i]+.003f,geometry.height(s.surfaceY[source]+1));
        }
        Map<BlockState,Material> materials=new HashMap<>();
        try(ByteBufferBuilder gb=new ByteBufferBuilder(262144);ByteBufferBuilder db=new ByteBufferBuilder(131072)){
            BufferBuilder g=new BufferBuilder(gb,VertexFormat.Mode.QUADS,DefaultVertexFormat.NEW_ENTITY),d=new BufferBuilder(db,VertexFormat.Mode.QUADS,DefaultVertexFormat.NEW_ENTITY);
            for(int z=0;z<n;z++)for(int x=0;x<n;x++){
                int cell=z*n+x,i=cells[cell];if(i<0)continue;
                float x0=boundary(x,stride,s.size),x1=boundary(x+1,stride,s.size),z0=boundary(z,stride,s.size),z1=boundary(z+1,stride,s.size),y=heights[cell];
                Material m=materials.computeIfAbsent(s.groundStates[i],SurveyMesh::material);int c=tint(m.tinted?s.groundColors[i]:0xFFFFFF,185);
                top(g,m.sprite,x0,z0,x1,z1,y,c);
                sideX(g,m.sprite,x0,z0,z1,neighbor(heights,cells,x-1,z,n),y,shade(c,.74f),-1);
                sideX(g,m.sprite,x1,z0,z1,neighbor(heights,cells,x+1,z,n),y,shade(c,.86f),1);
                sideZ(g,m.sprite,x0,x1,z0,neighbor(heights,cells,x,z-1,n),y,shade(c,.80f),-1);
                sideZ(g,m.sprite,x0,x1,z1,neighbor(heights,cells,x,z+1,n),y,shade(c,.92f),1);
                if((s.flags[i]&6)!=0&&s.surfaceY[i]>=s.groundY[i]){
                    boolean water=(s.flags[i]&2)!=0;float sy=tops[cell];
                    Material cover=water?waterMaterial():materials.computeIfAbsent(s.surfaceStates[i],SurveyMesh::material);int cc=tint(water||cover.tinted?s.surfaceColors[i]:0xFFFFFF,water?145:175);
                    top(d,cover.sprite,x0,z0,x1,z1,sy,cc);
                    float lower=water?Math.max(y,sy-.025f):Math.max(y,sy-Math.max(.025f,verticalStep*3));
                    sideX(d,cover.sprite,x0,z0,z1,lower,sy,shade(cc,.78f),-1);sideX(d,cover.sprite,x1,z0,z1,lower,sy,shade(cc,.90f),1);
                    sideZ(d,cover.sprite,x0,x1,z0,lower,sy,shade(cc,.82f),-1);sideZ(d,cover.sprite,x0,x1,z1,lower,sy,shade(cc,.95f),1);
                }
            }
            ProjectionBuffer terrain=null,cover=null,edgeMesh=null;
            try{
                terrain=ProjectionBuffer.create(g.build());cover=ProjectionBuffer.create(d.build());
                edgeMesh=buildEdges(tops,cells,n,stride,s.size,Math.max(.012f,verticalStep*.85f));
                return new SurveyMesh(terrain,cover,edgeMesh,geometry);
            }catch(RuntimeException|Error failure){if(terrain!=null)terrain.close();if(cover!=null)cover.close();if(edgeMesh!=null)edgeMesh.close();throw failure;}
        }
    }
    private static float boundary(int c,int stride,int size){return -WIDTH/2+WIDTH*Math.min(size,c*stride)/size;}
    private static ProjectionBuffer buildEdges(float[] tops,int[] cells,int n,int stride,int size,float threshold){
        int candidates=0;
        for(int z=0;z<n;z++)for(int x=0;x<n;x++)if(cells[z*n+x]>=0)
            for(int side=0;side<4;side++)if(exposed(tops,cells,x,z,n,side,threshold))candidates++;
        if(candidates==0)return null;
        int every=Math.max(1,(candidates+MAX_EDGE_QUADS-1)/MAX_EDGE_QUADS),seen=0,emitted=0;
        float width=Math.min(.0024f,WIDTH/n*.07f);
        try(ByteBufferBuilder bytes=new ByteBufferBuilder(65536)){
            BufferBuilder out=new BufferBuilder(bytes,VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR_LIGHTMAP);
            for(int z=0;z<n;z++)for(int x=0;x<n;x++)if(cells[z*n+x]>=0){
                float x0=boundary(x,stride,size),x1=boundary(x+1,stride,size),z0=boundary(z,stride,size),z1=boundary(z+1,stride,size),y=tops[z*n+x]+.0018f;
                for(int side=0;side<4;side++)if(exposed(tops,cells,x,z,n,side,threshold)){
                    if(seen++%every!=0||emitted>=MAX_EDGE_QUADS)continue;emitted++;
                    switch(side){case 0->edge(out,x0,z0,x0+width,z1,y);case 1->edge(out,x1-width,z0,x1,z1,y);case 2->edge(out,x0,z0,x1,z0+width,y);case 3->edge(out,x0,z1-width,x1,z1,y);}
                }
            }return ProjectionBuffer.create(out.build());
        }
    }
    private static boolean exposed(float[] tops,int[] cells,int x,int z,int n,int side,float threshold){
        int nx=x+(side==0?-1:side==1?1:0),nz=z+(side==2?-1:side==3?1:0);
        return tops[z*n+x]-neighbor(tops,cells,nx,nz,n)>threshold;
    }
    private static void edge(VertexConsumer out,float x0,float z0,float x1,float z1,float y){
        int color=0x4055C9DD;
        out.addVertex(x0,y,z0).setColor(color).setLight(LightTexture.FULL_BRIGHT);
        out.addVertex(x0,y,z1).setColor(color).setLight(LightTexture.FULL_BRIGHT);
        out.addVertex(x1,y,z1).setColor(color).setLight(LightTexture.FULL_BRIGHT);
        out.addVertex(x1,y,z0).setColor(color).setLight(LightTexture.FULL_BRIGHT);
    }
    private static float neighbor(float[] h,int[] c,int x,int z,int n){return x<0||z<0||x>=n||z>=n||c[z*n+x]<0?0:h[z*n+x];}
    private static Material material(BlockState state){
        if(state==null||state.isAir())state=Blocks.STONE.defaultBlockState();BakedModel model=Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
        try{List<BakedQuad> faces=model.getQuads(state,Direction.UP,RandomSource.create(42),ModelData.EMPTY,null);if(!faces.isEmpty())return new Material(faces.getFirst().getSprite(),faces.getFirst().isTinted());}
        catch(RuntimeException ignored){/* World-dependent models fall back to particle texture. */}
        return new Material(model.getParticleIcon(ModelData.EMPTY),state.is(net.minecraft.tags.BlockTags.LEAVES));
    }
    private static Material waterMaterial(){return new Material(Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(ResourceLocation.withDefaultNamespace("block/water_still")),true);}
    private static int tint(int rgb,int alpha){
        int r=Math.min(255,(int)((rgb>>16&255)*.60f+30)),g=Math.min(255,(int)((rgb>>8&255)*.68f+92)),b=Math.min(255,(int)((rgb&255)*.65f+145));return alpha<<24|r<<16|g<<8|b;
    }
    private static int shade(int c,float s){return c&0xFF000000|(int)((c>>16&255)*s)<<16|(int)((c>>8&255)*s)<<8|(int)((c&255)*s);}
    private static void top(VertexConsumer o,TextureAtlasSprite t,float x0,float z0,float x1,float z1,float y,int c){quad(o,t,c,0,1,0,x0,y,z0,x0,y,z1,x1,y,z1,x1,y,z0);}
    private static void sideX(VertexConsumer o,TextureAtlasSprite t,float x,float z0,float z1,float lo,float hi,int c,int normal){if(lo<hi-.001f)quad(o,t,c,normal,0,0,x,lo,z0,x,lo,z1,x,hi,z1,x,hi,z0);}
    private static void sideZ(VertexConsumer o,TextureAtlasSprite t,float x0,float x1,float z,float lo,float hi,int c,int normal){if(lo<hi-.001f)quad(o,t,c,0,0,normal,x0,lo,z,x1,lo,z,x1,hi,z,x0,hi,z);}
    private static void quad(VertexConsumer o,TextureAtlasSprite t,int c,float nx,float ny,float nz,float x0,float y0,float z0,float x1,float y1,float z1,float x2,float y2,float z2,float x3,float y3,float z3){
        float u0=t.getU(.002f),u1=t.getU(.998f),v0=t.getV(.002f),v1=t.getV(.998f);
        vertex(o,x0,y0,z0,u0,v0,c,nx,ny,nz);vertex(o,x1,y1,z1,u0,v1,c,nx,ny,nz);vertex(o,x2,y2,z2,u1,v1,c,nx,ny,nz);vertex(o,x3,y3,z3,u1,v0,c,nx,ny,nz);
    }
    private static void vertex(VertexConsumer o,float x,float y,float z,float u,float v,int c,float nx,float ny,float nz){o.addVertex(x,y,z).setColor(c).setUv(u,v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(nx,ny,nz);}
    void draw(Matrix4f transform,float intensity){
        float[] previous=RenderSystem.getShaderColor().clone();
        try{
            RenderSystem.setShaderColor(previous[0]*intensity,previous[1]*intensity,previous[2]*intensity,previous[3]);
            if(ground!=null)ground.render(HololithRenderTypes.TERRAIN,transform);
            if(detail!=null)detail.render(HololithRenderTypes.TERRAIN,transform);
            if(edges!=null)edges.render(HololithRenderTypes.GLOW,transform);
        }finally{RenderSystem.setShaderColor(previous[0],previous[1],previous[2],previous[3]);}
    }
    @Override public void close(){if(ground!=null)ground.close();if(detail!=null)detail.close();if(edges!=null)edges.close();}
    private record Material(TextureAtlasSprite sprite,boolean tinted){}
}
