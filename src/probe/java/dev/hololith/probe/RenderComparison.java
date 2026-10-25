package dev.hololith.probe;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexSorting;
import dev.hololith.survey.SurveySnapshot;
import java.lang.reflect.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.BiConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;

/** Private validation only. An external baseline jar is never copied into an artifact. */
public final class RenderComparison {
    private static final int SIZE=512, WARMUP=16, SAMPLES=21, BATCH=8;
    private static final String PREFIX="dev.hololith.client.";
    private RenderComparison(){}

    public static void run(SurveySnapshot snapshot,BiConsumer<String,Boolean> check){
        String configured=System.getProperty("hololith.baselineJar");
        if(configured==null||configured.isBlank())return;
        var report=new LinkedHashMap<String,Object>();
        var checks=new ArrayList<Map<String,Object>>();report.put("checks",checks);
        BiConsumer<String,Boolean> verify=(name,passed)->{checks.add(Map.of("test",name,"passed",passed));check.accept("render comparison "+snapshot.span+": "+name,passed);};
        report.put("span",snapshot.span);report.put("size",snapshot.size);report.put("revision",snapshot.revision);
        report.put("framebuffer",Map.of("width",SIZE,"height",SIZE,"format","RGBA8 + depth"));
        report.put("allocationEvidence","Buffer IDs, storage sizes, index counts and content hashes are measured before/after draws. Absence of redundant upload calls requires separate source review; this probe does not trace GL calls.");
        SavedState saved=null;TextureTarget target=null;
        try{
            RenderSystem.assertOnRenderThread();
            Path baseline=Path.of(configured).toAbsolutePath().normalize();
            if(!Files.isRegularFile(baseline))throw new IllegalArgumentException("Missing requested baseline: "+baseline);
            report.put("baseline",baseline.getFileName().toString());report.put("baselineSha256",hash(Files.readAllBytes(baseline)));
            saved=new SavedState();
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);
            target=new TextureTarget(SIZE,SIZE,true,Minecraft.ON_OSX);target.setClearColor(0,0,0,0);
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(-1.85f,1.85f,-1.85f,1.85f,.1f,20),VertexSorting.ORTHOGRAPHIC_Z);
            RenderSystem.getModelViewStack().identity();RenderSystem.applyModelViewMatrix();
            RenderSystem.resetTextureMatrix();RenderSystem.setShaderColor(1,1,1,1);
            RenderSystem.setShaderFogStart(100);RenderSystem.setShaderFogEnd(200);
            try(BaselineLoader loader=new BaselineLoader(baseline);
                Mesh old=Mesh.build(loader,snapshot);Mesh current=Mesh.build(RenderComparison.class.getClassLoader(),snapshot)){
                verify.accept("baseline mesh is loaded in an isolated class loader",old.value.getClass()!=current.value.getClass()&&old.value.getClass().getClassLoader()==loader);
                List<BufferState> oldBefore=buffers(old.value),newBefore=buffers(current.value);
                report.put("baselineBuffersBefore",oldBefore);report.put("currentBuffersBefore",newBefore);
                verify.accept("GPU buffer counts and storage layouts match",!oldBefore.isEmpty()&&shape(oldBefore).equals(shape(newBefore)));
                var cases=new ArrayList<Map<String,Object>>();report.put("pixelCases",cases);
                boolean exact=true,visible=true;
                for(int yaw:new int[]{0,90,180,270})for(float intensity:new float[]{.4f,1f,1.8f}){
                    Matrix4f view=view(yaw);byte[] a=frame(target,old,view,intensity),b=frame(target,current,view,intensity);
                    int different=0,shownA=0,shownB=0;
                    for(int p=0;p<a.length;p+=4){boolean mismatch=false;for(int c=0;c<4;c++)mismatch|=a[p+c]!=b[p+c];if(mismatch)different++;if(a[p+3]!=0)shownA++;if(b[p+3]!=0)shownB++;}
                    exact&=different==0;visible&=shownA>128&&shownB>128;
                    cases.add(Map.of("yaw",yaw,"intensity",intensity,"differentPixels",different,"baselineVisiblePixels",shownA,"currentVisiblePixels",shownB,"baselineSha256",hash(a),"currentSha256",hash(b)));
                }
                verify.accept("all 12 rotation/intensity frames match byte-for-byte",exact);
                verify.accept("both implementations draw visible nonempty terrain",visible);
                report.put("timing",benchmark(target,old,current));
                List<BufferState> oldAfter=buffers(old.value),newAfter=buffers(current.value);
                report.put("baselineBuffersAfter",oldAfter);report.put("currentBuffersAfter",newAfter);
                verify.accept("baseline GPU handles, sizes and contents stay unchanged across draws",oldBefore.equals(oldAfter));
                verify.accept("current GPU handles, sizes and contents stay unchanged across draws",newBefore.equals(newAfter));
            }
        }catch(Throwable failure){
            Throwable cause=failure instanceof InvocationTargetException invocation?invocation.getCause():failure;
            report.put("error",cause.toString());verify.accept("comparison completed without exception",false);
        }finally{
            try{if(target!=null)target.destroyBuffers();}catch(Throwable failure){report.put("targetCleanupError",failure.toString());verify.accept("offscreen target cleanup",false);}
            try{if(saved!=null)saved.restore();}catch(Throwable failure){report.put("stateRestoreError",failure.toString());verify.accept("render state restoration",false);}
            report.put("passed",checks.stream().allMatch(c->Boolean.TRUE.equals(c.get("passed"))));
            try{
                Path destination=Minecraft.getInstance().gameDirectory.toPath().resolve("validation/render-comparison-"+snapshot.span+".json");
                Files.createDirectories(destination.getParent());Files.writeString(destination,new GsonBuilder().setPrettyPrinting().create().toJson(report));
                System.out.println("HOLOLITH RENDER COMPARISON "+destination+" passed="+report.get("passed"));
            }catch(Exception failure){check.accept("render comparison report saved",false);System.err.println("Could not save render comparison: "+failure);}
        }
    }

    private static Matrix4f view(int yaw){return new Matrix4f().lookAt(3.6f,2.5f,3.6f,0,.30f,0,0,1,0).rotateY((float)Math.toRadians(yaw));}
    private static void prepare(TextureTarget target){
        RenderSystem.disableScissor();RenderSystem.colorMask(true,true,true,true);RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();RenderSystem.depthFunc(GL11.GL_LEQUAL);RenderSystem.disableCull();
        GL11.glDisable(GL30.GL_FRAMEBUFFER_SRGB);GL11.glDisable(GL11.GL_DITHER);
        GL20.glBlendEquationSeparate(GL14.GL_FUNC_ADD,GL14.GL_FUNC_ADD);
        target.clear(Minecraft.ON_OSX);target.bindWrite(true);
        RenderSystem.setShaderColor(1,1,1,1);
    }
    private static byte[] frame(TextureTarget target,Mesh mesh,Matrix4f transform,float intensity)throws Exception{
        prepare(target);mesh.draw(transform,intensity);
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,0);GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT,1);
        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
        ByteBuffer pixels=MemoryUtil.memAlloc(SIZE*SIZE*4);
        try{GL11.glReadPixels(0,0,SIZE,SIZE,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);byte[] bytes=new byte[pixels.capacity()];pixels.get(bytes);return bytes;}finally{MemoryUtil.memFree(pixels);}
    }
    private static Map<String,Object> benchmark(TextureTarget target,Mesh old,Mesh current)throws Exception{
        Matrix4f transform=view(37);
        for(int i=0;i<WARMUP;i++){prepare(target);old.draw(transform,1);prepare(target);current.draw(transform,1);}
        GL11.glFinish();
        boolean timer=(GL.getCapabilities().OpenGL33||GL.getCapabilities().GL_ARB_timer_query)&&GL15.glGetQueryi(GL33.GL_TIME_ELAPSED,GL15.GL_CURRENT_QUERY)==0;
        int query=timer?GL15.glGenQueries():0;
        double[] oldCpu=new double[SAMPLES],newCpu=new double[SAMPLES],oldGpu=new double[SAMPLES],newGpu=new double[SAMPLES];
        try{
            for(int sample=0;sample<SAMPLES;sample++)for(int turn=0;turn<2;turn++){
                boolean baseline=((sample+turn)&1)==0;
                Mesh mesh=baseline?old:current;prepare(target);GL11.glFinish();
                if(timer)GL15.glBeginQuery(GL33.GL_TIME_ELAPSED,query);
                long start=System.nanoTime(),cpu;
                try{for(int n=0;n<BATCH;n++)mesh.draw(transform,1);cpu=System.nanoTime()-start;}
                finally{if(timer)GL15.glEndQuery(GL33.GL_TIME_ELAPSED);}
                GL11.glFinish();long gpu=timer?GL33.glGetQueryObjectui64(query,GL15.GL_QUERY_RESULT):0;
                (baseline?oldCpu:newCpu)[sample]=cpu/(double)BATCH;
                (baseline?oldGpu:newGpu)[sample]=gpu/(double)BATCH;
            }
        }finally{if(query!=0)GL15.glDeleteQueries(query);}
        var result=new LinkedHashMap<String,Object>();result.put("pairedSamples",SAMPLES);result.put("drawsPerSample",BATCH);result.put("warmupDrawsPerImplementation",WARMUP);
        result.put("cpuIncludesReflection",true);result.put("gpuTimerAvailable",timer);
        result.put("baselineCpuMedianNsPerDraw",median(oldCpu));result.put("currentCpuMedianNsPerDraw",median(newCpu));result.put("cpuMedianRatioCurrentToBaseline",median(newCpu)/median(oldCpu));
        if(timer){result.put("baselineGpuMedianNsPerDraw",median(oldGpu));result.put("currentGpuMedianNsPerDraw",median(newGpu));result.put("gpuMedianRatioCurrentToBaseline",median(newGpu)/median(oldGpu));}
        result.put("interpretation","Alternating warmed batches on one context and target. Observational medians, not a hard performance gate; includes full mesh draw and render-state setup/teardown.");return result;
    }
    private static double median(double[] values){double[] copy=values.clone();Arrays.sort(copy);return copy[copy.length/2];}
    private static String hash(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    private static Field field(Class<?> owner,String name)throws Exception{Field f=owner.getDeclaredField(name);f.setAccessible(true);return f;}
    private static int number(Object owner,String name)throws Exception{return field(owner.getClass(),name).getInt(owner);}

    private static List<BufferState> buffers(Object mesh)throws Exception{
        var result=new ArrayList<BufferState>();collect(mesh,"mesh",0,new IdentityHashMap<>(),result);return List.copyOf(result);
    }
    private static void collect(Object value,String path,int depth,IdentityHashMap<Object,Boolean> seen,List<BufferState> result)throws Exception{
        if(value==null||seen.put(value,true)!=null||depth>5)return;
        if(value instanceof VertexBuffer buffer){
            int vertex=number(buffer,"vertexBufferId"),ownIndex=number(buffer,"indexBufferId"),vao=number(buffer,"arrayObjectId");
            Object sequential=field(VertexBuffer.class,"sequentialIndices").get(buffer);int activeIndex=sequential==null?ownIndex:number(sequential,"name");
            Storage vertices=storage(vertex),ownIndices=storage(ownIndex),indices=storage(activeIndex);
            String[] parts=path.split("\\.");String role=parts.length>1?parts[1]:path;
            result.add(new BufferState(role,vertex,ownIndex,activeIndex,vao,vertices.bytes,ownIndices.bytes,indices.bytes,number(buffer,"indexCount"),String.valueOf(buffer.getFormat()),String.valueOf(field(VertexBuffer.class,"mode").get(buffer)),vertices.sha256,indices.sha256));return;
        }
        if(!value.getClass().getName().startsWith(PREFIX))return;
        Field[] fields=value.getClass().getDeclaredFields();Arrays.sort(fields,Comparator.comparing(Field::getName));
        for(Field f:fields)if(!Modifier.isStatic(f.getModifiers())&&!f.getType().isPrimitive()){f.setAccessible(true);collect(f.get(value),path+"."+f.getName(),depth+1,seen,result);}
    }
    private static Storage storage(int id)throws Exception{
        if(id<=0||!GL15.glIsBuffer(id))return new Storage(0,"");
        int previous=GL11.glGetInteger(GL31.GL_COPY_READ_BUFFER);
        try{GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER,id);int size=GL15.glGetBufferParameteri(GL31.GL_COPY_READ_BUFFER,GL15.GL_BUFFER_SIZE);
            if(size<0||size>64*1024*1024)throw new IllegalStateException("Unexpected validation buffer size: "+size);
            ByteBuffer bytes=MemoryUtil.memAlloc(Math.max(1,size));try{if(size>0){bytes.limit(size);GL15.glGetBufferSubData(GL31.GL_COPY_READ_BUFFER,0,bytes);}byte[] array=new byte[size];bytes.get(array);return new Storage(size,hash(array));}finally{MemoryUtil.memFree(bytes);}
        }finally{GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER,previous);}
    }
    private static List<String> shape(List<BufferState> buffers){return buffers.stream().map(b->b.role+":"+b.vertexBytes+":"+b.ownIndexBytes+":"+b.activeIndexBytes+":"+b.indexCount+":"+b.format+":"+b.mode).toList();}
    private record Storage(int bytes,String sha256){}
    private record BufferState(String role,int vertexId,int ownIndexId,int activeIndexId,int vao,int vertexBytes,int ownIndexBytes,int activeIndexBytes,int indexCount,String format,String mode,String vertexSha256,String indexSha256){}

    private static final class Mesh implements AutoCloseable {
        final Object value;final Method draw;
        private Mesh(Object value,Method draw){this.value=value;this.draw=draw;}
        static Mesh build(ClassLoader loader,SurveySnapshot snapshot)throws Exception{
            Class<?> type=Class.forName(PREFIX+"SurveyMesh",true,loader);
            Method build=type.getDeclaredMethod("build",SurveySnapshot.class);build.setAccessible(true);
            Method draw=type.getDeclaredMethod("draw",Matrix4f.class,float.class);draw.setAccessible(true);
            return new Mesh(build.invoke(null,snapshot),draw);
        }
        void draw(Matrix4f transform,float intensity)throws Exception{draw.invoke(value,transform,intensity);}
        @Override public void close()throws Exception{((AutoCloseable)value).close();}
    }
    private static final class BaselineLoader extends URLClassLoader {
        BaselineLoader(Path jar)throws MalformedURLException{super(new URL[]{jar.toUri().toURL()},RenderComparison.class.getClassLoader());}
        private static boolean isolated(String name){return Arrays.stream(new String[]{"SurveyMesh","HololithGpuMesh","HololithRenderTypes"}).anyMatch(n->name.equals(PREFIX+n)||name.startsWith(PREFIX+n+"$"));}
        @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException{
            if(!isolated(name))return super.loadClass(name,resolve);
            synchronized(getClassLoadingLock(name)){Class<?> type=findLoadedClass(name);if(type==null)type=findClass(name);if(resolve)resolveClass(type);return type;}
        }
    }
    private static int[] integers(int name,int size){int[] result=new int[size];GL11.glGetIntegerv(name,result);return result;}
    private static float[] floats(int name,int size){float[] result=new float[size];GL11.glGetFloatv(name,result);return result;}
    private static void enabled(int flag,boolean value){if(value)GL11.glEnable(flag);else GL11.glDisable(flag);}
    private static final class SavedState {
        final Matrix4f projection=new Matrix4f(RenderSystem.getProjectionMatrix()),stack=new Matrix4f(RenderSystem.getModelViewStack()),applied=new Matrix4f(RenderSystem.getModelViewMatrix()),texture=new Matrix4f(RenderSystem.getTextureMatrix());
        final VertexSorting sorting=RenderSystem.getVertexSorting();
        final ShaderInstance shader=RenderSystem.getShader();
        final float[] shaderColor=RenderSystem.getShaderColor().clone(),clearColor=floats(GL11.GL_COLOR_CLEAR_VALUE,4);
        final float fogStart=RenderSystem.getShaderFogStart(),fogEnd=RenderSystem.getShaderFogEnd();
        final int drawFbo=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),readFbo=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        final int[] viewport=integers(GL11.GL_VIEWPORT,4),scissorBox=integers(GL11.GL_SCISSOR_BOX,4),colorMask=integers(GL11.GL_COLOR_WRITEMASK,4);
        final boolean depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST),blend=GL11.glIsEnabled(GL11.GL_BLEND),cull=GL11.glIsEnabled(GL11.GL_CULL_FACE),scissor=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST),srgb=GL11.glIsEnabled(GL30.GL_FRAMEBUFFER_SRGB),dither=GL11.glIsEnabled(GL11.GL_DITHER),depthMask=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        final int depthFunc=GL11.glGetInteger(GL11.GL_DEPTH_FUNC),srcRgb=GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),dstRgb=GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),srcAlpha=GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),dstAlpha=GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA),equationRgb=GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB),equationAlpha=GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);
        final int activeTexture=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE),program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),vao=GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING),arrayBuffer=GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING),packBuffer=GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        final int packAlignment=GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT),packRowLength=GL11.glGetInteger(GL11.GL_PACK_ROW_LENGTH),packSkipRows=GL11.glGetInteger(GL11.GL_PACK_SKIP_ROWS),packSkipPixels=GL11.glGetInteger(GL11.GL_PACK_SKIP_PIXELS);
        final double clearDepth=GL11.glGetDouble(GL11.GL_DEPTH_CLEAR_VALUE);
        final int[] shaderTextures=new int[12],boundTextures=new int[12];
        SavedState(){
            for(int i=0;i<12;i++){shaderTextures[i]=RenderSystem.getShaderTexture(i);RenderSystem.activeTexture(GL13.GL_TEXTURE0+i);boundTextures[i]=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);}
            RenderSystem.activeTexture(activeTexture);
            GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH,0);GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS,0);GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS,0);
        }
        void restore(){
            RenderSystem.setProjectionMatrix(projection,sorting);RenderSystem.getModelViewStack().set(stack);RenderSystem.applyModelViewMatrix();RenderSystem.getModelViewMatrix().set(applied);RenderSystem.setTextureMatrix(texture);
            RenderSystem.setShader(()->shader);RenderSystem.setShaderColor(shaderColor[0],shaderColor[1],shaderColor[2],shaderColor[3]);RenderSystem.setShaderFogStart(fogStart);RenderSystem.setShaderFogEnd(fogEnd);
            for(int i=0;i<12;i++){RenderSystem.setShaderTexture(i,shaderTextures[i]);RenderSystem.activeTexture(GL13.GL_TEXTURE0+i);RenderSystem.bindTexture(boundTextures[i]);}
            RenderSystem.activeTexture(activeTexture);
            if(depth)RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();
            if(blend)RenderSystem.enableBlend();else RenderSystem.disableBlend();
            if(cull)RenderSystem.enableCull();else RenderSystem.disableCull();
            if(scissor)RenderSystem.enableScissor(scissorBox[0],scissorBox[1],scissorBox[2],scissorBox[3]);else RenderSystem.disableScissor();
            enabled(GL30.GL_FRAMEBUFFER_SRGB,srgb);enabled(GL11.GL_DITHER,dither);
            RenderSystem.depthMask(depthMask);RenderSystem.depthFunc(depthFunc);RenderSystem.colorMask(colorMask[0]!=0,colorMask[1]!=0,colorMask[2]!=0,colorMask[3]!=0);
            RenderSystem.blendFuncSeparate(srcRgb,dstRgb,srcAlpha,dstAlpha);GL20.glBlendEquationSeparate(equationRgb,equationAlpha);
            RenderSystem.clearColor(clearColor[0],clearColor[1],clearColor[2],clearColor[3]);RenderSystem.clearDepth(clearDepth);
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,packBuffer);GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT,packAlignment);GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH,packRowLength);GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS,packSkipRows);GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS,packSkipPixels);
            VertexBuffer.unbind();RenderSystem.glBindVertexArray(vao);RenderSystem.glBindBuffer(GL15.GL_ARRAY_BUFFER,arrayBuffer);GlStateManager._glUseProgram(program);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,drawFbo);GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,readFbo);RenderSystem.viewport(viewport[0],viewport[1],viewport[2],viewport[3]);
        }
    }
}
